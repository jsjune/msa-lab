@echo off
setlocal enabledelayedexpansion

set MODULE=spring-cloud-gateway
set REGISTRY=master-1:30002/project
set ROOT_DIR=%~dp0..
if "%ROOT_DIR:~-1%"=="\" set ROOT_DIR=%ROOT_DIR:~0,-1%

for /f %%i in ('powershell -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set TAG=%%i
set IMAGE=%REGISTRY%/%MODULE%:%TAG%

echo ============================================
echo [BUILD] %MODULE% -^> %IMAGE%
echo ============================================

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

set K8S_FILE=%ROOT_DIR%\k8s\%MODULE%.yaml
if exist "%K8S_FILE%" (
    powershell -Command "(Get-Content '%K8S_FILE%') -replace 'image: .*%MODULE%.*', 'image: %IMAGE%' | Set-Content '%K8S_FILE%'"
    echo [YAML]  Updated %K8S_FILE%
)

echo.
echo Done! Tag: %TAG%
