# Pre-run steps

# Install Java 16
# https://sdkman.io/

curl -s "https://get.sdkman.io" | bash
bash
source sdkman-init.sh
sdk list java
sdk install java 16.0.2-zulu

- Set JAVE_HOME to the install (i.e. ~/.sdkman/candidates/java/16.0.2-zulu)

# Install Docker 

https://www.docker.com/get-started

# IF YOU'RE ON LINUX, you need to let docker have write access to the solr indexes / files
cd storage
sudo chown -R 8983:8983 var-solr

# Install extension

- In a Chrome based browser (maybe works on Firefox?), got to extensions (e.g. brave://extensions/)
- Select "Developer Mode"
- Click "Load unpacked"
- Select the 'chrome' directory in this folder (opencola)
- Pin the extension to the toolbar
- IF YOU'RE NOT RUNNING ON LOCALHOST - edit chrome/popup.js and replace localhost:7595 with yourhost:5795



