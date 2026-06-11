param(
    [Parameter(Mandatory = $true)]
    [string]$Schema,

    [ValidateSet("legacy", "compact", "adjacency", "compact-adjacency")]
    [string]$RouteKernel = "legacy",

    [string]$DemandFixtureCapture = "",

    [string]$DemandFixtureReplay = "",

    [string]$RouteCostSeed = "",

    [string]$LinkRandomSeed = "",

    [string]$LoyalistRandomSeed = "",

    [switch]$SequentialPassengerConsume
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($Schema.Equals("airline", [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to run a simulation cycle against the default airline schema."
}

if (![string]::IsNullOrWhiteSpace($DemandFixtureCapture) -and ![string]::IsNullOrWhiteSpace($DemandFixtureReplay)) {
    throw "Use either -DemandFixtureCapture or -DemandFixtureReplay, not both."
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$airlineData = Join-Path $repoRoot "airline-data"
$logDir = Join-Path $repoRoot "logs\route-kernel"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logPath = Join-Path $logDir "one_cycle_${Schema}_${RouteKernel}_$stamp.log"

function Resolve-ExperimentPath {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return [System.IO.Path]::GetFullPath((Join-Path $repoRoot $Path))
}

function Set-OptionalEnv {
    param(
        [string]$Name,
        [AllowNull()][string]$Value
    )

    if ($null -eq $Value) {
        Remove-Item "Env:$Name" -ErrorAction SilentlyContinue
    } else {
        Set-Item "Env:$Name" $Value
    }
}

$demandFixtureCapturePath = Resolve-ExperimentPath $DemandFixtureCapture
$demandFixtureReplayPath = Resolve-ExperimentPath $DemandFixtureReplay
$routeCostSeedValue = if ([string]::IsNullOrWhiteSpace($RouteCostSeed)) { $null } else { $RouteCostSeed }
$linkRandomSeedValue = if ([string]::IsNullOrWhiteSpace($LinkRandomSeed)) { $null } else { $LinkRandomSeed }
$loyalistRandomSeedValue = if ([string]::IsNullOrWhiteSpace($LoyalistRandomSeed)) { $null } else { $LoyalistRandomSeed }
$passengerConsumeParallelValue = if ($SequentialPassengerConsume.IsPresent) { "false" } else { $null }

$previousDbName = $env:DB_NAME
$previousRouteKernel = $env:ROUTE_KERNEL
$previousDemandFixtureCapture = $env:PASSENGER_DEMAND_FIXTURE_CAPTURE
$previousDemandFixtureReplay = $env:PASSENGER_DEMAND_FIXTURE_REPLAY
$previousRouteCostSeed = $env:PASSENGER_ROUTE_COST_SEED
$previousLinkRandomSeed = $env:LINK_SIMULATION_RANDOM_SEED
$previousLoyalistRandomSeed = $env:LOYALIST_RANDOM_SEED
$previousPassengerConsumeParallel = $env:PASSENGER_CONSUME_PARALLEL

try {
    $env:DB_NAME = $Schema
    $env:ROUTE_KERNEL = $RouteKernel
    Set-OptionalEnv "PASSENGER_DEMAND_FIXTURE_CAPTURE" $demandFixtureCapturePath
    Set-OptionalEnv "PASSENGER_DEMAND_FIXTURE_REPLAY" $demandFixtureReplayPath
    Set-OptionalEnv "PASSENGER_ROUTE_COST_SEED" $routeCostSeedValue
    Set-OptionalEnv "LINK_SIMULATION_RANDOM_SEED" $linkRandomSeedValue
    Set-OptionalEnv "LOYALIST_RANDOM_SEED" $loyalistRandomSeedValue
    Set-OptionalEnv "PASSENGER_CONSUME_PARALLEL" $passengerConsumeParallelValue

    Push-Location $airlineData
    try {
        $previousErrorActionPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = "Continue"
            java -jar ..\sbt-launch.jar "runMain com.patson.tools.RunOneSimulationCycle" 2>&1 |
                Tee-Object -FilePath $logPath
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
        if ($exitCode -ne 0) {
            throw "RunOneSimulationCycle failed with exit code $exitCode"
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:DB_NAME = $previousDbName
    $env:ROUTE_KERNEL = $previousRouteKernel
    Set-OptionalEnv "PASSENGER_DEMAND_FIXTURE_CAPTURE" $previousDemandFixtureCapture
    Set-OptionalEnv "PASSENGER_DEMAND_FIXTURE_REPLAY" $previousDemandFixtureReplay
    Set-OptionalEnv "PASSENGER_ROUTE_COST_SEED" $previousRouteCostSeed
    Set-OptionalEnv "LINK_SIMULATION_RANDOM_SEED" $previousLinkRandomSeed
    Set-OptionalEnv "LOYALIST_RANDOM_SEED" $previousLoyalistRandomSeed
    Set-OptionalEnv "PASSENGER_CONSUME_PARALLEL" $previousPassengerConsumeParallel
}

Write-Host "log=$logPath"
if ($null -ne $demandFixtureCapturePath) {
    Write-Host "demandFixtureCapture=$demandFixtureCapturePath"
}
if ($null -ne $demandFixtureReplayPath) {
    Write-Host "demandFixtureReplay=$demandFixtureReplayPath"
}
if (![string]::IsNullOrWhiteSpace($RouteCostSeed)) {
    Write-Host "routeCostSeed=$RouteCostSeed"
}
if (![string]::IsNullOrWhiteSpace($LinkRandomSeed)) {
    Write-Host "linkRandomSeed=$LinkRandomSeed"
}
if (![string]::IsNullOrWhiteSpace($LoyalistRandomSeed)) {
    Write-Host "loyalistRandomSeed=$LoyalistRandomSeed"
}
if ($SequentialPassengerConsume.IsPresent) {
    Write-Host "passengerConsumeParallel=false"
}
