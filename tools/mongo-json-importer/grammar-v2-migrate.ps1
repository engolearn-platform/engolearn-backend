[CmdletBinding()]
param([string]$Database, [string[]]$TopicId = @(), [switch]$Apply)
$ErrorActionPreference = 'Stop'
$grammarRepo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
function Read-GrammarMigrationEnv([string]$Path) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            $key = $matches[1]; $value = $matches[2].Trim()
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            $values[$key] = $value
        }
    }
    return $values
}
$grammarRootSettings = Read-GrammarMigrationEnv (Join-Path $grammarRepo '.env')
$grammarToolSettings = Read-GrammarMigrationEnv (Join-Path $PSScriptRoot '.env')
if ([string]::IsNullOrWhiteSpace($Database)) { $Database = $grammarToolSettings['MONGO_IMPORT_DATABASE'] }
if ([string]::IsNullOrWhiteSpace($Database)) { $Database = $grammarRootSettings['APP_DB'] }
if ([string]::IsNullOrWhiteSpace($Database) -or $Database -match '^(admin|config|local)$|(^|[_-])prod(uction)?($|[_-])') {
    throw 'Specify a development database.'
}
$grammarUri = $grammarToolSettings['MONGO_IMPORT_URI']
if ([string]::IsNullOrWhiteSpace($grammarUri)) {
    $grammarUri = 'mongodb://{0}:{1}@mongodb:27017/?authSource=admin' -f [Uri]::EscapeDataString($grammarRootSettings['MONGO_USER']), [Uri]::EscapeDataString($grammarRootSettings['MONGO_PASS'])
}
Push-Location $grammarRepo
try { $grammarContainer = (& docker compose ps -q mongodb | Select-Object -First 1) }
finally { Pop-Location }
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($grammarContainer)) { throw 'Start the existing MongoDB service first.' }
$grammarNetworks = (& docker inspect --format '{{json .NetworkSettings.Networks}}' $grammarContainer.Trim() | ConvertFrom-Json).PSObject.Properties
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect MongoDB network.' }
$grammarNetwork = ($grammarNetworks | Select-Object -First 1).Name
if ([string]::IsNullOrWhiteSpace($grammarNetwork)) { throw 'Cannot determine MongoDB network.' }
$grammarSettings = @{
    MONGO_IMPORT_URI = $grammarUri
    MONGO_IMPORT_DATABASE = $Database
    GRAMMAR_V2_TOPIC_IDS_JSON = (ConvertTo-Json -InputObject @($TopicId) -Compress)
    GRAMMAR_V2_APPLY = '0'
    GRAMMAR_V2_EXPECTED_PLAN_JSON = ''
}
$grammarOld = @{}
foreach ($key in $grammarSettings.Keys) { $grammarOld[$key] = [Environment]::GetEnvironmentVariable($key,'Process') }
function Invoke-GrammarMigrationCheck {
    foreach ($key in $grammarSettings.Keys) { [Environment]::SetEnvironmentVariable($key,$grammarSettings[$key],'Process') }
    $grammarArgs = @('run','--rm','--network',$grammarNetwork,'--mount',"type=bind,source=$grammarRepo,target=/workspace,readonly",'--workdir','/workspace')
    foreach ($key in $grammarSettings.Keys) { $grammarArgs += @('-e',$key) }
    $grammarArgs += @('mongo:8.0.29-noble','mongosh','--nodb','--quiet','--file','/workspace/tools/mongo-json-importer/grammar-v2-migrate.mongosh.js')
    $grammarOutput = @(& docker @grammarArgs)
    $grammarExit = $LASTEXITCODE
    $grammarOutput | ForEach-Object { Write-Host $_ }
    if ($grammarExit -ne 0) { throw 'Grammar migration validation failed; no automatic recovery was attempted.' }
    $grammarReportLine = $grammarOutput | Where-Object { $_ -like 'GRAMMAR_V2_REPORT *' } | Select-Object -Last 1
    if (-not $grammarReportLine) { throw 'Missing migration report.' }
    return ($grammarReportLine.Substring('GRAMMAR_V2_REPORT '.Length) | ConvertFrom-Json)
}
try {
    $grammarReport = Invoke-GrammarMigrationCheck
    if ($Apply -and @($grammarReport.plan).Count -gt 0) {
        if ((Read-Host 'Back up the selected database first. Type MIGRATE to apply this reviewed plan') -cne 'MIGRATE') { throw 'Migration cancelled.' }
        $grammarSettings['GRAMMAR_V2_EXPECTED_PLAN_JSON'] = ConvertTo-Json -InputObject @($grammarReport.plan) -Depth 10 -Compress
        $grammarSettings['GRAMMAR_V2_APPLY'] = '1'
        $null = Invoke-GrammarMigrationCheck
    }
} finally {
    foreach ($key in $grammarOld.Keys) { [Environment]::SetEnvironmentVariable($key,$grammarOld[$key],'Process') }
}
