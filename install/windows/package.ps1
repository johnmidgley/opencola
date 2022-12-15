# Requires jpackage to be in path
# Which needs https://wixtoolset.org installed and in path
# Which needs https://dotnet.microsoft.com/en-us/download/dotnet-framework/thank-you/net35-sp1-web-installer

if ($Args.count -ne 1) {
	"usage: package-win version"
	exit 1
}

"Creating Windows Installer"
# https://docs.oracle.com/en/java/javase/14/docs/specs/man/jpackage.html
jpackage --input ../opencola/server/lib/ `
    --name OpenCola `
    --vendor OpenCola `
    --app-version $Args[0] `
    --main-jar "opencola-server-$Args[0].jar" `
    --main-class opencola.server.ApplicationKt `
    --java-options '--enable-preview' `
    --arguments --desktop `
    --verbose `
    --type msi `
    --icon pull-tab.ico `
    --win-shortcut `
    --win-menu
# --win-console
