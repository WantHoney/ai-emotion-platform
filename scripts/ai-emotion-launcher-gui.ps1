$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$projectRoot = 'C:\Dev\projects\ai-emotion'
$scriptRoot = Join-Path $projectRoot 'scripts'
$launchScript = Join-Path $scriptRoot 'launch-best-dev.ps1'
$stopScript = Join-Path $scriptRoot 'stop-dev-all.ps1'
$stringsFile = Join-Path $scriptRoot 'ai-emotion-launcher-strings.zh.json'
$frontendUrl = 'http://127.0.0.1:5173'
$powershellExe = (Get-Command powershell.exe).Source
$ports = @(8001, 8080, 5173)

if (-not (Test-Path $stringsFile)) {
  throw "Missing strings file: $stringsFile"
}

$ui = Get-Content -Raw -Encoding UTF8 $stringsFile | ConvertFrom-Json

function T {
  param([string]$Key)
  $value = $ui.$Key
  if ([string]::IsNullOrWhiteSpace([string]$value)) {
    return $Key
  }
  return [string]$value
}

function Get-ServiceSnapshot {
  $items = @()
  foreach ($port in $ports) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
      Select-Object -First 1

    if ($conn) {
      $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
      $items += [PSCustomObject]@{
        Port   = $port
        Active = $true
        Pid    = $conn.OwningProcess
        Name   = if ($proc) { $proc.ProcessName } else { 'unknown' }
      }
    } else {
      $items += [PSCustomObject]@{
        Port   = $port
        Active = $false
        Pid    = $null
        Name   = ''
      }
    }
  }
  return $items
}

function New-ActionButton {
  param(
    [string]$Text,
    [int]$X,
    [int]$Y,
    [int]$Width = 140,
    [System.Drawing.Color]$BackColor = [System.Drawing.Color]::FromArgb(36, 57, 92),
    [System.Drawing.Color]$BorderColor = [System.Drawing.Color]::FromArgb(88, 123, 184)
  )

  $button = New-Object System.Windows.Forms.Button
  $button.Text = $Text
  $button.Location = New-Object System.Drawing.Point($X, $Y)
  $button.Size = New-Object System.Drawing.Size($Width, 42)
  $button.FlatStyle = 'Flat'
  $button.BackColor = $BackColor
  $button.ForeColor = [System.Drawing.Color]::White
  $button.FlatAppearance.BorderSize = 1
  $button.FlatAppearance.BorderColor = $BorderColor
  $button.FlatAppearance.MouseOverBackColor = [System.Drawing.Color]::FromArgb(
    [Math]::Min($BackColor.R + 12, 255),
    [Math]::Min($BackColor.G + 12, 255),
    [Math]::Min($BackColor.B + 12, 255)
  )
  $button.FlatAppearance.MouseDownBackColor = [System.Drawing.Color]::FromArgb(
    [Math]::Max($BackColor.R - 10, 0),
    [Math]::Max($BackColor.G - 10, 0),
    [Math]::Max($BackColor.B - 10, 0)
  )
  $button.Font = New-Object System.Drawing.Font('Microsoft YaHei UI', 9.5, [System.Drawing.FontStyle]::Bold)
  return $button
}

$form = New-Object System.Windows.Forms.Form
$form.Text = (T 'windowTitle')
$form.StartPosition = 'CenterScreen'
$form.Size = New-Object System.Drawing.Size(760, 560)
$form.MinimumSize = New-Object System.Drawing.Size(760, 560)
$form.BackColor = [System.Drawing.Color]::FromArgb(12, 18, 32)
$form.ForeColor = [System.Drawing.Color]::White
$form.Font = New-Object System.Drawing.Font('Microsoft YaHei UI', 10)

$titleLabel = New-Object System.Windows.Forms.Label
$titleLabel.Text = (T 'title')
$titleLabel.Font = New-Object System.Drawing.Font('Microsoft YaHei UI', 18, [System.Drawing.FontStyle]::Bold)
$titleLabel.AutoSize = $true
$titleLabel.Location = New-Object System.Drawing.Point(24, 18)
$titleLabel.ForeColor = [System.Drawing.Color]::White

$subtitleLabel = New-Object System.Windows.Forms.Label
$subtitleLabel.Text = (T 'subtitle')
$subtitleLabel.AutoSize = $true
$subtitleLabel.Location = New-Object System.Drawing.Point(27, 54)
$subtitleLabel.ForeColor = [System.Drawing.Color]::FromArgb(184, 200, 224)

$statusPanel = New-Object System.Windows.Forms.Panel
$statusPanel.Location = New-Object System.Drawing.Point(24, 88)
$statusPanel.Size = New-Object System.Drawing.Size(696, 92)
$statusPanel.BackColor = [System.Drawing.Color]::FromArgb(18, 28, 48)
$statusPanel.BorderStyle = [System.Windows.Forms.BorderStyle]::FixedSingle

$statusTitle = New-Object System.Windows.Forms.Label
$statusTitle.Text = (T 'currentStatus')
$statusTitle.AutoSize = $true
$statusTitle.Location = New-Object System.Drawing.Point(16, 14)
$statusTitle.ForeColor = [System.Drawing.Color]::FromArgb(184, 200, 224)

$statusDot = New-Object System.Windows.Forms.Label
$statusDot.Location = New-Object System.Drawing.Point(18, 42)
$statusDot.Size = New-Object System.Drawing.Size(16, 16)
$statusDot.Text = [string][char]0x25CF
$statusDot.TextAlign = [System.Drawing.ContentAlignment]::MiddleCenter
$statusDot.Font = New-Object System.Drawing.Font('Segoe UI Symbol', 12, [System.Drawing.FontStyle]::Bold)
$statusDot.ForeColor = [System.Drawing.Color]::FromArgb(255, 199, 125)
$statusDot.BackColor = [System.Drawing.Color]::Transparent

$statusValue = New-Object System.Windows.Forms.Label
$statusValue.Text = (T 'checking')
$statusValue.AutoSize = $true
$statusValue.Location = New-Object System.Drawing.Point(40, 38)
$statusValue.Font = New-Object System.Drawing.Font('Microsoft YaHei UI', 12, [System.Drawing.FontStyle]::Bold)
$statusValue.ForeColor = [System.Drawing.Color]::White

$statusDetail = New-Object System.Windows.Forms.Label
$statusDetail.Text = ''
$statusDetail.AutoSize = $false
$statusDetail.Location = New-Object System.Drawing.Point(240, 18)
$statusDetail.Size = New-Object System.Drawing.Size(430, 54)
$statusDetail.ForeColor = [System.Drawing.Color]::FromArgb(184, 200, 224)

$statusPanel.Controls.AddRange(@($statusTitle, $statusDot, $statusValue, $statusDetail))

$startButton = New-ActionButton -Text (T 'startRestart') -X 24 -Y 200 -Width 160 `
  -BackColor ([System.Drawing.Color]::FromArgb(40, 91, 165)) `
  -BorderColor ([System.Drawing.Color]::FromArgb(102, 157, 235))
$stopButton = New-ActionButton -Text (T 'stopServices') -X 196 -Y 200 -Width 120 `
  -BackColor ([System.Drawing.Color]::FromArgb(122, 45, 63)) `
  -BorderColor ([System.Drawing.Color]::FromArgb(198, 82, 115))
$openButton = New-ActionButton -Text (T 'openFrontend') -X 328 -Y 200 -Width 120 `
  -BackColor ([System.Drawing.Color]::FromArgb(41, 96, 84)) `
  -BorderColor ([System.Drawing.Color]::FromArgb(88, 171, 145))
$refreshButton = New-ActionButton -Text (T 'refreshStatus') -X 460 -Y 200 -Width 120 `
  -BackColor ([System.Drawing.Color]::FromArgb(52, 68, 102)) `
  -BorderColor ([System.Drawing.Color]::FromArgb(104, 129, 184))
$openFolderButton = New-ActionButton -Text (T 'openProject') -X 592 -Y 200 -Width 128 `
  -BackColor ([System.Drawing.Color]::FromArgb(71, 74, 92)) `
  -BorderColor ([System.Drawing.Color]::FromArgb(138, 144, 171))

$logTitle = New-Object System.Windows.Forms.Label
$logTitle.Text = (T 'launcherLog')
$logTitle.AutoSize = $true
$logTitle.Location = New-Object System.Drawing.Point(24, 252)
$logTitle.ForeColor = [System.Drawing.Color]::FromArgb(184, 200, 224)

$logBox = New-Object System.Windows.Forms.TextBox
$logBox.Location = New-Object System.Drawing.Point(24, 278)
$logBox.Size = New-Object System.Drawing.Size(696, 230)
$logBox.Multiline = $true
$logBox.ScrollBars = 'Vertical'
$logBox.ReadOnly = $true
$logBox.BackColor = [System.Drawing.Color]::FromArgb(10, 16, 28)
$logBox.ForeColor = [System.Drawing.Color]::FromArgb(224, 233, 247)
$logBox.BorderStyle = [System.Windows.Forms.BorderStyle]::FixedSingle
$logBox.Font = New-Object System.Drawing.Font('Consolas', 10)

function Append-Log {
  param([string]$Text)
  $timestamp = Get-Date -Format 'HH:mm:ss'
  $logBox.AppendText("[$timestamp] $Text$([Environment]::NewLine)")
}

function Set-ActionButtonsEnabled {
  param([bool]$Enabled)
  $startButton.Enabled = $Enabled
  $stopButton.Enabled = $Enabled
  $openButton.Enabled = $Enabled
  $refreshButton.Enabled = $Enabled
  $openFolderButton.Enabled = $Enabled
}

function Update-StatusView {
  $snapshot = Get-ServiceSnapshot
  $active = @($snapshot | Where-Object { $_.Active })

  if ($active.Count -eq 0) {
    $statusValue.Text = (T 'servicesOffline')
    $statusValue.ForeColor = [System.Drawing.Color]::FromArgb(255, 199, 125)
    $statusDot.ForeColor = [System.Drawing.Color]::FromArgb(255, 199, 125)
    $statusDetail.Text = (T 'offlineDetail')
    return
  }

  $statusValue.Text = (T 'servicesRunning')
  $statusValue.ForeColor = [System.Drawing.Color]::FromArgb(129, 221, 149)
  $statusDot.ForeColor = [System.Drawing.Color]::FromArgb(129, 221, 149)
  $detailParts = $active | ForEach-Object {
    [string]::Format((T 'portRunningTemplate'), $_.Port, $_.Pid, $_.Name)
  }
  $statusDetail.Text = (($ui.runningPrefix) + ($detailParts -join '    '))
}

function Invoke-DetachedScriptAction {
  param(
    [string]$ScriptPath,
    [string]$ActionName
  )

  if (-not (Test-Path $ScriptPath)) {
    [System.Windows.Forms.MessageBox]::Show(
      ((T 'missingScriptPrefix') + $ScriptPath),
      (T 'missingScriptTitle'),
      [System.Windows.Forms.MessageBoxButtons]::OK,
      [System.Windows.Forms.MessageBoxIcon]::Error
    ) | Out-Null
    return
  }

  Append-Log ($ActionName + (T 'startedSuffix'))

  try {
    $arguments = @(
      '-NoLogo'
      '-NoProfile'
      '-ExecutionPolicy'
      'Bypass'
      '-File'
      $ScriptPath
    )

    Start-Process -FilePath $powershellExe `
      -ArgumentList $arguments `
      -WorkingDirectory $projectRoot `
      -WindowStyle Hidden | Out-Null

    Start-Sleep -Milliseconds 800
    Update-StatusView
    Append-Log ($ActionName + (T 'dispatchedSuffix'))
  }
  catch {
    Append-Log $_.Exception.Message
    [System.Windows.Forms.MessageBox]::Show(
      $_.Exception.Message,
      (T 'executionFailed'),
      [System.Windows.Forms.MessageBoxButtons]::OK,
      [System.Windows.Forms.MessageBoxIcon]::Error
    ) | Out-Null
  }
}

$startButton.Add_Click({
    Invoke-DetachedScriptAction -ScriptPath $launchScript -ActionName (T 'startRestart')
  })

$stopButton.Add_Click({
    Invoke-DetachedScriptAction -ScriptPath $stopScript -ActionName (T 'stopServices')
  })

$openButton.Add_Click({
    Start-Process $frontendUrl
    Append-Log ((T 'openedFrontendPrefix') + $frontendUrl)
  })

$refreshButton.Add_Click({
    Update-StatusView
    Append-Log (T 'statusRefreshed')
  })

$openFolderButton.Add_Click({
    Start-Process $projectRoot
    Append-Log ((T 'openedProjectPrefix') + $projectRoot)
  })

$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 3000
$timer.Add_Tick({
    Update-StatusView
  })

$form.Controls.AddRange(@(
    $titleLabel,
    $subtitleLabel,
    $statusPanel,
    $startButton,
    $stopButton,
    $openButton,
    $refreshButton,
    $openFolderButton,
    $logTitle,
    $logBox
  ))

$form.Add_Shown({
    Update-StatusView
    Append-Log (T 'launcherReady')
    $timer.Start()
  })

$form.Add_FormClosing({
    $timer.Stop()
  })

[void]$form.ShowDialog()
