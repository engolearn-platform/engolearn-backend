[CmdletBinding()]
param([int]$Port = 8080)
$ErrorActionPreference='Stop'
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if(-not (Test-Path -LiteralPath (Join-Path $repoRoot '.env'))) {
    throw 'Missing root .env. Run tools/placement/setup-local.ps1 first.'
}
Push-Location $repoRoot
try {
    & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev,placement-local' "-Dspring-boot.run.arguments=--server.port=$Port"
    if($LASTEXITCODE -ne 0) { throw 'Backend exited unsuccessfully; inspect startup output.' }
} finally { Pop-Location }
