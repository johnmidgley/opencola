# Script execution must be enable to run this script. To do so, execute the following command:
# Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy Bypass -Force

if (Test-Path -Path "$env:ProgramFiles\Docker\Docker\resources\bin")
{
	Write-Output "Looks like Docker is installed in the default location"
} else {
	Write-Output "Looks like Docker is NOT installed in the default location."
    Write-Output "Install docker: https://www.docker.com/"
	exit 1
}

# Check if OpenCola storage dir already exists in the default location
if (Test-Path -Path "$env:AppData\opencola")
{
    Write-Output "OpenCola storage already exists."
} else {
        Write-Output "OpenCola storage does not exist. Making a new one..."
        Copy-Item -Path ..\opencola\storage\ "$env:AppData\opencola\storage" -Recurse
}

# Get Subject Altenative Names for cert (IP4 Addresses of machine)
$env:SANS = ""
$ips = Get-NetIPAddress -AddressFamily IPV4 | Where-Object {$_.AddressState -eq "Preferred"} | Select-Object IPAddress 

foreach($ip in $ips) {
    $env:SANS += $ip.IPAddress + ","
}

$certExisted = Test-Path -Path "$env:AppData\opencola\storage\cert\opencola-ssl.der"

if($certExisted)
{
    Write-Output "SSL certificate found"
} else {
    Write-Output "No SSL certificate found"
}

# Stop any running docker instances of oc
#docker compose -p opencola down

Write-Output "Starting OpenCola with docker.."
docker compose -p opencola up --build -d 

if (!$certExisted) {
    Write-Output "Waiting for certificate creation"

    for ($i = 0; $i -lt 30; $i++) {
        if (Test-Path -Path "$env:AppData\opencola\storage\cert\opencola-ssl.der") {
            break
        }
        else {
            Start-Sleep -Seconds 1
        }
    }

    if (Test-Path -Path "$env:AppData\opencola\storage\cert\opencola-ssl.der") {
        $installCert = Read-Host "New certificate found. Install? [y/n]"

        if ($installCert.toLower() -eq "y") {
            Push-Location "$env:AppData\opencola\storage\cert"
            .\install-cert.ps1
            Pop-Location
        }
    }
    else {
        Write-Output "Certficates not created. You will get privacy errors if using https"
    }
}

Write-Output "Server Started"
Write-Output ""
Write-Output "Insecure URLs:"
Write-Output "http://localhost:5795"
foreach($ip in $ips) {
    Write-Output "http://$($ip.ipAddress):5795"
}

Write-Output ""
Write-Output "Secure URLs:"
Write-Output "https://localhost:5796"
foreach($ip in $ips) {
    Write-Output "https://$($ip.ipAddress):5796"
}

Write-Output ""
Write-Output "Waiting to launch browser..."
Start-Sleep -Seconds 5
Start-Process https://localhost:5796
