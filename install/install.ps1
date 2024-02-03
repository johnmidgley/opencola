
echo "Removing old dist"
rm opencola
mkdir opencola

echo "Running gradle installDist"
cd ../opencola-server
./gradlew installDist

echo "Copying distribution"
cd ../install/opencola
mkdir server
cp -r ../../opencola-server/server/build/install/opencola-server/* server

$file = "./server/bin/opencola-server.bat"

echo "Fixing classpath"
(Get-Content $file) | ForEach-Object {
    $_ -replace "set CLASSPATH.*", "set CLASSPATH=%APP_HOME%\lib\*"
} | Set-Content $file

echo "Copying Chrome extension"
cp -r  ../../extension/chrome .

cd ..



