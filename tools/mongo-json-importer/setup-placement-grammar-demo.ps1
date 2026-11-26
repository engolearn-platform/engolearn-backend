[CmdletBinding()]
param(
    [string]$ApiBaseUrl = 'http://localhost:8080/api/v1',
    [switch]$Apply,
    [switch]$AllowRemote
)
$ErrorActionPreference = 'Stop'
$email = Read-Host 'Existing Admin email'
$password = Read-Host 'Admin password (not saved)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($password)
$previousOutputEncoding = $OutputEncoding
try {
    $OutputEncoding = [Text.UTF8Encoding]::new($false)
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $credentials = @{ email = $email; password = $plainPassword } | ConvertTo-Json -Compress
    $arguments = @((Join-Path $PSScriptRoot 'setup-placement-grammar-demo.mjs'), '--api', $ApiBaseUrl)
    if ($AllowRemote) { $arguments += '--allow-remote' }
    if ($Apply) {
        $confirmation = Read-Host "Run dry-run first and check the target BE/DB. Type DEMO to mark Ready/publish only the shared curriculum"
        if ($confirmation -cne 'DEMO') { Write-Host 'Cancelled. No content changes.'; return }
        $arguments += @('--apply', '--confirm', 'DEMO')
    }
    $credentials | & node @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Demo setup stopped. Review the report; do not replace/reset the database.' }
} finally {
    $OutputEncoding = $previousOutputEncoding
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $plainPassword = $null
    $credentials = $null
}
