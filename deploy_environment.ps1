# This is needed because AWS CodeDeploy Agent runs in 32-bit mode,
# script below needs to run in 64-bit mode.

# Are you running in 32-bit mode?
#   (\SysWOW64\ = 32-bit mode)

if ($PSHOME -like "*SysWOW64*")
{
  Write-Warning "Restarting this script under 64-bit Windows PowerShell."

  # Restart this script under 64-bit Windows PowerShell.
  #   (\SysNative\ redirects to \System32\ for 64-bit mode)

  & (Join-Path ($PSHOME -replace "SysWOW64", "SysNative") powershell.exe) -File `
    (Join-Path $PSScriptRoot $MyInvocation.MyCommand) @args

  # Exit 32-bit script.

  Exit $LastExitCode
}

# Was restart successful?
Write-Warning "Hello from $PSHOME"
Write-Warning "  (\SysWOW64\ = 32-bit mode, \System32\ = 64-bit mode)"
Write-Warning "Original arguments (if any): $args"

# Variables
$SiteName = "RAIS-EIS-APP"

# Stop Site and App Pools
Write-Host "Stopping $SiteName"
Stop-Website -name "$SiteName"
Write-Host "Stop status: $?"

Write-Host "Sleeping for 5 seconds for web site to stop"
Start-Sleep -Seconds 5

Write-Host "Stopping Application Pools"
Stop-WebAppPool -Name "eisauthapi"
Stop-WebAppPool -Name "eisfilesvc"
Stop-WebAppPool -Name "eiswapi"

Write-Host "Sleeping for 5 seconds for app pools to stop"
Start-Sleep -Seconds 5

Write-Host "Status of Application Pools"
Get-IISAppPool -Name eisauthapi, eisfilesvc, eiswapi

# Set environment variables for Vault access
Write-Host "Setting environment variables for Vault access"
[Environment]::SetEnvironmentVariable("VAULT_ADDRESS", "{VAULT_ADDR}", "Machine")
[Environment]::SetEnvironmentVariable("APPROLE_ROLE_ID", "{APPROLE_ROLE_ID}", "Machine")
[Environment]::SetEnvironmentVariable("APPROLE_SECRET_ID", "{APPROLE_SECRET_ID}", "Machine")
[Environment]::SetEnvironmentVariable("VAULT_SECRET_PATH", "{VAULT_SECRET_PATH}", "Machine")
[Environment]::SetEnvironmentVariable("VAULT_APPROLE_AUTH_PATH", "{VAULT_APPROLE_AUTH_PATH}", "Machine")

Write-Host "Setting environment variables for SAML authentication"
[Environment]::SetEnvironmentVariable("EIS_SAML_ISSUER", "{EIS_SAML_ISSUER}", "Machine")
[Environment]::SetEnvironmentVariable("EIS_SAML_AUDIENCE", "{EIS_SAML_AUDIENCE}", "Machine")
[Environment]::SetEnvironmentVariable("EIS_SAML_THUMBPRINT", "{EIS_SAML_THUMBPRINT}", "Machine")
[Environment]::SetEnvironmentVariable("EIS_SAML_AUDIENCE_AAD", "{EIS_SAML_AUDIENCE_AAD}", "Machine")
[Environment]::SetEnvironmentVariable("EIS_SAML_ISSUER_AAD", "{EIS_SAML_ISSUER_AAD}", "Machine")
[Environment]::SetEnvironmentVariable("EIS_SAML_THUMBPRINT_AAD", "{EIS_SAML_THUMBPRINT_AAD}", "Machine")

Write-Host "Setting environment variables for EDMS WS Endpoint"
[Environment]::SetEnvironmentVariable("EDMS_WS_ENDPOINT", "{EGISTICS_END_POINT_ADDRESS}", "Machine")

Write-Host "Setting environment variables for File Share Address"
[Environment]::SetEnvironmentVariable("EIS_FILE_SHARE_PATH", "{EIS_FILE_SHARE_PATH}", "Machine")

Write-Host "Setting environment variables for Database Data Source"
[Environment]::SetEnvironmentVariable("EIS_DATABASE_DATA_SOURCE", "{EIS_DATABASE_DATA_SOURCE}", "Machine")

Write-Host "Setting environment to enable Datadog file logging"
[Environment]::SetEnvironmentVariable("DD_LOGS_ENABLED", "true", "Machine")

# Start Site and App Pools
Write-Host "Starting Application Pools"
Start-WebAppPool -Name "eisauthapi"
Start-WebAppPool -Name "eisfilesvc"
Start-WebAppPool -Name "eiswapi"

Write-Host "Status of Application Pools"
Get-IISAppPool -Name eisauthapi, eisfilesvc, eiswapi

Write-Host "Starting $SiteName"
Start-Website -name "$SiteName"
Write-Host "Start status: $?"

Write-Host "Environment Deploy Complete"
