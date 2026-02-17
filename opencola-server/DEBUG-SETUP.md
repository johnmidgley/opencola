# Debugging OpenCola Server in Cursor - Standard Launch

## Prerequisites

1. **Install Required Extensions**
   - Open Extensions (`Cmd+Shift+X`)
   - Install **"Kotlin" by fwcd** (fwcd.kotlin)
   - Install **"Debugger for Java" by Microsoft** (vscjava.vscode-java-debug)
   - Restart Cursor after installation

2. **Build the Project**
   ```bash
   ./gradlew :opencola-server:build
   ```

## Debug Steps (Standard Direct Launch)

1. **Open the Kotlin file you want to debug**
   - Example: `server/src/main/kotlin/opencola/server/Application.kt`

2. **Set breakpoints**
   - Click in the gutter (left of line numbers) on lines you want to pause at
   - Example: Click on line 28 or 32

3. **Start Debugging**
   - Press `F5` OR
   - Go to Run and Debug view (`Cmd+Shift+D`)
   - Select **"Debug OpenCola Server"** from dropdown
   - Click the green play button

4. **The application will start with the debugger attached**
   - It will pause at your breakpoints
   - You can inspect variables, step through code, etc.

## Troubleshooting

### If debugging doesn't start:

**Option 1: Reload the Kotlin extension**
1. Press `Cmd+Shift+P`
2. Type "Developer: Reload Window"
3. Press Enter
4. Try debugging again

**Option 2: Check Kotlin Language Server**
1. Press `Cmd+Shift+P`
2. Type "Kotlin: Restart Language Server"
3. Try debugging again

**Option 3: Verify Gradle project sync**
1. Press `Cmd+Shift+P`
2. Type "Java: Clean Java Language Server Workspace"
3. Select "Restart and delete"
4. Wait for Gradle sync to complete
5. Try debugging again

### If Kotlin debugger still doesn't work:

Use the **fallback remote debugging method**:

1. In terminal, run:
   ```bash
   ./debug-server.sh
   ```

2. Wait for: `Listening for transport dt_socket at address: 5005`

3. In Cursor, add this to `.vscode/launch.json`:
   ```json
   {
     "type": "java",
     "request": "attach",
     "name": "Attach to Debug Server",
     "hostName": "localhost",
     "port": 5005
   }
   ```

4. Press `F5` and select "Attach to Debug Server"

## Configuration Files

- `.vscode/launch.json` - Debug configurations
- `.vscode/settings.json` - Kotlin/Java settings
- `.vscode/extensions.json` - Recommended extensions
- `debug-server.sh` - Script for remote debugging fallback

## Current Configuration

- **Main Class**: `opencola.server.ApplicationKt`
- **Project**: opencola-server (Gradle multi-project)
- **Kotlin Version**: 1.9.0
- **Build Tool**: Gradle with Kotlin DSL
