@echo off
setlocal
chcp 65001 > nul
set "JAVAC_CMD=javac"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC_CMD=%JAVA_HOME%\bin\javac.exe"
cd /d "%~dp0"
if not exist "build\all" mkdir "build\all"
"%JAVAC_CMD%" -encoding UTF-8 -cp "lib/*" -d "build\all" src\ui\*.java src\client\*.java src\server\*.java
if errorlevel 1 goto :error
echo [OK] Bien dich thanh cong: build\all
exit /b 0
:error
echo [ERROR] Khong the bien dich. Kiem tra JDK, JAVA_HOME va loi o tren.
pause
exit /b 1
