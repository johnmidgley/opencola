<img src="../../../img/pull-tab.svg" width="150" />

# OCR CLI

The OpenCola Relay (OCR) command line interface (CLI) facilitates administration of the relay server, which allows you to automate tasks and interact with the server in a flexible way. 

You can build the command line tool via Gradle tasks in your IDE or, starting in the source root, you can build and run it directly with:

```shell
> cd opencola-server
> ./gradlew relay:cli:installDist
> cd relay/cli/build/install/ocr/bin
> ocr
```

If you want to copy the `ocr` somewhere else, make sure to include the `../lib` directory too.

The first time you run the tool, it will create a storage directory and a default configuration file. The output will look something like (depending on your OS):

```shell
> ocr
Creating storage path: /Users/username/Library/Application Support/OpenCola/storage/ocr
Creating default config file: /Users/username/Library/Application Support/OpenCola/storage/ocr/ocr-cli.yaml
You can set the OCR_PASSWORD environment variable to avoid having to enter your password.
Password: ⏎
```

Type a password that is used to secure use of the tool.

The config file contains a few values:

```yaml
ocr:
  credentials:
    # It is recommended to use environment variables to specify your password - using this is less secure
    # password: password

  server:
    uri: "ocr://localhost:2652"
    connectTimeoutMilliseconds: 3000
    requestTimeoutMilliseconds: 5000
```

You can set a `password` directly in the config, if you aren't concerned about security. For higher security, you can set an environment variable (`ocr.credentials.password`) so that you don't need to enter a password on each exectuion. You will need to set the `uri` for the server to wherever you want to deploy the server. 

The tool provides built in help (just run `ocr` without arguments), but the commands available are:

|Command|Description|
|-------|-----------|
|config|Displays storage path and config yaml|
|identity|Create / view client and server identities|
|policy|Manage policies that control use of the relay server|
|user-policy|Manage assignment of policies to users|
|connections|List connections to the relay server|
|messages| List, remove or summarize messages stored by the server|
|storage| Examine storage of server|
|exec|Execute a command on the server|
|shell|Start an interactive shell on the server|
|file|Get files off the server|