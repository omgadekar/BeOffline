@echo off
setlocal

set JAVA_HOME=C:\PROGRA~1\JETBRA~1\INTELL~1.2\jbr
set ANDROID_HOME=C:\PROGRA~2\Android\ANDROI~1
set PATH=%JAVA_HOME%\bin;%PATH%

echo Build started at: %DATE% %TIME%
echo Logging to: D:\BeOffline\build.log

"%JAVA_HOME%\bin\java.exe" ^
    -classpath "D:\BeOffline\gradle\wrapper\gradle-wrapper.jar" ^
    "org.gradle.wrapper.GradleWrapperMain" ^
    assembleDebug ^
    --no-daemon ^
    --stacktrace > "D:\BeOffline\build.log" 2>&1

set BUILD_RESULT=%ERRORLEVEL%
echo Build exit code: %BUILD_RESULT%

if %BUILD_RESULT% EQU 0 (
    echo BUILD SUCCESSFUL
    echo APK: D:\BeOffline\app\build\outputs\apk\debug\app-debug.apk
) else (
    echo BUILD FAILED - See D:\BeOffline\build.log for details
    echo --- Last 50 lines of log ---
    powershell -command "Get-Content 'D:\BeOffline\build.log' -Tail 50"
)

exit /b %BUILD_RESULT%
