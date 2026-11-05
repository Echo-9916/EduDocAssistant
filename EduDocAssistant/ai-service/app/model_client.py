"""OpenAI 兼容适配层（D1：硅基流动一路凭据）。

httpx 直连 chat/embeddings/rerank；超时分级 + 429/5xx/超时重试一次 + Retry-After 透传；
错误映射 MODEL_FAILED/TIMEOUT/BAD_INPUT，可解释失败码，绝不回显 API Key。
"""

import json
import re
import time

import httpx

from .config import TIMEOUTS, Settings

PROVIDER = "siliconflow-compatible"


class ModelError(Exception):
    def __init__(self, code: str, message: str, model: str = "", elapsed_ms: int = 0,
                 retry_after: str | None = None):
        super().__init__(message)
        self.code = code
        self.message = message
        self.provider = PROVIDER
        self.model = model
        self.elapsed_ms = elapsed_ms
        self.retry_after = retry_after


def extract_json(text: str):
    """宽容剥壳（代码围栏/前后废话）取第一个完整 JSON 值；失败抛 ValueError。"""
    stripped = text.strip()
    fence = re.search(r"```(?:json)?\s*(.+?)\s*```", stripped, re.S)
    if fence:
        stripped = fence.group(1).strip()
    if not (stripped.startswith("{") or stripped.startswith("[")):
        start_obj, start_arr = stripped.find("{"), stripped.find("[")
        candidates = [i for i in (start_obj, start_arr) if i >= 0]
        if not candidates:
            raise ValueError("模型输出不含 JSON")
        stripped = stripped[min(candidates):]
    try:
        return json.loads(stripped)
    except json.JSONDecodeError as e:
        raise ValueError(f"JSON 解析失败：{e}") from e


class ModelClient:
    def __init__(self, settings: Settings, transport: httpx.BaseTransport | None = None):
        self.settings = settings
        self._http = httpx.Client(
            base_url=settings.base_url.rstrip("/") + "/",
            headers={"Authorization": f"Bearer {settings.api_key}"} if settings.api_key else {},
            transport=transport,
        )

    # ---- 底层：一次重试 + 错误映射 ----

    def _post(self, path: str, payload: dict, timeout_key: str) -> dict:
        timeout = TIMEOUTS[timeout_key]
        deadline = time.monotonic() + timeout * 2 + 1  # 重试总预算封顶
        last_error = None
        for attempt in (0, 1):
            if time.monotonic() > deadline:
                break
            try:
                resp = self._http.post(path, json=payload, timeout=timeout)
            except httpx.TimeoutException:
                last_error = ("TIMEOUT", f"{path} 调用超时（{timeout}s）", None)
                continue
            except httpx.HTTPError as e:
                last_error = ("MODEL_FAILED", f"{path} 网络失败：{type(e).__name__}", None)
                continue
            if resp.status_code == 429 or resp.status_code >= 500:
                retry_after = resp.headers.get("Retry-After")
                detail = _safe_detail(resp)
                last_error = ("MODEL_FAILED" if resp.status_code < 500 else "MODEL_FAILED",
                              f"{path} 上游 {resp.status_code}：{detail}", retry_after)
                continue
            if resp.status_code >= 400:
                # 4xx（非 429）不重试：请求问题
                raise ModelError("BAD_INPUT" if resp.status_code == 400 else "MODEL_FAILED",
                                 f"{path} 上游 {resp.status_code}：{_safe_detail(resp)}")
            return resp.json()
        code, message, retry_after = last_error
        raise ModelError(code, message, retry_after=retry_after)

    # ---- 六类能力 ----

    def embed(self, texts: list[str]) -> tuple[list[list[float]], str]:
        data = self._post("embeddings", {"model": self.settings.embedding_model, "input": texts},
                          "embed")
        items = sorted(data.get("data", []), key=lambda d: d.get("index", 0))
        vectors = [item["embedding"] for item in items]
        if len(vectors) != len(texts):
            raise ModelError("MODEL_FAILED",
                             f"embed 返回数量不对齐：请求 {len(texts)}，返回 {len(vectors)}",
                             model=self.settings.embedding_model)
        expected = self.settings.embedding_dimension
        for i, vector in enumerate(vectors):
            if len(vector) != expected:
                raise ModelError("MODEL_FAILED",
                                 f"向量维度不符：期望 {expected}，第 {i} 条为 {len(vector)}",
                                 model=self.settings.embedding_model)
        return vectors, self.settings.embedding_model

    def rerank(self, query: str, documents: list[str], top_n: int) -> list[dict]:
        data = self._post("rerank", {
            "model": self.settings.rerank_model, "query": query,
            "documents": documents, "top_n": top_n, "return_documents": False,
        }, "rerank")
        return [{"index": r["index"], "relevanceScore": r["relevance_score"]}
                for r in data.get("results", [])]

    def chat_json(self, user_prompt: str, max_tokens: int, timeout_key: str = "generate",
                  model: str | None = None) -> tuple[dict, dict]:
        """对话并要求 JSON：解析失败重试一次（换更严提示），仍失败报 MODEL_FAILED。"""
        prompt = user_prompt
        for attempt in (0, 1):
            content, usage, model_name = self._chat(prompt, max_tokens, timeout_key, model=model)
            try:
                parsed = extract_json(content)
                if not isinstance(parsed, dict):
                    raise ValueError("期望 JSON 对象")
                return parsed, usage | {"model": model_name}
            except ValueError as e:
                prompt = user_prompt + f"\n\n上次输出无法解析（{e}）。只输出合法 JSON 对象。"
        raise ModelError("MODEL_FAILED", "模型输出无法解析为 JSON（已重试一次）")

    def chat_text(self, user_prompt: str, max_tokens: int, timeout_key: str = "generate") -> tuple[str, dict]:
        # 审查修复：纯文本任务用中性 system——强 JSON 指令会污染润色输出；剥壳兜底防围栏
        from .prompts import SYSTEM_PLAIN_TEXT

        content, usage, model = self._chat(user_prompt, max_tokens, timeout_key, system=SYSTEM_PLAIN_TEXT)
        return _strip_fences(content), usage | {"model": model}

    def _chat(self, user_prompt: str, max_tokens: int, timeout_key: str,
              system: str | None = None, model: str | None = None) -> tuple[str, dict, str]:
        from .prompts import SYSTEM_STRICT_JSON

        messages = ([{"role": "system", "content": system or SYSTEM_STRICT_JSON}]
                    + [{"role": "user", "content": user_prompt}])
        data = self._post("chat/completions", {
            "model": model or self.settings.chat_model,
            "messages": messages,
            "temperature": 0.2,
            "max_tokens": max(1, min(int(max_tokens), 4000)),
        }, timeout_key)
        try:
            content = data["choices"][0]["message"]["content"]
        except (KeyError, IndexError) as e:
            raise ModelError("MODEL_FAILED", "模型响应缺少 choices[0].message.content") from e
        usage = data.get("usage", {}) or {}
        return (content,
                {"inputTokens": int(usage.get("prompt_tokens", 0) or 0),
                 "outputTokens": int(usage.get("completion_tokens", 0) or 0)},
                data.get("model", self.settings.chat_model))


def _strip_fences(text: str) -> str:
    """剥 ``` 围栏壳（兜底），其余原样。"""
    stripped = text.strip()
    fence = re.match(r"^```(?:\w+)?\s*\n(.*)\n```$", stripped, re.S)
    return (fence.group(1) if fence else stripped).strip()


def _safe_detail(resp: httpx.Response) -> str:
    """上游错误体摘要：截断且过滤任何疑似密钥串，不回显。"""
    text = resp.text[:300]
    return re.sub(r"sk-[A-Za-z0-9_\-]{6,}", "***", text)
