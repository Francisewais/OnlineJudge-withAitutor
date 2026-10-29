"""判题服务 HTTP 接口，只在内部网络里给后端调用，请求必须带 X-Judge-Token。"""
import hmac
import logging
import os
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field, field_validator

from . import judge
from .languages import LANGUAGES

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("judger")

TOKEN = os.environ.get("JUDGE_TOKEN", "")
if not TOKEN:
    raise RuntimeError("必须通过环境变量 JUDGE_TOKEN 设置判题服务的访问令牌")

SELF_CHECK = {
    "c": '#include <stdio.h>\nint main(){int a,b;scanf("%d%d",&a,&b);printf("%d\\n",a+b);return 0;}',
    "python": "a, b = map(int, input().split())\nprint(a + b)",
}


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动时实际评测一次 A+B，沙箱或容器配置有问题（如 tmpfs 不可执行）时直接退出，而不是把所有提交判成 RE
    for language, source in SELF_CHECK.items():
        result = judge.judge(language, source, [{"input": "1 2\n", "output": "3\n"}], 2000, 128)
        if result["verdict"] != "AC":
            raise RuntimeError("判题机自检失败（%s）：%s" % (language, result))
    log.info("self check passed")
    yield


app = FastAPI(title="Judger", docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan)


def require_token(x_judge_token: str = Header(default="")) -> None:
    if not hmac.compare_digest(x_judge_token.encode(), TOKEN.encode()):
        raise HTTPException(status_code=401, detail="invalid judge token")


class Limits(BaseModel):
    language: str
    source: str = Field(max_length=64 * 1024)
    time_limit_ms: int = Field(ge=100, le=10_000)
    memory_limit_mb: int = Field(ge=16, le=1024)

    @field_validator("language")
    @classmethod
    def known_language(cls, v: str) -> str:
        if v not in LANGUAGES:
            raise ValueError("unsupported language: " + v)
        return v


class TestCase(BaseModel):
    input: str
    output: str


class JudgeRequest(Limits):
    test_cases: list[TestCase] = Field(min_length=1, max_length=200)


class CaseResult(BaseModel):
    verdict: str
    time_ms: int
    memory_kb: int
    exit_code: int
    signal: int
    message: str


class JudgeResponse(BaseModel):
    verdict: str
    compile_message: str
    time_ms: int
    memory_kb: int
    cases: list[CaseResult]


class RunRequest(Limits):
    input: str = Field(default="", max_length=1024 * 1024)


class RunResponse(BaseModel):
    verdict: str
    compile_message: str = ""
    stdout: str = ""
    stderr: str = ""
    time_ms: int = 0
    memory_kb: int = 0
    exit_code: int = 0
    signal: int = 0
    message: str = ""


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.get("/languages")
def languages() -> list[dict]:
    return [{"id": lang.id, "name": lang.name, "time_factor": lang.time_factor} for lang in LANGUAGES.values()]


@app.post("/judge", dependencies=[Depends(require_token)])
def judge_submission(req: JudgeRequest) -> JudgeResponse:
    result = judge.judge(req.language, req.source, [c.model_dump() for c in req.test_cases],
                         req.time_limit_ms, req.memory_limit_mb)
    log.info("judge %s cases=%d -> %s", req.language, len(req.test_cases), result["verdict"])
    return JudgeResponse(**result)


@app.post("/run", dependencies=[Depends(require_token)])
def run_code(req: RunRequest) -> RunResponse:
    result = judge.run_with_input(req.language, req.source, req.input, req.time_limit_ms, req.memory_limit_mb)
    log.info("run %s -> %s", req.language, result["verdict"])
    return RunResponse(**result)
