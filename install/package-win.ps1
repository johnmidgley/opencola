# Requires jpackage to be in path
# Which needs https://wixtoolset.org installed and in path
# Which needs https://dotnet.microsoft.com/en-us/download/dotnet-framework/thank-you/net35-sp1-web-installer

if ($Args.count -ne 1) {
	"usage: package-win versions"
	exit 1
}


echo "Creating Windows Installer"
# https://docs.oracle.com/en/java/javase/14/docs/specs/man/jpackage.html
jpackage --input opencola/server/lib/ `
--name OpenCola `
--main-jar opencola-server-1.0-SNAPSHOT.jar `
--main-class opencola.server.ApplicationKt `
--verbose `
--type msi `
--java-options '--enable-preview' `
--icon icons/pull-tab.ico `
--arguments --desktop `
--vendor OpenCola `
--win-shortcut `
--app-version $Args[0] `
--win-menu
# --resource-dir opencola/server/resources/ \
# --win-console `
