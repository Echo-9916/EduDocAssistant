#!/usr/bin/env bash
# 停止容器，保留 Oracle / Redis 数据卷
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
docker compose --env-file deploy/.env down
echo "[stop] 已停止（数据卷保留；如需清空：docker compose --env-file deploy/.env down -v）"