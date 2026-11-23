#!/bin/bash
# Wird vom GNOME-Custom-Shortcut (Strg+Super) aufgerufen. Verarbeitet die
# Zwischenablage einmal ueber Claude und beendet sich. Wayland-tauglich, da
# GNOME selbst den globalen Hotkey besitzt (kein pynput-Listener noetig).
project_dir="$(cd "$(dirname "$0")" && pwd)"
export DISPLAY="${DISPLAY:-:0}"
"$project_dir/myenv/bin/python3" "$project_dir/ai_clipboard_claude.py" --once
