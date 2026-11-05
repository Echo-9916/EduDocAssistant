"""edudoc-ai-service 入口（C-09）：六类内部接口 + 服务间鉴权 + trace 透传 + 结构化错误。

错误响应形态 {code: MODEL_FAILED|TIMEOUT|BAD_INPUT, message, provider, model, elapsedMs}，
绝不回显 API Key；/v1/* 需 X-Internal-Token（缺失配置=503，不匹配=401），/health /smoke 免鉴权。
启动：uv sync && uvicorn app.main:app --port 8000（见 README.md）。
"""

import logging
import time

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from . import prompts
from .config import Settings
from .model_client import ModelClient, ModelError

log = logging.getLogger("edudoc.ai")


class Chunk(BaseModel):
    chunkId: str
    text: str


class HistoryMsg(BaseModel):
    role: str
    content: str


class EmbedRequest(BaseModel):
    texts: list[str] = Field(min_length=1)


class ChunksRequest(BaseModel):
    chunks: list[Chunk] = Field(min_length=1)


class RerankRequest(BaseModel):
    query: str = Field(min_length=1)
    documents: list[str] = Field(min_length=1)
    topN: int = Field(default=4, ge=1, le=64)


class AnswerRequest(BaseModel):
    question: str = Field(min_length=1)
    evidence: list[Chunk] = Field(min_length=1)
    history: list[HistoryMsg] = Field(default_factory=list)
    maxTokens: int = Field(default=900, ge=1, le=1600)


class PolishRequest(BaseModel):
    paragraphs: list[str] = Field(min_length=1)


def create_app(settings: Settings | None = None,
               client: ModelClient | None = None) -> FastAPI:
    settings = settings or Settings.from_env()
    app = FastAPI(title="edudoc-ai-service")
    app.state.settings = settings
    app.state.client = client or ModelClient(settings)

    @app.middleware("http")
    async def guard(request: Request, call_next):
        path = request.url.path
        trace = request.headers.get("x-trace-id", "")
        if path.startswith("/v1/"):
            if not settings.internal_token:
                response = _error(503, "MODEL_FAILED", "服务端未配置 EDUDOC_AI_INTERNAL_TOKEN")
            elif request.headers.get("x-internal-token") != settings.internal_token:
                response = _error(401, "BAD_INPUT", "X-Internal-Token 校验失败")
            else:
                response = None
            if response is not None:
                if trace:
                    response.headers["X-Trace-Id"] = trace
                return response
        started = time.monotonic()
        response = await call_next(request)
        if trace:
            response.headers["X-Trace-Id"] = trace
        if path.startswith("/v1/"):
            log.info("POST %s status=%s trace=%s elapsedMs=%d", path, response.status_code,
                     trace or "-", int((time.monotonic() - started) * 1000))
        return response

    @app.exception_handler(ModelError)
    async def model_error_handler(_: Request, exc: ModelError):
        status = {"TIMEOUT": 504, "BAD_INPUT": 400}.get(exc.code, 502)
        response = _error(status, exc.code, exc.message, exc.model, exc.elapsed_ms)
        if exc.retry_after:
            response.headers["Retry-After"] = exc.retry_after
        return response

    @app.exception_handler(ValueError)
    async def value_error_handler(_: Request, exc: ValueError):
        return _error(400, "BAD_INPUT", f"输入非法：{exc}")

    @app.get("/health")
    def health():
        return {"service": "ai-service", "status": "UP"}

    @app.get("/smoke")
    def smoke():
        import sys

        return {"service": "ai-service", "python": sys.version.split()[0], "status": "ok"}

    @app.post("/v1/embed")
    def embed(req: EmbedRequest):
        vectors, model = app.state.client.embed(req.texts)
        return {"model": model, "vectors": vectors}

    @app.post("/v1/summarize")
    def summarize(req: ChunksRequest):
        chunks = [c.model_dump() for c in req.chunks]
        parsed, meta = app.state.client.chat_json(prompts.summarize_prompt(chunks), 2000)
        segments = [{"text": str(s.get("text", "")),
                     "chunkIds": [str(x) for x in s.get("chunkIds", [])]}
                    for s in parsed.get("segments", [])]
        return _generation_body(meta, segments=segments)

    @app.post("/v1/extract-keypoints")
    def extract_keypoints(req: ChunksRequest):
        chunks = [c.model_dump() for c in req.chunks]
        parsed, meta = app.state.client.chat_json(prompts.keypoints_prompt(chunks), 1500)
        keypoints = [{"text": str(k.get("text", "")), "chunkId": k.get("chunkId")}
                     for k in parsed.get("keypoints", [])]
        return _generation_body(meta, keypoints=keypoints)

    @app.post("/v1/answer")
    def answer(req: AnswerRequest):
        parsed, meta = app.state.client.chat_json(
            prompts.answer_prompt(req.question,
                                  [c.model_dump() for c in req.evidence],
                                  [h.model_dump() for h in req.history]),
            req.maxTokens, timeout_key="answer", model=settings.answer_model)
        refused = bool(parsed.get("refused", True)) or not parsed.get("answer")
        citations = [] if refused else [
            {"chunkId": str(c.get("chunkId", "")), "quote": c.get("quote")}
            for c in parsed.get("citations", [])]
        body = _generation_body(meta,
                                 answer="" if refused else str(parsed.get("answer", "")),
                                 citations=citations)
        body["refused"] = refused
        return body

    @app.post("/v1/polish")
    def polish(req: PolishRequest):
        model = settings.chat_model
        tokens_in = tokens_out = 0
        polished = []
        for paragraph in req.paragraphs:
            text, meta = app.state.client.chat_text(prompts.polish_prompt(paragraph), 1500,
                                                    timeout_key="polish")
            model = meta.get("model", model)
            tokens_in += meta.get("inputTokens", 0)
            tokens_out += meta.get("outputTokens", 0)
            polished.append({"text": text})
        return _generation_body({"model": model, "inputTokens": tokens_in,
                                 "outputTokens": tokens_out}, polished=polished)

    @app.post("/v1/rerank")
    def rerank(req: RerankRequest):
        results = app.state.client.rerank(req.query, req.documents, req.topN)
        return {"results": results}

    return app


def _generation_body(meta: dict, **payload) -> dict:
    body = dict(payload)
    body.update({"model": meta.get("model", ""),
                 "promptVersion": prompts.PROMPT_VERSION,
                 "usage": {"inputTokens": meta.get("inputTokens", 0),
                           "outputTokens": meta.get("outputTokens", 0)}})
    return body


def _error(status: int, code: str, message: str, model: str = "", elapsed_ms: int = 0) -> JSONResponse:
    return JSONResponse(status_code=status, content={
        "code": code, "message": message, "provider": "siliconflow-compatible",
        "model": model, "elapsedMs": elapsed_ms})


app = create_app()
