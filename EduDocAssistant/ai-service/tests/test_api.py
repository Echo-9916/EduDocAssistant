"""C-09 pytest：httpx MockTransport 注入，覆盖批量对齐、引用解析、坏 JSON 重试、
token 校验、超时映射、Retry-After 透传、密钥不回显。"""

import json

import httpx
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.model_client import ModelClient

KEY = "sk-SECRET1234567890"
SETTINGS = Settings(api_key=KEY, base_url="https://provider.invalid/v1", chat_model="K",
                    answer_model="Fast-7B", embedding_model="bge-m3", rerank_model="rr",
                    embedding_dimension=4, internal_token="tok")


def make_client(responses):
    """responses: list of (status, body_dict_or_text, headers) / httpx exception / callable(request)->tuple。"""
    calls = []

    def handler(request: httpx.Request):
        calls.append(request)
        item = responses[min(len(calls) - 1, len(responses) - 1)]
        if isinstance(item, Exception):
            raise item
        if callable(item):
            item = item(request)
        status, body, headers = item
        if isinstance(body, (dict, list)):
            return httpx.Response(status, json=body, headers=headers)
        return httpx.Response(status, text=body, headers=headers)

    client = ModelClient(SETTINGS, transport=httpx.MockTransport(handler))
    app = create_app(SETTINGS, client)
    return TestClient(app, headers={"X-Internal-Token": "tok"}), calls


def chat_body(content, model="K"):
    return (200, {"choices": [{"message": {"content": content}}],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 5}, "model": model}, {})


def ok(responses):
    return make_client(responses)


# ---------- embed ----------

def test_embed_batch_alignment():
    http, calls = ok([(200, {"data": [
        {"index": 0, "embedding": [0.1, 0.2, 0.3, 0.4]},
        {"index": 1, "embedding": [0.5, 0.6, 0.7, 0.8]}]}, {})])
    resp = http.post("/v1/embed", json={"texts": ["甲", "乙"]})
    assert resp.status_code == 200
    body = resp.json()
    assert body["model"] == "bge-m3"
    assert len(body["vectors"]) == 2 and len(body["vectors"][0]) == 4
    assert json.loads(calls[0].content)["input"] == ["甲", "乙"]
    assert calls[0].headers["Authorization"] == f"Bearer {KEY}"


def test_embed_dimension_mismatch_is_502_structured():
    http, _ = ok([(200, {"data": [{"index": 0, "embedding": [1, 2]}]}, {})])
    resp = http.post("/v1/embed", json={"texts": ["a"]})
    assert resp.status_code == 502
    assert resp.json()["code"] == "MODEL_FAILED"
    assert "维度" in resp.json()["message"]


def test_embed_count_mismatch():
    http, _ = ok([(200, {"data": [{"index": 0, "embedding": [1, 2, 3, 4]}]}, {})])
    resp = http.post("/v1/embed", json={"texts": ["a", "b"]})
    assert resp.status_code == 502
    assert "不对齐" in resp.json()["message"]


# ---------- 鉴权与 trace ----------

def test_missing_and_wrong_internal_token():
    http, _ = ok([(200, {}, {})])
    plain = TestClient(create_app(SETTINGS, ModelClient(SETTINGS, transport=httpx.MockTransport(
        lambda r: httpx.Response(200, json={})  # noqa: S306
    ))))
    assert plain.post("/v1/embed", json={"texts": ["a"]}).status_code == 401
    assert http.post("/v1/embed", json={"texts": ["a"]},
                     headers={"X-Internal-Token": "WRONG"}).status_code == 401
    assert http.get("/health").status_code == 200  # 健康检查免鉴权


def test_token_not_configured_is_503():
    settings = Settings(api_key=KEY, base_url="https://x.invalid/v1", chat_model="K",
                        answer_model="Fast-7B", embedding_model="E", rerank_model="R",
                        embedding_dimension=4, internal_token="")
    http = TestClient(create_app(settings, ModelClient(settings, transport=httpx.MockTransport(
        lambda r: httpx.Response(200, json={})  # noqa: S306
    ))), headers={"X-Internal-Token": "anything"})
    assert http.post("/v1/embed", json={"texts": ["a"]}).status_code == 503


def test_trace_id_passthrough_response_header():
    http, calls = ok([(200, {"data": [{"index": 0, "embedding": [1, 2, 3, 4]}]}, {})])
    resp = http.post("/v1/embed", json={"texts": ["a"]}, headers={"X-Trace-Id": "abc123"})
    assert resp.headers["X-Trace-Id"] == "abc123"  # 响应回写 trace（鉴权通过本身即证明入站头被校验）
    assert "Authorization" in calls[0].headers  # 出站携带模型凭据


# ---------- summarize / keypoints ----------

def test_summarize_parses_fenced_json_and_returns_prompt_version():
    fenced = "```json\n{\"segments\": [{\"text\": \"结论一\", \"chunkIds\": [\"C1\"]}]}\n```"
    http, calls = ok([chat_body(fenced)])
    resp = http.post("/v1/summarize", json={"chunks": [{"chunkId": "C1", "text": "原文"}]})
    assert resp.status_code == 200
    body = resp.json()
    assert body["segments"] == [{"text": "结论一", "chunkIds": ["C1"]}]
    assert body["promptVersion"] == "v1"
    assert body["usage"] == {"inputTokens": 10, "outputTokens": 5}
    sent = json.loads(calls[0].content)
    assert "C1" in sent["messages"][1]["content"]  # chunkId 进入提示


def test_bad_json_retried_once_then_structured_error():
    http, calls = ok([chat_body("这不是 JSON"), chat_body("还这不是 JSON")])
    resp = http.post("/v1/extract-keypoints", json={"chunks": [{"chunkId": "C1", "text": "文"}]})
    assert resp.status_code == 502
    assert "无法解析" in resp.json()["message"]
    assert len(calls) == 2  # 恰好重试一次


def test_valid_json_wrong_structure_is_structured_502():
    # 1105 审查 P2-2：{"segments":["bad"]} 不得裸 500，必须 MODEL_FAILED 结构化
    http, _ = ok([chat_body('{"segments": ["不是对象"]}'), chat_body('{"segments": ["还是不是"]}'),
                  chat_body('{"segments": ["仍不是"]}'), chat_body('{"segments": ["终不是"]}')])
    resp = http.post("/v1/summarize", json={"chunks": [{"chunkId": "C1", "text": "文"}]})
    assert resp.status_code == 502
    assert resp.json()["code"] == "MODEL_FAILED"
    assert "结构" in resp.json()["message"]


def test_summarize_reduce_flag_uses_merge_prompt():
    http, calls = ok([chat_body('{"segments": [{"text": "合并摘要", "chunkIds": ["C1", "C2"]}]}')])
    resp = http.post("/v1/summarize", json={
        "chunks": [{"chunkId": "C1", "text": "摘要段一"}, {"chunkId": "C2", "text": "摘要段二"}],
        "reduce": True})
    assert resp.status_code == 200
    assert resp.json()["segments"] == [{"text": "合并摘要", "chunkIds": ["C1", "C2"]}]
    sent = json.loads(calls[0].content)["messages"][1]["content"]
    assert "融合重写" in sent  # reduce 专用提示词


def test_answer_citations_missing_defaults_to_empty():
    http, _ = ok([chat_body('{"refused": false, "answer": "有据可答"}')])  # citations 省略合法
    resp = http.post("/v1/answer", json={"question": "问？",
                                         "evidence": [{"chunkId": "C1", "text": "据"}]})
    body = resp.json()
    assert body["refused"] is False and body["citations"] == []


def test_keypoints_returns_array_shape():
    http, _ = ok([chat_body(json.dumps({"keypoints": [{"text": "要点", "chunkId": "C2"}]},
                                       ensure_ascii=False))])
    resp = http.post("/v1/extract-keypoints", json={"chunks": [{"chunkId": "C2", "text": "文"}]})
    assert resp.json()["keypoints"] == [{"text": "要点", "chunkId": "C2"}]


# ---------- answer ----------

def test_answer_with_citations_and_history_in_prompt():
    payload = {"refused": False, "answer": "依据是 X", "citations": [{"chunkId": "C1", "quote": "X 原文"}]}
    http, calls = ok([chat_body(json.dumps(payload, ensure_ascii=False))])
    resp = http.post("/v1/answer", json={
        "question": "X 是什么？",
        "evidence": [{"chunkId": "C1", "text": "X 原文在此"}],
        "history": [{"role": "user", "content": "上一问"}, {"role": "assistant", "content": "上一答"}],
        "maxTokens": 900})
    body = resp.json()
    assert body["refused"] is False
    assert body["answer"] == "依据是 X"
    assert body["citations"] == [{"chunkId": "C1", "quote": "X 原文"}]
    sent = json.loads(calls[0].content)
    sent_prompt = sent["messages"][1]["content"]
    assert "上一问" in sent_prompt and "上一答" in sent_prompt  # 历史由服务端拼接
    assert sent["max_tokens"] == 900
    assert sent["model"] == "Fast-7B"  # P-05：答案走独立快模型位，生成任务仍走 chat_model


def test_answer_refused_flag():
    http, _ = ok([chat_body(json.dumps({"refused": True}))])
    resp = http.post("/v1/answer", json={"question": "文档外？",
                                         "evidence": [{"chunkId": "C1", "text": "无关"}]})
    body = resp.json()
    assert body["refused"] is True and body["answer"] == "" and body["citations"] == []


def test_answer_timeout_maps_504_after_one_retry():
    http, calls = ok([httpx.TimeoutException("read timeout")])
    resp = http.post("/v1/answer", json={"question": "慢？",
                                         "evidence": [{"chunkId": "C1", "text": "文"}]})
    assert resp.status_code == 504
    assert resp.json()["code"] == "TIMEOUT"
    assert len(calls) == 2  # 重试一次


# ---------- rerank / polish ----------

def test_rerank_maps_scores():
    http, _ = ok([(200, {"results": [{"index": 1, "relevance_score": 0.9},
                                     {"index": 0, "relevance_score": 0.2}]}, {})])
    resp = http.post("/v1/rerank", json={"query": "问", "documents": ["a", "b"], "topN": 2})
    assert resp.json()["results"] == [{"index": 1, "relevanceScore": 0.9},
                                      {"index": 0, "relevanceScore": 0.2}]


def test_polish_keeps_paragraph_alignment():
    http, calls = ok([chat_body("润一"), chat_body("润二")])
    resp = http.post("/v1/polish", json={"paragraphs": ["原一", "原二"]})
    body = resp.json()
    assert body["polished"] == [{"text": "润一"}, {"text": "润二"}]
    assert len(calls) == 2
    assert body["usage"] == {"inputTokens": 20, "outputTokens": 10}


def test_polish_uses_plain_system_and_strips_fences():
    # 审查修复：纯文本润色不得带强 JSON system；围栏壳兜底剥离
    http, calls = ok([chat_body("```\n润一\n```"), chat_body("润二")])
    resp = http.post("/v1/polish", json={"paragraphs": ["原一", "原二"]})
    assert resp.json()["polished"] == [{"text": "润一"}, {"text": "润二"}]
    sent = json.loads(calls[0].content)
    assert "JSON" not in sent["messages"][0]["content"]
    assert "润色" in sent["messages"][0]["content"]


# ---------- 错误卫生 ----------

def test_upstream_error_body_key_masked_and_retry_after_passthrough():
    leaky = (500, "upstream failed: key sk-ABCDEF123456 detail", {"Retry-After": "30"})
    http, calls = ok([leaky, leaky])
    resp = http.post("/v1/embed", json={"texts": ["a"]})
    assert resp.status_code == 502
    text = resp.text
    assert "sk-ABCDEF123456" not in text and "***" in text  # 上游密钥残片过滤
    assert "sk-SECRET1234567890" not in text  # 自身凭据绝不回显
    assert resp.headers["Retry-After"] == "30"
    assert len(calls) == 2
