
param(
    [Parameter(Mandatory=$true)]
    [String] $sans
)

$jdkPath = "$env:ProgramFiles\Java\jdk-19"


if(!(Test-Path -Path $jdkPath -PathType Container)) {
    "Could not find JDK in $jdkPath"
    "Did you install it correctly?"
    exit 1
}

$keytool = "$jdkPath\bin\keytool"

&$keytool -genkeypair -alias opencola-ssl -keystore opencola-ssl.pks -dname "CN=opencola, O=OpenCola" -storepass password -keypass password -ext bc:critical=ca:true -ext san=$sans -keyalg rsa -validity 3650 -storetype PKCS12 
&$keytool -exportcert -keystore opencola-ssl.pks -alias opencola-ssl -file opencola-ssl.pem -rfc -storepass password -keypass password
&$keytool -exportcert -keystore opencola-ssl.pks -alias opencola-ssl -file opencola-ssl.der -storepass password -keypass password
&$keytool -printcert -file opencola-ssl.pem