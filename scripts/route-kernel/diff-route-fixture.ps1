param(
    [Parameter(Mandatory = $true)]
    [string]$Schema,

    [Parameter(Mandatory = $true)]
    [string]$Fixture,

    [ValidateSet("compact", "adjacency", "compact-adjacency")]
    [string]$Candidate = "compact",

    [int]$Loop = 0,

    [int]$GroupLimit = 0,

    [string]$RouteCostSeed = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($Schema.Equals("airline", [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to diff a route fixture against the default airline schema."
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$airlineData = Join-Path $repoRoot "airline-data"
$logDir = Join-Path $repoRoot "logs\route-kernel"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Resolve-ExperimentPath {
    param([string]$Path)

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

$fixturePath = Resolve-ExperimentPath $Fixture
if (!(Test-Path $fixturePath)) {
    throw "Fixture path does not exist: $fixturePath"
}

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logPath = Join-Path $logDir "route_fixture_diff_${Schema}_${Candidate}_$stamp.log"
$routeCostSeedValue = if ([string]::IsNullOrWhiteSpace($RouteCostSeed)) { $null } else { $RouteCostSeed }

$previousDbName = $env:DB_NAME
$previousRouteKernel = $env:ROUTE_KERNEL
$previousDiffFixture = $env:ROUTE_KERNEL_DIFF_FIXTURE
$previousDiffLoop = $env:ROUTE_KERNEL_DIFF_LOOP
$previousDiffGroupLimit = $env:ROUTE_KERNEL_DIFF_GROUP_LIMIT
$previousDiffCandidate = $env:ROUTE_KERNEL_DIFF_CANDIDATE
$previousRouteCostSeed = $env:PASSENGER_ROUTE_COST_SEED

try {
    $env:DB_NAME = $Schema
    $env:ROUTE_KERNEL = $Candidate
    $env:ROUTE_KERNEL_DIFF_FIXTURE = $fixturePath
    $env:ROUTE_KERNEL_DIFF_LOOP = $Loop.ToString()
    $env:ROUTE_KERNEL_DIFF_GROUP_LIMIT = $GroupLimit.ToString()
    $env:ROUTE_KERNEL_DIFF_CANDIDATE = $Candidate
    Set-OptionalEnv "PASSENGER_ROUTE_COST_SEED" $routeCostSeedValue

    Push-Location $airlineData
    try {
        $previousErrorActionPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = "Continue"
            java -jar ..\sbt-launch.jar "runMain com.patson.tools.RouteKernelFixtureDiff" 2>&1 |
                Tee-Object -FilePath $logPath
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
        if ($exitCode -ne 0) {
            throw "RouteKernelFixtureDiff failed with exit code $exitCode"
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:DB_NAME = $previousDbName
    $env:ROUTE_KERNEL = $previousRouteKernel
    Set-OptionalEnv "ROUTE_KERNEL_DIFF_FIXTURE" $previousDiffFixture
    Set-OptionalEnv "ROUTE_KERNEL_DIFF_LOOP" $previousDiffLoop
    Set-OptionalEnv "ROUTE_KERNEL_DIFF_GROUP_LIMIT" $previousDiffGroupLimit
    Set-OptionalEnv "ROUTE_KERNEL_DIFF_CANDIDATE" $previousDiffCandidate
    Set-OptionalEnv "PASSENGER_ROUTE_COST_SEED" $previousRouteCostSeed
}

Write-Host "log=$logPath"
