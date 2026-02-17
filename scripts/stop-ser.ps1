$ErrorActionPreference = "Stop"

$conn = Get-NetTCPConnection -LocalPort 8001 -State Listen -ErrorAction SilentlyContinue |
  Select-Object -First 1

if (-not $conn) {
  Write-Host "[stop-ser] no process listening on 8001"
  exit 0
}

$pidValue = $conn.OwningProcess
Stop-Process -Id $pidValue -Force
Write-Host "[stop-ser] stopped PID=$pidValue on port 8001"

