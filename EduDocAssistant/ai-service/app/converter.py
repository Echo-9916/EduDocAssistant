"""格式转换引擎（D-06，J2 拍板：落 ai-service，后端不 spawn 外部进程）。

- DOCX→PDF：`soffice --headless --convert-to pdf`，独立 `-env:UserInstallation` 临时目录
  避免 LibreOffice 单实例锁（任务文档 R5）；soffice 路径环境变量 EDUDOC_SOFFICE_BIN。
- PDF→DOCX：pdf2docx（未安装 → ENGINE_UNAVAILABLE 干净降级）。
失败一律抛 ConvertError(code)：ENGINE_UNAVAILABLE|TIMEOUT|ENCRYPTED|CORRUPTED|UNSUPPORTED|BAD_INPUT。
"""

import logging
import os
import shutil
import subprocess
import tempfile

DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
PDF_MIME = "application/pdf"
_CONVERT_TIMEOUT_S = 120
# 终审 Minor：输入体积上限（内网 + token，风险低，但仍兜住内存膨胀；与后端 20MB 上传上限同量级留余）
_MAX_INPUT_BYTES = 25 * 1024 * 1024


def _log():
    return logging.getLogger("edudoc.ai.convert")


class ConvertError(Exception):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


def soffice_bin() -> str:
    return os.environ.get("EDUDOC_SOFFICE_BIN", "soffice")


def docx_to_pdf_bytes(data: bytes) -> bytes:
    """soffice 子进程转换；FileNotFoundError=未安装，TimeoutExpired=超预算。

    第四轮 P2-3：加密 DOCX 单独归类——OOXML 加密件是 OLE 复合文档容器（D0CF11E0 魔数，非 zip），
    调用 soffice 前先识别；soffice 无产出时再按 stderr 关键字兜底映射 ENCRYPTED。
    """
    if data[:8] == bytes.fromhex("D0CF11E0A1B11AE1"):
        raise ConvertError("ENCRYPTED", "文档为加密容器（或受密码保护的旧版格式），无法直接转换")
    # 第五轮 P3-2：UNSUPPORTED 显式分类——非 OOXML zip 容器不是 Word 文档（区别于内容损坏）
    if data[:2] != b"PK":
        raise ConvertError("UNSUPPORTED", "输入不是 Word(.docx) 文档容器")
    workdir = tempfile.mkdtemp(prefix="edudoc-convert-")
    try:
        src = os.path.join(workdir, "input.docx")
        outdir = os.path.join(workdir, "out")
        profile = os.path.join(workdir, "lo-profile")
        os.makedirs(outdir)
        with open(src, "wb") as f:
            f.write(data)
        cmd = [
            soffice_bin(),
            f"-env:UserInstallation={_file_url(profile)}",
            "--headless", "--norestore",
            "--convert-to", "pdf", "--outdir", outdir, src,
        ]
        try:
            proc = subprocess.run(cmd, capture_output=True, timeout=_CONVERT_TIMEOUT_S, check=False)
        except FileNotFoundError:
            raise ConvertError("ENGINE_UNAVAILABLE",
                               "服务器未安装 LibreOffice（EDUDOC_SOFFICE_BIN 未指向 soffice 可执行文件）")
        except subprocess.TimeoutExpired:
            raise ConvertError("TIMEOUT", "转换超过 120s 预算")
        produced = os.path.join(outdir, "input.pdf")
        if not os.path.exists(produced):
            stderr_tail = (proc.stderr or b"")[-400:].decode("utf-8", "replace")
            _log().warning("soffice 转换无产出 rc=%s stderr=%s", proc.returncode, stderr_tail)
            lowered = stderr_tail.lower()
            if "encrypt" in lowered or "password" in lowered:
                raise ConvertError("ENCRYPTED", "文档受密码保护，无法转换")
            raise ConvertError("CORRUPTED", "转换未产出文件（文件损坏或不支持的格式）")
        with open(produced, "rb") as f:
            return f.read()
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


def pdf_to_docx_bytes(data: bytes) -> bytes:
    """pdf2docx 转 Word；扫描件/加密件落 CORRUPTED/ENCRYPTED；未装库落 ENGINE_UNAVAILABLE。"""
    # 第五轮 P3-2：%PDF 头缺失 = 不是 PDF 输入，归类 UNSUPPORTED（先判输入再判引擎依赖）
    if data[:4] != b"%PDF":
        raise ConvertError("UNSUPPORTED", "输入不是 PDF 文档")
    try:
        from pdf2docx import Converter
    except ImportError:
        raise ConvertError("ENGINE_UNAVAILABLE", "ai-service 未安装 pdf2docx（uv sync 转换依赖组）")
    workdir = tempfile.mkdtemp(prefix="edudoc-pdf2docx-")
    try:
        src = os.path.join(workdir, "input.pdf")
        dst = os.path.join(workdir, "output.docx")
        with open(src, "wb") as f:
            f.write(data)
        try:
            cv = Converter(src)
            try:
                cv.convert(dst)
            finally:
                cv.close()
        except ConvertError:
            raise
        except Exception as e:  # pdf2docx/pymupdf 异常类型多，统一映射
            text = str(e).lower()
            if "password" in text or "encrypt" in text:
                raise ConvertError("ENCRYPTED", "PDF 已加密，无法解析")
            raise ConvertError("CORRUPTED", f"PDF 无法解析：{str(e)[:120]}")
        if not os.path.exists(dst):
            raise ConvertError("CORRUPTED", "转换未产出文件")
        with open(dst, "rb") as f:
            return f.read()
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


def convert(data: bytes, target_format: str) -> tuple[bytes, str]:
    """按目标格式路由；返回 (内容字节, MIME)。"""
    if len(data) > _MAX_INPUT_BYTES:
        raise ConvertError("BAD_INPUT", "文件超过 25MB 上限")
    if target_format == "PDF":
        return docx_to_pdf_bytes(data), PDF_MIME
    if target_format == "DOCX":
        return pdf_to_docx_bytes(data), DOCX_MIME
    raise ConvertError("BAD_INPUT", f"不支持的目标格式：{target_format}")


def _file_url(path: str) -> str:
    normalized = path.replace("\\", "/")
    if not normalized.startswith("/"):
        normalized = "/" + normalized  # Windows 盘符绝对路径：C:/x → /C:/x
    return "file://" + normalized
