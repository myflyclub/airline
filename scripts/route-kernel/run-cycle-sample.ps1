param(
    [Parameter(Mandatory = $true)]
    [string]$Schema,

    [ValidateSet("legacy", "compact", "adjacency", "compact-adjacency")]
    [string]$RouteKernel = "legacy",

    [ValidateRange(1, 100)]
    [int]$Cycles = 10,

    [string]$Stamp = (Get-Date -Format "yyyyMMdd_HHmmss"),

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
    throw "Refusing to run a simulation cycle sample against the default airline schema."
}

if (![string]::IsNullOrWhiteSpace($DemandFixtureCapture) -and ![string]::IsNullOrWhiteSpace($DemandFixtureReplay)) {
    throw "Use either -DemandFixtureCapture or -DemandFixtureReplay, not both."
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$logDir = Join-Path $repoRoot "logs\route-kernel"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$wrapperLog = Join-Path $logDir "cycle_sample_${Schema}_${RouteKernel}_${Cycles}cycles_$Stamp.log"

$oneCycleArgs = @{
    Schema = $Schema
    RouteKernel = $RouteKernel
}

if (![string]::IsNullOrWhiteSpace($DemandFixtureCapture)) { $oneCycleArgs.DemandFixtureCapture = $DemandFixtureCapture }
if (![string]::IsNullOrWhiteSpace($DemandFixtureReplay)) { $oneCycleArgs.DemandFixtureReplay = $DemandFixtureReplay }
if (![string]::IsNullOrWhiteSpace($RouteCostSeed)) { $oneCycleArgs.RouteCostSeed = $RouteCostSeed }
if (![string]::IsNullOrWhiteSpace($LinkRandomSeed)) { $oneCycleArgs.LinkRandomSeed = $LinkRandomSeed }
if (![string]::IsNullOrWhiteSpace($LoyalistRandomSeed)) { $oneCycleArgs.LoyalistRandomSeed = $LoyalistRandomSeed }
if ($SequentialPassengerConsume.IsPresent) { $oneCycleArgs.SequentialPassengerConsume = $true }

function Write-SampleLog {
    param([string]$Message)

    $Message | Tee-Object -FilePath $wrapperLog -Append
}

Write-SampleLog "cycleSample schema=$Schema routeKernel=$RouteKernel cycles=$Cycles start $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')"

for ($cycleRun = 1; $cycleRun -le $Cycles; $cycleRun++) {
    Write-SampleLog "cycleSample schema=$Schema routeKernel=$RouteKernel cycleRun=$cycleRun start $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')"

    & (Join-Path $PSScriptRoot "run-one-cycle.ps1") @oneCycleArgs 2>&1 |
        Tee-Object -FilePath $wrapperLog -Append

    Write-SampleLog "cycleSample schema=$Schema routeKernel=$RouteKernel cycleRun=$cycleRun finish $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')"
}

Write-SampleLog "cycleSample schema=$Schema routeKernel=$RouteKernel cycles=$Cycles finish $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')"
Write-Host "wrapperLog=$wrapperLog"
