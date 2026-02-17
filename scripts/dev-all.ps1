$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot

Write-Host '[dev-all] Starting SER service on 127.0.0.1:8001 ...'
& (Join-Path $PSScriptRoot 'start-ser.ps1')

Write-Host '[dev-all] Starting backend on 127.0.0.1:8080 ...'
Start-Process -FilePath 'cmd.exe' -ArgumentList '/k', "cd /d `"$root\backend`" && set SER_ENABLED=true && set SER_BASE_URL=http://127.0.0.1:8001 && mvn spring-boot:run"

Write-Host '[dev-all] Starting frontend on 127.0.0.1:5173 ...'
Start-Process -FilePath 'cmd.exe' -ArgumentList '/k', "cd /d `"$root\frontend`" && npm run dev -- --host 127.0.0.1 --port 5173"

Write-Host '[dev-all] Done. Open http://127.0.0.1:5173'
