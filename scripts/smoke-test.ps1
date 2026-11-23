<#
.SYNOPSIS
  Read-only post-deployment smoke checks for EngoLearn.
.EXAMPLE
  .\scripts\smoke-test.ps1 -BackendBaseUrl https://engolearn-backend-deploy.onrender.com
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$BackendBaseUrl,

    [string]$FrontendBaseUrl = "",

    [ValidateRange(1, 300)]
    [int]$TimeoutSec = 30,

    [ValidateRange(0, 600)]
    [int]$MaxReadyWaitSec = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-CheckGet {
    param([string]$Url)

    try {
        $response = Invoke-WebRequest -Uri $Url -Method Get -UseBasicParsing -TimeoutSec $TimeoutSec -Headers @{ "Cache-Control" = "no-cache" }
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Body = $response.Content }
    } catch {
        $httpResponse = $_.Exception.Response
        if ($null -eq $httpResponse) {
            throw "Request failed ($Url): $($_.Exception.Message)"
        }
        # Windows PowerShell 5.1 and PowerShell 7 expose StatusCode here.
        return [pscustomobject]@{ Status = [int]$httpResponse.StatusCode; Body = "" }
    }
}

function Join-Endpoint {
    param([string]$Base, [string]$Path)
    return $Base.TrimEnd('/') + $Path
}

function Assert-HealthUp {
    param([string]$Url, [string]$Label)
    $result = Invoke-CheckGet -Url $Url
    if ($result.Status -ne 200) {
        throw "$Label failed: expected HTTP 200, got $($result.Status) ($Url)"
    }
    try {
        $json = $result.Body | ConvertFrom-Json
    } catch {
        throw "$Label failed: invalid JSON ($Url)"
    }
    if ($json.status -ne "UP") {
        throw "$Label failed: expected status UP, got $($json.status) ($Url)"
    }
    Write-Host "[PASS] $Label (200 UP)"
}

function Assert-Topics {
    param([string]$Url, [string]$Label)
    $result = Invoke-CheckGet -Url $Url
    if ($result.Status -ne 200) {
        throw "$Label failed: expected HTTP 200, got $($result.Status) ($Url)"
    }
    try {
        $json = $result.Body | ConvertFrom-Json
    } catch {
        throw "$Label failed: invalid JSON ($Url)"
    }
    if ($null -eq $json.data -or $json.data.PSObject.Properties.Name -notcontains "items") {
        throw "$Label failed: response does not contain data.items ($Url)"
    }
    Write-Host "[PASS] $Label (200, data.items present)"
}

function Assert-AnonymousAuth {
    param([string]$Url, [string]$Label)
    $result = Invoke-CheckGet -Url $Url
    if ($result.Status -ne 401) {
        throw "$Label failed: expected HTTP 401 without cookies, got $($result.Status) ($Url)"
    }
    Write-Host "[PASS] $Label (401 anonymous)"
}

$backend = $BackendBaseUrl.TrimEnd('/')
$readiness = Join-Endpoint -Base $backend -Path "/actuator/health/readiness"
$timer = [System.Diagnostics.Stopwatch]::StartNew()

# Render Free can require a cold start. Wait for readiness before checking business routes.
while ($true) {
    try {
        Assert-HealthUp -Url $readiness -Label "Backend readiness"
        break
    } catch {
        if ($timer.Elapsed.TotalSeconds -ge $MaxReadyWaitSec) {
            throw "Backend did not become ready within $MaxReadyWaitSec seconds. Last error: $($_.Exception.Message)"
        }
        Write-Host "[WAIT] Backend readiness: $($_.Exception.Message)"
        Start-Sleep -Seconds 5
    }
}

Assert-HealthUp -Url (Join-Endpoint -Base $backend -Path "/actuator/health/liveness") -Label "Backend liveness"
Assert-HealthUp -Url (Join-Endpoint -Base $backend -Path "/actuator/health") -Label "Backend overall health (MongoDB included)"
Assert-Topics -Url (Join-Endpoint -Base $backend -Path "/api/v1/topics") -Label "Backend public topics"
Assert-AnonymousAuth -Url (Join-Endpoint -Base $backend -Path "/api/v1/auth/me") -Label "Backend auth boundary"

if (-not [string]::IsNullOrWhiteSpace($FrontendBaseUrl)) {
    $frontend = $FrontendBaseUrl.TrimEnd('/')
    Assert-Topics -Url (Join-Endpoint -Base $frontend -Path "/api/v1/topics") -Label "Vercel proxy to backend"
    Assert-AnonymousAuth -Url (Join-Endpoint -Base $frontend -Path "/api/v1/auth/me") -Label "Vercel proxy auth boundary"
}

Write-Host "[PASS] EngoLearn read-only smoke checks completed."
