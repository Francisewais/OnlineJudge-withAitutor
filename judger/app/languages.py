"""各语言的编译、运行配置。

命令里的占位符：{src} 源文件路径，{exe} 可执行文件路径，{dir} 源码目录，{mem} 内存限制（MB）。
"""
from dataclasses import dataclass, field

# Python 没有编译步骤，用 compile() 先做一次语法检查，语法错误按编译错误处理
PY_SYNTAX_CHECK = (
    "import sys, traceback\n"
    "try:\n"
    "    compile(open(sys.argv[1], encoding='utf-8').read(), 'main.py', 'exec')\n"
    "except (SyntaxError, ValueError) as e:\n"
    "    traceback.print_exception_only(e)\n"
    "    sys.exit(1)\n"
)


@dataclass(frozen=True)
class Language:
    id: str
    name: str
    source_name: str
    compile: list[str]
    run_exe: str
    run_args: list[str] = field(default_factory=list)
    seccomp_rule: str | None = None
    # Java、Python 比 C/C++ 慢，时间限制按倍数放宽
    time_factor: float = 1.0
    # JVM 预留的虚拟内存很大，不能用 RLIMIT_AS 限制，只在结束后检查实际占用（RSS）
    memory_limit_check_only: bool = False
    # 额外放宽的内存（MB），给 JVM 自身的元空间、代码缓存等
    extra_memory_mb: int = 0


LANGUAGES: dict[str, Language] = {lang.id: lang for lang in [
    Language(
        id="c",
        name="C (GCC 13, C17)",
        source_name="main.c",
        compile=["/usr/bin/gcc", "-O2", "-std=c17", "-DONLINE_JUDGE", "-fmax-errors=10",
                 "-o", "{exe}", "{src}", "-lm"],
        run_exe="{exe}",
        seccomp_rule="c_cpp",
    ),
    Language(
        id="cpp",
        name="C++ (G++ 13, C++17)",
        source_name="main.cpp",
        compile=["/usr/bin/g++", "-O2", "-std=c++17", "-DONLINE_JUDGE", "-fmax-errors=10",
                 "-o", "{exe}", "{src}"],
        run_exe="{exe}",
        seccomp_rule="c_cpp",
    ),
    Language(
        id="java",
        name="Java 21 (OpenJDK)",
        source_name="Main.java",
        compile=["/usr/bin/javac", "-J-Xmx512m", "-J-XX:+UseSerialGC", "-encoding", "UTF-8",
                 "-d", "{dir}", "{src}"],
        run_exe="/usr/bin/java",
        run_args=["-cp", "{dir}", "-Xmx{mem}m", "-Xss64m", "-XX:+UseSerialGC", "-XX:-UsePerfData",
                  "-Djava.awt.headless=true", "Main"],
        time_factor=2.0,
        memory_limit_check_only=True,
        extra_memory_mb=64,
    ),
    Language(
        id="python",
        name="Python 3.12",
        source_name="main.py",
        compile=["/usr/bin/python3", "-c", PY_SYNTAX_CHECK, "{src}"],
        run_exe="/usr/bin/python3",
        run_args=["-B", "{src}"],
        seccomp_rule="general",
        time_factor=3.0,
    ),
]}
