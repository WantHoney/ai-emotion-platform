$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'enable-utf8.ps1')

function Stop-PortListener {
  param(
    [Parameter(Mandatory = $true)]
    [int]$Port
  )

  $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1

  if (-not $conn) {
    Write-Host "[stop-all] no listener on $Port"
    return
  }

  $pidValue = $conn.OwningProcess
  $proc = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
  $name = if ($proc) { $proc.ProcessName } else { 'unknown' }
  Stop-Process -Id $pidValue -Force -ErrorAction SilentlyContinue
  Write-Host "[stop-all] stopped listener on $Port (PID=$pidValue, Name=$name)"
}

Stop-PortListener -Port 8001
Stop-PortListener -Port 8080
Stop-PortListener -Port 5173

Write-Host '[stop-all] done'
