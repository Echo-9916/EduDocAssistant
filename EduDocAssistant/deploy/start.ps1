# 一键启动（Windows PowerShell）
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

Set-Location $PSScriptRoot


if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "未找到 docker，请先安装 Docker Desktop。"
}
docker compose version | Out-Null

$envPath = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path $envPath)) {
    Copy-Item (Join-Path $PSScriptRoot ".env.example") $envPath
    Write-Host "[init] 已生成 .env（请按需填写 OSS / AI 密钥）"
}

$envText = Get-Content $envPath -Raw
if ($envText -match "(?m)^EDUDOC_JWT_SECRET=\s*$") {
    $secret = (New-Guid).Guid.Replace("-", "") + (New-Guid).Guid.Replace("-", "")
    $envText = $envText -replace "(?m)^EDUDOC_JWT_SECRET=\s*$", "EDUDOC_JWT_SECRET=$secret"
    Write-Host "[init] 已生成 EDUDOC_JWT_SECRET"
}
if ($envText -match "(?m)^EDUDOC_AI_INTERNAL_TOKEN=\s*$") {
    $token = (New-Guid).Guid.Replace("-", "") + (New-Guid).Guid.Replace("-", "")
    $envText = $envText -replace "(?m)^EDUDOC_AI_INTERNAL_TOKEN=\s*$", "EDUDOC_AI_INTERNAL_TOKEN=$token"
    Write-Host "[init] 已生成 EDUDOC_AI_INTERNAL_TOKEN"
}
Set-Content -Path $envPath -Value $envText -NoNewline

Write-Host "[start] 构建并启动容器（首次 Oracle 初始化约 5-10 分钟）..."
docker compose --env-file .env up -d --build

$portLine = Select-String -Path $envPath -Pattern "^EDUDOC_WEB_PORT=(.*)$"
$port = if ($portLine) { $portLine.Matches.Groups[1].Value.Trim() } else { "8088" }
if ([string]::IsNullOrWhiteSpace($port)) { $port = "8088" }

Write-Host "[wait] 等待 http://localhost:$port 就绪..."
$ready = $false
for ($i = 0; $i -lt 180; $i++) {
    try {
        Invoke-WebRequest -Uri "http://localhost:$port/" -UseBasicParsing -TimeoutSec 3 | Out-Null
        $ready = $true
        break
    } catch {
        Start-Sleep -Seconds 5
    }
}

if (-not $ready) {
    Write-Host "[WARN] Web 尚未就绪。查看状态与日志："
    Write-Host "  docker compose --env-file .env ps"
    Write-Host "  docker compose --env-file .env logs -f"
    exit 1
}

Write-Host ""
Write-Host "============================================================"
Write-Host "CIDAS / EduDocAssistant 已启动"
Write-Host "  Web      : http://localhost:$port"
Write-Host "  教师账号 : teacher / Cidas@123"
Write-Host "  学生账号 : student / Cidas@123"
Write-Host "  运维账号 : ops / Cidas@123"
Write-Host "  日志     : docker compose --env-file .env logs -f"
Write-Host "  停止     : .\stop.ps1（保留数据卷）"
Write-Host "  重置     : docker compose --env-file .env down -v（删除数据库）"
Write-Host "============================================================"