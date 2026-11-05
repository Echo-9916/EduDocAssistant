"""提示模板集中管理（C-09）：prompt_version 常量随结果返回，供 edudoc_ai_result.prompt_version。

输出契约均为严格 JSON（后端按结构解析；解析失败服务侧重试一次后报结构化错误）。
evidence-only（§4.2/R9）：只依据给定分块文本，不以常识补写。
"""

PROMPT_VERSION = "v1"

SYSTEM_STRICT_JSON = (
    "你是校园文档助手。严格按用户要求的 JSON 结构输出，不要输出任何解释文字或 Markdown 代码围栏。"
)

SYSTEM_PLAIN_TEXT = (
    "你是校园文本润色助手。只输出用户要求的文本内容本身，不要解释、前缀或代码围栏。"
)


def summarize_prompt(chunks: list[dict]) -> str:
    return (
        "仅依据以下分块文本生成摘要。分块以 JSON 提供（chunkId/text）。\n"
        "输出 JSON 对象：{\"segments\": [{\"text\": \"摘要句\", \"chunkIds\": [\"来源chunkId\"]}]}\n"
        "每个摘要句的 chunkIds 只能引用输入中真实存在的 chunkId。\n\n"
        "分块：\n" + _chunks_block(chunks)
    )


def keypoints_prompt(chunks: list[dict]) -> str:
    return (
        "从以下分块文本中按文档顺序提取关键要点（尽量 5 条以上，每条须可定位到来源分块）。\n"
        "输出 JSON 对象：{\"keypoints\": [{\"text\": \"要点\", \"chunkId\": \"来源chunkId\"}]}\n"
        "chunkId 只能引用输入中真实存在的 chunkId。\n\n"
        "分块：\n" + _chunks_block(chunks)
    )


def answer_prompt(question: str, evidence: list[dict], history: list[dict]) -> str:
    history_lines = "\n".join(
        f"{'用户' if h['role'] == 'user' else '助手'}: {h['content']}" for h in history
    ) or "（无）"
    return (
        "只依据证据回答用户问题；证据不足时必须拒答，不得用常识补写。\n"
        "输出 JSON 对象：可答时 {\"refused\": false, \"answer\": \"回答\", "
        "\"citations\": [{\"chunkId\": \"证据chunkId\", \"quote\": \"逐字引用的证据片段\"}]}\n"
        "拒答时 {\"refused\": true}。citations 的 chunkId 只能引用证据编号，quote 须逐字来自证据。\n\n"
        f"最近对话：\n{history_lines}\n\n"
        f"问题：{question}\n\n"
        "证据：\n" + _chunks_block(evidence)
    )


def polish_prompt(text: str) -> str:
    return (
        "润色下面这段文字，使其更通顺规范；保留专业术语与数值，不改变含义，不添加内容。\n"
        "直接输出润色后的文本本身，不要任何前缀、解释或代码围栏。\n\n" + text
    )


def _chunks_block(chunks: list[dict]) -> str:
    import json

    return json.dumps(
        [{"chunkId": c["chunkId"], "text": c["text"]} for c in chunks], ensure_ascii=False
    )
