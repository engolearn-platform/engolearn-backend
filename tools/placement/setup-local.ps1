[CmdletBinding()]
param(
    [switch]$Apply,
    [switch]$NoStartMongo,
    [string]$MongoUri,
    [string]$Database,
    [string]$Network
)
$ErrorActionPreference='Stop'
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$envPath=Join-Path $repoRoot '.env'
if(-not (Test-Path -LiteralPath $envPath)) {
    $content=[IO.File]::ReadAllText((Join-Path $repoRoot '.env.example'))
    $bytes=New-Object byte[] 48
    $random=[Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    $content=$content.Replace('replace-with-random-32-byte-minimum-secret',[Convert]::ToBase64String($bytes))
    [IO.File]::WriteAllText($envPath,$content,[Text.UTF8Encoding]::new($false))
    Write-Host 'Created local .env with a random JWT secret; existing .env files are never overwritten.'
}
$settings=@{}
foreach($line in Get-Content -LiteralPath $envPath) {
    if($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
        $settings[$matches[1]]=$matches[2].Trim().Trim('"').Trim("'")
    }
}
if(-not $Database) { $Database=$settings['APP_DB'] }
if(-not $Database -or $Database -match '^(admin|config|local)$|(^|[_-])prod(uction)?($|[_-])') {
    throw 'A non-system, non-production development database is required.'
}
if(-not $MongoUri) {
    if(-not $settings['MONGO_USER'] -or -not $settings['MONGO_PASS']) { throw 'Missing Mongo root credentials in .env.' }
    $MongoUri='mongodb://{0}:{1}@mongodb:27017/?authSource=admin&directConnection=true' -f
        [uri]::EscapeDataString($settings['MONGO_USER']),[uri]::EscapeDataString($settings['MONGO_PASS'])
}
$names=@('MONGO_IMPORT_URI','MONGO_IMPORT_DATABASE','MONGO_IMPORT_FILES_JSON','MONGO_IMPORT_OBJECT_ID_FIELDS','MONGO_IMPORT_APPLY','MONGO_IMPORT_CONFLICT_POLICY','MONGO_IMPORT_BATCH_SIZE','MONGO_IMPORT_CONTINUE_ON_ERROR')
$saved=@{}
foreach($name in $names) { $saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
Push-Location $repoRoot
try {
    if(-not $NoStartMongo) {
        & docker compose up -d --wait mongodb
        if($LASTEXITCODE -ne 0) { throw 'MongoDB startup/health check failed; no import performed.' }
    }
    if(-not $Network) {
        $id=(& docker compose ps -q mongodb | Select-Object -First 1)
        if(-not $id) { throw 'MongoDB container not found; specify -Network with -NoStartMongo for an explicit test environment.' }
        $inspect=@((& docker inspect $id | ConvertFrom-Json))[0]
        $Network=($inspect.NetworkSettings.Networks.PSObject.Properties | Select-Object -First 1).Name
    }
    if(-not $Network) { throw 'Docker network is required.' }
    $env:MONGO_IMPORT_URI=$MongoUri
    $env:MONGO_IMPORT_DATABASE=$Database
    $env:MONGO_IMPORT_FILES_JSON='["/workspace/tools/mongo-json-importer/samples/placement-v3-questions.importer.json","/workspace/tools/mongo-json-importer/samples/placement-v3-test-sets-v2.importer.json"]'
    $env:MONGO_IMPORT_OBJECT_ID_FIELDS='none'
    $env:MONGO_IMPORT_BATCH_SIZE='500'
    $env:MONGO_IMPORT_CONTINUE_ON_ERROR='false'
    $env:MONGO_IMPORT_CONFLICT_POLICY='skip'
    Remove-Item Env:MONGO_IMPORT_APPLY -ErrorAction SilentlyContinue
    if($Apply) { $env:MONGO_IMPORT_APPLY='1' }
    Write-Host "Placement $(if($Apply){'apply'}else{'preflight'}) database: $Database (questions + V2 only; no replacements)"
    & docker run --rm --network $Network --mount "type=bind,source=$repoRoot,target=/workspace,readonly" --workdir /workspace `
        -e MONGO_IMPORT_URI -e MONGO_IMPORT_DATABASE -e MONGO_IMPORT_FILES_JSON -e MONGO_IMPORT_OBJECT_ID_FIELDS `
        -e MONGO_IMPORT_APPLY -e MONGO_IMPORT_CONFLICT_POLICY -e MONGO_IMPORT_BATCH_SIZE -e MONGO_IMPORT_CONTINUE_ON_ERROR `
        mongo:8.0.29-noble mongosh --nodb --norc --quiet --file /workspace/tools/placement/import-local.mongosh.js
    if($LASTEXITCODE -ne 0) { throw 'Placement preflight/import failed; review the report, no replacements are attempted.' }
} finally {
    Pop-Location
    foreach($name in $names) { [Environment]::SetEnvironmentVariable($name,$saved[$name],'Process') }
}
