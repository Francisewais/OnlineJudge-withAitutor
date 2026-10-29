"""编译源码、逐个运行测试点并比对输出。

目录与权限：
    /judger/run/<id>/        root 0711，其他用户无法列出目录
    /judger/run/<id>/src/    compiler 用户所有，存放源码和编译产物，运行用户只能读
    /judger/run/<id>/data/   root 0700，存放输入、期望输出、程序输出和编译日志
输入输出文件由沙箱在切换用户前以 root 打开，用户程序拿到的只是文件描述符，读不到其他测试数据。
"""
import os
import pwd
import shutil
import signal
import threading
import uuid

from . import sandbox
from .languages import LANGUAGES, Language

WORK_ROOT = "/judger/run"
MB = 1024 * 1024

COMPILER = pwd.getpwnam("compiler")
RUNNER = pwd.getpwnam("code")
ENV = ["PATH=/usr/bin:/bin", "LANG=C.UTF-8", "LC_ALL=C.UTF-8", "HOME=/tmp"]

COMPILE_CPU_MS = 10_000
COMPILE_REAL_MS = 20_000
COMPILE_MEMORY = 1024 * MB
OUTPUT_LIMIT = 32 * MB
PROCESS_LIMIT = 128

COMPILE_MESSAGE_LIMIT = 4096
CASE_MESSAGE_LIMIT = 1024
RUN_STDOUT_LIMIT = 64 * 1024
RUN_STDERR_LIMIT = 16 * 1024

# 同时进行的评测数量
_slots = threading.BoundedSemaphore(int(os.environ.get("JUDGE_CONCURRENCY", "4")))

SIGNAL_HINTS = {
    signal.SIGSEGV: "段错误（Segmentation fault），可能是数组越界、空指针或递归太深",
    signal.SIGFPE: "算术异常（Floating point exception），可能是除以零",
    signal.SIGABRT: "程序异常终止（Aborted）",
    signal.SIGSYS: "使用了被禁止的系统调用（如创建进程、写文件、联网）",
    signal.SIGKILL: "程序被强制结束",
}
OOM_MARKERS = ("std::bad_alloc", "MemoryError", "java.lang.OutOfMemoryError")


class CompileError(Exception):
    pass


class Workspace:
    def __init__(self, language: Language):
        self.language = language
        self.root = os.path.join(WORK_ROOT, uuid.uuid4().hex)
        self.src_dir = os.path.join(self.root, "src")
        self.data_dir = os.path.join(self.root, "data")
        self.src = os.path.join(self.src_dir, language.source_name)
        self.exe = os.path.join(self.src_dir, "main")

    def __enter__(self):
        for path, mode in ((self.root, 0o711), (self.src_dir, 0o755), (self.data_dir, 0o700)):
            os.mkdir(path)
            os.chmod(path, mode)
        os.chown(self.src_dir, COMPILER.pw_uid, COMPILER.pw_gid)
        return self

    def __exit__(self, *exc):
        shutil.rmtree(self.root, ignore_errors=True)

    def data(self, name: str) -> str:
        return os.path.join(self.data_dir, name)

    def fill(self, parts: list[str], memory_mb: int = 0) -> list[str]:
        return [p.format(src=self.src, exe=self.exe, dir=self.src_dir, mem=memory_mb) for p in parts]

    def clean_message(self, text: str) -> str:
        """去掉编译信息里的临时目录前缀，只留下 main.cpp:3:5 这样的位置。"""
        return text.replace(self.src_dir + "/", "")


def read_text(path: str, limit: int) -> str:
    try:
        with open(path, "rb") as f:
            data = f.read(limit + 1)
    except FileNotFoundError:
        return ""
    text = data[:limit].decode("utf-8", errors="replace")
    return text + "\n...（内容过长，已截断）" if len(data) > limit else text


def same_output(expected: bytes, actual: bytes) -> bool:
    """忽略每行行尾空白和末尾空行后逐行比较，兼容 \\r\\n 换行。"""
    def lines(b: bytes) -> list[bytes]:
        return [line.rstrip() for line in b.replace(b"\r\n", b"\n").rstrip().split(b"\n")]
    return lines(expected) == lines(actual)


def compile_source(ws: Workspace, source: str) -> None:
    with open(ws.src, "w", encoding="utf-8") as f:
        f.write(source)
    lang = ws.language
    cmd = ws.fill(lang.compile)
    log = ws.data("compile.log")
    r = sandbox.run(exe_path=cmd[0], args=cmd[1:], env=ENV, uid=COMPILER.pw_uid, gid=COMPILER.pw_gid,
                    max_cpu_time=COMPILE_CPU_MS, max_real_time=COMPILE_REAL_MS,
                    max_memory=COMPILE_MEMORY, max_stack=128 * MB,
                    max_process_number=PROCESS_LIMIT, max_output_size=128 * MB,
                    output_path=log, error_path=log,
                    memory_limit_check_only=lang.memory_limit_check_only)
    message = ws.clean_message(read_text(log, COMPILE_MESSAGE_LIMIT))
    if r.result in (sandbox.CPU_TIME_LIMIT_EXCEEDED, sandbox.REAL_TIME_LIMIT_EXCEEDED):
        raise CompileError("编译超时（超过 %d 秒）" % (COMPILE_CPU_MS // 1000))
    if r.result == sandbox.MEMORY_LIMIT_EXCEEDED:
        raise CompileError("编译时内存超限")
    if r.exit_code != 0 or r.signal != 0:
        raise CompileError(message or "编译失败")


def run_program(ws: Workspace, input_path: str, output_path: str, error_path: str,
                time_limit_ms: int, memory_limit_mb: int) -> sandbox.SandboxResult:
    lang = ws.language
    cpu_ms = int(time_limit_ms * lang.time_factor)
    memory = memory_limit_mb * MB
    return sandbox.run(exe_path=ws.fill([lang.run_exe])[0], args=ws.fill(lang.run_args, memory_limit_mb),
                       env=ENV, uid=RUNNER.pw_uid, gid=RUNNER.pw_gid,
                       max_cpu_time=cpu_ms, max_real_time=cpu_ms * 3,
                       max_memory=memory + lang.extra_memory_mb * MB, max_stack=memory,
                       max_process_number=PROCESS_LIMIT, max_output_size=OUTPUT_LIMIT,
                       input_path=input_path, output_path=output_path, error_path=error_path,
                       seccomp_rule=lang.seccomp_rule,
                       memory_limit_check_only=lang.memory_limit_check_only)


def classify(r: sandbox.SandboxResult, stderr: str) -> str:
    """把沙箱结果换成判题结果；返回 OK 表示正常结束，还需要比对输出。"""
    if r.result in (sandbox.CPU_TIME_LIMIT_EXCEEDED, sandbox.REAL_TIME_LIMIT_EXCEEDED):
        return "TLE"
    if r.result == sandbox.MEMORY_LIMIT_EXCEEDED:
        return "MLE"
    if r.result == sandbox.RUNTIME_ERROR:
        if r.signal == signal.SIGXFSZ:
            return "OLE"
        if any(marker in stderr for marker in OOM_MARKERS):
            return "MLE"
        return "RE"
    if r.result == sandbox.SYSTEM_ERROR:
        return "SE"
    return "OK"


def describe(r: sandbox.SandboxResult, verdict: str, stderr: str) -> str:
    if verdict == "RE":
        if r.signal:
            reason = SIGNAL_HINTS.get(r.signal, "程序被信号 %d 终止" % r.signal)
        else:
            reason = "程序返回值为 %d（非 0）" % r.exit_code
        return reason + ("\n" + stderr if stderr else "")
    if verdict == "OLE":
        return "输出超过 %d MB" % (OUTPUT_LIMIT // MB)
    return ""


def case_result(r: sandbox.SandboxResult, verdict: str, message: str = "") -> dict:
    time_ms = max(r.cpu_time, r.real_time) if verdict == "TLE" else r.cpu_time
    return {"verdict": verdict, "time_ms": time_ms, "memory_kb": r.memory // 1024,
            "exit_code": r.exit_code, "signal": r.signal, "message": message}


def judge(language: str, source: str, test_cases: list[dict], time_limit_ms: int, memory_limit_mb: int) -> dict:
    lang = LANGUAGES[language]
    with _slots, Workspace(lang) as ws:
        try:
            compile_source(ws, source)
        except CompileError as e:
            return {"verdict": "CE", "compile_message": str(e), "time_ms": 0, "memory_kb": 0, "cases": []}

        cases = []
        for i, case in enumerate(test_cases):
            input_path, expected_path = ws.data("%d.in" % i), ws.data("%d.ans" % i)
            output_path, error_path = ws.data("%d.out" % i), ws.data("%d.err" % i)
            with open(input_path, "w", encoding="utf-8") as f:
                f.write(case["input"])
            r = run_program(ws, input_path, output_path, error_path, time_limit_ms, memory_limit_mb)
            stderr = read_text(error_path, CASE_MESSAGE_LIMIT)
            verdict = classify(r, stderr)
            if verdict == "OK":
                with open(output_path, "rb") as f:
                    actual = f.read()
                verdict = "AC" if same_output(case["output"].encode("utf-8"), actual) else "WA"
            cases.append(case_result(r, verdict, describe(r, verdict, stderr)))

    failed = next((c["verdict"] for c in cases if c["verdict"] != "AC"), "AC")
    return {"verdict": failed, "compile_message": "",
            "time_ms": max(c["time_ms"] for c in cases), "memory_kb": max(c["memory_kb"] for c in cases),
            "cases": cases}


def run_with_input(language: str, source: str, stdin: str, time_limit_ms: int, memory_limit_mb: int) -> dict:
    """自测运行：用给定输入跑一次，返回程序的输出。"""
    lang = LANGUAGES[language]
    with _slots, Workspace(lang) as ws:
        try:
            compile_source(ws, source)
        except CompileError as e:
            return {"verdict": "CE", "compile_message": str(e)}

        input_path, output_path, error_path = ws.data("input"), ws.data("stdout"), ws.data("stderr")
        with open(input_path, "w", encoding="utf-8") as f:
            f.write(stdin)
        r = run_program(ws, input_path, output_path, error_path, time_limit_ms, memory_limit_mb)
        stderr = read_text(error_path, RUN_STDERR_LIMIT)
        verdict = classify(r, stderr)
        result = case_result(r, verdict, describe(r, verdict, "") if verdict != "OK" else "")
        result.update(stdout=read_text(output_path, RUN_STDOUT_LIMIT), stderr=stderr, compile_message="")
        return result
