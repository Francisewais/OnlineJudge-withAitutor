"""模拟一个 OpenAI 兼容的流式 /chat/completions 接口，供端到端测试使用（不需要真实的 API Key）。

GET /last 返回最近一次收到的请求，测试用它检查后端拼好的提示词。
用户消息里包含 MOCK_FAIL 时返回 HTTP 500，用来测试出错处理。
"""
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

API_KEY = "test-key"
last_request: dict = {}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        if self.path == "/last":
            self.send_json(200, last_request)
        else:
            self.send_json(404, {"error": {"message": "not found"}})

    def do_POST(self):
        global last_request
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        last_request = {"authorization": self.headers.get("Authorization"), "body": body}
        if self.path != "/chat/completions":
            return self.send_json(404, {"error": {"message": "not found"}})
        if self.headers.get("Authorization") != "Bearer " + API_KEY:
            return self.send_json(401, {"error": {"message": "Authentication Fails (mock)"}})
        if "MOCK_FAIL" in body["messages"][-1]["content"]:
            return self.send_json(500, {"error": {"message": "mock upstream failure"}})

        title = next((line[len("# 题目："):] for line in body["messages"][0]["content"].splitlines()
                      if line.startswith("# 题目：")), "?")
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(b": keep-alive\n\n")
        pieces = [("reasoning_content", "先看看题目要求。"), ("content", "## 提示\n\n"),
                  ("content", "这是关于「%s」的回答。" % title), ("content", "\n\n第二段。")]
        for field, text in pieces:
            self.write_chunk({"delta": {field: text}, "finish_reason": None})
            time.sleep(0.3)
        self.write_chunk({"delta": {}, "finish_reason": "stop"})
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()

    def write_chunk(self, choice):
        chunk = {"id": "mock", "object": "chat.completion.chunk", "model": "mock", "choices": [dict(index=0, **choice)]}
        self.wfile.write(b"data: " + json.dumps(chunk, ensure_ascii=False).encode() + b"\n\n")
        self.wfile.flush()

    def send_json(self, status, data):
        raw = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 9000), Handler).serve_forever()
