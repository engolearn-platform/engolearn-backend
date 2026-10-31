[CmdletBinding()]
param([string]$File, [switch]$DryRun)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $repoRoot '.env'
$samplesDirectory = Join-Path $PSScriptRoot 'samples'

function Read-DotEnv([string]$Path) {
    $values = @{}
    foreach ($line in Get-Content -Encoding UTF8 $Path) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            $key = $matches[1]
            $value = $matches[2].Trim()
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            $values[$key] = $value
        }
    }
    return $values
}
function Get-BooleanSetting([hashtable]$Settings, [string]$Name, [bool]$Default) {
    $raw = $Settings[$Name]
    if ([string]::IsNullOrWhiteSpace($raw)) { return $Default }
    switch ($raw.Trim().ToLowerInvariant()) {
        { $_ -in @('true','1','yes') } { return $true }
        { $_ -in @('false','0','no') } { return $false }
        default { throw "$Name must be true or false; received '$raw'." }
    }
}

if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw "Cannot find root .env at '$envFile'. Copy .env.example to .env first."
}
$settings = Read-DotEnv $envFile
$database = $settings['MONGO_IMPORT_DATABASE']
if ([string]::IsNullOrWhiteSpace($database)) { $database = $settings['APP_DB'] }
if ([string]::IsNullOrWhiteSpace($database)) { throw 'Set APP_DB or MONGO_IMPORT_DATABASE in .env.' }
if ($database -match '^(admin|config|local)$|(^|[_-])prod(uction)?($|[_-])') {
    throw "Refusing system or production-looking database name: $database"
}

$policy = $settings['MONGO_IMPORT_CONFLICT_POLICY']
if ([string]::IsNullOrWhiteSpace($policy)) { $policy = 'skip' }
$policy = $policy.Trim().ToLowerInvariant()
if ($policy -notin @('skip','fail','replace')) { throw 'MONGO_IMPORT_CONFLICT_POLICY must be skip, fail, or replace.' }

$batchSizeRaw = $settings['MONGO_IMPORT_BATCH_SIZE']
if ([string]::IsNullOrWhiteSpace($batchSizeRaw)) { $batchSizeRaw = '500' }
$batchSize = 0
if (-not [int]::TryParse($batchSizeRaw, [ref]$batchSize) -or $batchSize -lt 1) {
    throw "MONGO_IMPORT_BATCH_SIZE must be a positive integer; received '$batchSizeRaw'."
}
$continueOnError = Get-BooleanSetting $settings 'MONGO_IMPORT_CONTINUE_ON_ERROR' $false
$requireConfirmation = Get-BooleanSetting $settings 'MONGO_IMPORT_REQUIRE_CONFIRMATION' $true

$mongoUri = $settings['MONGO_IMPORT_URI']
if ([string]::IsNullOrWhiteSpace($mongoUri)) {
    $mongoUser = $settings['MONGO_USER']
    $mongoPass = $settings['MONGO_PASS']
    if ([string]::IsNullOrWhiteSpace($mongoUser) -or [string]::IsNullOrWhiteSpace($mongoPass)) {
        throw 'MONGO_USER and MONGO_PASS are required when MONGO_IMPORT_URI is empty.'
    }
    $userEscaped = [Uri]::EscapeDataString($mongoUser)
    $passEscaped = [Uri]::EscapeDataString($mongoPass)
    $mongoUri = 'mongodb://{0}:{1}@mongodb:27017/?authSource=admin' -f $userEscaped, $passEscaped
}

$resolvedFiles = @()
if ([string]::IsNullOrWhiteSpace($File)) {
    if (-not (Test-Path -LiteralPath $samplesDirectory -PathType Container)) { throw "Samples directory not found: $samplesDirectory" }
    $resolvedFiles = @(Get-ChildItem -LiteralPath $samplesDirectory -Filter '*.json' -File | Sort-Object Name | ForEach-Object { $_.FullName })
    if ($resolvedFiles.Count -eq 0) { throw "No *.json files found in '$samplesDirectory'." }
} else {
    if ([IO.Path]::IsPathRooted($File)) { $candidate = [IO.Path]::GetFullPath($File) }
    else { $candidate = [IO.Path]::GetFullPath((Join-Path (Get-Location).Path $File)) }
    $rootPrefix = $repoRoot.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $candidate.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Input file must be inside the repository because Docker mounts the repository read-only: $candidate"
    }
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { throw "Input JSON file does not exist: $candidate" }
    if ([IO.Path]::GetExtension($candidate) -ine '.json') { throw 'Input file must have a .json extension.' }
    $resolvedFiles = @($candidate)
}

$containerFiles = @()
foreach ($resolvedFile in $resolvedFiles) {
    $relative = $resolvedFile.Substring($repoRoot.Length).TrimStart('\', '/')
    $containerFiles += '/workspace/' + $relative.Replace('\', '/')
}

Write-Host ''
Write-Host 'Generic MongoDB JSON Importer' -ForegroundColor Cyan
Write-Host "Database : $database"
Write-Host "Policy   : $policy"
Write-Host "Batch    : $batchSize"
Write-Host "Files    : $($resolvedFiles.Count)"
foreach ($filePath in $resolvedFiles) { Write-Host "  - $($filePath.Substring($repoRoot.Length).TrimStart('\', '/'))" }

$environmentNames = @('MONGO_IMPORT_URI','MONGO_IMPORT_DATABASE','MONGO_IMPORT_FILES_JSON','MONGO_IMPORT_CONFLICT_POLICY','MONGO_IMPORT_BATCH_SIZE','MONGO_IMPORT_CONTINUE_ON_ERROR','MONGO_IMPORT_APPLY')
$oldEnvironment = @{}
foreach ($name in $environmentNames) { $oldEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }

try {
    $env:MONGO_IMPORT_URI = $mongoUri
    $env:MONGO_IMPORT_DATABASE = $database
    $env:MONGO_IMPORT_FILES_JSON = ConvertTo-Json -InputObject @($containerFiles) -Compress
    $env:MONGO_IMPORT_CONFLICT_POLICY = $policy
    $env:MONGO_IMPORT_BATCH_SIZE = [string]$batchSize
    $env:MONGO_IMPORT_CONTINUE_ON_ERROR = ([string]$continueOnError).ToLowerInvariant()
    Remove-Item Env:MONGO_IMPORT_APPLY -ErrorAction SilentlyContinue

    Push-Location $repoRoot
    try {
        Write-Host ''
        Write-Host 'Ensuring local MongoDB service is running...' -ForegroundColor Cyan
        & docker compose up -d mongodb
        if ($LASTEXITCODE -ne 0) { throw 'docker compose up -d mongodb failed.' }

        $containerId = (& docker compose ps -q mongodb | Select-Object -First 1)
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) { throw 'Could not find MongoDB container.' }
        $inspectOutput = & docker inspect $containerId.Trim()
        if ($LASTEXITCODE -ne 0) { throw 'docker inspect failed for MongoDB container.' }
        $inspect = @($inspectOutput | ConvertFrom-Json)[0]
        $networkName = ($inspect.NetworkSettings.Networks.PSObject.Properties | Select-Object -First 1).Name
        if ([string]::IsNullOrWhiteSpace($networkName)) { throw 'Could not determine MongoDB Docker network.' }

        $mount = "type=bind,source=$repoRoot,target=/workspace,readonly"
        function Invoke-Importer([bool]$ApplyMode) {
            if ($ApplyMode) { $env:MONGO_IMPORT_APPLY = '1' }
            else { Remove-Item Env:MONGO_IMPORT_APPLY -ErrorAction SilentlyContinue }

            $dockerArgs = @(
                'run','--rm','--network',$networkName,
                '--mount',$mount,'--workdir','/workspace',
                '-e','MONGO_IMPORT_URI','-e','MONGO_IMPORT_DATABASE','-e','MONGO_IMPORT_FILES_JSON',
                '-e','MONGO_IMPORT_CONFLICT_POLICY','-e','MONGO_IMPORT_BATCH_SIZE',
                '-e','MONGO_IMPORT_CONTINUE_ON_ERROR','-e','MONGO_IMPORT_APPLY',
                'mongo:8.0.29-noble','mongosh','--nodb','--file',
                '/workspace/tools/mongo-json-importer/importer.mongosh.js'
            )
            & docker @dockerArgs
            if ($LASTEXITCODE -ne 0) { throw 'Importer preflight or write failed. Review the MongoDB report above.' }
        }

        Write-Host ''
        Write-Host 'Step 1/2 - Read-only preflight for every input file...' -ForegroundColor Cyan
        Invoke-Importer $false
        if ($DryRun) {
            Write-Host 'Dry-run complete. No data was written.' -ForegroundColor Yellow
            return
        }

        if ($requireConfirmation) {
            $confirmation = Read-Host 'Review the preflight report. Type IMPORT to write these documents'
            if ($confirmation -cne 'IMPORT') {
                Write-Host 'Cancelled. No data was written.' -ForegroundColor Yellow
                return
            }
        }

        Write-Host ''
        Write-Host 'Step 2/2 - Applying the import...' -ForegroundColor Cyan
        Invoke-Importer $true
        Write-Host 'Import completed. Review the write report above.' -ForegroundColor Green
    } finally { Pop-Location }
} finally {
    foreach ($name in $environmentNames) {
        $previous = $oldEnvironment[$name]
        if ($null -eq $previous) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
        else { [Environment]::SetEnvironmentVariable($name, $previous, 'Process') }
    }
}
