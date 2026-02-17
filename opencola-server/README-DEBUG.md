# Debugging OpenCola Server in Cursor

## Quick Start

### Option 1: Using the debug script (Recommended)
```bash
./debug-server.sh
```

### Option 2: Manual command
```bash
export JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
./gradlew :opencola-server:run
```

### Option 3: One-liner
```bash
JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005" ./gradlew :opencola-server:run
```

## Steps to Debug

1. **Start the server in debug mode** (using any option above)
   - You'll see: `Listening for transport dt_socket at address: 5005`
   - The server will wait for the debugger to attach (suspend=y)

2. **Set breakpoints** in your Kotlin code
   - Open `server/src/main/kotlin/opencola/server/Application.kt`
   - Click in the gutter (left margin) to add breakpoints

3. **Attach the debugger**
   - Press `F5` or go to Run and Debug (⌘+Shift+D)
   - Select "Debug OpenCola Server (Kotlin)" 
   - Click the green play button
   - The server will start running and stop at your breakpoints

## Troubleshooting

### If attach fails:
- Make sure the Kotlin extension is installed: `fwcd.kotlin`
- Try the "Debug OpenCola Server (Java Fallback)" configuration instead
- Check that port 5005 isn't already in use: `lsof -i :5005`

### Debug without waiting (suspend=n):
If you want the server to start immediately without waiting for the debugger:
```bash
JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" ./gradlew :opencola-server:run
```

Then attach the debugger whenever you want.
