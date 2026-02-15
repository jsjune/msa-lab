@echo off
setlocal enabledelayedexpansion

set MODULE=server-a
set REGISTRY=master-1:30002/project
set ROOT_DIR=%~dp0..
if "%ROOT_DIR:~-1%"=="\" set ROOT_DIR=%ROOT_DIR:~0,-1%

for /f %%i in ('powershell -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set TAG=%%i
set IMAGE=%REGISTRY%/%MODULE%:%TAG%

echo ============================================
echo [BUILD] %MODULE% -^> %IMAGE%
echo ============================================

echo [GRADLE] Building %MODULE% jar...
call "%ROOT_DIR%\gradlew.bat" -p "%ROOT_DIR%" :%MODULE%:build -x test
if %errorlevel% neq 0 (
    echo [ERROR] gradle build failed
    exit /b 1
)

docker build -t %IMAGE% -f "%~dp0Dockerfile" "%ROOT_DIR%"
if %errorlevel% neq 0 (
    echo [ERROR] docker build failed
    exit /b 1
)

echo [PUSH]  %IMAGE%
docker push %IMAGE%
if %errorlevel% neq 0 (
    echo [ERROR] docker push failed
    exit /b 1
)

echo [CLEAN] Removing old %REGISTRY%/%MODULE% images (keeping %TAG%)...
for /f "tokens=*" %%I in ('docker images --format "{{.Repository}}:{{.Tag}}" %REGISTRY%/%MODULE%') do (
    if /i not "%%I"=="%IMAGE%" (
        echo         Removing %%I
        docker rmi %%I 2>nul
    )
)

set K8S_FILE=%ROOT_DIR%\k8s\%MODULE%.yaml
if exist "%K8S_FILE%" (
    powershell -Command "(Get-Content '%K8S_FILE%') -replace 'image: .*%MODULE%.*', 'image: %IMAGE%' | Set-Content '%K8S_FILE%'"
    echo [YAML]  Updated %K8S_FILE%
)

echo.
echo Done! Tag: %TAG%