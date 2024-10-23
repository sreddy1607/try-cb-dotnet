REM Install Internet Information Server (IIS). 
c:\Windows\Sysnative\WindowsPowerShell\v1.0\powershell.exe -Command Import-Module -Name ServerManager
c:\Windows\Sysnative\WindowsPowerShell\v1.0\powershell.exe -Command Install-WindowsFeature Web-Server

REM Ensure that staging directory is empty before deploying files
DEL /S /Q D:\rais-eis-staging\app\eisauthapi\*
DEL /S /Q D:\rais-eis-staging\app\eiswapi\*
DEL /S /Q D:\rais-eis-staging\app\eisfilesvc\*

DEL /S /Q D:\rais-eis-staging\serverconfig\*
DEL /S /Q D:\rais-eis-staging\environment\*
DEL /S /Q D:\rais-eis-staging\scripts\*

exit 0
