param(
    [ValidateSet("Verify", "Data", "Web")]
    [string]$App = "Verify",

    [string]$Schema = "airline_perf_candidate_10cycle_20260609",

    [ValidateSet("compact", "adjacency")]
    [string]$RouteKernel = "compact",

    [string]$MysqlExe = "C:\Program Files (x86)\MySQL\mysql-5.7.44-winx64\bin\mysql.exe",

    [string]$MysqlHost = "127.0.0.1",

    [int]$MysqlPort = 3307,

    [string]$MysqlUser = "root",

    [string]$MysqlPassword = "",

    [int]$MinimumCycle = 33,

    [switch]$RequireExactCycle,

    [switch]$SkipSchemaCheck
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($Schema -notmatch "^[A-Za-z0-9_]+$") {
    throw "Schema must contain only letters, numbers, and underscores."
}

if ($Schema.Equals("airline", [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to run the compact trial against the default airline schema."
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$airlineData = Join-Path $repoRoot "airline-data"
$airlineWeb = Join-Path $repoRoot "airline-web"
$dataConfig = Join-Path $airlineData "src\main\resources\application-trial-compact-cycle33.conf"
$webConfig = Join-Path $airlineWeb "conf\application-trial-compact-cycle33.conf"

function Resolve-MysqlCommand {
    if (Test-Path -LiteralPath $MysqlExe) {
        return $MysqlExe
    }
    return "mysql"
}

function Invoke-MysqlQuery {
    param([string]$Query)

    $mysqlCommand = Resolve-MysqlCommand
    $arguments = @(
        "--host=$MysqlHost",
        "--port=$MysqlPort",
        "--user=$MysqlUser",
        "--batch",
        "--raw",
        "--skip-column-names"
    )
    if (![string]::IsNullOrEmpty($MysqlPassword)) {
        $arguments += "--password=$MysqlPassword"
    }
    $arguments += "--execute=$Query"

    $output = & $mysqlCommand @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "mysql failed: $($output -join [System.Environment]::NewLine)"
    }
    return @($output)
}

function Assert-TrialSchema {
    if ($SkipSchemaCheck.IsPresent) {
        return
    }

    $escapedSchema = $Schema.Replace("'", "''")
    $schemaIdent = "$([char]96)$Schema$([char]96)"
    $schemaCount = Invoke-MysqlQuery "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = '$escapedSchema';"
    $schemaCountValue = @($schemaCount)[0]
    if ([int]$schemaCountValue -ne 1) {
        throw "Schema '$Schema' does not exist on $MysqlHost`:$MysqlPort."
    }

    $cycle = Invoke-MysqlQuery "SELECT cycle FROM $schemaIdent.cycle LIMIT 1;"
    if (@($cycle).Count -eq 0) {
        throw "Schema '$Schema' has no cycle row."
    }
    $cycleValue = @($cycle)[0]
    $cycleNumber = [int]$cycleValue
    if ($RequireExactCycle.IsPresent) {
        if ($cycleNumber -ne $MinimumCycle) {
            throw "Schema '$Schema' is at cycle $cycleValue, expected exactly cycle $MinimumCycle."
        }
    } elseif ($cycleNumber -lt $MinimumCycle) {
        throw "Schema '$Schema' is at cycle $cycleValue, expected at least cycle $MinimumCycle."
    }

    $linkCount = Invoke-MysqlQuery "SELECT COUNT(*) FROM $schemaIdent.link;"
    $linkCountValue = @($linkCount)[0]
    Write-Host "schema=$Schema"
    Write-Host "cycle=$cycleValue"
    Write-Host "minimumCycle=$MinimumCycle"
    Write-Host "linkCount=$linkCountValue"
    Write-Host "routeKernel=$RouteKernel"
    Write-Host "dataConfig=$dataConfig"
    Write-Host "webConfig=$webConfig"
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

function Invoke-WithTrialEnvironment {
    param([scriptblock]$Script)

    $previousDbName = $env:DB_NAME
    $previousRouteKernel = $env:ROUTE_KERNEL

    try {
        $env:DB_NAME = $Schema
        $env:ROUTE_KERNEL = $RouteKernel
        & $Script
    } finally {
        Set-OptionalEnv "DB_NAME" $previousDbName
        Set-OptionalEnv "ROUTE_KERNEL" $previousRouteKernel
    }
}

function Start-DataApp {
    Write-Host "Starting airline-data with DB_NAME=$Schema and ROUTE_KERNEL=$RouteKernel"
    Invoke-WithTrialEnvironment {
        Push-Location $airlineData
        try {
            java -jar ..\sbt-launch.jar "runMain com.patson.MainSimulation"
            if ($LASTEXITCODE -ne 0) {
                throw "airline-data exited with code $LASTEXITCODE"
            }
        } finally {
            Pop-Location
        }
    }
}

function Start-WebApp {
    Write-Host "Starting airline-web with DB_NAME=$Schema"
    Invoke-WithTrialEnvironment {
        Push-Location $airlineWeb
        try {
            java -jar ..\sbt-launch.jar run
            if ($LASTEXITCODE -ne 0) {
                throw "airline-web exited with code $LASTEXITCODE"
            }
        } finally {
            Pop-Location
        }
    }
}

Assert-TrialSchema

switch ($App) {
    "Verify" {
        Write-Host "Trial schema is ready. Use -App Data and -App Web in separate terminals to run it."
    }
    "Data" { Start-DataApp }
    "Web" { Start-WebApp }
}
