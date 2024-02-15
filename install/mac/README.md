<img src="../../img/pull-tab.svg" width="150" />

# Mac Packaging

## Creating Icons
Creating mac icons was done by following [this](https://dev.to/craftzdog/how-to-create-icns-file-from-png-files-on-cli-4c16).

Make a folder named ***.iconset (e.g., desktop-icon.iconset) and prepare files like so:
```
> ll
total 49976
drwxr-xr-x  13 user  staff       416 Jan 12 19:23 desktop-icon.iconset/

> ll desktop-icon.iconset/
total 1376
-rw-------  1 user  staff   10416 May  6  2019 icon_128x128.png
-rw-------  1 user  staff   26457 May  6  2019 icon_128x128@2x.png
-rw-------  1 user  staff     665 May  6  2019 icon_16x16.png
-rw-------  1 user  staff    1651 May  6  2019 icon_16x16@2x.png
-rw-------  1 user  staff   26457 May  6  2019 icon_256x256.png
-rw-r--r--@ 1 user  staff   91117 Jan 12 19:06 icon_256x256@2x.png
-rw-------  1 user  staff    1651 May  6  2019 icon_32x32.png
-rw-------  1 user  staff    4331 May  6  2019 icon_32x32@2x.png
-rw-r--r--@ 1 user  staff   91117 Jan 12 19:06 icon_512x512.png
-rw-r--r--@ 1 user  staff  422658 Jan 12 19:21 icon_512x512@2x.png

iconutil --convert icns desktop-icon.iconset
```