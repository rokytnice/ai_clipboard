# Datei: ai_clipboard/ai_clipboard_claude.py
#
# Zwei Betriebsarten:
#   - Listener-Modus (Standard): globaler pynput-Hotkey Strg+Windows.
#     Funktioniert nur unter X11 (XRecord-Extension), NICHT unter nativem
#     Wayland/GNOME (dort werden keine Events empfangen, ohne Fehlermeldung).
#   - Einzelaufruf-Modus ("--once"): verarbeitet die Zwischenablage einmal und
#     beendet sich. Gedacht zum Binden an einen GNOME-Custom-Shortcut, der auf
#     Wayland zuverlaessig funktioniert (siehe ai_clipboard_claude_run_once.sh,
#     registriert als GNOME-Shortcut Strg+Super+C).
#
# Da der GNOME-Shortcut ohne sichtbares Terminal laeuft, wird jede Ausgabe
# zusaetzlich in eine Logdatei geschrieben (_tmp/logs/ai_clipboard.log).

import logging
import os
import subprocess
import sys
import time
import pyperclip
from pynput import keyboard

logging.basicConfig()
logging.getLogger().setLevel(logging.DEBUG)

PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
LOG_DIR = os.path.join(PROJECT_DIR, "_tmp", "logs")
LOG_FILE = os.path.join(LOG_DIR, "ai_clipboard.log")

# Kontrolltoene: werden abgespielt, wenn Verarbeitung beginnt / fertig ist /
# fehlschlaegt, damit ohne Blick auf ein Terminal klar ist, was passiert.
SOUND_START = os.getenv(
    "AI_CLIPBOARD_SOUND_START",
    "/usr/share/sounds/freedesktop/stereo/dialog-information.oga",
)
SOUND_DONE = os.getenv(
    "AI_CLIPBOARD_SOUND_DONE",
    "/usr/share/sounds/freedesktop/stereo/complete.oga",
)
SOUND_ERROR = os.getenv(
    "AI_CLIPBOARD_SOUND_ERROR",
    "/usr/share/sounds/freedesktop/stereo/dialog-error.oga",
)

# Modell aus der Umgebungsvariable CLAUDE_LLM lesen (Alias wie "haiku",
# "sonnet", "opus", "fable" oder vollstaendiger Modellname). Standard: haiku
# (schnell, passend fuer kurze Text-Umschreibungen).
MODEL_NAME = os.getenv("CLAUDE_LLM", "haiku")
CLAUDE_BIN = os.getenv("CLAUDE_BIN", "claude")

# ydotool statt pyautogui fuer das simulierte Strg+V: pyautogui/pyxlib-basierte
# Eingaben erreichen unter nativem Wayland (GNOME/Mutter) nur XWayland-Fenster,
# nicht native Wayland-Fenster. ydotool schreibt ueber /dev/uinput auf
# Kernel-Ebene und funktioniert deshalb unabhaengig von X11/Wayland.
YDOTOOL_SOCKET = os.getenv("YDOTOOL_SOCKET", "/tmp/.ydotool_socket")
# Keycodes nach linux/input-event-codes.h: KEY_LEFTCTRL=29, KEY_V=47
CTRL_V_KEYCODES = ["29:1", "47:1", "47:0", "29:0"]

HELP_TEXT = """\
Verwendung: ai_clipboard_claude.py [--once] [--help]

Liest den Inhalt der Zwischenablage, schickt ihn an die Claude CLI und
kopiert die Antwort zurueck in die Zwischenablage (inkl. simuliertem Strg+V).

Optionen:
  (kein Argument)  Listener-Modus: wartet dauerhaft auf den globalen
                    Hotkey Strg+Windows. Funktioniert nur unter X11.
  --once           Einzelaufruf: verarbeitet die Zwischenablage einmal und
                    beendet sich. Fuer den GNOME-Custom-Shortcut (Wayland).
  -h, --help       Diese Hilfe anzeigen.

Umgebungsvariablen:
  CLAUDE_LLM              Modell-Alias/-Name (Standard: haiku)
  CLAUDE_BIN              Pfad/Name der Claude CLI (Standard: claude)
  AI_CLIPBOARD_SOUND_START/_DONE/_ERROR
                          Pfade zu Kontrolltoenen (.oga/.wav), leer = stumm.

Log-Datei: {log_file}
""".format(log_file=LOG_FILE)


def log(message):
    print(message)
    try:
        os.makedirs(LOG_DIR, exist_ok=True)
        with open(LOG_FILE, "a") as f:
            f.write(f"{time.strftime('%Y-%m-%d %H:%M:%S')} {message}\n")
    except Exception as e:
        print(f"[WARN] Konnte nicht ins Log schreiben: {e}")


def paste_ctrl_v():
    env = os.environ.copy()
    env["YDOTOOL_SOCKET"] = YDOTOOL_SOCKET
    try:
        result = subprocess.run(
            ["ydotool", "key", *CTRL_V_KEYCODES],
            env=env,
            capture_output=True,
            text=True,
            timeout=5,
        )
        if result.returncode != 0:
            log(f"[WARN] ydotool konnte Strg+V nicht senden: {result.stderr.strip()}")
    except FileNotFoundError:
        log("[WARN] ydotool nicht gefunden — Ergebnis liegt in der Zwischenablage, "
            "manuell mit Strg+V einfuegen.")
    except Exception as e:
        log(f"[WARN] Fehler beim Senden von Strg+V: {e}")


def play_sound(path):
    if not path or not os.path.isfile(path):
        return
    try:
        subprocess.Popen(
            ["paplay", path],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    except Exception as e:
        log(f"[WARN] Konnte Ton nicht abspielen: {e}")


def handle_clipboard(content):
    try:
        if not content:
            log("⚠️ Zwischenablage ist leer.")
            return

        # Der Prompt wird direkt (ohne Wrapping) an Claude geschickt.
        # Claude ist gut darin, den Kontext "verbessere diesen Text" zu verstehen.
        prompt = content
        log(f"[LOG] Request an Claude: {prompt[:80]}...")  # Kuerzt lange Prompts im Log

        result = subprocess.run(
            [
                CLAUDE_BIN,
                "-p", prompt,
                "--model", MODEL_NAME,
                "--output-format", "text",
                "--restricted",
                "--strict-mcp-config",
                "--append-system-prompt",
                "Antworte ausschliesslich mit dem fertigen Ergebnistext, "
                "ohne Einleitung, Erklaerung, Kommentar oder Markdown-Formatierung. "
                "Deine Ausgabe wird direkt an der Cursor-Position eingefuegt.",
            ],
            capture_output=True,
            text=True,
            timeout=60,
        )

        if result.returncode != 0:
            log(f"❌ Claude CLI Fehler (Exit {result.returncode}): {result.stderr.strip()}")
            play_sound(SOUND_ERROR)
            return

        reply = result.stdout.strip()
        log(f"[LOG] Response von Claude: {reply[:80]}...")
        play_sound(SOUND_DONE)

        # Antwort in Zwischenablage kopieren und STRG+V simulieren (Umlaute/Sonderzeichen sicher)
        time.sleep(0.3)
        pyperclip.copy(reply)
        paste_ctrl_v()

    except subprocess.TimeoutExpired:
        log("❌ Fehler: Claude CLI hat nicht rechtzeitig geantwortet (Timeout).")
        play_sound(SOUND_ERROR)
    except Exception as e:
        log(f"❌ Fehler: {e}")
        play_sound(SOUND_ERROR)


def check_claude_available():
    if subprocess.run(["which", CLAUDE_BIN], capture_output=True).returncode != 0:
        raise RuntimeError(f"Claude CLI ('{CLAUDE_BIN}') wurde nicht im PATH gefunden!")


DEBOUNCE_LOCK_FILE = os.path.join(LOG_DIR, ".last_run")
DEBOUNCE_SECONDS = 2.0


def debounced():
    """True, wenn der letzte Lauf zu kurz her ist (z.B. Tastenwiederholung
    beim Halten von Strg+Super+C loest den Shortcut mehrfach aus)."""
    now = time.time()
    try:
        os.makedirs(LOG_DIR, exist_ok=True)
        last = os.path.getmtime(DEBOUNCE_LOCK_FILE) if os.path.exists(DEBOUNCE_LOCK_FILE) else 0
    except Exception:
        last = 0
    is_too_soon = (now - last) < DEBOUNCE_SECONDS
    try:
        with open(DEBOUNCE_LOCK_FILE, "w") as f:
            f.write(str(now))
    except Exception:
        pass
    return is_too_soon


def run_once():
    """Einzelaufruf fuer GNOME-Custom-Shortcut (Wayland-tauglich)."""
    check_claude_available()
    if debounced():
        log("[INFO] Hotkey-Wiederholung ignoriert (Debounce).")
        return
    log("[INFO] Hotkey erkannt: Strg+Super+C")
    play_sound(SOUND_START)
    content = pyperclip.paste().strip()
    log(f"[LOG] Clipboard-Inhalt: {content}")
    handle_clipboard(content)


def run_listener():
    """Globaler pynput-Hotkey-Listener (nur X11)."""
    check_claude_available()

    log(f"[INFO] Verwende das Modell: {MODEL_NAME}")
    log("[INFO] LLM-Hintergrunddienst laeuft.")
    log("[INFO] Warte auf Tastenkombination: Strg+Windows ...")

    current_keys = set()

    def on_press(key):
        try:
            if key == keyboard.Key.ctrl_l or key == keyboard.Key.ctrl_r:
                current_keys.add('ctrl')
            if key == keyboard.Key.cmd_l or key == keyboard.Key.cmd_r:
                current_keys.add('win')
            if current_keys == {'ctrl', 'win'}:
                log("[INFO] Hotkey erkannt: Strg+Windows")
                play_sound(SOUND_START)
                content = pyperclip.paste().strip()
                log(f"[LOG] Clipboard-Inhalt: {content}")
                handle_clipboard(content)
        except Exception as e:
            log(f"[ERROR] Fehler beim Erfassen der Tastendruecke: {e}")

    def on_release(key):
        try:
            if key == keyboard.Key.ctrl_l or key == keyboard.Key.ctrl_r:
                current_keys.discard('ctrl')
            if key == keyboard.Key.cmd_l or key == keyboard.Key.cmd_r:
                current_keys.discard('win')
        except Exception as e:
            log(f"[ERROR] Fehler beim Loslassen der Taste: {e}")

    log("[INFO] Eingabewarteschleife aktiv – das Programm ist bereit (Hotkey: Strg+Windows).")
    with keyboard.Listener(on_press=on_press, on_release=on_release) as listener:
        listener.join()


def main():
    args = sys.argv[1:]
    if "-h" in args or "--help" in args:
        print(HELP_TEXT)
        return
    if "--once" in args:
        run_once()
    else:
        run_listener()


if __name__ == "__main__":
    main()
