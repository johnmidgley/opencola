<img src="../../img/pull-tab.svg" width="150" />

# Chrome Browser Extension

OpenCola provides a browser extension to make it easier to save and share web pages. The code in this directory is for a Chrome based browser (Google Chrome, Brave, Edge, etc.). The intention is to support any browser, but the work needs to be prioritized. 

The basic isnstructions followed for making the extension can be found [here](https://developer.chrome.com/docs/extensions/mv3/getstarted/).

When you save / share a page with the extension, OpenCola archives a copy of the page for you, which allows you to see the original page, even if it changes on the web, as well as letting you view the page when offline. This was done using the MHT archive ability in Chrome. There are some things that don't seem to get saved (global images or css). A future possiblity to better save the entire page that works cross browsers is [SingleFile](https://github.com/gildas-lormeau/SingleFile) or [SingleFileZ](https://github.com/gildas-lormeau/SingleFileZ).

The extension gets bundled into the application, and is available in the help menu. If you make changes to the extension, you need to run the ```deploy``` script in order to propagate the changes to the resources of the application.

There are a number of improvements to be made to this extension:

* Show existing activity for the page in the popup (e.g. likes, saves, comments, etc.)
* Show a rich edit UI, similar to when creating a post on the feed
* Use SingleFile for page archiving
* Add functionality so that the extension can automatically save activity from chosen sites to OpenCola (e.g. Facebook likes, Amazon ratings, etc.)
* Support more browsers