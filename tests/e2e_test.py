"""端到端测试：通过前端 nginx（默认 http://127.0.0.1:8080）测试整套系统，AI 部分使用 tests/mock_ai.py。

用法：
    docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --build
    python3 tests/e2e_test.py
管理员密码从环境变量或 .env 里的 OJ_ADMIN_PASSWORD 读取。
"""
import json
import os
import random
import socket
import string
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASE = os.environ.get("OJ_URL", "http://127.0.0.1:8080")
MOCK_AI = os.environ.get("MOCK_AI_URL", "http://127.0.0.1:9000")


def env_value(key, default=""):
    if key in os.environ:
        return os.environ[key]
    env = ROOT / ".env"
    if env.exists():
        for line in env.read_text(encoding="utf-8").splitlines():
            if line.startswith(key + "="):
                return line.split("=", 1)[1].strip()
    return default


ADMIN = (env_value("OJ_ADMIN_USERNAME", "admin"), env_value("OJ_ADMIN_PASSWORD"))
_lock = threading.Lock()
_results = []


def check(desc, ok, detail=""):
    with _lock:
        _results.append(bool(ok))
        print("[%s] %s%s" % ("PASS" if ok else "FAIL", desc, "" if ok else "\n       " + str(detail)[:800]), flush=True)
    return ok


def call(method, path, body=None, token=None, base=BASE):
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(base + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            raw, ctype = resp.read(), resp.headers.get("Content-Type", "")
            return resp.status, (json.loads(raw) if raw and "json" in ctype else raw.decode("utf-8", "replace"))
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, raw.decode("utf-8", "replace")


def sse(path, body, token):
    """返回 (200, [(秒, 事件名, 数据)])；服务端开始推送前报错时返回 (HTTP 状态码, JSON)。"""
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode(), method="POST", headers={
        "Content-Type": "application/json", "Accept": "text/event-stream, application/json",
        "Authorization": "Bearer " + token})
    try:
        resp = urllib.request.urlopen(req, timeout=120)
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"{}")
    events, name, data, start = [], "message", [], time.time()
    with resp:
        for raw in resp:
            line = raw.decode("utf-8").rstrip("\r\n")
            if not line:
                if data:
                    events.append((time.time() - start, name, "\n".join(data)))
                name, data = "message", []
            elif line.startswith("event:"):
                name = line[6:].strip()
            elif line.startswith("data:"):
                data.append(line[5:][1:] if line[5:].startswith(" ") else line[5:])
    return 200, events


def register(prefix):
    name = "%s_%s" % (prefix, "".join(random.choices(string.ascii_lowercase + string.digits, k=6)))
    status, data = call("POST", "/api/auth/register", {"username": name, "password": "secret123"})
    if status != 200:
        raise SystemExit("注册失败：%s %s" % (status, data))
    return data["token"], data["user"]


def judge(token, problem_id, language, code, timeout=300):
    status, data = call("POST", "/api/submissions", {"problemId": problem_id, "language": language, "code": code}, token)
    if status != 200:
        return {"verdict": "提交失败 %s %s" % (status, data)}
    deadline = time.time() + timeout
    while time.time() < deadline:
        _, detail = call("GET", "/api/submissions/%d" % data["id"], token=token)
        if detail["status"] == "FINISHED":
            return detail
        time.sleep(0.5)
    return {"verdict": "评测超时"}


def wait_ready():
    for _ in range(120):
        try:
            if call("GET", "/api/health")[0] == 200:
                return
        except OSError:
            pass
        time.sleep(2)
    raise SystemExit("后端没有启动：%s/api/health" % BASE)


def test_frontend():
    status, html = call("GET", "/")
    check("首页返回前端页面", status == 200 and '<div id="app">' in html, html[:200])
    status, html2 = call("GET", "/problems/1")
    check("前端路由（刷新子页面）回退到 index.html", status == 200 and html2 == html)
    asset = html.split('src="', 1)[1].split('"', 1)[0] if 'src="/assets/' in html else None
    if check("页面引用了打包后的 JS", asset is not None, html[:300]):
        with urllib.request.urlopen(BASE + asset) as resp:
            check("静态资源长期缓存", "immutable" in resp.headers.get("Cache-Control", ""), dict(resp.headers))
    status, docs = call("GET", "/v3/api-docs")
    check("接口文档可以访问", status == 200 and "/api/submissions" in json.dumps(docs), str(docs)[:200])


def test_auth():
    status, data = call("POST", "/api/auth/login", {"username": ADMIN[0], "password": ADMIN[1]})
    if not check("管理员登录", status == 200 and data["user"]["role"] == "ADMIN", data):
        raise SystemExit("管理员登录失败，请确认 .env 里的 OJ_ADMIN_PASSWORD")
    admin_token = data["token"]
    token, user = register("e2e")
    check("重复注册被拒绝", call("POST", "/api/auth/register", {"username": user["username"], "password": "secret123"})[0] == 409)
    check("密码错误不能登录", call("POST", "/api/auth/login", {"username": user["username"], "password": "wrong-pass"})[0] == 401)
    check("不合法的用户名被拒绝", call("POST", "/api/auth/register", {"username": "a b", "password": "secret123"})[0] == 400)
    check("获取当前用户", call("GET", "/api/auth/me", token=token)[1].get("username") == user["username"])
    check("伪造的 token 被拒绝", call("GET", "/api/auth/me", token=token[:-4] + "abcd")[0] == 401)
    status, _ = call("POST", "/api/auth/password", {"oldPassword": "secret123", "newPassword": "secret456"}, token)
    status2, _ = call("POST", "/api/auth/login", {"username": user["username"], "password": "secret456"})
    check("修改密码后用新密码登录", status == 200 and status2 == 200, (status, status2))
    return admin_token


def test_problems():
    status, page = call("GET", "/api/problems?size=100")
    check("题目列表包含 8 道示例题", status == 200 and page["total"] >= 8, page)
    problems = {p["title"]: p["id"] for p in page["items"]}
    hard = call("GET", "/api/problems?difficulty=HARD&size=100")[1]["items"]
    check("按难度筛选", [p["title"] for p in hard] == ["最长上升子序列"], hard)
    by_tag = call("GET", "/api/problems?keyword=%E6%A0%88")[1]["items"]  # 关键字「栈」（标签）
    check("按标签搜索", [p["title"] for p in by_tag] == ["括号匹配"], by_tag)
    detail = call("GET", "/api/problems/%d" % problems["走迷宫"])[1]
    check("题目详情和样例", detail["samples"][0]["output"].strip() == "5" and detail["hasEditorial"], detail)
    editorial = call("GET", "/api/problems/%d/editorial" % problems["走迷宫"])[1]["editorial"]
    check("题解", "广度优先搜索" in editorial and "```cpp" in editorial, editorial[:200])
    check("不存在的题目返回 404", call("GET", "/api/problems/99999")[0] == 404)
    return problems


def test_reference_solutions(problems):
    """每道示例题的标准解法都要 AC，错误解法要得到预期的结果；三个用户并行提交。"""
    fixtures = json.loads((ROOT / "tests/fixtures/solutions.json").read_text(encoding="utf-8"))
    tasks = []
    for item in fixtures:
        for lang, code in item["solutions"].items():
            tasks.append((item["title"], lang, code, "AC", "标准解法"))
        for wrong in item["wrong"]:
            tasks.append((item["title"], wrong["language"], wrong["code"], wrong["verdict"], wrong["note"]))
    workers = [register("solver") for _ in range(3)]
    lanes = [tasks[i::3] for i in range(3)]
    solved = [set() for _ in range(3)]

    def run_lane(i):
        token = workers[i][0]
        for title, lang, code, expected, note in lanes[i]:
            started = time.time()
            r = judge(token, problems[title], lang, code)
            if r.get("verdict") == "AC":
                solved[i].add(title)
            check("%s / %s（%s）：期望 %s，实际 %s，%.1fs" % (title, lang, note, expected, r.get("verdict"), time.time() - started),
                  r.get("verdict") == expected, json.dumps({k: r.get(k) for k in ("verdict", "compileMessage", "cases")},
                                                           ensure_ascii=False))

    with ThreadPoolExecutor(3) as pool:
        list(pool.map(run_lane, range(3)))
    for (token, user), titles in zip(workers, solved):
        me = call("GET", "/api/auth/me", token=token)[1]
        check("%s 的通过题数统计正确（%d）" % (user["username"], len(titles)), me["solvedCount"] == len(titles), me)
    ranking = call("GET", "/api/ranking?size=100")[1]["items"]
    check("排行榜按通过题数排序", ranking == sorted(ranking, key=lambda r: (-r["solvedCount"], r["submitCount"])) or
          all(ranking[i]["solvedCount"] >= ranking[i + 1]["solvedCount"] for i in range(len(ranking) - 1)), ranking[:5])
    return workers[0][0]


def test_visibility(admin_token, problems):
    owner, _ = register("owner")
    other, _ = register("other")
    r = judge(owner, problems["A + B"], "python", "print(sum(map(int, input().split())))\n")
    sid = r["id"]
    check("本人能看到自己的代码", call("GET", "/api/submissions/%d" % sid, token=owner)[1]["code"] is not None)
    check("其他用户看不到代码", call("GET", "/api/submissions/%d" % sid, token=other)[1]["code"] is None)
    check("未登录看不到代码", call("GET", "/api/submissions/%d" % sid)[1]["code"] is None)
    check("管理员能看到代码", call("GET", "/api/submissions/%d" % sid, token=admin_token)[1]["code"] is not None)
    mine = call("GET", "/api/submissions?mine=true", token=owner)[1]["items"]
    check("只看我的提交", [s["id"] for s in mine] == [sid], mine)
    check("未登录不能提交", call("POST", "/api/submissions", {"problemId": problems["A + B"], "language": "c", "code": "x"})[0] == 401)
    return sid


def test_run(problems):
    token, _ = register("runner")
    status, r = call("POST", "/api/run", {"problemId": problems["回文串"], "language": "cpp",
                                          "code": "#include <iostream>\nint main(){std::string s;std::cin>>s;std::cout<<s.size()<<'\\n';}",
                                          "input": "level\n"}, token)
    check("自测运行返回输出", status == 200 and r["verdict"] == "OK" and r["stdout"] == "5\n", r)
    status, r = call("POST", "/api/run", {"language": "java", "code": "public class Main{", "input": ""}, token)
    check("自测运行编译错误", status == 200 and r["verdict"] == "CE" and "Main.java" in r["compileMessage"], r)


def test_admin(admin_token):
    form = {"title": "A - B（测试）", "difficulty": "EASY", "tags": ["测试"], "description": "输出 $a - b$。",
            "inputFormat": "两个整数", "outputFormat": "一个整数", "samples": [{"input": "3 1", "output": "2"}],
            "hint": "", "editorial": "", "timeLimitMs": 1000, "memoryLimitMb": 128, "visible": False,
            "testCases": [{"input": "3 1\n", "output": "2\n"}, {"input": "-1 -1\n", "output": "0\n"}]}
    user, _ = register("viewer")
    check("普通用户不能创建题目", call("POST", "/api/admin/problems", form, user)[0] == 403)
    status, created = call("POST", "/api/admin/problems", form, admin_token)
    if not check("管理员创建题目", status == 201, created):
        return
    pid = created["id"]
    check("隐藏题目对普通用户不可见", call("GET", "/api/problems/%d" % pid, token=user)[0] == 404)
    check("隐藏题目管理员可见", call("GET", "/api/problems/%d" % pid, token=admin_token)[0] == 200)
    check("隐藏题目不出现在题库列表", pid not in [p["id"] for p in call("GET", "/api/problems?size=100")[1]["items"]])
    code = "a, b = map(int, input().split())\nprint(a - b)\n"
    check("管理员可以提交隐藏题目", judge(admin_token, pid, "python", code).get("verdict") == "AC")
    form.update(visible=True, testCases=form["testCases"] + [{"input": "0 5\n", "output": "-5\n"}])
    check("修改题目", call("PUT", "/api/admin/problems/%d" % pid, form, admin_token)[0] == 200)
    full = call("GET", "/api/admin/problems/%d" % pid, token=admin_token)[1]
    check("修改后测试数据变为 3 组", len(full["testCases"]) == 3, full)
    r = judge(user, pid, "python", code)
    check("公开后普通用户可以提交", r.get("verdict") == "AC" and r.get("totalCount") == 3, r)
    solved_before = call("GET", "/api/auth/me", token=user)[1]["solvedCount"]
    check("删除题目", call("DELETE", "/api/admin/problems/%d" % pid, token=admin_token)[0] == 204)
    check("删除后题目不存在", call("GET", "/api/problems/%d" % pid)[0] == 404)
    check("删除题目后重新统计通过题数", call("GET", "/api/auth/me", token=user)[1]["solvedCount"] == solved_before - 1)


def test_ai(admin_token, problems, foreign_submission):
    token, _ = register("aiuser")
    status = call("GET", "/api/ai/status", token=token)[1]
    check("AI 助教已开启", status["enabled"] and status["remaining"] == status["dailyLimit"], status)
    limit = status["dailyLimit"]

    pid = problems["数组求和"]
    wrong = ("#include <iostream>\nusing namespace std;\nint main(){int n,s=0,x;cin>>n;"
             "while(n--){cin>>x;s+=x;}cout<<s<<endl;}\n")
    r = judge(token, pid, "cpp", wrong)
    check("先提交一份会溢出的代码", r.get("verdict") == "WA", r)

    status_code, events = sse("/api/ai/tutor", {"problemId": pid, "mode": "HINT", "language": "cpp", "code": wrong}, token)
    if not check("AI 流式回答", status_code == 200 and events and events[-1][1] == "done", events):
        return
    text = "".join(json.loads(d)["text"] for _, e, d in events if e == "delta")
    reasoning = "".join(json.loads(d)["text"] for _, e, d in events if e == "reasoning")
    check("回答内容完整", "这是关于「数组求和」的回答" in text and text.endswith("第二段。"), text)
    check("转发了思考过程", reasoning == "先看看题目要求。", reasoning)
    deltas = [t for t, e, _ in events if e in ("delta", "reasoning")]
    check("经过 nginx 后仍是逐段到达（没有被缓冲）", deltas[-1] - deltas[0] > 0.6, deltas)

    sent = call("GET", "/last", base=MOCK_AI)[1]
    body, messages = sent["body"], sent["body"]["messages"]
    check("请求了正确的模型和参数", body["model"] == "deepseek-flash" and body["stream"] is True
          and body.get("thinking") == {"type": "disabled"} and sent["authorization"] == "Bearer test-key", body)
    check("系统提示包含题面和题解", messages[0]["role"] == "system" and "# 题目：数组求和" in messages[0]["content"]
          and "long long" in messages[0]["content"], messages[0]["content"][:300])
    last = messages[-1]["content"]
    check("提问包含模式、代码和评测结果", "【提示思路】" in last and "s+=x" in last and "答案错误（WA）" in last
          and "通过 4/8" in last, last)
    hidden = call("GET", "/api/admin/problems/%d" % pid, token=admin_token)[1]["testCases"]
    leaked = [c for c in hidden[1:] if len(c["input"]) > 40 and c["input"][:40] in json.dumps(body, ensure_ascii=False)]
    check("隐藏测试数据没有发给 AI", not leaked, leaked[:1])

    history = [{"role": "user", "content": "💡 给点思路"}, {"role": "assistant", "content": text}]
    status_code, events = sse("/api/ai/tutor", {"problemId": pid, "mode": "CHAT", "language": "cpp", "code": wrong,
                                                "history": history, "question": "为什么 int 不够？"}, token)
    messages = call("GET", "/last", base=MOCK_AI)[1]["body"]["messages"]
    check("追问带上了对话历史", status_code == 200 and [m["role"] for m in messages] == ["system", "user", "assistant", "user"]
          and "为什么 int 不够？" in messages[-1]["content"], [m["role"] for m in messages])

    sse("/api/ai/tutor", {"problemId": problems["A + B"], "mode": "DEBUG", "language": "python", "code": "print(1)",
                          "submissionId": foreign_submission}, token)
    last = call("GET", "/last", base=MOCK_AI)[1]["body"]["messages"][-1]["content"]
    check("不会把别人的提交结果发给 AI", "最近一次提交" not in last, last)

    before = call("GET", "/api/ai/status", token=token)[1]["remaining"]
    status_code, events = sse("/api/ai/tutor", {"problemId": pid, "mode": "CHAT", "question": "MOCK_FAIL"}, token)
    error = [json.loads(d)["message"] for _, e, d in events if e == "error"]
    check("AI 服务出错时返回错误事件", error and "mock upstream failure" in error[0], events)
    check("出错的提问不扣次数", call("GET", "/api/ai/status", token=token)[1]["remaining"] == before)

    check("追问必须填写问题", sse("/api/ai/tutor", {"problemId": pid, "mode": "CHAT"}, token)[0] == 400)
    used = limit - before
    for _ in range(limit - used):
        sse("/api/ai/tutor", {"problemId": pid, "mode": "HINT"}, token)
    status_code, data = sse("/api/ai/tutor", {"problemId": pid, "mode": "HINT"}, token)
    check("超过每日次数后被拒绝", status_code == 429 and "用完" in data.get("message", ""), (status_code, data))
    check("管理员不限次数", call("GET", "/api/ai/status", token=admin_token)[1]["remaining"] is None)
    req = urllib.request.Request(BASE + "/api/ai/tutor", data=b'{"problemId":1,"mode":"HINT"}', method="POST",
                                 headers={"Content-Type": "application/json"})
    try:
        urllib.request.urlopen(req)
        check("未登录不能使用 AI", False)
    except urllib.error.HTTPError as e:
        check("未登录不能使用 AI", e.code == 401, e.code)


def test_ports_not_exposed():
    for port, name in ((8000, "判题机"), (3306, "MySQL"), (8081, "后端")):
        with socket.socket() as s:
            s.settimeout(1)
            check("%s端口 %d 没有暴露到本机" % (name, port), s.connect_ex(("127.0.0.1", port)) != 0)


def main():
    wait_ready()
    test_frontend()
    admin_token = test_auth()
    problems = test_problems()
    test_reference_solutions(problems)
    foreign = test_visibility(admin_token, problems)
    test_run(problems)
    test_admin(admin_token)
    test_ai(admin_token, problems, foreign)
    test_ports_not_exposed()
    failed = _results.count(False)
    print("\n%d / %d 通过" % (len(_results) - failed, len(_results)))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
