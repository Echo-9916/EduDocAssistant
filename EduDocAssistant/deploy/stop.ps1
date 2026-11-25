# 停止容器，保留 Oracle / Redis 数据卷
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
docker compose --env-file .env down
Write-Host "[stop] 已停止（数据卷保留；如需清空：docker compose --env-file .env down -v）"