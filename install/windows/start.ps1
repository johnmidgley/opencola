# Script execution must be enable to run this script. To do so, execute the following command:
# Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy Bypass -Force

$ErrorActionPreference= 'silentlycontinue'

$certDirectoryPath = "$env:AppData\opencola\storage\cert\"
$certDerPath = "$certDirectoryPath\opencola-ssl.der"
$certInstalledPath = "$certDirectoryPath\opencola-ssl.installed"

if (Test-Path -Path "$env:ProgramFiles\Docker\Docker\resources\bin") {
    "Looks like Docker is installed in the default location"
} else {
    Write-Host -ForegroundColor Red "Looks like Docker is NOT installed in the default location."
    "Install docker: https://www.docker.com/"
    exit 1
}

# Check if OpenCola storage dir already exists in the default location
if (Test-Path -Path "$env:AppData\opencola\storage") {
    "OpenCola storage already exists."
} else {
    "OpenCola storage does not exist. Making a new one..."
    Copy-Item -Path ..\opencola\storage\ "$env:AppData\opencola\storage" -Recurse
}

# Get Subject Altenative Names for cert (IP4 Addresses of machine)
$env:SANS = ""
$ips = Get-NetIPAddress -AddressFamily IPV4 | Where-Object {$_.AddressState -eq "Preferred"} | Select-Object IPAddress 

foreach($ip in $ips) {
    $env:SANS += $ip.IPAddress + ","
}

$certInstalled = (Test-Path -Path $certInstalledPath)
if($certInstalled) {
    "SSL certificate installed"
} else {
    "SSL certificate NOT installed"

    if(!([Security.Principal.WindowsPrincipal] `
        [Security.Principal.WindowsIdentity]::GetCurrent() `
        ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
	        Write-Host -ForegroundColor Red "You must be running as administrator to install certificates"
	        exit 1
    }
}

"Starting OpenCola with docker."
docker compose -p opencola up --build -d 

if($LASTEXITCODE -ne 0) {
    Write-Host -ForegroundColor Red "Docker failed to start Opencola." 
    exit 1
}

if (!$certInstalled) {
    Write-Host -NoNewline "Waiting for certificate creation."

    for ($i = 0; $i -lt 30; $i++) {
        if (Test-Path -Path $certDerPath) {
            break
        } else {
            Write-Host -NoNewline "."
            Start-Sleep -Seconds 1
        }
    }

    Write-Host ""

    if(!(Test-Path -Path $certDerPath)) {
        Write-Host -ForegroundColor Red "Certficates not created. Please check logs." 
        exit 1
    }
}

if(!$certInstalled) {
    Write-Host -ForegroundColor Green -NoNewline "New certificate found. Install? [y/n] "
    $installCert = Read-Host

    if ($installCert.toLower() -eq "y") {
        Push-Location "$env:AppData\opencola\storage\cert"
        .\install-cert.ps1
        "ERROR: $LASTEXITCODE"
        Pop-Location

        if($LASTEXITCODE -eq 0) {
            Write-Output "" >> $certInstalledPath
        }
    }
}

if(!(Test-Path -Path $certInstalledPath)) {
    Write-Host -ForegroundColor Red "Certificate did not get installed. Can't start OpenCola"
    exit 1    
}

$started = $false
Write-Host -NoNewline "Waiting for server to start."

for($i = 0; $i -lt 10; $i++){
    $status = (Invoke-WebRequest "http://localhost:5795/start").statusCode

    if($status -eq "200") {
        $started = $true
        break
    }

    Write-Host -NoNewline "."
    Start-Sleep -Seconds 1
}

""

if(!$started) {
    Write-Host -ForegroundColor Red "Server failed to start. Please check the logs."
    exit 1
}

Write-Host -ForegroundColor Green "Server Started"
""
"Insecure URLs:"
"http://localhost:5795/start"
foreach($ip in $ips) {
    "http://$($ip.ipAddress):5795/start"
}

""
"Secure URLs:"
"https://localhost:5796"
foreach($ip in $ips) {
     "https://$($ip.ipAddress):5796/start"
}

Start-Process https://localhost:5796/start
