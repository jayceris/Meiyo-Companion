#!/data/data/com.termux/files/usr/bin/bash
set -e
ROOT="$HOME/meiyo"
mkdir -p "$ROOT/data" "$ROOT/data/backups" "$ROOT/termux"
cd "$ROOT"
command -v node >/dev/null 2>&1 || { echo "Install Node.js: pkg install nodejs"; exit 1; }
if ! command -v zip >/dev/null 2>&1 || ! command -v unzip >/dev/null 2>&1; then pkg install -y zip unzip; fi
if pgrep -f "meiyo-control-server.js" >/dev/null 2>&1; then echo "Meiyo dashboard bridge already running."
else nohup node "$ROOT/termux/meiyo-control-server.js" >> "$ROOT/data/meiyo-control-bridge.log" 2>&1 < /dev/null & sleep 1; echo "Dashboard: http://127.0.0.1:8765/"; fi
