# Install Docker 

https://www.docker.com/get-started

To keep OpenCola running, even after a restart, you will want to have Docker run at login. To do this, open
the docker control dashboard, click the gear icon at the top right and select "Start Docker Desktop when you log in".

NOTE: If you're running on Linux (or using a docker daemon without Docker Desktop), the docker daemon should start
automatically, so this step isn't necessary.  


# Install the Browser Extension

- In a Chrome based browser (maybe works on Firefox?), got to extensions (e.g. brave://extensions/)
- Select "Developer Mode"
- Click "Load unpacked"
- Select the 'chrome' directory in this folder (opencola)
- Pin the extension to the toolbar
- IF YOU'RE NOT RUNNING ON LOCALHOST - edit chrome/popup.js and replace localhost:7595 with yourhost:5795


# You should now be ready to run: 

# To start on Unix (including MacOS)
cd unix
start

# On Windows
...

# To stop OpenCola (which stops the docker image):
stop






