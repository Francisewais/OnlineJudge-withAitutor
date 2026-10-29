"""判题机接口测试，在容器里运行：docker compose exec judger python tests/test_judger.py"""
import json
import os
import sys
import urllib.error
import urllib.request

URL = "http://127.0.0.1:8000"
TOKEN = os.environ["JUDGE_TOKEN"]

AB = [{"input": "1 2\n", "output": "3\n"},
      {"input": "100 200\n", "output": "300"},
      {"input": "-5 5\n", "output": "0\n\n"}]

C_AB = '#include <stdio.h>\nint main(){int a,b;scanf("%d%d",&a,&b);printf("%d\\n",a+b);return 0;}'
CPP_AB = '#include <iostream>\nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b<<"\\n";}'
JAVA_AB = ('import java.util.*;\npublic class Main{public static void main(String[] a){'
           'Scanner s=new Scanner(System.in);System.out.println(s.nextLong()+s.nextLong());}}')
PY_AB = 'a, b = map(int, input().split())\nprint(a + b)'

# (说明, 语言, 代码, 期望结果)
JUDGE_CASES = [
    ("C 正确", "c", C_AB, "AC"),
    ("C 答案错误", "c", C_AB.replace("a+b", "a-b"), "WA"),
    ("C 行尾空格和 \\r\\n 不影响结果", "c", C_AB.replace('"%d\\n"', '"%d  \\r\\n"'), "AC"),
    ("C 编译错误", "c", "int main(){ return 0 }", "CE"),
    ("C 只有警告（缺少 #include）", "c", C_AB.replace("#include <stdio.h>\n", ""), "AC"),
    ("C 段错误", "c", "int main(){int *p=0;*p=1;return 0;}", "RE"),
    ("C 返回值非 0", "c", "int main(){return 3;}", "RE"),
    ("C 死循环", "c", "int main(){while(1);}", "TLE"),
    # 用 volatile 逐页写入，避免 -O2 把分配优化掉；输出本身是对的，只能靠内存检查判出 MLE
    ("C 内存超限", "c", "#include <stdio.h>\n#include <stdlib.h>\nint main(){int a,b;scanf(\"%d%d\",&a,&b);"
     "size_t n=200u<<20;volatile char*p=malloc(n);if(!p)return 1;for(size_t i=0;i<n;i+=4096)p[i]=1;"
     "printf(\"%d\\n\",a+b+p[n-4096]-1);return 0;}", "MLE"),
    ("C 输出超限", "c", '#include <stdio.h>\nint main(){for(;;)puts("0123456789abcdef");}', "OLE"),
    ("C realloc 大数组（mremap）", "c", "#include <stdio.h>\n#include <stdlib.h>\n#include <string.h>\n"
     "int main(){int a,b;scanf(\"%d%d\",&a,&b);char*p=NULL;size_t n=1;"
     "for(int i=0;i<26;i++){n*=2;p=realloc(p,n);memset(p,0,n);}"
     "printf(\"%d\\n\",a+b+p[n-1]);return 0;}", "AC"),
    ("C fork（沙箱拦截）", "c", "#include <unistd.h>\nint main(){fork();return 0;}", "RE"),
    ("C 写文件（沙箱拦截）", "c", '#include <stdio.h>\nint main(){FILE*f=fopen("/tmp/x","w");return f?0:0;}', "RE"),
    ("C++ 正确", "cpp", CPP_AB, "AC"),
    ("C++ bad_alloc 判为内存超限", "cpp", "#include <vector>\nint main(){std::vector<long long> v(1LL<<32);return v[1];}", "MLE"),
    ("Java 正确", "java", JAVA_AB, "AC"),
    ("Java 编译错误", "java", "public class Main{public static void main(String[] a){int x=}}", "CE"),
    ("Java 抛异常", "java", "public class Main{public static void main(String[] a){throw new RuntimeException(\"boom\");}}", "RE"),
    ("Java 内存超限", "java", "public class Main{public static void main(String[] a){long[][] x=new long[64][];"
     "for(int i=0;i<64;i++)x[i]=new long[1<<20];System.out.println(x.length);}}", "MLE"),
    ("Java 死循环", "java", "public class Main{public static void main(String[] a){while(true){}}}", "TLE"),
    ("Python 正确", "python", PY_AB, "AC"),
    ("Python 语法错误判为编译错误", "python", "print(", "CE"),
    ("Python 抛异常", "python", "raise ValueError('boom')", "RE"),
    ("Python os.system（沙箱拦截）", "python", "import os\nos.system('ls')\n" + PY_AB, "RE"),
    ("Python 创建线程（沙箱拦截）", "python", "import threading\nthreading.Thread(target=print).start()\n" + PY_AB, "RE"),
    ("Python 联网（沙箱拒绝）", "python", "import socket\nsocket.socket()\n" + PY_AB, "RE"),
    ("Python 死循环", "python", "while True: pass", "TLE"),
    ("Python 内存超限", "python", "a = bytearray(512 * 1024 * 1024)\n" + PY_AB, "MLE"),
    # 判题令牌只在 root 进程的环境变量里，编译和运行用户都不应该读到
    ("编译时 include 读取 1 号进程环境变量", "c", '#include "/proc/1/environ"\nint main(){}', "CE"),
    ("运行时读取 1 号进程环境变量", "python", "print(open('/proc/1/environ').read())", "RE"),
    ("运行时列出评测目录", "python", "import os\nprint(os.listdir('/judger/run'))", "RE"),
]


def post(path, payload, token=TOKEN):
    req = urllib.request.Request(URL + path, data=json.dumps(payload).encode(),
                                 headers={"Content-Type": "application/json", "X-Judge-Token": token})
    with urllib.request.urlopen(req, timeout=300) as resp:
        return json.loads(resp.read())


def judge(lang, code, cases=AB, time_limit_ms=1000, memory_limit_mb=128):
    return post("/judge", {"language": lang, "source": code, "time_limit_ms": time_limit_ms,
                           "memory_limit_mb": memory_limit_mb, "test_cases": cases})


def main():
    failed = 0

    def check(desc, ok, detail=""):
        nonlocal failed
        failed += not ok
        print("[%s] %s%s" % ("PASS" if ok else "FAIL", desc, "" if ok else "\n       " + detail[:800]))

    try:
        post("/judge", {}, token="wrong")
        check("错误的令牌被拒绝", False, "请求没有被拒绝")
    except urllib.error.HTTPError as e:
        check("错误的令牌被拒绝", e.code == 401, "HTTP %d" % e.code)

    for desc, lang, code, expected in JUDGE_CASES:
        r = judge(lang, code)
        text = json.dumps(r, ensure_ascii=False)
        ok = r["verdict"] == expected and TOKEN not in text
        if expected == "CE":
            ok = ok and r["compile_message"] != ""
        elif expected != "AC":
            ok = ok and len(r["cases"]) == len(AB)
        check("%s：期望 %s，实际 %s" % (desc, expected, r["verdict"]), ok, text)

    r = post("/run", {"language": "python", "source": "import sys\ns=input()\nprint(s[::-1])\nprint('warn', file=sys.stderr)",
                      "input": "hello\n", "time_limit_ms": 1000, "memory_limit_mb": 128})
    check("自测运行返回 stdout 和 stderr", r["verdict"] == "OK" and r["stdout"] == "olleh\n" and r["stderr"] == "warn\n",
          json.dumps(r, ensure_ascii=False))
    r = post("/run", {"language": "cpp", "source": "int main(){", "input": "", "time_limit_ms": 1000, "memory_limit_mb": 128})
    check("自测运行编译错误", r["verdict"] == "CE" and "main.cpp" in r["compile_message"] and "/judger/run" not in r["compile_message"],
          json.dumps(r, ensure_ascii=False))

    print("\n%d / %d 通过" % (len(JUDGE_CASES) + 3 - failed, len(JUDGE_CASES) + 3))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
