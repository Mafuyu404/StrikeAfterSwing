[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Target,

    [int] $RconPort = 25575,

    [int] $ServerPort = 25565,

    [string] $RconPassword = 'behaviortest',

    [int] $StopTimeoutSeconds = 90
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Drives the behaviour test for one target: prepares run/, starts the dedicated
# server with RCON enabled, runs scripts/behavior-test/behavior_test.py against it,
# waits for the server to stop and reports the result.
#
# JAVA_HOME must already point at the JDK that target uses (the same convention as
# scripts/build-target.ps1); on CI the matrix job sets it up.

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$targetsRoot = Join-Path $repoRoot 'targets'

if ([string]::IsNullOrWhiteSpace($Target) -or
    [System.IO.Path]::IsPathRooted($Target) -or
    $Target.IndexOfAny([System.IO.Path]::GetInvalidFileNameChars()) -ge 0 -or
    $Target.Contains('/') -or
    $Target.Contains('\') -or
    $Target.Contains('..')) {
    throw "Target must be a direct directory name under targets/: $Target"
}

$targetDir = Join-Path $targetsRoot $Target
if (-not (Test-Path -LiteralPath $targetDir -PathType Container)) {
    throw "Target directory not found: $targetDir"
}

$wrapper = Join-Path $targetDir 'gradlew.bat'
if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) {
    throw "Target wrapper not found: $wrapper"
}

# A target may pin its own ports in behavior-test.json so several targets can be
# tested at the same time without fighting over the default ports.
$overridePath = Join-Path $targetDir 'behavior-test.json'
if (Test-Path -LiteralPath $overridePath -PathType Leaf) {
    $override = Get-Content -LiteralPath $overridePath -Raw | ConvertFrom-Json
    $overrideKeys = @($override.PSObject.Properties.Name)
    if ($overrideKeys -contains 'rconPort' -and -not $PSBoundParameters.ContainsKey('RconPort')) {
        $RconPort = [int] $override.rconPort
    }
    if ($overrideKeys -contains 'serverPort' -and -not $PSBoundParameters.ContainsKey('ServerPort')) {
        $ServerPort = [int] $override.serverPort
    }
}

if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    throw 'JAVA_HOME is not set. Point it at the JDK declared in targets/<name>/ci.properties.'
}

function Write-ServerProperties {
    param([string] $Path, [System.Collections.IDictionary] $Values)

    $kept = @()
    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        foreach ($line in Get-Content -LiteralPath $Path) {
            $separator = $line.IndexOf('=')
            if ($separator -lt 1) { continue }
            $key = $line.Substring(0, $separator).Trim()
            if (-not $Values.Contains($key)) { $kept += $line }
        }
    }

    $lines = $kept
    foreach ($key in $Values.Keys) { $lines += "$key=$($Values[$key])" }
    Set-Content -LiteralPath $Path -Value $lines -Encoding ascii
}

# The dedicated server writes its world and logs here, and both ForgeGradle and
# Loom/ModDevGradle use run/ as the run directory.
$runDir = Join-Path $targetDir 'run'
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Value 'eula=true' -Encoding ascii
Write-ServerProperties -Path (Join-Path $runDir 'server.properties') -Values ([ordered]@{
    'enable-rcon'    = 'true'
    'rcon.port'      = "$RconPort"
    'rcon.password'  = $RconPassword
    'server-port'    = "$ServerPort"
    'online-mode'    = 'false'
    'level-name'     = 'behavior-test-world'
    'spawn-protection' = '0'
    # Without this a server with nobody online pauses itself after a minute, which
    # freezes gametime and stops the test mobs from ticking.
    'pause-when-empty-seconds' = '0'
})

$logPath = Join-Path $runDir 'behavior-test-server.log'
Remove-Item -LiteralPath $logPath -Force -ErrorAction SilentlyContinue

Write-Host "== behavior test driver: $Target"
Write-Host "   JDK        : $env:JAVA_HOME"
Write-Host "   server log : $logPath"

$serverProcess = Start-Process -FilePath $wrapper `
    -ArgumentList 'runServer', '--console', 'plain', '--no-daemon' `
    -WorkingDirectory $targetDir `
    -RedirectStandardOutput $logPath `
    -RedirectStandardError "$logPath.err" `
    -PassThru -NoNewWindow

$testExit = 1
try {
    # behavior_test.py waits for the RCON port itself, drives the measurement and
    # asks the server to stop when it is done. PYTHONDONTWRITEBYTECODE keeps the
    # working tree free of __pycache__ directories.
    $env:PYTHONDONTWRITEBYTECODE = '1'
    & python (Join-Path $PSScriptRoot 'behavior_test.py') `
        --target $Target --port $RconPort --password $RconPassword
    $testExit = $LASTEXITCODE
}
finally {
    $deadline = (Get-Date).AddSeconds($StopTimeoutSeconds)
    while (-not $serverProcess.HasExited -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
        $serverProcess.Refresh()
    }

    if (-not $serverProcess.HasExited) {
        Write-Warning "Server did not stop within $StopTimeoutSeconds seconds; terminating it."
        $targetPathPattern = '*' + ($targetDir -replace '/', '\') + '*'
        Get-CimInstance Win32_Process |
            Where-Object { $_.Name -like 'java*' -and $_.CommandLine -like $targetPathPattern } |
            ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    }
}

# A failed mixin injection would normally crash the server before RCON opens, but
# check explicitly so a pass can never hide one.
$mixinErrors = @()
if (Test-Path -LiteralPath $logPath -PathType Leaf) {
    $mixinErrors = @(Select-String -LiteralPath $logPath -Pattern 'InjectionError|Mixin apply failed|Caused by: org.spongepowered.asm' -SimpleMatch:$false)
}
if ($mixinErrors.Count -gt 0) {
    Write-Host ''
    Write-Host '   mixin errors found in the server log:'
    $mixinErrors | Select-Object -First 10 | ForEach-Object { Write-Host "     $($_.Line.Trim())" }
    $testExit = 1
}

Write-Host ''
if ($testExit -eq 0) {
    Write-Host "   RESULT: PASS ($Target)"
} else {
    Write-Host "   RESULT: FAIL ($Target) - see $logPath"
}

exit $testExit
