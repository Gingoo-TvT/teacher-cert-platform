param(
    [Parameter(Mandatory = $true)]
    [string] $Name,
    [Parameter(Mandatory = $true)]
    [string] $HealthUrl,
    [Parameter(Mandatory = $true, ValueFromRemainingArguments = $true)]
    [string[]] $Command
)

$ErrorActionPreference = 'Stop'

# Do not invoke this script from Codex/headless exec. It starts a persistent
# service and is intended for an external terminal or review harness only.

if ($Command.Count -lt 1) {
    Write-Error 'Usage: .\scripts\dev-serve.ps1 <name> <health-url> <command> [args...]'
    exit 2
}

$runtimeDir = Join-Path $env:TEMP 'teacher-cert-platform'
New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null

$pidFile = Join-Path $runtimeDir "$Name.pid"
$logFile = Join-Path $runtimeDir "$Name.log"
$errFile = Join-Path $runtimeDir "$Name.err.log"

if (Test-Path $pidFile) {
    $oldPid = (Get-Content -LiteralPath $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($oldPid) {
        $oldProcess = Get-Process -Id ([int] $oldPid) -ErrorAction SilentlyContinue
        if ($oldProcess) {
            Write-Output "$Name already running: pid=$oldPid log=$logFile"
            exit 0
        }
    }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
}

$exe = $Command[0]
$args = if ($Command.Count -gt 1) { $Command[1..($Command.Count - 1)] } else { @() }
$process = Start-Process -FilePath $exe `
    -ArgumentList $args `
    -WorkingDirectory (Get-Location).Path `
    -RedirectStandardOutput $logFile `
    -RedirectStandardError $errFile `
    -WindowStyle Hidden `
    -PassThru

Set-Content -LiteralPath $pidFile -Value $process.Id -Encoding ASCII
Write-Output "$Name starting: pid=$($process.Id) log=$logFile err=$errFile"

for ($i = 0; $i -lt 60; $i++) {
    try {
        Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 2 | Out-Null
        Write-Output "$Name UP"
        exit 0
    } catch {
        $running = Get-Process -Id $process.Id -ErrorAction SilentlyContinue
        if (-not $running) {
            Write-Error "$Name exited before health check passed, see $logFile / $errFile"
            exit 1
        }
        Start-Sleep -Seconds 2
    }
}

Write-Error "$Name health check timed out, see $logFile / $errFile"
exit 1
