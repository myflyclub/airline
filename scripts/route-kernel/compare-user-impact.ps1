param(
    [Parameter(Mandatory = $true)]
    [string]$LegacySchema,

    [Parameter(Mandatory = $true)]
    [string]$CompactSchema,

    [Parameter(Mandatory = $true)]
    [int]$StartCycle,

    [Parameter(Mandatory = $true)]
    [int]$EndCycle,

    [string]$MySqlBin = "C:\Program Files (x86)\MySQL\mysql-5.7.44-winx64\bin",

    [string]$HostName = "127.0.0.1",

    [int]$Port = 3307,

    [string]$User = "root",

    [string]$OutputDir = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($EndCycle -lt $StartCycle) {
    throw "EndCycle must be greater than or equal to StartCycle."
}

foreach ($schema in @($LegacySchema, $CompactSchema)) {
    if ($schema.Equals("airline", [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to compare against the default airline schema."
    }
    if ($schema -notmatch "^[A-Za-z0-9_]+$") {
        throw "Unsupported schema name: $schema"
    }
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$logDir = Join-Path $repoRoot "logs\route-kernel"
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $OutputDir = Join-Path $logDir "user_impact_${LegacySchema}_vs_${CompactSchema}_${StartCycle}_${EndCycle}_$stamp"
}
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$mysql = Join-Path $MySqlBin "mysql.exe"
if (!(Test-Path $mysql)) {
    throw "mysql.exe not found at $mysql"
}

function Quote-Schema {
    param([string]$Schema)
    return "$([char]96)$Schema$([char]96)"
}

function Invoke-ImpactQuery {
    param(
        [string]$Name,
        [string]$Query
    )

    $queryPath = Join-Path $OutputDir "$Name.sql"
    $outputPath = Join-Path $OutputDir "$Name.tsv"
    Set-Content -Path $queryPath -Value $Query -Encoding UTF8

    $mysqlArgs = @(
        "--host=$HostName",
        "--port=$Port",
        "--user=$User",
        "--batch",
        "--raw"
    )
    $output = Get-Content -Raw -Path $queryPath | & $mysql @mysqlArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        $output | Set-Content -Path $outputPath -Encoding UTF8
        throw "Query '$Name' failed. See $outputPath"
    }
    $output | Set-Content -Path $outputPath -Encoding UTF8
    Write-Host "$Name=$outputPath"
}

$legacy = Quote-Schema $LegacySchema
$compact = Quote-Schema $CompactSchema

$topAirlines = @"
(
SELECT 1 AS rank_num, 'Delta Air Lines Inc.' AS airline_name UNION ALL
SELECT 2, 'United Air Lines Inc.' UNION ALL
SELECT 3, 'American Airlines Inc.' UNION ALL
SELECT 4, 'THY Turkish Airlines' UNION ALL
SELECT 5, 'Emirates' UNION ALL
SELECT 6, 'British Airways' UNION ALL
SELECT 7, 'China Southern Airlines' UNION ALL
SELECT 8, 'China Eastern Airlines' UNION ALL
SELECT 9, 'Southwest Airlines Co.' UNION ALL
SELECT 10, 'Ryanair' UNION ALL
SELECT 11, 'Air China' UNION ALL
SELECT 12, 'Lufthansa' UNION ALL
SELECT 13, 'Cathay Pacific' UNION ALL
SELECT 14, 'Singapore Airlines' UNION ALL
SELECT 15, 'KLM' UNION ALL
SELECT 16, 'Air France' UNION ALL
SELECT 17, 'Air Canada' UNION ALL
SELECT 18, 'Indigo' UNION ALL
SELECT 19, 'All Nippon Airways' UNION ALL
SELECT 20, 'Korean Air'
)
"@

$schemaSummary = @"
SELECT
  'legacy' AS algorithm,
  '$LegacySchema' AS schema_name,
  (SELECT cycle FROM $legacy.cycle LIMIT 1) AS cycle_pointer,
  (SELECT COUNT(*) FROM $legacy.link) AS link_count,
  (SELECT COUNT(*) FROM $legacy.airline) AS airline_count,
  (SELECT COUNT(*) FROM $legacy.link_consumption WHERE cycle BETWEEN $StartCycle AND $EndCycle) AS link_consumption_rows_in_window
UNION ALL
SELECT
  'compact',
  '$CompactSchema',
  (SELECT cycle FROM $compact.cycle LIMIT 1),
  (SELECT COUNT(*) FROM $compact.link),
  (SELECT COUNT(*) FROM $compact.airline),
  (SELECT COUNT(*) FROM $compact.link_consumption WHERE cycle BETWEEN $StartCycle AND $EndCycle);
"@

$airlineMapping = @"
SELECT
  t.rank_num,
  t.airline_name,
  la.id AS legacy_airline_id,
  ca.id AS compact_airline_id
FROM $topAirlines t
LEFT JOIN $legacy.airline la ON la.name = t.airline_name
LEFT JOIN $compact.airline ca ON ca.name = t.airline_name
ORDER BY t.rank_num;
"@

$airlineFinal = @"
SELECT
  t.rank_num,
  t.airline_name,
  ROUND(ls.load_factor * 100, 2) AS legacy_load_factor_pct,
  ROUND(cs.load_factor * 100, 2) AS compact_load_factor_pct,
  ROUND((cs.load_factor - ls.load_factor) * 100, 2) AS load_factor_delta_pp,
  (ld.ticket_revenue + ld.lounge_revenue) AS legacy_revenue,
  (cd.ticket_revenue + cd.lounge_revenue) AS compact_revenue,
  (cd.ticket_revenue + cd.lounge_revenue) - (ld.ticket_revenue + ld.lounge_revenue) AS revenue_delta,
  ROUND(((cd.ticket_revenue + cd.lounge_revenue) - (ld.ticket_revenue + ld.lounge_revenue)) / NULLIF((ld.ticket_revenue + ld.lounge_revenue), 0) * 100, 2) AS revenue_delta_pct,
  lb.income AS legacy_profit,
  cb.income AS compact_profit,
  cb.income - lb.income AS profit_delta,
  ROUND((cb.income - lb.income) / NULLIF(lb.income, 0) * 100, 2) AS profit_delta_pct,
  GREATEST(-lb.income, 0) AS legacy_loss,
  GREATEST(-cb.income, 0) AS compact_loss,
  ls.total AS legacy_passengers,
  cs.total AS compact_passengers,
  cs.total - ls.total AS passenger_delta,
  ROUND((cs.total - ls.total) / NULLIF(ls.total, 0) * 100, 2) AS passenger_delta_pct,
  ls.link_count AS legacy_link_count,
  cs.link_count AS compact_link_count
FROM $topAirlines t
JOIN $legacy.airline la ON la.name = t.airline_name
JOIN $compact.airline ca ON ca.name = t.airline_name
JOIN $legacy.balance lb ON lb.airline = la.id AND lb.period = 0 AND lb.cycle = $EndCycle
JOIN $legacy.balance_details ld ON ld.airline = lb.airline AND ld.period = lb.period AND ld.cycle = lb.cycle
JOIN $compact.balance cb ON cb.airline = ca.id AND cb.period = 0 AND cb.cycle = $EndCycle
JOIN $compact.balance_details cd ON cd.airline = cb.airline AND cd.period = cb.period AND cd.cycle = cb.cycle
JOIN $legacy.airline_statistics ls ON ls.airline = la.id AND ls.period = 0 AND ls.cycle = $EndCycle
JOIN $compact.airline_statistics cs ON cs.airline = ca.id AND cs.period = 0 AND cs.cycle = $EndCycle
ORDER BY t.rank_num;
"@

$airlineWindow = @"
SELECT
  t.rank_num,
  t.airline_name,
  SUM(ld.ticket_revenue + ld.lounge_revenue) AS legacy_revenue_total,
  SUM(cd.ticket_revenue + cd.lounge_revenue) AS compact_revenue_total,
  SUM(cd.ticket_revenue + cd.lounge_revenue) - SUM(ld.ticket_revenue + ld.lounge_revenue) AS revenue_delta,
  ROUND((SUM(cd.ticket_revenue + cd.lounge_revenue) - SUM(ld.ticket_revenue + ld.lounge_revenue)) / NULLIF(SUM(ld.ticket_revenue + ld.lounge_revenue), 0) * 100, 2) AS revenue_delta_pct,
  SUM(lb.income) AS legacy_profit_total,
  SUM(cb.income) AS compact_profit_total,
  SUM(cb.income) - SUM(lb.income) AS profit_delta,
  ROUND((SUM(cb.income) - SUM(lb.income)) / NULLIF(SUM(lb.income), 0) * 100, 2) AS profit_delta_pct,
  ROUND(AVG(ls.load_factor) * 100, 2) AS legacy_avg_load_factor_pct,
  ROUND(AVG(cs.load_factor) * 100, 2) AS compact_avg_load_factor_pct,
  ROUND((AVG(cs.load_factor) - AVG(ls.load_factor)) * 100, 2) AS avg_load_factor_delta_pp,
  ROUND(MIN(ls.load_factor) * 100, 2) AS legacy_min_load_factor_pct,
  ROUND(MIN(cs.load_factor) * 100, 2) AS compact_min_load_factor_pct,
  SUM(ls.total) AS legacy_passengers_total,
  SUM(cs.total) AS compact_passengers_total,
  SUM(cs.total) - SUM(ls.total) AS passenger_delta,
  ROUND((SUM(cs.total) - SUM(ls.total)) / NULLIF(SUM(ls.total), 0) * 100, 2) AS passenger_delta_pct
FROM $topAirlines t
JOIN $legacy.airline la ON la.name = t.airline_name
JOIN $compact.airline ca ON ca.name = t.airline_name
JOIN $legacy.balance lb ON lb.airline = la.id AND lb.period = 0 AND lb.cycle BETWEEN $StartCycle AND $EndCycle
JOIN $legacy.balance_details ld ON ld.airline = lb.airline AND ld.period = lb.period AND ld.cycle = lb.cycle
JOIN $compact.balance cb ON cb.airline = ca.id AND cb.period = 0 AND cb.cycle = lb.cycle
JOIN $compact.balance_details cd ON cd.airline = cb.airline AND cd.period = cb.period AND cd.cycle = cb.cycle
JOIN $legacy.airline_statistics ls ON ls.airline = la.id AND ls.period = 0 AND ls.cycle = lb.cycle
JOIN $compact.airline_statistics cs ON cs.airline = ca.id AND cs.period = 0 AND cs.cycle = lb.cycle
GROUP BY t.rank_num, t.airline_name
ORDER BY t.rank_num;
"@

$routeAggLegacy = @"
SELECT
  lc.link,
  lc.airline,
  SUM(lc.sold_seats_economy + lc.sold_seats_business + lc.sold_seats_first) AS sold,
  SUM(lc.capacity_economy + lc.capacity_business + lc.capacity_first) AS capacity,
  SUM(lc.revenue) AS revenue,
  SUM(lc.profit) AS profit,
  AVG(lc.satisfaction) AS satisfaction,
  MIN(lc.cycle) AS min_cycle,
  MAX(lc.cycle) AS max_cycle
FROM $legacy.link_consumption lc
JOIN $legacy.airline a ON a.id = lc.airline
JOIN $topAirlines t ON t.airline_name = a.name
WHERE lc.cycle BETWEEN $StartCycle AND $EndCycle
GROUP BY lc.link, lc.airline
"@

$routeAggCompact = @"
SELECT
  lc.link,
  lc.airline,
  SUM(lc.sold_seats_economy + lc.sold_seats_business + lc.sold_seats_first) AS sold,
  SUM(lc.capacity_economy + lc.capacity_business + lc.capacity_first) AS capacity,
  SUM(lc.revenue) AS revenue,
  SUM(lc.profit) AS profit,
  AVG(lc.satisfaction) AS satisfaction,
  MIN(lc.cycle) AS min_cycle,
  MAX(lc.cycle) AS max_cycle
FROM $compact.link_consumption lc
JOIN $compact.airline a ON a.id = lc.airline
JOIN $topAirlines t ON t.airline_name = a.name
WHERE lc.cycle BETWEEN $StartCycle AND $EndCycle
GROUP BY lc.link, lc.airline
"@

$routeWorstLoadFactor = @"
SELECT
  t.rank_num,
  a.name AS airline_name,
  l.link,
  CONCAT(f.iata, '-', tos.iata) AS route,
  ROUND(l.sold / NULLIF(l.capacity, 0) * 100, 2) AS legacy_load_factor_pct,
  ROUND(c.sold / NULLIF(c.capacity, 0) * 100, 2) AS compact_load_factor_pct,
  ROUND((c.sold / NULLIF(c.capacity, 0) - l.sold / NULLIF(l.capacity, 0)) * 100, 2) AS load_factor_delta_pp,
  l.sold AS legacy_passengers,
  c.sold AS compact_passengers,
  c.sold - l.sold AS passenger_delta,
  ROUND((c.sold - l.sold) / NULLIF(l.sold, 0) * 100, 2) AS passenger_delta_pct,
  l.revenue AS legacy_revenue,
  c.revenue AS compact_revenue,
  c.revenue - l.revenue AS revenue_delta,
  ROUND((c.revenue - l.revenue) / NULLIF(l.revenue, 0) * 100, 2) AS revenue_delta_pct,
  l.profit AS legacy_profit,
  c.profit AS compact_profit,
  c.profit - l.profit AS profit_delta
FROM ($routeAggLegacy) l
JOIN ($routeAggCompact) c ON c.link = l.link
JOIN $legacy.link link ON link.id = l.link
JOIN $legacy.airline a ON a.id = l.airline
JOIN $topAirlines t ON t.airline_name = a.name
JOIN $legacy.airport f ON f.id = link.from_airport
JOIN $legacy.airport tos ON tos.id = link.to_airport
WHERE l.capacity >= 10000 AND l.sold >= 5000
ORDER BY load_factor_delta_pp ASC, passenger_delta_pct ASC
LIMIT 100;
"@

$routeWorstRevenue = @"
SELECT
  t.rank_num,
  a.name AS airline_name,
  l.link,
  CONCAT(f.iata, '-', tos.iata) AS route,
  l.revenue AS legacy_revenue,
  c.revenue AS compact_revenue,
  c.revenue - l.revenue AS revenue_delta,
  ROUND((c.revenue - l.revenue) / NULLIF(l.revenue, 0) * 100, 2) AS revenue_delta_pct,
  ROUND(l.sold / NULLIF(l.capacity, 0) * 100, 2) AS legacy_load_factor_pct,
  ROUND(c.sold / NULLIF(c.capacity, 0) * 100, 2) AS compact_load_factor_pct,
  ROUND((c.sold / NULLIF(c.capacity, 0) - l.sold / NULLIF(l.capacity, 0)) * 100, 2) AS load_factor_delta_pp,
  l.sold AS legacy_passengers,
  c.sold AS compact_passengers,
  c.sold - l.sold AS passenger_delta,
  ROUND((c.sold - l.sold) / NULLIF(l.sold, 0) * 100, 2) AS passenger_delta_pct
FROM ($routeAggLegacy) l
JOIN ($routeAggCompact) c ON c.link = l.link
JOIN $legacy.link link ON link.id = l.link
JOIN $legacy.airline a ON a.id = l.airline
JOIN $topAirlines t ON t.airline_name = a.name
JOIN $legacy.airport f ON f.id = link.from_airport
JOIN $legacy.airport tos ON tos.id = link.to_airport
WHERE l.revenue >= 1000000
ORDER BY revenue_delta_pct ASC, load_factor_delta_pp ASC
LIMIT 100;
"@

$hubAggLegacy = @"
SELECT
  b.airline,
  b.airport,
  SUM(lc.sold_seats_economy + lc.sold_seats_business + lc.sold_seats_first) AS sold,
  SUM(lc.capacity_economy + lc.capacity_business + lc.capacity_first) AS capacity,
  SUM(lc.revenue) AS revenue,
  SUM(lc.profit) AS profit,
  COUNT(DISTINCT lc.link) AS route_count
FROM $legacy.airline_base b
JOIN $legacy.airline a ON a.id = b.airline
JOIN $topAirlines t ON t.airline_name = a.name
LEFT JOIN $legacy.link_consumption lc ON lc.airline = b.airline
  AND lc.cycle BETWEEN $StartCycle AND $EndCycle
  AND (lc.from_airport = b.airport OR lc.to_airport = b.airport)
GROUP BY b.airline, b.airport
"@

$hubAggCompact = @"
SELECT
  b.airline,
  b.airport,
  SUM(lc.sold_seats_economy + lc.sold_seats_business + lc.sold_seats_first) AS sold,
  SUM(lc.capacity_economy + lc.capacity_business + lc.capacity_first) AS capacity,
  SUM(lc.revenue) AS revenue,
  SUM(lc.profit) AS profit,
  COUNT(DISTINCT lc.link) AS route_count
FROM $compact.airline_base b
JOIN $compact.airline a ON a.id = b.airline
JOIN $topAirlines t ON t.airline_name = a.name
LEFT JOIN $compact.link_consumption lc ON lc.airline = b.airline
  AND lc.cycle BETWEEN $StartCycle AND $EndCycle
  AND (lc.from_airport = b.airport OR lc.to_airport = b.airport)
GROUP BY b.airline, b.airport
"@

$hubSummaryBase = @"
SELECT
  t.rank_num,
  a.name AS airline_name,
  ap.iata AS hub_iata,
  ap.city AS hub_city,
  lb.scale,
  lb.headquarter,
  l.route_count AS legacy_route_count,
  c.route_count AS compact_route_count,
  ROUND(l.sold / NULLIF(l.capacity, 0) * 100, 2) AS legacy_load_factor_pct,
  ROUND(c.sold / NULLIF(c.capacity, 0) * 100, 2) AS compact_load_factor_pct,
  ROUND((c.sold / NULLIF(c.capacity, 0) - l.sold / NULLIF(l.capacity, 0)) * 100, 2) AS load_factor_delta_pp,
  l.sold AS legacy_passengers,
  c.sold AS compact_passengers,
  c.sold - l.sold AS passenger_delta,
  ROUND((c.sold - l.sold) / NULLIF(l.sold, 0) * 100, 2) AS passenger_delta_pct,
  l.revenue AS legacy_revenue,
  c.revenue AS compact_revenue,
  c.revenue - l.revenue AS revenue_delta,
  ROUND((c.revenue - l.revenue) / NULLIF(l.revenue, 0) * 100, 2) AS revenue_delta_pct,
  l.profit AS legacy_profit,
  c.profit AS compact_profit,
  c.profit - l.profit AS profit_delta
FROM ($hubAggLegacy) l
JOIN ($hubAggCompact) c ON c.airline = l.airline AND c.airport = l.airport
JOIN $legacy.airline_base lb ON lb.airline = l.airline AND lb.airport = l.airport
JOIN $legacy.airline a ON a.id = l.airline
JOIN $topAirlines t ON t.airline_name = a.name
JOIN $legacy.airport ap ON ap.id = l.airport
"@

$hubSummary = @"
$hubSummaryBase
ORDER BY rank_num, headquarter DESC, scale DESC, hub_iata;
"@

$hubWorstDrops = @"
SELECT *
FROM (
  $hubSummaryBase
) hub
WHERE legacy_passengers >= 5000
ORDER BY load_factor_delta_pp ASC, passenger_delta_pct ASC
LIMIT 100;
"@

Invoke-ImpactQuery "schema_summary" $schemaSummary
Invoke-ImpactQuery "airline_mapping" $airlineMapping
Invoke-ImpactQuery "airline_final" $airlineFinal
Invoke-ImpactQuery "airline_window" $airlineWindow
Invoke-ImpactQuery "route_worst_load_factor" $routeWorstLoadFactor
Invoke-ImpactQuery "route_worst_revenue" $routeWorstRevenue
Invoke-ImpactQuery "hub_summary" $hubSummary
Invoke-ImpactQuery "hub_worst_drops" $hubWorstDrops

Write-Host "outputDir=$OutputDir"
