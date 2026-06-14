param(
    [Parameter(Mandatory = $true)]
    [string] $Name
)

$ErrorActionPreference = 'Stop'

$runtimeDir = Join-Path $env:TEMP 'teacher-cert-platform'
$pidFile = Join-Path $runtimeDir "$Name.pid"

if (-not (Test-Path $pidFile)) {
    Write-Output "$Name not running"
    exit 0
}

$pidValue = (Get-Content -LiteralPath $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue

if (-not $pidValue) {
    Write-Output "$Name pid file was empty"
    exit 0
}

$process = Get-Process -Id ([int] $pidValue) -ErrorAction SilentlyContinue
if ($process) {
    Stop-Process -Id ([int] $pidValue) -Force -ErrorAction SilentlyContinue
}

Write-Output "$Name stopped: pid=$pidValue"
