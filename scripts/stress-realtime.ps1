param(
  [Parameter(Mandatory = $true)]
  [int]$TaskId,

  [Parameter(Mandatory = $true)]
  [string]$AccessToken,

  [string]$WsBaseUrl = "ws://127.0.0.1:8080",
  [int]$Connections = 30,
  [int]$DurationSec = 40,
  [int]$ReceiveTimeoutMs = 2000,
  [switch]$ShowErrors
)

if ($Connections -lt 1) {
  throw "Connections must be >= 1"
}
if ($DurationSec -lt 1) {
  throw "DurationSec must be >= 1"
}
if ([string]::IsNullOrWhiteSpace($AccessToken)) {
  throw "AccessToken is required"
}

$wsUrl = "$WsBaseUrl/ws/tasks/stream?taskId=$TaskId&accessToken=$([uri]::EscapeDataString($AccessToken))"
Write-Host "[stress] target=$wsUrl"
Write-Host "[stress] connections=$Connections durationSec=$DurationSec"

$jobScript = {
  param($id, $url, $durationSec, $receiveTimeoutMs)

  $ws = [System.Net.WebSockets.ClientWebSocket]::new()
  $cts = [System.Threading.CancellationTokenSource]::new()
  $messageCount = 0

  try {
    $ws.ConnectAsync([Uri]$url, $cts.Token).GetAwaiter().GetResult()
    $buffer = New-Object byte[] 16384
    $deadline = [DateTime]::UtcNow.AddSeconds($durationSec)

    while ([DateTime]::UtcNow -lt $deadline -and $ws.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
      $segment = [System.ArraySegment[byte]]::new($buffer, 0, $buffer.Length)
      $receiveTask = $ws.ReceiveAsync($segment, $cts.Token)
      if (-not $receiveTask.Wait($receiveTimeoutMs)) {
        continue
      }
      $result = $receiveTask.Result
      if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
        break
      }
      if ($result.Count -gt 0) {
        $messageCount++
      }
    }

    if ($ws.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
      $ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "stress_done", $cts.Token).GetAwaiter().GetResult()
    }

    [PSCustomObject]@{
      id       = $id
      ok       = $true
      messages = $messageCount
      error    = $null
    }
  }
  catch {
    [PSCustomObject]@{
      id       = $id
      ok       = $false
      messages = $messageCount
      error    = $_.Exception.Message
    }
  }
  finally {
    $cts.Dispose()
    $ws.Dispose()
  }
}

$jobs = @()
for ($i = 1; $i -le $Connections; $i++) {
  $jobs += Start-Job -ScriptBlock $jobScript -ArgumentList $i, $wsUrl, $DurationSec, $ReceiveTimeoutMs
}

$waitBudget = $DurationSec + 30
Wait-Job -Job $jobs -Timeout $waitBudget | Out-Null

$running = $jobs | Where-Object { $_.State -eq "Running" }
if ($running.Count -gt 0) {
  $running | Stop-Job | Out-Null
}

$results = Receive-Job -Job $jobs
$jobs | Remove-Job -Force | Out-Null

$total = $results.Count
$okCount = ($results | Where-Object { $_.ok }).Count
$failCount = $total - $okCount
$msgTotal = ($results | Measure-Object -Property messages -Sum).Sum
$avgMessages = if ($total -gt 0) { [math]::Round($msgTotal / $total, 2) } else { 0 }

Write-Host ""
Write-Host "[stress] summary"
Write-Host "  total clients : $total"
Write-Host "  success       : $okCount"
Write-Host "  failed        : $failCount"
Write-Host "  total messages: $msgTotal"
Write-Host "  avg/client    : $avgMessages"

if ($ShowErrors) {
  $errors = $results | Where-Object { -not $_.ok } | Select-Object -First 20
  if ($errors.Count -gt 0) {
    Write-Host ""
    Write-Host "[stress] first errors"
    $errors | Format-Table id, error -AutoSize
  }
}

