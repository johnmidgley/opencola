# Script execution must be enable to run this script. To do so, execute the following command:
# Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy Bypass -Force

param(
    [Parameter()]
    [String] $mode = "docker"
)

$jdkPath = "$env:ProgramFiles\Java\jdk-19"


if($mode -eq "docker") {
    if (Test-Path -Path "$env:ProgramFiles\Docker\Docker\resources\bin")
    {
	    "Looks like Docker is installed in the default location"
    } else {
	    "Looks like Docker is NOT installed in the default location."
        "Install docker: https://www.docker.com/"
	    exit 1
    }
} elseif(mode -eq "java") {
    if(!(Test-Path -Path $jdkPath -PathType Container)) {
        "Could not find required JDK in $jdkPath"
        $installJava = Read-Host "Would you like to install java now? [y/n]"
        if($installJava.toLower() -eq "y") {
            .\install-java 
            if($LASTEXITCODE -ne 0) {
                "Java install failed. Can't start OpenCola."
                exit 1
            }
        } else {
            "You must install Java to run OpenCola in java mode"
            exit 1
        }
    }
} else {
    "ERROR: Unknown mode: $mode"
    exit 1
}

# Check if OpenCola storage dir already exists in the default location
if (Test-Path -Path "$env:AppData\opencola")
{
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

$certExisted = Test-Path -Path "$env:AppData\opencola\storage\cert\opencola-ssl.der"

if($certExisted)
{
    "SSL certificate found"
} else {
    "No SSL certificate found"

    if(!([Security.Principal.WindowsPrincipal] `
        [Security.Principal.WindowsIdentity]::GetCurrent() `
        ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
	        Write-Host "TYou must be running as administrator to install certificates" -ForegroundColor Red
	        exit 1
    }
}

if($mode -eq "docker") {
    "Starting OpenCola with docker.."
    docker compose -p opencola up --build -d 

    if($LASTEXITCODE -ne 0) {
        "Docker failed to start Opencola."
        exit 1
    }

    if (!$certExisted) {
        "Waiting for certificate creation"

        for ($i = 0; $i -lt 30; $i++) {
            if (Test-Path -Path "$env:AppData\opencola\storage\cert\opencola-ssl.der") {
                break
            }
            else {
                Start-Sleep -Seconds 1
            }
        }
    }
} elseif($mode -eq "java") {
    if(!$certExisted) {
        $sans = "dns:localhost"

        foreach($ip in $ips) {
            $sans += ",ip:" + $ip.IPAddress 
        }

        Push-Location "$env:AppData\opencola\storage\cert"
        .\gen-ssl-cert.ps1 $sans
        Pop-location 

        if($LASTEXITCODE -ne 0) {
            "ERROR: Unable to create certificates"
        }
    }
}

# If a cert was created, install it
if(!$certExisted) {
    if (Test-Path -Path "$env:AppData\opencola\storage\cert\opencola-ssl.der") {
        $installCert = Read-Host "New certificate found. Install? [y/n]"

        if ($installCert.toLower() -eq "y") {
            Push-Location "$env:AppData\opencola\storage\cert"
            .\install-cert.ps1
            "ERROR: $LASTEXITCODE"
            Pop-Location

            if($LASTEXITCODE -ne 0) {
                "Certificat did not get installed. Did you forget to run as administrator?"
                "If so, goto $env:AppData\opencola\storage\cert and run install-cert.ps1 in an Administrator shell"
                exit 1
            }
        }
    }
    else {
        "Certficates not created. You will get privacy errors if using https"
    }
}

if($mode -eq "java") {
    $processOptions = @{
        FilePath = "$jdkPath\bin\java"
        WorkingDirectory = "."
        #RedirectStandardOutput = "stdout.log"
        #RedirectStandardError = "stderr.log"
        WindowStyle = "Minimized"
        ArgumentList = "-classpath ..\opencola\server\lib\* opencola.server.ApplicationKt -s $env:appData\opencola\storage\ -a ..\opencola\server"
    }

    Start-Process @processOptions
    # java -classpath "..\opencola\server\lib\*" opencola.server.ApplicationKt -s "$env:appData\opencola\storage\" -a ..\opencola\server
}

"Server Started"
""
"Insecure URLs:"
"http://localhost:5795"
foreach($ip in $ips) {
    "http://$($ip.ipAddress):5795"
}

""
"Secure URLs:"
"https://localhost:5796"
foreach($ip in $ips) {
     "https://$($ip.ipAddress):5796"
}

""
"Waiting to launch browser..."
Start-Sleep -Seconds 5
Start-Process https://localhost:5796
