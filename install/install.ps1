
echo "Removing old dist"
rm opencola
mkdir opencola

echo "Running gradle installDist"
cd ../opencola-server
./gradlew installDist

echo "Copying distribution"
cd ../install/opencola
cp ../scripts/* .
mkdir server
cp -r ../../opencola-server/server/build/install/opencola-server/* server

echo "Copying config"
#cp ../../opencola-server/opencola-server.yaml server
mkdir oc-network
cp ../../network/docker-compose.yml oc-network

echo "Copying Chrome extension"
cp -r  ../../extension/chrome .


echo "Creating storage"
mkdir -p storage/cert
cp ../../opencola-server/server/src/main/resources/storage/opencola-server.yaml storage/
cp ../../opencola-server/server/src/main/resources/storage/cert/gen-ssl-cert storage/cert
cp ../../opencola-server/server/src/main/resources/storage/cert/gen-ssl-cert.ps1 storage/cert
cp ../../opencola-server/server/src/main/resources/storage/cert/install-cert storage/cert
cp ../../opencola-server/server/src/main/resources/storage/cert/install-cert.ps1 storage/cert
echo "Done."

cd ..



