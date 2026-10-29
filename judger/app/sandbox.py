"""调用沙箱可执行文件 /usr/lib/judger/libjudger.so（源码在 sandbox/，来自 QingdaoU/Judger）。"""
import json
import subprocess
from dataclasses import dataclass

LIBJUDGER = "/usr/lib/judger/libjudger.so"
LOG_PATH = "/judger/log/sandbox.log"
UNLIMITED = -1

# result 字段的取值
SUCCESS = 0
CPU_TIME_LIMIT_EXCEEDED = 1
REAL_TIME_LIMIT_EXCEEDED = 2
MEMORY_LIMIT_EXCEEDED = 3
RUNTIME_ERROR = 4
SYSTEM_ERROR = 5


class SandboxError(RuntimeError):
    pass


@dataclass
class SandboxResult:
    cpu_time: int   # 毫秒，用户态 + 内核态
    real_time: int  # 毫秒
    memory: int     # 字节（ru_maxrss）
    signal: int
    exit_code: int
    error: int      # 沙箱自身出错时非 0
    result: int


def run(*, exe_path: str, args: list[str], env: list[str], uid: int, gid: int,
        max_cpu_time: int, max_real_time: int, max_memory: int, max_stack: int,
        max_process_number: int, max_output_size: int,
        input_path: str = "/dev/null", output_path: str = "/dev/null", error_path: str = "/dev/null",
        seccomp_rule: str | None = None, memory_limit_check_only: bool = False) -> SandboxResult:
    """以 uid/gid 运行 exe_path，返回资源占用和结果。所有限制值 -1 表示不限制。"""
    cmd = [LIBJUDGER]
    limits = {
        "max_cpu_time": max_cpu_time,
        "max_real_time": max_real_time,
        "max_memory": max_memory,
        "max_stack": max_stack,
        "max_process_number": max_process_number,
        "max_output_size": max_output_size,
    }
    for name, value in limits.items():
        if value != UNLIMITED:
            cmd.append("--%s=%d" % (name, value))
    cmd += ["--exe_path=" + exe_path, "--input_path=" + input_path,
            "--output_path=" + output_path, "--error_path=" + error_path,
            "--log_path=" + LOG_PATH, "--uid=%d" % uid, "--gid=%d" % gid]
    cmd += ["--args=" + a for a in args]
    cmd += ["--env=" + e for e in env]
    if seccomp_rule:
        cmd.append("--seccomp_rule_name=" + seccomp_rule)
    if memory_limit_check_only:
        cmd.append("--memory_limit_check_only=1")

    # 沙箱自己会按 max_real_time 杀掉超时进程，这里的超时只是兜底
    timeout = max_real_time / 1000 + 30 if max_real_time != UNLIMITED else None
    proc = subprocess.run(cmd, capture_output=True, timeout=timeout)
    try:
        data = json.loads(proc.stdout)
    except ValueError:
        raise SandboxError("沙箱输出无法解析: %r %r" % (proc.stdout[:200], proc.stderr[:200]))
    result = SandboxResult(**data)
    if result.error != 0:
        raise SandboxError("沙箱运行失败，错误码 %d（详见 %s）" % (result.error, LOG_PATH))
    return result
