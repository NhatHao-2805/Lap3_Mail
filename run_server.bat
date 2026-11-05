@echo off
setlocal
chcp 65001 > nul
set "JAVA_CMD=java"
set "JAVAC_CMD=javac"
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
    if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC_CMD=%JAVA_HOME%\bin\javac.exe"
)
cd /d "%~dp0"
if not exist "build\server" mkdir "build\server"
"%JAVAC_CMD%" -encoding UTF-8 -cp "lib/*" -d "build\server" src\ui\*.java src\server\*.java
if errorlevel 1 goto :error
"%JAVA_CMD%" -cp "build\server;lib/*" server.MailServer
if errorlevel 1 goto :error
exit /b 0
:error
echo [ERROR] Khong the chay Mail Server. Kiem tra JDK, JAVA_HOME va loi o tren.
pause
exit /b 1
