#!/usr/bin/env bash
# 一键启动：准备 .env → 构建并启动 compose → 等待 Web 就绪
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DEPLOY_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] 未找到 docker，请先安装 Docker Desktop。"
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "[ERROR] 未找到 docker compose（需要 Compose v2）。"
  exit 1
fi

if [ ! -f .env ]; then
  cp .env.example .env
  echo "[init] 已生成 .env（请按需填写 OSS / AI 密钥）"
fi

gen_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
  else
    od -An -N32 -tx1 /dev/urandom | tr -d ' \n'
  fi
}

fill_empty() {
  local key="$1"
  if grep -q "^${key}=$" .env; then
    local value
    value="$(gen_secret)"
    if sed --version >/dev/null 2>&1; then
      sed -i "s|^${key}=$|${key}=${value}|" .env
    else
      sed -i.bak "s|^${key}=$|${key}=${value}|" .env && rm -f .env.bak
    fi
    echo "[init] 已生成 ${key}"
  fi
}

fill_empty EDUDOC_JWT_SECRET
fill_empty EDUDOC_AI_INTERNAL_TOKEN

echo "[start] 构建并启动容器（首次 Oracle 初始化约 5-10 分钟）..."
docker compose --env-file .env up -d --build

PORT="$(grep -E '^EDUDOC_WEB_PORT=' .env | cut -d= -f2 | tr -d '[:space:]')"
PORT="${PORT:-8088}"
echo "[wait] 等待 http://localhost:${PORT} 就绪..."
READY=0
for _ in $(seq 1 180); do
  if curl -fsS "http://localhost:${PORT}/" >/dev/null 2>&1; then
    READY=1
    break
  fi
  sleep 5
done

if [ "$READY" != "1" ]; then
  echo "[WARN] Web 尚未就绪。查看状态与日志："
  echo "  docker compose --env-file .env ps"
  echo "  docker compose --env-file .env logs -f"
  exit 1
fi

cat <<EOF

============================================================
CIDAS / EduDocAssistant 已启动
  Web      : http://localhost:${PORT}
  教师账号 : teacher / Cidas@123
  学生账号 : student / Cidas@123
  运维账号 : ops / Cidas@123
  日志     : docker compose --env-file .env logs -f
  停止     : ./stop.sh（保留数据卷）
  重置     : docker compose --env-file .env down -v（删除数据库）
============================================================
EOF