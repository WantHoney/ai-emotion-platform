$ErrorActionPreference = "Stop"

# Ensure terminal code page and PowerShell stream encodings are UTF-8.
chcp 65001 > $null

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8NoBom
[Console]::OutputEncoding = $utf8NoBom
$OutputEncoding = $utf8NoBom

# Default cmdlet output encoding (PowerShell 5/7 compatible).
$PSDefaultParameterValues["Out-File:Encoding"] = "utf8"
$PSDefaultParameterValues["Set-Content:Encoding"] = "utf8"
$PSDefaultParameterValues["Add-Content:Encoding"] = "utf8"
$env:PYTHONUTF8 = "1"

Write-Host "[utf8] Shell encoding initialized."
Write-Host ("[utf8] CodePage=" + (chcp | Out-String).Trim())
Write-Host ("[utf8] InputEncoding=" + [Console]::InputEncoding.WebName)
Write-Host ("[utf8] OutputEncoding=" + [Console]::OutputEncoding.WebName)
Write-Host ("[utf8] PYTHONUTF8=" + $env:PYTHONUTF8)
