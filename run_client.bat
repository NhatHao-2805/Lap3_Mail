@echo off
setlocal
chcp 65001 > nul
cd /d "%~dp0"
call "%~dp0compile.bat"
if errorlevel 1 goto :error
set "JAVA_CMD=java"
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
)
echo [RUN] Java: %JAVA_CMD%
"%JAVA_CMD%" -version
if errorlevel 1 goto :error
"%JAVA_CMD%" -cp "build/classes;lib/*" client.ChatClientGUI
if errorlevel 1 goto :error
exit /b 0
:error
echo [ERROR] Khong the khoi dong. Xem loi o tren.
pause
exit /b 1
