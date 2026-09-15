@echo off
setlocal

set "PROJECT_ROOT=%~dp0"
if not defined RELEASE_STORE_FILE set "RELEASE_STORE_FILE=%USERPROFILE%\REALASSKEYS"
if not defined RELEASE_KEY_ALIAS set "RELEASE_KEY_ALIAS=REALKEY"

if not exist "%RELEASE_STORE_FILE%" (
    echo ERROR: Release keystore was not found at:
    echo        %RELEASE_STORE_FILE%
    exit /b 1
)

rem Skip prompts in non-interactive runs. Agents must set passwords by environment.
if defined RELEASE_STORE_PASSWORD goto haveStorePassword
if defined AGENT_NONINTERACTIVE goto haveStorePassword
if defined CI goto haveStorePassword
    for /f "usebackq delims=" %%P in (`powershell.exe -NoProfile -Command "$s=Read-Host 'Release keystore password' -AsSecureString; $b=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($s); try {[Runtime.InteropServices.Marshal]::PtrToStringBSTR($b)} finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b)}"`) do set "RELEASE_STORE_PASSWORD=%%P"
:haveStorePassword
if defined RELEASE_KEY_PASSWORD goto haveKeyPassword
if defined AGENT_NONINTERACTIVE goto haveKeyPassword
if defined CI goto haveKeyPassword
    for /f "usebackq delims=" %%P in (`powershell.exe -NoProfile -Command "$s=Read-Host 'Release key password' -AsSecureString; $b=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($s); try {[Runtime.InteropServices.Marshal]::PtrToStringBSTR($b)} finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b)}"`) do set "RELEASE_KEY_PASSWORD=%%P"
:haveKeyPassword

if not defined RELEASE_STORE_PASSWORD (
    echo ERROR: A release keystore password is required.
    exit /b 1
)
if not defined RELEASE_KEY_PASSWORD (
    echo ERROR: A release key password is required.
    exit /b 1
)

rem Agents require no-daemon and plain console. This prevents a post-build hang.
call "%PROJECT_ROOT%gradlew.bat" --no-daemon --console=plain :app:assembleRelease <NUL
set "BUILD_EXIT_CODE=%ERRORLEVEL%"
if not "%BUILD_EXIT_CODE%"=="0" (
    echo ERROR: Release APK build failed.
    exit /b %BUILD_EXIT_CODE%
)

set "APK=%PROJECT_ROOT%app\build\outputs\apk\release\app-release.apk"
if not exist "%APK%" (
    echo ERROR: Gradle completed, but the release APK was not found at:
    echo        %APK%
    exit /b 1
)

echo Release APK: %APK%
exit /b 0
