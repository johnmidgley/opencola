# Requires jpackage to be in path
# Which needs https://wixtoolset.org installed and in path
# Which needs https://dotnet.microsoft.com/en-us/download/dotnet-framework/thank-you/net35-sp1-web-installer

# Doesn't seem to work

echo "Creating Windows Installer"
# https://docs.oracle.com/en/java/javase/14/docs/specs/man/jpackage.html
jpackage --input opencola/server/lib/ `
--name OpenCola `
--main-jar opencola-server-1.0-SNAPSHOT.jar `
--main-class opencola.server.ApplicationKt `
--verbose `
--win-console `
--type msi `
--java-options '--enable-preview' `
--win-shortcut `
--win-menu
# --resource-dir opencola/server/resources/ \
