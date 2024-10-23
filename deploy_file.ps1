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
$SiteFolder = 'D:\inetpub'
$StagingFolder = 'D:\rais-eis-staging\app'

Import-Module -Name WebAdministration

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

# Remove Existing and Copy Deployed Files for eisauthapi if app deployed
if (Test-Path $StagingFolder\eisauthapi\*) {
  Write-Host "Removing existing eisauthapi files from $SiteFolder\eisauthapi"
  Remove-Item -Recurse $SiteFolder\eisauthapi\*
  Write-Host "Removal status for eisauthapi files: $?"
  Write-Host "Copying newly deployed eisauthapi files to $SiteName\eisauthapi"
  xcopy /s/y/e  $StagingFolder\eisauthapi $SiteFolder\eisauthapi
  Write-Host "Copy status for eisauthapi files: $?"
}

# Remove Existing and Copy Deployed Files for eisfilesvc if app deployed
if (Test-Path $StagingFolder\eisfilesvc\*) {
  Write-Host "Removing existing eisfilesvc files from $SiteFolder\eisfilesvc"
  Remove-Item -Recurse $SiteFolder\eisfilesvc\*
  Write-Host "Removal status for eisfilesvc files: $?"
  Write-Host "Copying newly deployed eisfilesvc files to $SiteName\eisfilesvc"
  xcopy /s/y/e  $StagingFolder\eisfilesvc $SiteFolder\eisfilesvc
  Write-Host "Copy status for eisfilesvc files: $?"
}

# Remove Existing and Copy Deployed Files for eiswapi if app deployed
if (Test-Path $StagingFolder\eiswapi\*) {
  Write-Host "Removing existing eiswapi files from $SiteFolder\eiswapi"
  Remove-Item -Recurse $SiteFolder\eiswapi\*
  Write-Host "Removal status for eiswapi files: $?"
  Write-Host "Copying newly deployed eiswapi files to $SiteName\eiswapi"
  xcopy /s/y/e  $StagingFolder\eiswapi $SiteFolder\eiswapi
  Write-Host "Copy status for eiswapi files: $?"
}

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

Write-Host "Deploy Complete"
