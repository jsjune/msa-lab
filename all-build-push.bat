@echo off
setlocal enabledelayedexpansion

set REGISTRY=master-1:30002/project

:: Remove trailing backslash from SCRIPT_DIR
set SCRIPT_DIR=%~dp0
if "%SCRIPT_DIR:~-1%"=="\" set SCRIPT_DIR=%SCRIPT_DIR:~0,-1%

:: Generate tag from current datetime (yyyyMMdd-HHmmss) using PowerShell
for /f %%i in ('powershell -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set TAG=%%i

echo ============================================
echo Registry : %REGISTRY%
echo Tag      : %TAG%
echo ============================================

echo [GRADLE] Building all modules...
call "%SCRIPT_DIR%\gradlew.bat" -p "%SCRIPT_DIR%" build -x test
if %errorlevel% neq 0 (
    echo [ERROR] gradle build failed
    exit /b 1
)

for %%M in (spring-cloud-gateway server-a server-b server-c) do (
    set MODULE=%%M
    set IMAGE=%REGISTRY%/%%M:!TAG!

    echo.
    echo --------------------------------------------
    echo [BUILD] %%M -^> !IMAGE!
    echo --------------------------------------------

    docker build -t !IMAGE! -f "!SCRIPT_DIR!\%%M\Dockerfile" "!SCRIPT_DIR!"
    if !errorlevel! neq 0 (
        echo [ERROR] docker build failed for %%M
        exit /b 1
    )

    echo [PUSH]  !IMAGE!
    docker push !IMAGE!
    if !errorlevel! neq 0 (
        echo [ERROR] docker push failed for %%M
        exit /b 1
    )

    echo [CLEAN] Removing old %REGISTRY%/%%M images ^(keeping !TAG!^)...
    for /f "tokens=*" %%J in ('docker images --format "{{.Repository}}:{{.Tag}}" %REGISTRY%/%%M') do (
        if /i not "%%J"=="!IMAGE!" (
            echo         Removing %%J
            docker rmi %%J 2>nul
        )
    )

    :: Update k8s YAML
    set K8S_FILE=!SCRIPT_DIR!\k8s\%%M.yaml
    if exist "!K8S_FILE!" (
        powershell -Command "(Get-Content '!K8S_FILE!') -replace 'image: .*%%M.*', 'image: !IMAGE!' | Set-Content '!K8S_FILE!'"
        echo [YAML]  Updated !K8S_FILE!
    ) else (
        echo [WARN]  !K8S_FILE! not found, skipping YAML update
    )
)

echo.
echo ============================================
echo All modules built and pushed with tag: %TAG%
echo ============================================
echo.
echo To deploy:
echo   kubectl apply -f %SCRIPT_DIR%\k8s\