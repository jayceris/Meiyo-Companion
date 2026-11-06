#!/data/data/com.termux/files/usr/bin/bash
cd "$(dirname "$0")/.." || exit 1
mkdir -p data
if pgrep -f "meiyo-control-server.js" >/dev/null; then
  echo "Meiyo Control bridge already running."
else
  nohup node termux/meiyo-control-server.js >> data/meiyo-control-bridge.log 2>&1 &
  sleep 1
  echo "Meiyo Control bridge started on 127.0.0.1:8765."
fi
