# ACE Web - Start.ps1
# Save at: pjt_ACE\scripts\Start.ps1
# Run from project root: powershell -ExecutionPolicy Bypass -File .\scripts\Start.ps1

[CmdletBinding()]
param(
    [string]$Jdk17Home,
    [string]$StoragePath
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$web = Join-Path $root 'web'
$python = Join-Path $root '.venv\Scripts\python.exe'
$jar = Join-Path $web 'build\libs\ace-web-1.0.0.jar'
$runDir = Join-Path $root '.run'
$logDir = Join-Path $root 'logs'
$pidFile = Join-Path $runDir 'ace-processes.json'

function Test-Jdk17([string]$javaPath) {
    if (-not (Test-Path -LiteralPath $javaPath -PathType Leaf)) { return $false }
    $javac = Join-Path (Split-Path -Parent $javaPath) 'javac.exe'
    if (-not (Test-Path -LiteralPath $javac -PathType Leaf)) { return $false }

    # Windows PowerShell 5.1 can wrap native stderr as an error record.
    # java -version writes there even on success, so use a local preference.
    $ErrorActionPreference = 'Continue'
    $version = & $javaPath -version 2>&1
    return ($LASTEXITCODE -eq 0 -and ($version -join "`n") -match '(?m)^\s*(?:openjdk|java)\s+version\s+"17(?:[.+"-])')
}

function Find-Jdk17Java([string]$requestedHome) {
    if (-not [string]::IsNullOrWhiteSpace($requestedHome)) {
        $requestedJava = Join-Path $requestedHome 'bin\java.exe'
        if (-not (Test-Jdk17 $requestedJava)) {
            throw "The -Jdk17Home folder does not contain a working JDK 17: $requestedHome"
        }
        return (Resolve-Path -LiteralPath $requestedJava).Path
    }

    if ($env:JAVA_HOME) {
        $homeJava = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Jdk17 $homeJava) { return (Resolve-Path -LiteralPath $homeJava).Path }
    }

    foreach ($command in @(Get-Command java.exe -CommandType Application -All -ErrorAction SilentlyContinue)) {
        if (Test-Jdk17 $command.Source) { return $command.Source }
    }

    # Check standard installer folders and IDE-managed JDKs if PATH only lists Java 8.
    $installRoots = @()
    if ($env:ProgramFiles) {
        $installRoots += @('Eclipse Adoptium', 'Java', 'Microsoft', 'Amazon Corretto', 'Zulu', 'BellSoft') |
            ForEach-Object { Join-Path $env:ProgramFiles $_ }
    }
    if ($env:USERPROFILE) {
        $installRoots += Join-Path $env:USERPROFILE '.jdks'
    }

    foreach ($installRoot in $installRoots) {
        if (-not (Test-Path -LiteralPath $installRoot -PathType Container)) { continue }
        foreach ($folder in @(Get-ChildItem -LiteralPath $installRoot -Directory -ErrorAction SilentlyContinue)) {
            $candidate = Join-Path $folder.FullName 'bin\java.exe'
            if (Test-Jdk17 $candidate) { return $candidate }
        }
    }
    throw 'JDK 17 was not found. Install JDK 17 or pass -Jdk17Home "C:\path\to\jdk-17".'
}

function Test-Port([int]$port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect('127.0.0.1', $port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(600)) { return $false }
        try { $client.EndConnect($async); return $true } catch { return $false }
    } catch { return $false }
    finally { $client.Close() }
}

function Ensure-MySQL {
    # A listening MySQL port is enough; don't try to start another service.
    if (Test-Port 3306) {
        Write-Host 'MySQL port 3306 is already available.' -ForegroundColor Green
        return
    }

    # Common Windows MySQL service names: MySQL84, MySQL80, MySQL.
    $services = @(Get-Service -Name 'MySQL*' -ErrorAction SilentlyContinue)
    if ($services.Count -eq 0) {
        throw 'Port 3306 is closed and no MySQL* Windows service was found. Check MySQL installation/service name.'
    }
    $preferred = @($services | Where-Object { $_.Name -in @('MySQL84', 'MySQL80', 'MySQL') })
    if ($preferred.Count -eq 1) {
        $service = $preferred[0]
    } elseif ($services.Count -eq 1) {
        $service = $services[0]
    } else {
        $names = ($services | ForEach-Object { $_.Name }) -join ', '
        throw "Multiple MySQL services found ($names). Choose the correct one and start it manually."
    }

    Write-Host "MySQL port 3306 is closed. Checking service: $($service.Name)" -ForegroundColor Yellow
    if ($service.Status -ne 'Running') {
        Write-Host "Starting MySQL service: $($service.Name)..." -ForegroundColor Cyan
        try {
            Start-Service -Name $service.Name -ErrorAction Stop
        } catch {
            throw "Could not start MySQL service '$($service.Name)'. Run PowerShell as Administrator, or start the service manually. Details: $($_.Exception.Message)"
        }
        try {
            (Get-Service -Name $service.Name).WaitForStatus('Running', [TimeSpan]::FromSeconds(30))
        } catch {
            throw "MySQL service '$($service.Name)' did not reach Running within 30 seconds. Check Windows Event Viewer."
        }
    }

    # Service Running and database ready to accept connections are different states.
    for ($i = 0; $i -lt 30; $i++) {
        if (Test-Port 3306) {
            Write-Host 'MySQL is ready on 127.0.0.1:3306.' -ForegroundColor Green
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "MySQL service '$($service.Name)' is running, but 127.0.0.1:3306 is unavailable. Check my.ini (port/bind-address) and logs."
}

try {
    Write-Host '[1/4] Checking environment...' -ForegroundColor Cyan
    if (-not (Test-Path -LiteralPath $python)) { throw "Python venv not found: $python" }
    if (-not (Test-Path -LiteralPath $jar)) { throw "Built JAR not found: $jar (run Gradle bootJar first)" }
    $java = Find-Jdk17Java $Jdk17Home
    Write-Host "JDK 17: $java" -ForegroundColor Green

    # Set one absolute storage path before spawning either server.
    $selectedStorage = $StoragePath
    if ([string]::IsNullOrWhiteSpace($selectedStorage)) { $selectedStorage = $env:ACE_STORAGE }
    if ([string]::IsNullOrWhiteSpace($selectedStorage)) { $selectedStorage = 'ACE_STORAGE' }
    if (-not [System.IO.Path]::IsPathRooted($selectedStorage)) {
        $selectedStorage = Join-Path $root $selectedStorage
    }
    $env:ACE_STORAGE = [System.IO.Path]::GetFullPath($selectedStorage)
    Write-Host "ACE storage: $env:ACE_STORAGE" -ForegroundColor Green
    Ensure-MySQL
    if ((Test-Port 8090) -or (Test-Port 8080)) { throw 'Port 8090 or 8080 is already in use. Stop existing servers first.' }
    if (Test-Path -LiteralPath $pidFile) { throw "Previous start record exists: $pidFile. Run Stop.ps1 first (or inspect it if stale)." }

    New-Item -ItemType Directory -Force -Path $runDir, $logDir | Out-Null

    Write-Host '[2/4] Starting Python AI server (8090)...' -ForegroundColor Cyan
    $pyArgs = @('-m', 'uvicorn', 'app.ai_server:app', '--host', '127.0.0.1', '--port', '8090')
    $py = Start-Process -FilePath $python -ArgumentList $pyArgs -WorkingDirectory $root -PassThru `
        -RedirectStandardOutput (Join-Path $logDir 'ai.out.log') `
        -RedirectStandardError (Join-Path $logDir 'ai.err.log')
    Start-Sleep -Seconds 2
    $py.Refresh()
    if ($py.HasExited) { throw 'Python server stopped. Check logs\ai.err.log.' }

    Write-Host '[3/4] Reading MySQL password in this terminal...' -ForegroundColor Cyan
    $securePassword = Read-Host -Prompt 'ace_app MySQL password (hidden input)' -AsSecureString
    if ($null -eq $securePassword -or $securePassword.Length -eq 0) { throw 'Password input was cancelled or empty.' }
    $env:ACE_DB_USER = 'ace_app'
    $passwordPointer = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $env:ACE_DB_PASSWORD = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    }
    finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
        $securePassword = $null
    }

    Write-Host '[4/4] Starting Spring Boot (8080)...' -ForegroundColor Cyan
    $spring = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $jar + '"')) -WorkingDirectory $web -PassThru `
        -RedirectStandardOutput (Join-Path $logDir 'spring.out.log') `
        -RedirectStandardError (Join-Path $logDir 'spring.err.log')

    # Do not keep credentials in the PowerShell session.
    Remove-Item Env:\ACE_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\ACE_DB_USER -ErrorAction SilentlyContinue

    Start-Sleep -Seconds 3
    $spring.Refresh()
    if ($spring.HasExited) { throw 'Spring Boot stopped. Check logs\spring.out.log and logs\spring.err.log.' }

    # Keep PID and start time to avoid terminating unrelated processes after PID reuse.
    $record = [ordered]@{
        python = [ordered]@{ pid = $py.Id; started = $py.StartTime.ToUniversalTime().ToString('o') }
        spring = [ordered]@{ pid = $spring.Id; started = $spring.StartTime.ToUniversalTime().ToString('o') }
    }
    $record | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $pidFile -Encoding UTF8

    Write-Host ''
    Write-Host 'Start commands submitted.' -ForegroundColor Green
    Write-Host 'ACE Home: http://127.0.0.1:8080/'
    Write-Host 'AI Health: http://127.0.0.1:8090/api/health'
    Write-Host 'If the website is not ready yet, check logs\spring.out.log.'
    Write-Host 'To stop: powershell -ExecutionPolicy Bypass -File .\scripts\Stop.ps1'
    Write-Host ''
    Write-Host 'http://127.0.0.1:8080/' -ForegroundColor Green
}
catch {
    # Stop any processes newly started by this invocation on failure.
    foreach ($proc in @($spring, $py)) {
        if ($null -ne $proc) {
            try { $proc.Refresh(); if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction Stop } } catch {}
        }
    }
    Remove-Item Env:\ACE_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\ACE_DB_USER -ErrorAction SilentlyContinue
    Write-Error $_.Exception.Message
    exit 1
}
