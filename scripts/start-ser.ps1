$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot 'enable-utf8.ps1')

$root = Split-Path -Parent $PSScriptRoot
$serDir = Join-Path $root "backend\ser-service"
$venvGpuPy = Join-Path $serDir "venv-gpu\Scripts\python.exe"
$venvPy = Join-Path $serDir "venv\Scripts\python.exe"
$usingVenv = "venv"
$py = $venvPy
$logDir = Join-Path $serDir "logs"
$stdoutLog = Join-Path $logDir "ser-stdout.log"
$stderrLog = Join-Path $logDir "ser-stderr.log"

if (-not (Test-Path $serDir)) {
  throw "SER directory not found: $serDir"
}

if (Test-Path $venvGpuPy) {
  $py = $venvGpuPy
  $usingVenv = "venv-gpu"
} elseif (-not (Test-Path $venvPy)) {
  Write-Host "[start-ser] venv missing, creating..."
  python -m venv (Join-Path $serDir "venv")
  $py = $venvPy
}

function Set-DefaultEnv {
  param(
    [string]$Name,
    [string]$Value
  )
  $current = [Environment]::GetEnvironmentVariable($Name, "Process")
  if ([string]::IsNullOrWhiteSpace($current)) {
    Set-Item -Path ("Env:" + $Name) -Value $Value
  }
}

$defaultSerModelEn = Join-Path $serDir "training\checkpoints\ser_multilingual_4class_exp02\best_model"
$defaultSerModelZh = Join-Path $serDir "training\checkpoints\ser_multilingual_esd_stageB_exp01\best_model"
$defaultTextModelEn = Join-Path $serDir "text_models\en_roberta_sentiment"
$defaultTextModelZh = Join-Path $serDir "training\text_models\zh_sentiment_exp03\best_model"
$defaultFusionModel = Join-Path $serDir "training\fusion\models\fusion_exp03_perlang"

Set-DefaultEnv -Name "SER_ENGINE" -Value "hf_wav2vec2"
Set-DefaultEnv -Name "SER_HF_ROUTING" -Value "language"
Set-DefaultEnv -Name "SER_HF_DEFAULT_LANGUAGE" -Value "zh"
Set-DefaultEnv -Name "SER_HF_MODEL_DIR_EN" -Value $defaultSerModelEn
Set-DefaultEnv -Name "SER_HF_MODEL_DIR_ZH" -Value $defaultSerModelZh
Set-DefaultEnv -Name "TEXT_ENGINE" -Value "hf"
Set-DefaultEnv -Name "TEXT_HF_ROUTING" -Value "language"
Set-DefaultEnv -Name "TEXT_HF_DEFAULT_LANGUAGE" -Value "zh"
Set-DefaultEnv -Name "TEXT_HF_MODEL_EN" -Value $defaultTextModelEn
Set-DefaultEnv -Name "TEXT_HF_MODEL_ZH" -Value $defaultTextModelZh
Set-DefaultEnv -Name "FUSION_ENABLED" -Value "true"
Set-DefaultEnv -Name "FUSION_MODEL_DIR" -Value $defaultFusionModel

Write-Host "[start-ser] using environment:"
Write-Host "  SER_ENGINE=$env:SER_ENGINE"
Write-Host "  SER_HF_MODEL_DIR_EN=$env:SER_HF_MODEL_DIR_EN"
Write-Host "  SER_HF_MODEL_DIR_ZH=$env:SER_HF_MODEL_DIR_ZH"
Write-Host "  TEXT_ENGINE=$env:TEXT_ENGINE"
Write-Host "  TEXT_HF_MODEL_EN=$env:TEXT_HF_MODEL_EN"
Write-Host "  TEXT_HF_MODEL_ZH=$env:TEXT_HF_MODEL_ZH"
Write-Host "  FUSION_MODEL_DIR=$env:FUSION_MODEL_DIR"
Write-Host "[start-ser] python env=$usingVenv, executable=$py"

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
Write-Host "[start-ser] warming up models via /warmup ..."
try {
  $warmup = Invoke-RestMethod -Uri "http://127.0.0.1:8001/warmup" -Method Get -TimeoutSec 240
  Write-Host ("[start-ser] WARMUP => " + ($warmup | ConvertTo-Json -Compress))
  $health = Invoke-RestMethod -Uri "http://127.0.0.1:8001/health" -Method Get -TimeoutSec 5
  Write-Host ("[start-ser] HEALTH SUMMARY => " + ($health | Select-Object `
    serEngine, `
    serHfDefaultLanguage, `
    serHfModelDirEn, `
    serHfModelDirZh, `
    textEngine, `
    textHfDefaultLanguage, `
    textHfModelEn, `
    textHfModelZh, `
    fusionEnabled, `
    fusionModelDir `
  | ConvertTo-Json -Compress))
} catch {
  Write-Host "[start-ser] warmup request failed, service is still running. Check logs if needed."
}
