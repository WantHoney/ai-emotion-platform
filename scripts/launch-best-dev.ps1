$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'enable-utf8.ps1')

$root = Split-Path -Parent $PSScriptRoot
$serModelZh = Join-Path $root 'backend\ser-service\training\checkpoints\ser_multilingual_xlsr_stageB_exp04_hardfix_v1\best_model'
$textModelZh = Join-Path $root 'backend\ser-service\training\manifests\thesis_v4\deferred_training\local_text\model_v4_teacher_distill_humanfix_rw35_bestfix\best_model'

function Stop-PortListener {
  param(
    [Parameter(Mandatory = $true)]
    [int]$Port
  )

  $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1

  if (-not $conn) {
    Write-Host "[launcher] port $Port is free"
    return
  }

  $pidValue = $conn.OwningProcess
  $proc = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
  $name = if ($proc) { $proc.ProcessName } else { 'unknown' }
  Write-Host "[launcher] stopping existing listener on $Port (PID=$pidValue, Name=$name)"
  Stop-Process -Id $pidValue -Force -ErrorAction SilentlyContinue
  Start-Sleep -Milliseconds 500
}

if (-not (Test-Path $serModelZh)) {
  throw "SER zh model not found: $serModelZh"
}

if (-not (Test-Path $textModelZh)) {
  throw "Text zh model not found: $textModelZh"
}

Write-Host '[launcher] preparing clean ports...'
Stop-PortListener -Port 8001
Stop-PortListener -Port 8080
Stop-PortListener -Port 5173

Write-Host '[launcher] applying recommended local debug profile...'
$env:SER_ENGINE = 'hf_wav2vec2'
$env:SER_HF_ROUTING = 'language'
$env:SER_HF_DEFAULT_LANGUAGE = 'zh'
$env:SER_HF_MODEL_DIR_ZH = $serModelZh
$env:TEXT_ENGINE = 'hf'
$env:TEXT_HF_ROUTING = 'language'
$env:TEXT_HF_DEFAULT_LANGUAGE = 'zh'
$env:TEXT_HF_MODEL_ZH = $textModelZh
$env:FUSION_ENABLED = 'true'
$env:FUSION_ZH_MODE = 'semantic_guarded'
$env:AI_MODE = 'mock'
$env:SPRING_AI_OPENAI_ENABLED = 'false'

Write-Host "[launcher] SER_HF_MODEL_DIR_ZH=$env:SER_HF_MODEL_DIR_ZH"
Write-Host "[launcher] TEXT_HF_MODEL_ZH=$env:TEXT_HF_MODEL_ZH"
Write-Host "[launcher] FUSION_ZH_MODE=$env:FUSION_ZH_MODE"
Write-Host "[launcher] AI_MODE=$env:AI_MODE"

Write-Host '[launcher] starting all services...'
& (Join-Path $PSScriptRoot 'dev-all.ps1')

Write-Host '[launcher] one-click start finished. Frontend: http://127.0.0.1:5173'
