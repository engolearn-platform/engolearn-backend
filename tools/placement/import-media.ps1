#requires -Version 7.0
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [Parameter(Mandatory = $true)][pscredential]$Credential,
    [string]$Directory = (Join-Path $PSScriptRoot '../mongo-json-importer/samples/placement-media'),
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
$endpoint = "$BaseUrl/api/v1/placement/admin/questions/media"
$loginBody = @{ email = $Credential.UserName; password = $Credential.GetNetworkCredential().Password } | ConvertTo-Json
$null = Invoke-RestMethod "$BaseUrl/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody -SessionVariable authSession
$preflight = (Invoke-RestMethod "$endpoint/preflight" -WebSession $authSession).data
$files = @()
foreach ($hash in $preflight.missingHashes) {
    if ($hash -notmatch '^[a-f0-9]{64}$') { throw 'Unexpected audio hash.' }
    $file = Get-Item -LiteralPath (Join-Path $Directory "$hash.wav")
    if ((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash) { throw "Audio checksum mismatch: $($file.Name)" }
    $files += $file
}
Write-Output "Audio requiring upload: $($files.Count). Apply: $Apply"
if (-not $Apply) { Write-Output 'Preflight only. No audio upload or reference update.'; return }
foreach ($file in $files) {
    $null = Invoke-RestMethod $endpoint -Method Post -Form @{file=$file} -WebSession $authSession
    Write-Output "Uploaded: $($file.Name)"
}
$result = (Invoke-RestMethod "$endpoint/migrate" -Method Post -WebSession $authSession).data
if ($result.missingHashes.Count -ne 0) { throw 'Migration still has missing audio. Rerun preflight; do not publish incomplete media.' }
Write-Output "Reference updates: $($result.linkedDocuments). Missing audio: 0."
