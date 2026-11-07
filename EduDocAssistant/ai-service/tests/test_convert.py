"""D-06 /v1/convert 测试：token 守卫生效、DOCX→PDF 走 soffice 子进程、PDF→DOCX 走 pdf2docx、
引擎缺失/加密/损坏分别回 ENGINE_UNAVAILABLE / ENCRYPTED / CORRUPTED，不回显凭据。
子进程与 pdf2docx 用 monkeypatch 打桩，保持测试离线可跑（本机不装 LibreOffice）。"""

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app import converter
from app.main import create_app

SETTINGS = Settings(api_key="sk-SECRET", base_url="https://provider.invalid/v1",
                    chat_model="K", answer_model="K", embedding_model="e", rerank_model="r",
                    embedding_dimension=4, internal_token="tok")


def client():
    return TestClient(create_app(SETTINGS), headers={"X-Internal-Token": "tok"})


# ---------- docx -> pdf via soffice ----------

def test_docx_to_pdf_invokes_soffice(monkeypatch):
    captured = {}

    class Result:
        returncode = 0

    def fake_run(cmd, **kwargs):
        captured["cmd"] = cmd
        # 模拟 soffice 在 outdir 生成同名 pdf
        import os
        for i, arg in enumerate(cmd):
            if arg == "--outdir":
                outdir = cmd[i + 1]
                with open(os.path.join(outdir, "input.pdf"), "wb") as f:
                    f.write(b"%PDF-1.4 fake")
        return Result()

    monkeypatch.setattr(converter.subprocess, "run", fake_run)
    resp = client().post("/v1/convert",
                         files={"file": ("a.docx", b"PKdocx-bytes",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")},
                         data={"targetFormat": "PDF"})
    assert resp.status_code == 200
    assert resp.content == b"%PDF-1.4 fake"
    assert resp.headers["content-type"] == "application/pdf"
    assert "--headless" in captured["cmd"] and "--convert-to" in captured["cmd"]
    assert any("UserInstallation=" in c for c in captured["cmd"])  # 独立 profile 避单实例锁


# ---------- pdf -> docx via pdf2docx ----------

def test_pdf_to_docx(monkeypatch):
    def fake_pdf2docx(data: bytes) -> bytes:
        assert data == b"pdf-bytes"
        return b"docx-out"

    monkeypatch.setattr(converter, "pdf_to_docx_bytes", fake_pdf2docx)
    resp = client().post("/v1/convert", files={"file": ("a.pdf", b"pdf-bytes", "application/pdf")},
                         data={"targetFormat": "DOCX"})
    assert resp.status_code == 200
    assert resp.content == b"docx-out"
    assert "wordprocessingml" in resp.headers["content-type"]


# ---------- 失败映射 ----------

def test_engine_unavailable_when_soffice_missing(monkeypatch):
    import errno
    import os

    def raise_fnf(cmd, **kwargs):
        raise FileNotFoundError(errno.ENOENT, os.strerror(errno.ENOENT), "soffice")

    monkeypatch.setattr(converter.subprocess, "run", raise_fnf)
    resp = client().post("/v1/convert", files={"file": ("a.docx", b"PKx", "application/octet-stream")},
                         data={"targetFormat": "PDF"})
    assert resp.status_code == 503
    assert resp.json()["code"] == "ENGINE_UNAVAILABLE"
    assert "SECRET" not in resp.text


def test_encrypted_pdf_maps_encrypted(monkeypatch):
    def boom(data: bytes) -> bytes:
        raise converter.ConvertError("ENCRYPTED", "PDF 已加密，无法解析")

    monkeypatch.setattr(converter, "pdf_to_docx_bytes", boom)
    resp = client().post("/v1/convert", files={"file": ("a.pdf", b"x", "application/pdf")},
                         data={"targetFormat": "DOCX"})
    assert resp.status_code == 422
    assert resp.json()["code"] == "ENCRYPTED"


def test_no_output_maps_corrupted(monkeypatch):
    class Result:
        returncode = 1
        stderr = b"error: cannot read input file"

    monkeypatch.setattr(converter.subprocess, "run", lambda cmd, **kwargs: Result())
    resp = client().post("/v1/convert", files={"file": ("a.docx", b"PKjunk", "application/octet-stream")},
                         data={"targetFormat": "PDF"})
    assert resp.status_code == 422
    assert resp.json()["code"] == "CORRUPTED"


def test_encrypted_docx_container_maps_encrypted(monkeypatch):
    # 第四轮 P2-3：OLE 复合文档魔数=OOXML 加密容器，调用 soffice 前即识别（无需安装 LibreOffice）
    monkeypatch.setattr(converter.subprocess, "run",
                        lambda cmd, **kwargs: (_ for _ in ()).throw(AssertionError("不应调用 soffice")))
    encrypted = bytes.fromhex("D0CF11E0A1B11AE1") + b"\x00" * 100
    resp = client().post("/v1/convert", files={"file": ("a.docx", encrypted, "application/octet-stream")},
                         data={"targetFormat": "PDF"})
    assert resp.status_code == 422
    assert resp.json()["code"] == "ENCRYPTED"


def test_encrypted_docx_via_stderr_maps_encrypted(monkeypatch):
    class Result:
        returncode = 1
        stderr = b"source file is encrypted or password protected"

    monkeypatch.setattr(converter.subprocess, "run", lambda cmd, **kwargs: Result())
    resp = client().post("/v1/convert", files={"file": ("a.docx", b"PK\x03\x04junk", "application/octet-stream")},
                         data={"targetFormat": "PDF"})
    assert resp.status_code == 422
    assert resp.json()["code"] == "ENCRYPTED"


def test_no_output_without_password_stays_corrupted(monkeypatch):
    class Result:
        returncode = 1
        stderr = b"Error: could not load document"

    monkeypatch.setattr(converter.subprocess, "run", lambda cmd, **kwargs: Result())
    resp = client().post("/v1/convert", files={"file": ("a.docx", b"PKjunk", "application/octet-stream")},
                         data={"targetFormat": "PDF"})
    assert resp.status_code == 422
    assert resp.json()["code"] == "CORRUPTED"


def test_unsupported_docx_container_maps_unsupported():
    # 第五轮 P3-2：非 zip 非 OLE=不是 Word 文档，归 UNSUPPORTED（区别于内容损坏），无需 soffice 在场
    resp = client().post("/v1/convert",
                         files={"file": ("a.docx", b"hello not a container", "application/octet-stream")},
                         data={"targetFormat": "PDF"})
    assert resp.status_code == 415
    assert resp.json()["code"] == "UNSUPPORTED"


def test_unsupported_pdf_input_maps_unsupported():
    resp = client().post("/v1/convert",
                         files={"file": ("a.pdf", b"not-pdf-bytes", "application/pdf")},
                         data={"targetFormat": "DOCX"})
    assert resp.status_code == 415
    assert resp.json()["code"] == "UNSUPPORTED"


def test_bad_target_format_rejected():
    resp = client().post("/v1/convert", files={"file": ("a.docx", b"x", "application/octet-stream")},
                         data={"targetFormat": "EXE"})
    assert resp.status_code == 400
    assert resp.json()["code"] == "BAD_INPUT"


def test_oversized_input_rejected():
    big = b"0" * (25 * 1024 * 1024 + 1)
    resp = client().post("/v1/convert", files={"file": ("a.docx", big, "application/octet-stream")},
                         data={"targetFormat": "PDF"})
    assert resp.status_code == 400
    assert resp.json()["code"] == "BAD_INPUT"


def test_convert_requires_token():
    noauth = TestClient(create_app(SETTINGS))
    resp = noauth.post("/v1/convert", files={"file": ("a.docx", b"x", "application/octet-stream")},
                       data={"targetFormat": "PDF"})
    assert resp.status_code == 401
