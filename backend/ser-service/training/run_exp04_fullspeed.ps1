param(
    [string]$PythonExe = ".\venv-gpu\Scripts\python.exe",
    [int]$StageBWorkers = 8,
    [int]$WhisperThreads = 24,
    [switch]$SkipStageB,
    [switch]$SkipFeatureBuild,
    [switch]$SkipFusion
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

Set-Location (Join-Path $PSScriptRoot "..")

$cpu = Get-CimInstance Win32_Processor | Select-Object -First 1 Name, NumberOfCores, NumberOfLogicalProcessors
$ram = Get-CimInstance Win32_ComputerSystem | Select-Object -First 1 TotalPhysicalMemory
$gpuLine = ""
$gpuFreeMiB = $null
try {
    $gpuLine = (nvidia-smi --query-gpu=name,memory.total,memory.free,driver_version --format=csv,noheader,nounits | Select-Object -First 1)
    if ($gpuLine) {
        $parts = $gpuLine.Split(",") | ForEach-Object { $_.Trim() }
        if ($parts.Count -ge 3) {
            $gpuFreeMiB = [int]$parts[2]
        }
    }
} catch {
    $gpuLine = "nvidia-smi unavailable"
}

Write-Host "=== Training Host ==="
Write-Host ("CPU: {0} | cores={1} | logical={2}" -f $cpu.Name.Trim(), $cpu.NumberOfCores, $cpu.NumberOfLogicalProcessors)
Write-Host ("RAM: {0:N1} GB" -f ($ram.TotalPhysicalMemory / 1GB))
Write-Host ("GPU: {0}" -f $gpuLine)
if ($gpuFreeMiB -ne $null -and $gpuFreeMiB -lt 6500) {
    Write-Warning "Current free VRAM is below 6.5 GB. Close overlays/browser/Codex or other GPU apps before Stage B to reduce OOM risk."
}
Write-Host ""

if (-not (Test-Path $PythonExe)) {
    throw "Python executable not found: $PythonExe"
}

$env:PYTHONUTF8 = "1"
$env:TOKENIZERS_PARALLELISM = "false"
$env:PYTORCH_CUDA_ALLOC_CONF = "expandable_segments:True"
$env:OMP_NUM_THREADS = "1"
$env:MKL_NUM_THREADS = "1"

$stageBOutput = "training\checkpoints\ser_multilingual_xlsr_stageB_exp04"
$featureOutput = "training\fusion\features_exp04_full"
$gatedOutput = "training\fusion\models\fusion_exp04_gated"
$mlpOutput = "training\fusion\models\fusion_exp04_mlp"

function Invoke-Step {
    param(
        [string]$Name,
        [string[]]$CommandArgs
    )

    Write-Host ""
    Write-Host (">>> {0}" -f $Name)
    Write-Host ("{0} {1}" -f $PythonExe, ($CommandArgs -join " "))
    & $PythonExe @CommandArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Step failed: $Name"
    }
}

if (-not $SkipStageB) {
    Invoke-Step -Name "Stage B exp04 (formal output dir)" -CommandArgs @(
        "-u",
        "training\train_wav2vec2_cls.py",
        "--train-manifest", "training\manifests\multilingual_4class_v3\train.csv",
        "--val-manifest", "training\manifests\multilingual_4class_v3\val.csv",
        "--test-manifest", "training\manifests\multilingual_4class_v3\test.csv",
        "--base-model", "training\checkpoints\ser_zh_xlsr_stageA_exp04\best_model",
        "--output-dir", $stageBOutput,
        "--epochs", "10",
        "--batch-size", "8",
        "--learning-rate", "4e-6",
        "--weight-decay", "1e-4",
        "--patience", "3",
        "--num-workers", "$StageBWorkers",
        "--fp16",
        "--source-weight-map", "casia_4class=1.0,esd_4class=1.0,iemocap_4class=1.5,ravdess_4class=1.5",
        "--source-language-map", "casia_4class=zh,esd_4class=zh,iemocap_4class=en,ravdess_4class=en",
        "--device", "cuda"
    )
}

if (-not $SkipFeatureBuild) {
    Invoke-Step -Name "Build exp04_full features (stable zh text model)" -CommandArgs @(
        "-u",
        "training\build_fusion_features.py",
        "--train-manifest", "training\manifests\multilingual_4class_v3\train.csv",
        "--val-manifest", "training\manifests\multilingual_4class_v3\val.csv",
        "--test-manifest", "training\manifests\multilingual_4class_v3\test.csv",
        "--output-dir", $featureOutput,
        "--audio-model-en", "training\checkpoints\ser_multilingual_4class_exp02\best_model",
        "--audio-model-zh", "$stageBOutput\best_model",
        "--text-model-en", ".\text_models\en_roberta_sentiment",
        "--text-model-zh", ".\training\text_models\zh_sentiment_exp03\best_model",
        "--audio-device", "cuda",
        "--text-device", "cuda",
        "--whisper-model", "small",
        "--whisper-device", "cpu",
        "--whisper-cpu-threads", "$WhisperThreads",
        "--asr-cache-json", "training\fusion\features_exp04_seed\asr_cache.json",
        "--retry-empty-cache"
    )
}

if (-not $SkipFusion) {
    Invoke-Step -Name "Train fusion_exp04_gated" -CommandArgs @(
        "-u",
        "training\train_late_fusion.py",
        "--train-features", "$featureOutput\train_features.csv",
        "--val-features", "$featureOutput\val_features.csv",
        "--test-features", "$featureOutput\test_features.csv",
        "--output-dir", $gatedOutput,
        "--mode", "fusion",
        "--fusion-arch", "gated",
        "--gate-hidden-size", "64",
        "--epochs", "100",
        "--batch-size", "128",
        "--hidden-size", "128",
        "--dropout", "0.2",
        "--learning-rate", "1.5e-3",
        "--weight-decay", "1e-4",
        "--patience", "15",
        "--device", "cuda",
        "--calibration-mode", "per_language_temperature",
        "--calibration-max-iter", "300",
        "--min-language-samples", "100"
    )

    Invoke-Step -Name "Train fusion_exp04_mlp" -CommandArgs @(
        "-u",
        "training\train_late_fusion.py",
        "--train-features", "$featureOutput\train_features.csv",
        "--val-features", "$featureOutput\val_features.csv",
        "--test-features", "$featureOutput\test_features.csv",
        "--output-dir", $mlpOutput,
        "--mode", "fusion",
        "--fusion-arch", "mlp",
        "--epochs", "100",
        "--batch-size", "128",
        "--hidden-size", "128",
        "--dropout", "0.2",
        "--learning-rate", "1.5e-3",
        "--weight-decay", "1e-4",
        "--patience", "15",
        "--device", "cuda",
        "--calibration-mode", "per_language_temperature",
        "--calibration-max-iter", "300",
        "--min-language-samples", "100"
    )
}

Write-Host ""
Write-Host "=== Reports ==="
foreach ($report in @(
    "$stageBOutput\train_report.json",
    "$gatedOutput\train_report.json",
    "$mlpOutput\train_report.json"
)) {
    if (Test-Path $report) {
        Write-Host ("--- {0}" -f $report)
        Get-Content $report
    } else {
        Write-Warning ("Missing report: {0}" -f $report)
    }
}

Write-Host ""
Write-Host "=== Compare ==="
$reports = @(
    "training\fusion\models\fusion_exp03_perlang\train_report.json",
    "$gatedOutput\train_report.json",
    "$mlpOutput\train_report.json"
)

$existingReports = $reports | Where-Object { Test-Path $_ }

$existingReports | ForEach-Object {
    $r = Get-Content $_ -Raw | ConvertFrom-Json
    [PSCustomObject]@{
        run = Split-Path (Split-Path $_ -Parent) -Leaf
        arch = if ($r.PSObject.Properties.Name -contains "fusion_arch") { $r.fusion_arch } else { "mlp" }
        testF1 = [double]$r.test_metrics_calibrated.macro_f1
        testECE = [double]$r.test_metrics_calibrated.ece
        zhF1 = [double]$r.test_macro_f1_zh
        enF1 = [double]$r.test_macro_f1_en
    }
} | Sort-Object testF1 -Descending | Format-Table -AutoSize
