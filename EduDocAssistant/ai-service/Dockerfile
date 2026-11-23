# syntax=docker/dockerfile:1
# AI 服务：Python 3.13 + uv；默认带 LibreOffice，支持 DOCX↔PDF 转换
# 国内构建：PyPI/apt 使用清华镜像，避免 files.pythonhosted.org 下载过慢
FROM python:3.13-slim

ARG INSTALL_CONVERT=true

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_LINK_MODE=copy \
    UV_DEFAULT_INDEX=https://pypi.tuna.tsinghua.edu.cn/simple \
    TZ=Asia/Shanghai

# Debian 基础镜像换清华 apt 源（兼容 sources.list 与 deb822 两种格式）
RUN for f in /etc/apt/sources.list /etc/apt/sources.list.d/debian.sources; do \
        if [ -f "$f" ]; then \
            sed -i 's|deb.debian.org|mirrors.tuna.tsinghua.edu.cn|g; s|security.debian.org|mirrors.tuna.tsinghua.edu.cn|g' "$f"; \
        fi; \
    done

RUN pip install --no-cache-dir -i "$UV_DEFAULT_INDEX" uv

WORKDIR /app
COPY pyproject.toml uv.lock ./

# 用国内镜像重新锁定（仅镜像内文件，不改仓库 lock），再按 lock 安装
RUN uv lock --default-index "$UV_DEFAULT_INDEX" || uv lock --index-url "$UV_DEFAULT_INDEX"
RUN if [ "$INSTALL_CONVERT" = "true" ]; then \
        uv sync --frozen --no-dev --extra convert; \
    else \
        uv sync --frozen --no-dev; \
    fi

RUN if [ "$INSTALL_CONVERT" = "true" ]; then \
        apt-get update \
        && apt-get install -y --no-install-recommends \
            libreoffice-writer fonts-noto-cjk libgl1 libglib2.0-0 \
        && rm -rf /var/lib/apt/lists/*; \
    fi

COPY app ./app

ENV PATH="/app/.venv/bin:$PATH" \
    EDUDOC_SOFFICE_BIN=soffice

EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=5s --retries=20 --start-period=30s \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=3).status == 200 else 1)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]