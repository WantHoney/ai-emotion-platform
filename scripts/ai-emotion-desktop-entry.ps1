$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'enable-utf8.ps1')

$root = Split-Path -Parent $PSScriptRoot
$frontendUrl = 'http://127.0.0.1:5173'
$serModelZh = Join-Path $root 'backend\ser-service\training\checkpoints\ser_multilingual_xlsr_stageB_exp04_hardfix_v1\best_model'
$textModelZh = Join-Path $root 'backend\ser-service\training\manifests\thesis_v4\deferred_training\local_text\model_v4_teacher_distill_humanfix_rw35_bestfix\best_model'

function Get-ListeningPorts {
  param(
    [int[]]$Ports
  )

  $active = @()
  foreach ($port in $Ports) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
      Select-Object -First 1
    if ($conn) {
      $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
      $active += [PSCustomObject]@{
        Port = $port
        Pid = $conn.OwningProcess
        Name = if ($proc) { $proc.ProcessName } else { 'unknown' }
      }
    }
  }
  return $active
}

function Start-BestProfile {
  if (-not (Test-Path $serModelZh)) {
    throw "SER zh model not found: $serModelZh"
  }

  if (-not (Test-Path $textModelZh)) {
    throw "Text zh model not found: $textModelZh"
  }

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

  Write-Host '[launcher] using best local debug profile'
  Write-Host "[launcher] SER_HF_MODEL_DIR_ZH=$env:SER_HF_MODEL_DIR_ZH"
  Write-Host "[launcher] TEXT_HF_MODEL_ZH=$env:TEXT_HF_MODEL_ZH"

  & (Join-Path $PSScriptRoot 'dev-all.ps1')
  Start-Sleep -Seconds 2
  Start-Process $frontendUrl
}

function Stop-AllServices {
  & (Join-Path $PSScriptRoot 'stop-dev-all.ps1')
}

$portsToCheck = @(8001, 8080, 5173)
$activePorts = Get-ListeningPorts -Ports $portsToCheck

Write-Host '========================================'
Write-Host ' AI情绪系统统一入口'
Write-Host '========================================'

if ($activePorts.Count -eq 0) {
  Write-Host '[launcher] no running services detected, starting all...'
  Start-BestProfile
  exit 0
}

Write-Host '[launcher] detected running listeners:'
$activePorts | ForEach-Object {
  Write-Host ("  - Port {0}: PID={1}, Name={2}" -f $_.Port, $_.Pid, $_.Name)
}

Write-Host ''
Write-Host 'Choose an action:'
Write-Host '  [R] Restart all services'
Write-Host '  [S] Stop all services'
Write-Host '  [O] Open frontend only'
Write-Host '  [Q] Quit'

$choice = (Read-Host 'Enter R / S / O / Q').Trim().ToUpperInvariant()

switch ($choice) {
  'R' {
    Write-Host '[launcher] restarting services...'
    Stop-AllServices
    Start-Sleep -Seconds 1
    Start-BestProfile
  }
  'S' {
    Write-Host '[launcher] stopping services...'
    Stop-AllServices
  }
  'O' {
    Write-Host '[launcher] opening frontend...'
    Start-Process $frontendUrl
  }
  default {
    Write-Host '[launcher] nothing changed.'
  }
}
