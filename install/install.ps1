
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

echo "Copying Chrome extension"
cp -r  ../../extension/chrome .

cd ..



