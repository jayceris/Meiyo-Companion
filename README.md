# Meiyo Control Android app

Native Android companion app for controlling the existing Meiyo bot installation in Termux.

## Build the APK
1. Open the **Actions** tab.
2. Run **Build Meiyo Control APK**.
3. Download the `Meiyo-Control-APK` artifact from the completed workflow.
4. Extract and install `app-debug.apk`.

## Termux bridge
Copy `termux/meiyo-control-server.js` and `termux/meiyo-control-start.sh` into the root of your existing Meiyo bot folder, preserving `.env` and `data`. Then start the bridge from that folder:

```sh
bash scripts/meiyo-control-start.sh
```

The app connects to `127.0.0.1:8765`. The bridge binds to localhost only and controls the existing bot directory; it does not create a second bot installation.
