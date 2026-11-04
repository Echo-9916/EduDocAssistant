# edudoc-ai-service

CIDAS 智能处理服务（FastAPI，Python 3.13，uv 管理）：向量化、摘要、要点、问答、润色、重排。
模型经 OpenAI 兼容端点接入（默认硅基流动），密钥只走环境变量，不入仓。

## 运行

```bash
uv sync
uvicorn app.main:app --port 8000
```

## 环境变量

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `SILICONFLOW_API_KEY` | 空 | 模型凭据（缺失时上游 401 → 本服务 502 结构化错误） |
| `SILICONFLOW_BASE_URL` | `https://api.siliconflow.cn/v1` | OpenAI 兼容端点 |
| `EDUDOC_LLM_MODEL` | `Pro/moonshotai/Kimi-K2-Instruct` | 对话模型 |
| `EDUDOC_EMBEDDING_MODEL` | `BAAI/bge-m3` | 嵌入模型（须 1024 维） |
| `EDUDOC_RERANK_MODEL` | `BAAI/bge-reranker-v2-m3` | 重排模型 |
| `EDUDOC_EMBEDDING_DIM` | `1024` | 嵌入维度校验（与 Oracle VECTOR(1024) 对齐） |
| `EDUDOC_AI_INTERNAL_TOKEN` | 空 | 服务间共享密钥，与后端同名；未配置=503，不匹配=401 |

## 接口（/v1/* 需 `X-Internal-Token`，`X-Trace-Id` 透传回写）

`POST /v1/embed` `POST /v1/summarize` `POST /v1/extract-keypoints` `POST /v1/answer`
`POST /v1/polish` `POST /v1/rerank`；免鉴权：`GET /health` `GET /smoke`。
错误响应 `{code: MODEL_FAILED|TIMEOUT|BAD_INPUT, message, provider, model, elapsedMs}`，不回显密钥。

## 测试

```bash
python -m pytest
```
