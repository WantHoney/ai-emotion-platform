$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$serDir = Join-Path $root "backend\ser-service"
$py = Join-Path $serDir "venv\Scripts\python.exe"
$logDir = Join-Path $serDir "logs"
$stdoutLog = Join-Path $logDir "ser-stdout.log"
$stderrLog = Join-Path $logDir "ser-stderr.log"

if (-not (Test-Path $serDir)) {
  throw "SER directory not found: $serDir"
}

if (-not (Test-Path $py)) {
  Write-Host "[start-ser] venv missing, creating..."
  python -m venv (Join-Path $serDir "venv")
}

$runningConn = Get-NetTCPConnection -LocalPort 8001 -State Listen -ErrorAction SilentlyContinue |
  Select-Object -First 1

if ($runningConn) {
  try {
    $existingHealth = Invoke-RestMethod -Uri "http://127.0.0.1:8001/health" -Method Get -TimeoutSec 2
    if ($existingHealth.status -eq "ok") {
      Write-Host "[start-ser] already healthy on 127.0.0.1:8001"
      exit 0
    }
  } catch {
  }
}

Write-Host "[start-ser] ensuring dependencies..."
& $py -m pip install -r (Join-Path $serDir "requirements.txt") | Out-Host

if (-not (Test-Path $logDir)) {
  New-Item -ItemType Directory -Path $logDir | Out-Null
}

$listenProc = Get-NetTCPConnection -LocalPort 8001 -State Listen -ErrorAction SilentlyContinue |
  Select-Object -First 1 -ExpandProperty OwningProcess
if ($listenProc) {
  Stop-Process -Id $listenProc -Force
  Start-Sleep -Milliseconds 500
}

if (Test-Path $stdoutLog) { Remove-Item $stdoutLog -Force -ErrorAction SilentlyContinue }
if (Test-Path $stderrLog) { Remove-Item $stderrLog -Force -ErrorAction SilentlyContinue }

$proc = Start-Process -FilePath $py `
  -ArgumentList "-m","uvicorn","app:app","--host","127.0.0.1","--port","8001" `
  -WorkingDirectory $serDir `
  -PassThru `
  -RedirectStandardOutput $stdoutLog `
  -RedirectStandardError $stderrLog
Write-Host "[start-ser] process started PID=$($proc.Id)"

$ok = $false
for ($i = 0; $i -lt 30; $i++) {
  Start-Sleep -Seconds 1
  try {
    $resp = Invoke-RestMethod -Uri "http://127.0.0.1:8001/health" -Method Get -TimeoutSec 2
    if ($resp.status -eq "ok") {
      $ok = $true
      break
    }
  } catch {
  }
}

if (-not $ok) {
  Write-Host "[start-ser] failed to become healthy, check logs:"
  Write-Host "  $stdoutLog"
  Write-Host "  $stderrLog"
  exit 1
}

Write-Host "[start-ser] HEALTH OK => http://127.0.0.1:8001/health"
