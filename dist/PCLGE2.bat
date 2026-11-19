@echo off
cd /d "%~dp0"
if exist "jre\bin\javaw.exe" (
    set JAVA="jre\bin\javaw.exe"
) else (
    set JAVA=javaw
)
start "" %JAVA% --module-path "lib" --add-modules javafx.controls -cp "PCLGE2.jar;lib\gson-2.11.0.jar" pclge2.Main
