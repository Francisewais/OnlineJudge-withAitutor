"""生成示例题目：题面、题解、测试数据。

输出：
    backend/src/main/resources/seed/problems.json.gz   后端首次启动时导入
    tests/fixtures/solutions.json                      端到端测试用的标准解法和错误解法

测试数据由本文件里的 Python 参考实现算出，端到端测试再用题解里的 C++ / Python 代码提交验证。
用法：python3 scripts/generate_seed.py
"""
import gzip
import json
import random
from collections import deque
from bisect import bisect_left
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
rng = random.Random(2026)
MOD = 10 ** 9 + 7


def editorial(idea: str, complexity: str, codes: dict) -> str:
    names = {"cpp": "C++", "python": "Python", "java": "Java", "c": "C"}
    parts = ["## 思路\n\n" + idea.strip(), "## 复杂度\n\n" + complexity.strip(), "## 参考代码"]
    for lang, code in codes.items():
        parts.append("### %s\n\n```%s\n%s\n```" % (names[lang], lang, code.strip()))
    return "\n\n".join(parts) + "\n"


def problem(title, difficulty, tags, description, input_format, output_format, hint, samples, cases, idea,
            complexity, solutions, wrong=None):
    """samples / cases: [(input, output)]；样例也会作为第一组测试数据。"""
    tests = samples + cases
    return {
        "form": {
            "title": title, "difficulty": difficulty, "tags": tags,
            "description": description.strip(), "inputFormat": input_format.strip(),
            "outputFormat": output_format.strip(), "hint": hint.strip(),
            "samples": [{"input": i, "output": o} for i, o in samples],
            "editorial": editorial(idea, complexity, solutions),
            "timeLimitMs": 1000, "memoryLimitMb": 256, "visible": True,
            "testCases": [{"input": i, "output": o} for i, o in tests],
        },
        "solutions": {k: v.strip() + "\n" for k, v in solutions.items()},
        "wrong": wrong or [],
    }


def a_plus_b():
    def case(a, b):
        return "%d %d\n" % (a, b), "%d\n" % (a + b)
    cases = [case(0, 0), case(10 ** 6, 10 ** 6), case(-10 ** 6, -10 ** 6), case(-10 ** 6, 10 ** 6)]
    cases += [case(rng.randint(-10 ** 6, 10 ** 6), rng.randint(-10 ** 6, 10 ** 6)) for _ in range(6)]
    return problem(
        "A + B", "EASY", ["入门"],
        """
输入两个整数 $a$ 和 $b$，输出它们的和。

这是本站的第一道题，可以用它熟悉提交流程：选择语言，写好代码，点击「提交」，等待评测结果。
""",
        "一行，两个整数 $a, b$，用空格隔开。",
        "一个整数，表示 $a + b$ 的值。",
        """
$-10^6 \\le a, b \\le 10^6$

程序只需要从标准输入读入、向标准输出打印结果，不要输出「请输入两个数」之类的提示文字，否则会被判为答案错误。
""",
        [case(1, 2), case(-5, 8)], cases,
        "按题目要求读入两个整数，把它们的和打印出来即可。注意不要输出多余的内容。",
        "$O(1)$。",
        {
            "c": '#include <stdio.h>\n\nint main(void) {\n    int a, b;\n    scanf("%d %d", &a, &b);\n'
                 '    printf("%d\\n", a + b);\n    return 0;\n}',
            "cpp": "#include <iostream>\nusing namespace std;\n\nint main() {\n    int a, b;\n    cin >> a >> b;\n"
                   "    cout << a + b << endl;\n    return 0;\n}",
            "java": "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n"
                    "        Scanner in = new Scanner(System.in);\n        int a = in.nextInt(), b = in.nextInt();\n"
                    "        System.out.println(a + b);\n    }\n}",
            "python": "a, b = map(int, input().split())\nprint(a + b)",
        },
        wrong=[{"language": "python", "verdict": "WA", "note": "输出了提示文字",
                "code": "a, b = map(int, input().split())\nprint('结果是', a + b)\n"}],
    )


def array_sum():
    def case(values):
        return "%d\n%s\n" % (len(values), " ".join(map(str, values))), "%d\n" % sum(values)
    big = 10 ** 9
    cases = [case([0]), case([-big]), case([big] * 20000), case([-big] * 20000),
             case([rng.randint(-big, big) for _ in range(20000)]),
             case([rng.randint(-100, 100) for _ in range(1000)]),
             case([rng.randint(big // 2, big) for _ in range(5000)])]
    return problem(
        "数组求和", "EASY", ["入门", "数据类型"],
        "给定 $n$ 个整数，求它们的和。",
        "第一行一个整数 $n$。\n\n第二行 $n$ 个整数 $a_1, a_2, \\dots, a_n$，用空格隔开。",
        "一个整数，表示这 $n$ 个数的和。",
        "$1 \\le n \\le 2 \\times 10^4$，$|a_i| \\le 10^9$",
        [case([1, 2, 3, 4, 5])], cases,
        """
直接累加即可，但要注意数据范围：和的绝对值最大可以到 $2 \\times 10^4 \\times 10^9 = 2 \\times 10^{13}$，
远远超过 32 位 `int` 能表示的范围（约 $2.1 \\times 10^9$）。用 `int` 累加会溢出，得到错误的结果。

C/C++ 要用 `long long`，Java 用 `long`；Python 的整数没有大小限制，不用担心。

做题时养成先看数据范围的习惯：先估算答案最大可能是多少，再决定用什么类型。
""",
        "$O(n)$。",
        {
            "cpp": "#include <iostream>\nusing namespace std;\n\nint main() {\n    int n;\n    cin >> n;\n"
                   "    long long sum = 0;  // 和可能超过 int 的范围\n    for (int i = 0; i < n; i++) {\n"
                   "        long long x;\n        cin >> x;\n        sum += x;\n    }\n    cout << sum << endl;\n"
                   "    return 0;\n}",
            "java": "import java.io.*;\nimport java.util.*;\n\npublic class Main {\n"
                    "    public static void main(String[] args) throws IOException {\n"
                    "        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));\n"
                    "        int n = Integer.parseInt(in.readLine().trim());\n"
                    "        StringTokenizer st = new StringTokenizer(in.readLine());\n        long sum = 0;\n"
                    "        for (int i = 0; i < n; i++) {\n            sum += Long.parseLong(st.nextToken());\n        }\n"
                    "        System.out.println(sum);\n    }\n}",
            "python": "n = int(input())\nprint(sum(map(int, input().split())))",
        },
        wrong=[{"language": "cpp", "verdict": "WA", "note": "用 int 累加溢出",
                "code": "#include <iostream>\nusing namespace std;\n\nint main() {\n    int n, sum = 0;\n    cin >> n;\n"
                        "    for (int i = 0; i < n; i++) {\n        int x;\n        cin >> x;\n        sum += x;\n    }\n"
                        "    cout << sum << endl;\n    return 0;\n}\n"}],
    )


def palindrome():
    def case(s):
        return s + "\n", ("Yes" if s == s[::-1] else "No") + "\n"

    def rand_str(n):
        return "".join(rng.choice("ab") for _ in range(n))

    half = "".join(rng.choice("abcdefghijklmnopqrstuvwxyz") for _ in range(50000))
    cases = [case("a"), case("ab"), case("aa"), case("abcba"), case("abccba"), case("abcdab"),
             case(half + half[::-1]), case(half + "x" + half[::-1]), case(half + "xy" + half[::-1]),
             case(rand_str(1000))]
    return problem(
        "回文串", "EASY", ["字符串"],
        "如果一个字符串正着读和反着读完全一样，就称它是**回文串**，例如 `level`、`noon`。\n\n"
        "给定一个只包含小写字母的字符串，判断它是不是回文串。",
        "一行，一个只包含小写字母的字符串 $s$。",
        "如果 $s$ 是回文串，输出 `Yes`，否则输出 `No`。",
        "$1 \\le |s| \\le 10^5$",
        [case("level"), case("abca")], cases,
        """
用两个下标 $i$、$j$ 分别从字符串的两端向中间移动，每次比较 $s_i$ 和 $s_j$：只要有一对不相等就不是回文串；
两个下标相遇时还没发现不相等，就是回文串。

也可以直接把字符串反转后和原串比较，代码更短，但要多用一份空间。
""",
        "时间 $O(|s|)$，额外空间 $O(1)$。",
        {
            "cpp": "#include <iostream>\n#include <string>\nusing namespace std;\n\nint main() {\n    string s;\n"
                   "    cin >> s;\n    int i = 0, j = (int)s.size() - 1;\n    while (i < j && s[i] == s[j]) {\n"
                   "        i++;\n        j--;\n    }\n    cout << (i >= j ? \"Yes\" : \"No\") << endl;\n"
                   "    return 0;\n}",
            "python": "s = input().strip()\nprint('Yes' if s == s[::-1] else 'No')",
        },
    )


def brackets():
    pairs = {")": "(", "]": "[", "}": "{"}

    def balanced(s):
        stack = []
        for ch in s:
            if ch in "([{":
                stack.append(ch)
            elif not stack or stack.pop() != pairs[ch]:
                return False
        return not stack

    def case(s):
        return s + "\n", ("Yes" if balanced(s) else "No") + "\n"

    def rand_balanced(n):
        out, stack = [], []
        while len(out) + len(stack) < n:
            if stack and rng.random() < 0.5:
                out.append({"(": ")", "[": "]", "{": "}"}[stack.pop()])
            else:
                ch = rng.choice("([{")
                stack.append(ch)
                out.append(ch)
        out += [{"(": ")", "[": "]", "{": "}"}[c] for c in reversed(stack)]
        return "".join(out)

    good = rand_balanced(100000)
    cases = [case("()"), case(")("), case("("), case("]"), case("{[()()]}"), case("([)]"),
             case("((((((((((" * 1000), case(good), case(good[:-1] + ("(" if good[-1] != "(" else ")")),
             case(rand_balanced(5000) + ")"), case("[" * 50000 + "]" * 50000)]
    return problem(
        "括号匹配", "EASY", ["栈"],
        """
给定一个只由 `(`、`)`、`[`、`]`、`{`、`}` 组成的字符串，判断其中的括号是否正确匹配。

正确匹配的定义：空串是正确匹配的；如果 $A$、$B$ 都是正确匹配的，那么 `(A)`、`[A]`、`{A}` 和 $AB$ 也都是正确匹配的。
例如 `([]){}` 是正确匹配的，而 `([)]`、`((` 都不是。
""",
        "一行，一个括号字符串 $s$。",
        "正确匹配输出 `Yes`，否则输出 `No`。",
        "$1 \\le |s| \\le 10^5$",
        [case("([]){}"), case("([)]")], cases,
        """
从左到右扫描字符串，用一个**栈**保存还没有配对的左括号：

- 遇到左括号，压入栈；
- 遇到右括号，栈顶必须是和它同类型的左括号，是就弹出，否则（包括栈为空）说明不匹配；
- 扫描结束后栈必须为空，否则还有左括号没有配对。

只统计每种括号的数量是不够的：`([)]` 中每种括号的数量都相等，但顺序不对。
""",
        "时间 $O(|s|)$，空间 $O(|s|)$。",
        {
            "cpp": "#include <iostream>\n#include <string>\n#include <vector>\nusing namespace std;\n\n"
                   "int main() {\n    string s;\n    cin >> s;\n    vector<char> st;\n    bool ok = true;\n"
                   "    for (char c : s) {\n        if (c == '(' || c == '[' || c == '{') {\n            st.push_back(c);\n"
                   "        } else {\n            char need = c == ')' ? '(' : (c == ']' ? '[' : '{');\n"
                   "            if (st.empty() || st.back() != need) {\n                ok = false;\n                break;\n"
                   "            }\n            st.pop_back();\n        }\n    }\n"
                   "    cout << (ok && st.empty() ? \"Yes\" : \"No\") << endl;\n    return 0;\n}",
            "python": "s = input().strip()\npairs = {')': '(', ']': '[', '}': '{'}\nstack = []\nok = True\n"
                      "for c in s:\n    if c in '([{':\n        stack.append(c)\n"
                      "    elif not stack or stack.pop() != pairs[c]:\n        ok = False\n        break\n"
                      "print('Yes' if ok and not stack else 'No')",
        },
    )


def stairs():
    ways = [0, 1, 2]
    for i in range(3, 10 ** 6 + 1):
        ways.append((ways[-1] + ways[-2]) % MOD)

    def case(n):
        return "%d\n" % n, "%d\n" % ways[n]
    cases = [case(n) for n in (1, 2, 4, 45, 46, 100, 1000, 99999, 10 ** 6)]
    return problem(
        "爬楼梯", "EASY", ["动态规划"],
        """
楼梯一共有 $n$ 级台阶，每一步可以向上跨 1 级或 2 级。从地面走到第 $n$ 级台阶，一共有多少种不同的走法？

答案可能很大，请输出它除以 $10^9 + 7$ 的余数。
""",
        "一个整数 $n$。",
        "一个整数，表示走法数除以 $10^9 + 7$ 的余数。",
        "$1 \\le n \\le 10^6$\n\n样例 1 解释：走到第 3 级台阶有 1+1+1、1+2、2+1 三种走法。",
        [case(3), case(10)], cases,
        """
设 $f(i)$ 为走到第 $i$ 级台阶的走法数。最后一步要么从第 $i-1$ 级跨 1 级上来，要么从第 $i-2$ 级跨 2 级上来，所以

$$f(i) = f(i-1) + f(i-2)$$

其中 $f(1) = 1$，$f(2) = 2$。

如果直接按这个式子写递归函数，同一个 $f(i)$ 会被重复计算很多次，调用次数随 $n$ 指数增长，$n$ 稍大就会超时。
从小到大依次算出每个 $f(i)$（**动态规划**），每个值只算一次。

答案增长得非常快，每一步加法之后都要对 $10^9 + 7$ 取模，否则会溢出。两个小于 $10^9 + 7$ 的数相加不超过 `int` 的范围，
但为了保险可以用 `long long`。
""",
        "时间 $O(n)$；只保留前两项时空间 $O(1)$。",
        {
            "cpp": "#include <iostream>\nusing namespace std;\n\nint main() {\n    const long long MOD = 1000000007;\n"
                   "    int n;\n    cin >> n;\n    long long a = 1, b = 2;  // f(1), f(2)\n    if (n == 1) {\n"
                   "        cout << 1 << endl;\n        return 0;\n    }\n    for (int i = 3; i <= n; i++) {\n"
                   "        long long c = (a + b) % MOD;\n        a = b;\n        b = c;\n    }\n"
                   "    cout << b << endl;\n    return 0;\n}",
            "python": "n = int(input())\nMOD = 10 ** 9 + 7\na, b = 1, 2  # f(1), f(2)\nfor _ in range(n - 1):\n"
                      "    a, b = b, (a + b) % MOD\nprint(a)",
        },
        wrong=[{"language": "cpp", "verdict": "TLE", "note": "直接递归，指数级复杂度",
                "code": "#include <iostream>\nusing namespace std;\n\nlong long f(int n) {\n    if (n <= 2) return n;\n"
                        "    return (f(n - 1) + f(n - 2)) % 1000000007;\n}\n\nint main() {\n    int n;\n    cin >> n;\n"
                        "    cout << f(n) << endl;\n    return 0;\n}\n"}],
    )


def lower_bound():
    def case(a, queries):
        out = []
        for x in queries:
            pos = bisect_left(a, x)
            out.append(str(pos + 1) if pos < len(a) else "-1")
        text = "%d %d\n%s\n%s\n" % (len(a), len(queries), " ".join(map(str, a)), "\n".join(map(str, queries)))
        return text, "\n".join(out) + "\n"

    def rand_case(n, q, hi):
        a = sorted(rng.randint(0, hi) for _ in range(n))
        return case(a, [rng.randint(0, hi) for _ in range(q)])

    cases = [case([5], [0, 5, 6]), case([1, 1, 1, 1], [0, 1, 2]), case([2, 4, 6, 8], [1, 2, 3, 8, 9]),
             rand_case(100, 100, 50), rand_case(1000, 1000, 10 ** 6), rand_case(50000, 50000, 10 ** 6),
             rand_case(50000, 50000, 100)]
    return problem(
        "第一个不小于 x 的数", "MEDIUM", ["二分查找"],
        """
给定一个长度为 $n$、从小到大排好序的整数序列 $a_1 \\le a_2 \\le \\dots \\le a_n$，以及 $q$ 次询问。

每次询问给出一个整数 $x$，请找出序列中第一个**大于或等于** $x$ 的数的位置（位置从 1 开始编号）；
如果所有数都小于 $x$，输出 `-1`。
""",
        "第一行两个整数 $n, q$。\n\n第二行 $n$ 个整数，表示序列 $a$，保证从小到大排列。\n\n"
        "接下来 $q$ 行，每行一个整数 $x$，表示一次询问。",
        "对每次询问输出一行，表示答案。",
        "$1 \\le n, q \\le 5 \\times 10^4$，$0 \\le a_i, x \\le 10^6$",
        [case([1, 3, 3, 5, 9], [3, 4, 10, 0])], cases,
        """
每次询问都从头到尾扫一遍，最坏要比较 $n \\times q = 2.5 \\times 10^9$ 次，会超时。

序列是有序的，可以**二分查找**：维护区间 $[lo, hi)$，答案一定在其中。每次看中间位置 $mid$，
如果 $a_{mid} \\ge x$，答案在左半边（包括 $mid$），令 $hi = mid$；否则令 $lo = mid + 1$。区间每次缩小一半，
$\\log_2 n$ 次之后就能确定答案。

二分的细节（区间开闭、`mid` 取哪边）很容易写错，可以先用样例里的重复元素、比所有数都大或都小的 $x$ 检查一遍。
C++ 的 `lower_bound`、Python 的 `bisect.bisect_left` 就是这个功能，可以直接用。

询问很多时输入输出量也很大：C++ 用 `scanf`/`printf` 或关闭同步的 `cin`，Python 一次性读入全部输入。
""",
        "时间 $O((n + q) \\log n)$。",
        {
            "cpp": "#include <cstdio>\n#include <vector>\nusing namespace std;\n\nint main() {\n    int n, q;\n"
                   "    scanf(\"%d %d\", &n, &q);\n    vector<int> a(n);\n    for (int i = 0; i < n; i++) scanf(\"%d\", &a[i]);\n"
                   "    while (q--) {\n        int x;\n        scanf(\"%d\", &x);\n        int lo = 0, hi = n;  // 答案在 [lo, hi) 中，hi == n 表示不存在\n"
                   "        while (lo < hi) {\n            int mid = (lo + hi) / 2;\n            if (a[mid] >= x) hi = mid;\n"
                   "            else lo = mid + 1;\n        }\n        printf(\"%d\\n\", lo < n ? lo + 1 : -1);\n    }\n"
                   "    return 0;\n}",
            "python": "import sys\nfrom bisect import bisect_left\n\ndata = sys.stdin.buffer.read().split()\n"
                      "n, q = int(data[0]), int(data[1])\na = list(map(int, data[2:2 + n]))\nout = []\n"
                      "for x in map(int, data[2 + n:2 + n + q]):\n    pos = bisect_left(a, x)\n"
                      "    out.append(str(pos + 1) if pos < n else '-1')\nprint('\\n'.join(out))",
        },
        wrong=[{"language": "python", "verdict": "TLE", "note": "每次询问都线性扫描",
                "code": "import sys\ndata = sys.stdin.read().split()\nn, q = int(data[0]), int(data[1])\n"
                        "a = list(map(int, data[2:2 + n]))\nfor x in map(int, data[2 + n:]):\n    ans = -1\n"
                        "    for i in range(n):\n        if a[i] >= x:\n            ans = i + 1\n            break\n"
                        "    print(ans)\n"}],
    )


def maze():
    def solve(grid):
        n, m = len(grid), len(grid[0])
        start = next((i, j) for i in range(n) for j in range(m) if grid[i][j] == "S")
        dist = [[-1] * m for _ in range(n)]
        dist[start[0]][start[1]] = 0
        queue = deque([start])
        while queue:
            x, y = queue.popleft()
            if grid[x][y] == "T":
                return dist[x][y]
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < n and 0 <= ny < m and grid[nx][ny] != "#" and dist[nx][ny] < 0:
                    dist[nx][ny] = dist[x][y] + 1
                    queue.append((nx, ny))
        return -1

    def case(grid):
        return "%d %d\n%s\n" % (len(grid), len(grid[0]), "\n".join(grid)), "%d\n" % solve(grid)

    def rand_grid(n, m, wall):
        g = [["#" if rng.random() < wall else "." for _ in range(m)] for _ in range(n)]
        cells = rng.sample([(i, j) for i in range(n) for j in range(m)], 2)
        g[cells[0][0]][cells[0][1]], g[cells[1][0]][cells[1][1]] = "S", "T"
        return ["".join(row) for row in g]

    # 蛇形迷宫：路径很长，DFS 递归会很深
    snake = []
    for i in range(499):
        if i % 2 == 0:
            snake.append("." * 500)
        elif i % 4 == 1:
            snake.append("#" * 499 + ".")
        else:
            snake.append("." + "#" * 499)
    snake[0] = "S" + snake[0][1:]
    snake[-1] = snake[-1][:-1] + "T"

    cases = [case(["ST"]), case(["S", "#", "T"]), case(["S.#", "##.", "..T"]), case(snake),
             case(rand_grid(50, 50, 0.3)), case(rand_grid(500, 500, 0.2)), case(rand_grid(500, 500, 0.35)),
             case(rand_grid(300, 400, 0.45)), case(["S" + "." * 498 + "T"])]
    return problem(
        "走迷宫", "MEDIUM", ["搜索", "BFS"],
        """
有一个 $n$ 行 $m$ 列的迷宫，`.` 表示空地，`#` 表示墙，`S` 是起点，`T` 是终点。

每一步可以向上、下、左、右走到相邻的格子，不能走出迷宫，也不能穿过墙（起点和终点也算空地）。
求从起点走到终点最少需要多少步；如果走不到，输出 `-1`。
""",
        "第一行两个整数 $n, m$。\n\n接下来 $n$ 行，每行一个长度为 $m$ 的字符串，描述迷宫。保证 `S` 和 `T` 各恰好出现一次。",
        "一个整数，表示最少步数；走不到输出 `-1`。",
        "$1 \\le n, m \\le 500$",
        [case(["S..#", ".#..", "...T"]), case(["S#", "#T"])], cases,
        """
求网格上的最短步数，用**广度优先搜索（BFS）**：从起点出发，先访问所有 1 步能到的格子，再访问 2 步能到的格子……
用一个队列按距离从小到大依次扩展，第一次到达终点时的距离就是答案。

每个格子只需要入队一次：入队时就记录它的距离（同时当作「已访问」标记），之后再遇到就跳过。
如果在入队时不做标记、出队时才标记，同一个格子可能被重复加入很多次，导致超时或内存超限。

深度优先搜索（DFS）只能找到「一条」路径，不能保证最短；而且在路径很长的迷宫里递归层数会非常深，容易栈溢出。
""",
        "每个格子最多入队一次，时间和空间都是 $O(nm)$。",
        {
            "cpp": "#include <iostream>\n#include <string>\n#include <vector>\n#include <queue>\nusing namespace std;\n\n"
                   "int main() {\n    int n, m;\n    cin >> n >> m;\n    vector<string> g(n);\n"
                   "    for (auto &row : g) cin >> row;\n    vector<vector<int>> dist(n, vector<int>(m, -1));\n"
                   "    queue<pair<int, int>> q;\n    for (int i = 0; i < n; i++)\n        for (int j = 0; j < m; j++)\n"
                   "            if (g[i][j] == 'S') {\n                dist[i][j] = 0;\n                q.push({i, j});\n"
                   "            }\n    int dx[] = {1, -1, 0, 0}, dy[] = {0, 0, 1, -1};\n    while (!q.empty()) {\n"
                   "        auto [x, y] = q.front();\n        q.pop();\n        if (g[x][y] == 'T') {\n"
                   "            cout << dist[x][y] << endl;\n            return 0;\n        }\n"
                   "        for (int d = 0; d < 4; d++) {\n            int nx = x + dx[d], ny = y + dy[d];\n"
                   "            if (nx < 0 || nx >= n || ny < 0 || ny >= m) continue;\n"
                   "            if (g[nx][ny] == '#' || dist[nx][ny] != -1) continue;\n"
                   "            dist[nx][ny] = dist[x][y] + 1;  // 入队时就标记，避免重复入队\n"
                   "            q.push({nx, ny});\n        }\n    }\n    cout << -1 << endl;\n    return 0;\n}",
            "python": "import sys\nfrom collections import deque\n\ndata = sys.stdin.read().split()\n"
                      "n, m = int(data[0]), int(data[1])\ng = data[2:2 + n]\ndist = [[-1] * m for _ in range(n)]\n"
                      "q = deque()\nfor i in range(n):\n    j = g[i].find('S')\n    if j >= 0:\n        dist[i][j] = 0\n"
                      "        q.append((i, j))\nans = -1\nwhile q:\n    x, y = q.popleft()\n    if g[x][y] == 'T':\n"
                      "        ans = dist[x][y]\n        break\n    for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):\n"
                      "        if 0 <= nx < n and 0 <= ny < m and g[nx][ny] != '#' and dist[nx][ny] < 0:\n"
                      "            dist[nx][ny] = dist[x][y] + 1\n            q.append((nx, ny))\nprint(ans)",
        },
    )


def lis():
    def solve(a):
        tails = []
        for x in a:
            i = bisect_left(tails, x)
            if i == len(tails):
                tails.append(x)
            else:
                tails[i] = x
        return len(tails)

    def case(a):
        return "%d\n%s\n" % (len(a), " ".join(map(str, a))), "%d\n" % solve(a)

    cases = [case([1]), case([5, 5, 5, 5]), case(list(range(1, 1001))), case(list(range(1000, 0, -1))),
             case([rng.randint(1, 10) for _ in range(100)]), case([rng.randint(1, 10 ** 6) for _ in range(5000)]),
             case([rng.randint(1, 10 ** 6) for _ in range(100000)]),
             case([i // 2 + rng.randint(0, 3) for i in range(100000)])]
    return problem(
        "最长上升子序列", "HARD", ["动态规划", "二分查找"],
        """
给定一个长度为 $n$ 的整数序列，求它的**最长严格上升子序列**的长度。

子序列是从原序列中删去若干个（可以是 0 个）元素后，剩下的元素保持原来的顺序组成的序列；
严格上升是指每个数都比前一个数大。
""",
        "第一行一个整数 $n$。\n\n第二行 $n$ 个整数 $a_1, a_2, \\dots, a_n$。",
        "一个整数，表示最长严格上升子序列的长度。",
        "$1 \\le n \\le 10^5$，$1 \\le a_i \\le 10^6$\n\n样例解释：2 3 7 18 是一个最长的严格上升子序列，长度为 4。",
        [case([10, 9, 2, 5, 3, 7, 101, 18])], cases,
        """
**$O(n^2)$ 的动态规划**：设 $f_i$ 为以 $a_i$ 结尾的最长上升子序列长度，$f_i = 1 + \\max\\{f_j \\mid j < i, a_j < a_i\\}$。
$n = 10^5$ 时要算约 $5 \\times 10^9$ 次，会超时。

**$O(n \\log n)$ 的做法**：维护数组 $d$，$d_k$ 表示「长度为 $k$ 的上升子序列中，结尾元素最小是多少」。
$d$ 一定是严格递增的。依次处理每个 $a_i$：

- 如果 $a_i$ 比 $d$ 的最后一个元素还大，它可以接在最长的那个子序列后面，把它追加到 $d$ 的末尾；
- 否则在 $d$ 中二分找到第一个 $\\ge a_i$ 的位置，用 $a_i$ 替换它：同样长度的子序列，结尾更小，后面更容易接上新的数。

最后 $d$ 的长度就是答案（注意 $d$ 本身不一定是一个真实的子序列）。要求严格上升，所以找的是第一个 **$\\ge a_i$** 的位置；
如果是「不下降」子序列，就要改成第一个 $> a_i$ 的位置。
""",
        "时间 $O(n \\log n)$，空间 $O(n)$。",
        {
            "cpp": "#include <cstdio>\n#include <vector>\n#include <algorithm>\nusing namespace std;\n\nint main() {\n"
                   "    int n;\n    scanf(\"%d\", &n);\n    vector<int> d;  // d[k]：长度为 k+1 的上升子序列的最小结尾\n"
                   "    for (int i = 0; i < n; i++) {\n        int x;\n        scanf(\"%d\", &x);\n"
                   "        auto it = lower_bound(d.begin(), d.end(), x);\n        if (it == d.end()) d.push_back(x);\n"
                   "        else *it = x;\n    }\n    printf(\"%d\\n\", (int)d.size());\n    return 0;\n}",
            "python": "import sys\nfrom bisect import bisect_left\n\ndata = sys.stdin.buffer.read().split()\nn = int(data[0])\n"
                      "d = []  # d[k]：长度为 k+1 的上升子序列的最小结尾\nfor x in map(int, data[1:1 + n]):\n"
                      "    i = bisect_left(d, x)\n    if i == len(d):\n        d.append(x)\n    else:\n        d[i] = x\n"
                      "print(len(d))",
        },
        wrong=[{"language": "cpp", "verdict": "TLE", "note": "O(n^2) 动态规划",
                "code": "#include <cstdio>\n#include <vector>\nusing namespace std;\n\nint main() {\n    int n;\n"
                        "    scanf(\"%d\", &n);\n    vector<int> a(n), f(n, 1);\n    for (auto &x : a) scanf(\"%d\", &x);\n"
                        "    int best = 0;\n    for (int i = 0; i < n; i++) {\n        for (int j = 0; j < i; j++)\n"
                        "            if (a[j] < a[i] && f[j] + 1 > f[i]) f[i] = f[j] + 1;\n"
                        "        if (f[i] > best) best = f[i];\n    }\n    printf(\"%d\\n\", best);\n    return 0;\n}\n"}],
    )


def main():
    problems = [a_plus_b(), array_sum(), palindrome(), brackets(), stairs(), lower_bound(), maze(), lis()]
    seed = ROOT / "backend/src/main/resources/seed/problems.json.gz"
    seed.parent.mkdir(parents=True, exist_ok=True)
    data = json.dumps([p["form"] for p in problems], ensure_ascii=False).encode("utf-8")
    # mtime=0 让输出与生成时间无关，重复运行不会产生 git 改动
    with gzip.GzipFile(seed, "wb", mtime=0) as f:
        f.write(data)

    fixtures = ROOT / "tests/fixtures/solutions.json"
    fixtures.parent.mkdir(parents=True, exist_ok=True)
    fixtures.write_text(json.dumps([{"title": p["form"]["title"], "solutions": p["solutions"], "wrong": p["wrong"]}
                                    for p in problems], ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    cases = sum(len(p["form"]["testCases"]) for p in problems)
    print("%d 道题，%d 组测试数据，原始 %.1f MB，压缩后 %.1f MB" % (len(problems), cases, len(data) / 2 ** 20,
                                                          seed.stat().st_size / 2 ** 20))


if __name__ == "__main__":
    main()
