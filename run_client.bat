@echo off
setlocal
chcp 65001 > nul
echo [VKU LTM] Dang khoi dong VKU Gmail Client...
set "JAVA_CMD=java"
set "JAVAC_CMD=javac"
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
    if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC_CMD=%JAVA_HOME%\bin\javac.exe"
)
cd /d "%~dp0"
echo Java: %JAVA_CMD%
"%JAVA_CMD%" -version
if errorlevel 1 goto :error
if not exist "build\client" mkdir "build\client"
if errorlevel 1 goto :error
"%JAVAC_CMD%" -encoding UTF-8 -cp "lib/*" -d "build\client" src\ui\*.java src\client\*.java
if errorlevel 1 goto :error
"%JAVA_CMD%" -cp "build\client;lib/*" client.MailClientGUI
if errorlevel 1 goto :error
exit /b 0

:error
echo.
echo [ERROR] Khong the chay Mail Client. Kiem tra loi o tren.
echo Can cai JDK va dat JAVA_HOME tro den thu muc JDK, vi du C:\Program Files\Java\jdk-21
pause
exit /b 1
