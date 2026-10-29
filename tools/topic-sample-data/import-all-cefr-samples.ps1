[CmdletBinding()]
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $repoRoot '.env'

if (-not (Test-Path $envFile)) {
    throw "Cannot find .env at $repoRoot. Create it from .env.example and set MONGO_USER/MONGO_PASS first."
}

$settings = @{}
foreach ($line in Get-Content -Encoding UTF8 $envFile) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$' -and $matches[1] -notmatch '^#') {
        $key = $matches[1]
        $value = $matches[2].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $settings[$key] = $value
    }
}

$mongoUser = $settings['MONGO_USER']
$mongoPass = $settings['MONGO_PASS']
$database = $settings['APP_DB']

if ([string]::IsNullOrWhiteSpace($mongoUser) -or [string]::IsNullOrWhiteSpace($mongoPass)) {
    throw 'MONGO_USER or MONGO_PASS is missing from .env. These are the local Docker Compose MongoDB credentials.'
}
if ([string]::IsNullOrWhiteSpace($database)) { $database = 'engolearn_db' }
if ($database -match '^(admin|config|local)$|(^|[_-])prod(uction)?($|[_-])') {
    throw "Refusing system or production-looking database name: $database"
}

$oldVars = @{}
$varNames = @(
    'TOPIC_SAMPLE_MONGO_URI',
    'TOPIC_SAMPLE_DATABASE',
    'TOPIC_SAMPLE_APPLY',
    'TOPIC_SAMPLE_ALLOW_MISSING_VOCAB_AUDIO_FOR_LOCAL_PREVIEW'
)
foreach ($name in $varNames) {
    $oldVars[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    $userEscaped = [Uri]::EscapeDataString($mongoUser)
    $passEscaped = [Uri]::EscapeDataString($mongoPass)
    $env:TOPIC_SAMPLE_MONGO_URI = "mongodb://${userEscaped}:${passEscaped}@mongodb:27017/?authSource=admin"
    $env:TOPIC_SAMPLE_DATABASE = $database
    Remove-Item Env:TOPIC_SAMPLE_APPLY -ErrorAction SilentlyContinue
    Remove-Item Env:TOPIC_SAMPLE_ALLOW_MISSING_VOCAB_AUDIO_FOR_LOCAL_PREVIEW -ErrorAction SilentlyContinue

    Push-Location $repoRoot
    try {
        Write-Host "`nStep 1/2 - Ensuring local MongoDB is running..." -ForegroundColor Cyan
        & docker compose up -d mongodb
        if ($LASTEXITCODE -ne 0) { throw 'docker compose up -d mongodb failed.' }

        $containerId = (& docker compose ps -q mongodb | Select-Object -First 1)
        if ($null -ne $containerId) { $containerId = $containerId.Trim() }
        if ([string]::IsNullOrWhiteSpace($containerId)) {
            throw 'Could not find the MongoDB container from Docker Compose.'
        }

        $inspectOutput = & docker inspect $containerId
        if ($LASTEXITCODE -ne 0) { throw 'docker inspect failed for the MongoDB container.' }
        $inspectRows = @($inspectOutput | ConvertFrom-Json)
        $inspect = $inspectRows[0]
        $networkName = ($inspect.NetworkSettings.Networks.PSObject.Properties | Select-Object -First 1).Name
        if ([string]::IsNullOrWhiteSpace($networkName)) {
            throw 'Could not determine the Docker network used by MongoDB.'
        }

        $mount = "type=bind,source=$repoRoot,target=/workspace,readonly"
        function Invoke-SampleScript([bool]$ApplyMode) {
            $dockerArgs = @(
                'run', '--rm', '--network', $networkName,
                '--mount', $mount,
                '--workdir', '/workspace',
                '-e', 'TOPIC_SAMPLE_MONGO_URI',
                '-e', 'TOPIC_SAMPLE_DATABASE'
            )
            if ($ApplyMode) {
                $env:TOPIC_SAMPLE_APPLY = '1'
                $env:TOPIC_SAMPLE_ALLOW_MISSING_VOCAB_AUDIO_FOR_LOCAL_PREVIEW = '1'
                $dockerArgs += @('-e', 'TOPIC_SAMPLE_APPLY', '-e', 'TOPIC_SAMPLE_ALLOW_MISSING_VOCAB_AUDIO_FOR_LOCAL_PREVIEW')
            } else {
                Remove-Item Env:TOPIC_SAMPLE_APPLY -ErrorAction SilentlyContinue
                Remove-Item Env:TOPIC_SAMPLE_ALLOW_MISSING_VOCAB_AUDIO_FOR_LOCAL_PREVIEW -ErrorAction SilentlyContinue
            }
            $dockerArgs += @(
                'mongo:8.0.29-noble', 'mongosh', '--nodb', '--file',
                '/workspace/tools/topic-sample-data/import-all-cefr-samples.mongosh.js'
            )
            & docker @dockerArgs
            if ($LASTEXITCODE -ne 0) {
                throw 'The import script failed. Check the ERROR output above; import success was not confirmed.'
            }
        }

        Write-Host "`nStep 1/2 - Read-only preflight for database: $database" -ForegroundColor Cyan
        Invoke-SampleScript $false
        if ($DryRun) {
            Write-Host 'Dry-run complete. No data was written.' -ForegroundColor Yellow
            return
        }

        Write-Host "`nWARNING: 90 vocabulary entries have no UK/US audio. This pack is for local UI preview only, not publish-ready content." -ForegroundColor Yellow
        Write-Host 'The import will add 6 topics, 30 items, 90 vocabulary entries, 60 expression groups, and 180 quiz questions. Existing documents will not be deleted or updated.' -ForegroundColor Yellow
        $confirmation = Read-Host 'To write this sample data to the local database above, type IMPORT exactly'
        if ($confirmation -cne 'IMPORT') {
            Write-Host 'Cancelled. No data was written.' -ForegroundColor Yellow
            return
        }

        Write-Host "`nStep 2/2 - Importing preview data into $database ..." -ForegroundColor Cyan
        Invoke-SampleScript $true
        Write-Host 'Import finished. Check the topics collection in Mongo Express, then call GET /api/v1/topics?level=A1 (or A2/B1/B2/C1/C2).' -ForegroundColor Green
    } finally {
        Pop-Location
    }
} finally {
    foreach ($name in $varNames) {
        $previous = $oldVars[$name]
        if ($null -eq $previous) {
            Remove-Item "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $previous, 'Process')
        }
    }
}