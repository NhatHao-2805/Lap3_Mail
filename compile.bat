@echo off
setlocal
chcp 65001 > nul
cd /d "%~dp0"
set "JAVAC_CMD=javac"
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC_CMD=%JAVA_HOME%\bin\javac.exe"
)
echo [BUILD] Compiler: %JAVAC_CMD%
"%JAVAC_CMD%" -version
if errorlevel 1 goto :error
if not exist "build\classes" mkdir "build\classes"
if errorlevel 1 goto :error
"%JAVAC_CMD%" --release 21 -encoding UTF-8 -cp "lib/*" -d "build\classes" src\common\*.java src\server\*.java src\client\*.java
if errorlevel 1 goto :error
echo [BUILD] Thanh cong.
exit /b 0
:error
echo [ERROR] Can JDK 21 tro len. Kiem tra JAVA_HOME va thong bao o tren.
exit /b 1
