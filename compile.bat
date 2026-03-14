@echo off
echo Cleaning and packaging FirstJoinRTP...
call mvn clean package
if %errorlevel% neq 0 (
    echo.
    echo Compilation failed! Please check the output above.
    pause
    exit /b %errorlevel%
)
echo.
echo Compilation successful!
pause
