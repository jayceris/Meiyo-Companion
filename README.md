# Meiyo Companion

Android dashboard for the existing Meiyo bot in Termux. Keep the installation at **`~/meiyo`**.

```
~/meiyo/
  src/  data/  package.json  .env
  termux/
    meiyo-control-server.js
    meiyo-control-start.sh
    dashboard.html
```

Copy all three files from this repository's `termux/` directory into `~/meiyo/termux/`. Do not replace your bot's `.env` or `src/`.

Start in Termux:
```sh
cd ~/meiyo
bash termux/meiyo-control-start.sh
```
The starter checks Node.js and installs `zip` and `unzip` if missing. Open the Companion app after the bridge starts.

Features: bot controls, command registration, live-output bash console, fullscreen console, timestamped ZIP backups, and ZIP import. The console executes commands in `~/meiyo`; full TTY editors such as nano are not supported. Backups include source, config, .env and data but exclude node_modules, Git metadata and old backups. Imports require a top-level `meiyo/` folder, validate paths, create a safety backup, merge into the existing installation, and preserve the current root `.env` and `data/backups/`.

The bridge binds to localhost only. Shell access is powerful; keep the device secured.

Build: GitHub Actions → **Build Meiyo Control APK** → download `Meiyo-Control-APK`.
