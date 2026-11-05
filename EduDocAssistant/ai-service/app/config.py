"""环境配置：模型名/端点/凭据全部环境变量注入，不入仓（D1 决策）。"""

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    api_key: str
    base_url: str
    chat_model: str
    answer_model: str
    embedding_model: str
    rerank_model: str
    embedding_dimension: int
    internal_token: str

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            api_key=os.environ.get("SILICONFLOW_API_KEY", ""),
            base_url=os.environ.get("SILICONFLOW_BASE_URL", "https://api.siliconflow.cn/v1"),
            chat_model=os.environ.get("EDUDOC_LLM_MODEL", "deepseek-ai/DeepSeek-V3"),
            # P-05 问答 5s：答案走小模型（实测 0.6-0.8s），生成类任务仍走 chat_model（60s 预算内）
            answer_model=os.environ.get("EDUDOC_ANSWER_MODEL", "Qwen/Qwen2.5-7B-Instruct"),
            embedding_model=os.environ.get("EDUDOC_EMBEDDING_MODEL", "BAAI/bge-m3"),
            rerank_model=os.environ.get("EDUDOC_RERANK_MODEL", "BAAI/bge-reranker-v2-m3"),
            embedding_dimension=int(os.environ.get("EDUDOC_EMBEDDING_DIM", "1024")),
            internal_token=os.environ.get("EDUDOC_AI_INTERNAL_TOKEN", ""),
        )


# 超时分级（概要 §7 I04）：answer 对齐问答 5s 端到端预算，生成类走任务 60s 预算内
TIMEOUTS = {"embed": 8.0, "rerank": 8.0, "generate": 50.0, "answer": 4.6, "polish": 30.0}
