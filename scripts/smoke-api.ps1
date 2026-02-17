$ErrorActionPreference = "Stop"

$base = "http://127.0.0.1:8080"
$proxy = "http://127.0.0.1:5173"
$results = @()

function Add-Result {
  param(
    [string]$Name,
    [bool]$Ok,
    [string]$Detail
  )
  $script:results += [PSCustomObject]@{
    name = $Name
    ok = $Ok
    detail = $Detail
  }
}

function Call-Json {
  param(
    [string]$Name,
    [string]$Uri,
    [hashtable]$Headers = @{}
  )
  try {
    $resp = Invoke-RestMethod -Uri $Uri -Method Get -Headers $Headers
    $count = if ($resp -is [System.Array]) { $resp.Count } elseif ($null -ne $resp.items) { $resp.items.Count } else { -1 }
    $detail = if ($count -ge 0) { "count=$count" } else { "ok" }
    Add-Result -Name $Name -Ok $true -Detail $detail
    return $resp
  } catch {
    Add-Result -Name $Name -Ok $false -Detail $_.Exception.Message
    return $null
  }
}

Write-Host "[smoke] checking service health..."
$health = Call-Json -Name "backend:/api/health" -Uri "$base/api/health"
[void](Call-Json -Name "proxy:/api/health" -Uri "$proxy/api/health")

Write-Host "[smoke] checking public endpoints..."
[void](Call-Json -Name "backend:/api/home" -Uri "$base/api/home")
[void](Call-Json -Name "backend:/api/psy-centers(cityCode)" -Uri "$base/api/psy-centers?cityCode=310100")
[void](Call-Json -Name "backend:/api/psy-centers(city_code)" -Uri "$base/api/psy-centers?city_code=310100")
[void](Call-Json -Name "proxy:/api/home" -Uri "$proxy/api/home")

Write-Host "[smoke] checking user flow..."
$uname = "smokeu" + (Get-Random -Minimum 10000 -Maximum 99999)
$pwd = "QaPass1234"
$userToken = $null
try {
  $register = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType "application/json" -Body (@{ username = $uname; password = $pwd } | ConvertTo-Json)
  $userToken = $register.accessToken
  Add-Result -Name "user:/api/auth/register" -Ok $true -Detail "role=$($register.user.role)"
} catch {
  Add-Result -Name "user:/api/auth/register" -Ok $false -Detail $_.Exception.Message
}

if ($userToken) {
  $uh = @{ Authorization = "Bearer $userToken" }
  [void](Call-Json -Name "user:/api/auth/me" -Uri "$base/api/auth/me" -Headers $uh)
  [void](Call-Json -Name "user:/api/tasks" -Uri "$base/api/tasks?page=1&pageSize=10&sortBy=createdAt&sortOrder=desc" -Headers $uh)
  [void](Call-Json -Name "user:/api/reports" -Uri "$base/api/reports?page=1&pageSize=10&sortBy=createdAt&sortOrder=desc" -Headers $uh)
  [void](Call-Json -Name "user:/api/reports/trend" -Uri "$base/api/reports/trend?days=7" -Headers $uh)

  if (-not (Test-Path "backend/ser-service/tmp-ser-test.wav")) {
    Add-Result -Name "user:upload/testfile" -Ok $false -Detail "missing backend/ser-service/tmp-ser-test.wav"
  } else {
    try {
      $filePath = (Resolve-Path "backend/ser-service/tmp-ser-test.wav").Path
      $fileInfo = Get-Item $filePath

      $init = Invoke-RestMethod -Uri "$base/api/audio/upload-sessions/init" -Method Post -Headers $uh -ContentType "application/json" -Body (
        @{ fileName = $fileInfo.Name; contentType = "audio/wav"; fileSize = $fileInfo.Length; totalChunks = 1 } | ConvertTo-Json
      )
      Add-Result -Name "user:upload/init" -Ok $true -Detail "uploadId=$($init.uploadId)"

      $chunk = curl.exe -s -X PUT "$base/api/audio/upload-sessions/$($init.uploadId)/chunks/0" -H "Authorization: Bearer $userToken" -F "file=@$filePath"
      Add-Result -Name "user:upload/chunk" -Ok $true -Detail $chunk

      $complete = Invoke-RestMethod -Uri "$base/api/audio/upload-sessions/$($init.uploadId)/complete" -Method Post -Headers $uh -ContentType "application/json" -Body (
        @{ autoStartTask = $true } | ConvertTo-Json
      )
      Add-Result -Name "user:upload/complete" -Ok $true -Detail "taskId=$($complete.taskId)"

      $taskId = [int64]$complete.taskId
      $finalStatus = ""
      for ($i = 0; $i -lt 25; $i++) {
        Start-Sleep -Milliseconds 800
        $task = Invoke-RestMethod -Uri "$base/api/analysis/task/$taskId" -Headers $uh
        $finalStatus = [string]$task.status
        if ($finalStatus -in @("SUCCESS", "FAILED", "CANCELED")) {
          break
        }
      }
      Add-Result -Name "user:analysis/task" -Ok ($finalStatus -eq "SUCCESS") -Detail "status=$finalStatus"

      if ($finalStatus -eq "SUCCESS") {
        $taskResult = Invoke-RestMethod -Uri "$base/api/analysis/task/$taskId/result" -Headers $uh
        Add-Result -Name "user:analysis/result" -Ok $true -Detail "segments=$($taskResult.analysis_segment.Count)"
      }
    } catch {
      Add-Result -Name "user:upload-and-analysis" -Ok $false -Detail $_.Exception.Message
    }
  }
}

Write-Host "[smoke] checking admin flow..."
$adminToken = $null
try {
  $admin = Invoke-RestMethod -Uri "$base/api/auth/admin/login" -Method Post -ContentType "application/json" -Body (@{ username = "operator"; password = "operator123" } | ConvertTo-Json)
  $adminToken = $admin.accessToken
  Add-Result -Name "admin:/api/auth/admin/login" -Ok $true -Detail "role=$($admin.user.role)"
} catch {
  Add-Result -Name "admin:/api/auth/admin/login" -Ok $false -Detail $_.Exception.Message
}

if ($adminToken) {
  $ah = @{ Authorization = "Bearer $adminToken" }
  $adminEndpoints = @(
    "/api/system/status",
    "/api/admin/dashboard/light",
    "/api/admin/models",
    "/api/admin/warning-rules",
    "/api/admin/warnings?page=1&pageSize=10",
    "/api/admin/analytics/daily?days=7",
    "/api/admin/analytics/quality?windowDays=7&baselineDays=7",
    "/api/admin/governance/summary",
    "/api/admin/banners",
    "/api/admin/quotes",
    "/api/admin/articles",
    "/api/admin/books",
    "/api/admin/psy-centers"
  )
  foreach ($ep in $adminEndpoints) {
    [void](Call-Json -Name ("admin:" + $ep) -Uri ($base + $ep) -Headers $ah)
  }
}

Write-Host ""
$results | Sort-Object name | Format-Table -AutoSize

$failed = @($results | Where-Object { -not $_.ok })
Write-Host ""
Write-Host ("FAILED_COUNT=" + $failed.Count)
if ($failed.Count -gt 0) {
  $failed | Format-Table -AutoSize
  exit 1
}

if ($health -and $health.status -ne "UP") {
  Write-Host "[smoke] warning: backend health status is not UP"
}

Write-Host "[smoke] PASS"
