# 停止容器，保留 Oracle / Redis 数据卷
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
docker compose --env-file deploy/.env down
Write-Host "[stop] 已停止（数据卷保留；如需清空：docker compose --env-file deploy/.env down -v）"