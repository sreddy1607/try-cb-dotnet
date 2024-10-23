@echo off

REM Remove the files necessary to get apps pushed if they are empty
DEL D:\rais-eis-staging\app\eiswapi\placeholder.txt
DEL D:\rais-eis-staging\app\eisauthapi\placeholder.txt
DEL D:\rais-eis-staging\app\eisfilesvc\placeholder.txt

REM These values are replaced during the build based on parameters set
SET "DEPLOY_FILES={DEPLOY_FILES}"
SET "DEPLOY_CONFIG={DEPLOY_CONFIG}" 
SET "DEPLOY_ENVIRONMENT={DEPLOY_ENVIRONMENT}"

REM If environment is being pushed, perform thes first
if %DEPLOY_ENVIRONMENT%==true (
    echo "ENVIRONMENT will be deployed"
    C:\Windows\system32\WindowsPowerShell\v1.0\powershell.exe D:\rais-eis-staging\environment\deploy-environment.ps1
)

REM If config is being pushed for the environment, perform these steps second
if %DEPLOY_CONFIG%==true (
    echo "CONFIG will be deployed"
    C:\Windows\system32\WindowsPowerShell\v1.0\powershell.exe D:\rais-eis-staging\serverconfig\deploy-config.ps1
)

REM Finally, if files are being pushed, perform these steps last
if %DEPLOY_FILES%==true (
    echo "FILES will be deployed"
    C:\Windows\system32\WindowsPowerShell\v1.0\powershell.exe D:\rais-eis-staging\scripts\deploy-files.ps1
)
