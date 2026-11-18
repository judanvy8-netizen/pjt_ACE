# ACE Web - Stop.ps1
# Save at: pjt_ACE_web3\scripts\Stop.ps1
# Run from project root: powershell -ExecutionPolicy Bypass -File .\scripts\Stop.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $root '.run\ace-processes.json'

if (-not (Test-Path -LiteralPath $pidFile)) {
    Write-Host 'No start record found. Nothing to stop.' -ForegroundColor Yellow
    exit 0
}

try { $record = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json }
catch { Write-Error "Cannot read process record: $pidFile"; exit 1 }

$hadError = $false
foreach ($name in @('spring', 'python')) {
    $entry = $record.$name
    if ($null -eq $entry) { continue }
    $processId = [int]$entry.pid
    $proc = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($null -eq $proc) {
        Write-Host "$name already stopped (PID $processId)."
        continue
    }
    $actual = $proc.StartTime.ToUniversalTime()
    $expected = [datetime]::Parse($entry.started).ToUniversalTime()
    if ([math]::Abs(($actual - $expected).TotalSeconds) -gt 2) {
        Write-Warning "PID $processId now belongs to another process. Skipping."
        continue
    }
    try {
        Stop-Process -Id $processId -Force -ErrorAction Stop
        Write-Host "Stopped $name (PID $processId)." -ForegroundColor Green
    }
    catch {
        $hadError = $true
        Write-Warning "Could not stop $name (PID $processId): $_"
    }
}
if (-not $hadError) { Remove-Item -LiteralPath $pidFile -Force }
if ($hadError) { exit 1 }
