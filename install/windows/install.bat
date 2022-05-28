:: Run oc with Docker. Do some simple checks for prerequisites Docker and Zerotier
@echo off

:: Check if Docker is installed in the default location
if exist "%programfiles%\Docker\Docker\resources\bin\" (
    echo Looks like Docker is installed in the default location
) else (
    echo Looks like Docker is NOT installed in the default location.
    echo Install docker: https://www.docker.com/
)

:: Check if ZeroTier is installed in the default location
if exist "%programfiles(x86)%\ZeroTier\One\" (
    echo Looks like ZeroTier is installed in the default location.
) else (
    echo Looks like ZeroTier is NOT installed in the default location.
    echo Install ZeroTier: https://www.zerotier.com/
)

:: Check if OpenCola storage dir already exists in the default location
if exist "%appdata%\opencola" (
        echo OpenCola storage already exists.
    ) else (
        echo OpenCola storage does not exist. Making a new one...
        xcopy ..\opencola\storage\ %appdata%\opencola\storage\ /E

    )
echo Starting OpenCola with docker..
docker compose up -d 

echo All done. Make sure to install the Chrome extension.

:: opencola folder with chrome extension in it 
start ..\opencola\chrome\

:: Start default browser
start http://localhost:5795