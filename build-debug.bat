@echo off
setlocal

set "PROJECT_ROOT=%~dp0"
call "%PROJECT_ROOT%gradlew.bat" --no-daemon :app:assembleDebug
if errorlevel 1 (
    echo ERROR: Debug APK build failed.
    exit /b %ERRORLEVEL%
)

set "APK=%PROJECT_ROOT%app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" (
    echo ERROR: Gradle completed, but the debug APK was not found at:
    echo        %APK%
    exit /b 1
)

echo Debug APK: %APK%
exit /b 0
