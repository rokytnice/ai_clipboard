# Datei: ai_clipboard/main.py

import google.generativeai as genai
import os
import pyperclip
import time
from pynput import keyboard
import pyautogui

def main():
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise RuntimeError("Umgebungsvariable GEMINI_API_KEY ist nicht gesetzt!")

    genai.configure(api_key=api_key)
    model = genai.GenerativeModel("gemini-1.5-flash")

    print("[INFO] LLM-Hintergrunddienst läuft.")
    print("[INFO] Warte auf Tastenkombination: Strg+Umschalt+Alt ...")


    # Status der Modifier-Tasten
    current_keys = set()

    def on_press(key):
        try:
            if key == keyboard.Key.ctrl_l or key == keyboard.Key.ctrl_r:
                current_keys.add('ctrl')
            if key == keyboard.Key.shift_l or key == keyboard.Key.shift_r:
                current_keys.add('shift')
            # Wenn Strg und Shift gedrückt sind, auslösen
            if current_keys == {'ctrl', 'shift'}:
                print("[INFO] Hotkey erkannt: Strg+Umschalt")
                # Clipboard-Inhalt loggen
                content = pyperclip.paste().strip()
                print(f"[LOG] Clipboard-Inhalt: {content}")
                handle_clipboard(content)
        except Exception as e:
            print(f"[ERROR] Fehler beim Erfassen der Tastendrücke: {e}")

    def on_release(key):
        try:
            if key == keyboard.Key.ctrl_l or key == keyboard.Key.ctrl_r:
                current_keys.discard('ctrl')
            if key == keyboard.Key.shift_l or key == keyboard.Key.shift_r:
                current_keys.discard('shift')
        except Exception as e:
            print(f"[ERROR] Fehler beim Loslassen der Taste: {e}")

    def handle_clipboard(content):
        try:
            if not content:
                print("⚠️ Zwischenablage ist leer.")
                return

            prompt = f"verbessere den folgenden Text.\n{content}"
            print(f"[LOG] Request an Gemini: {prompt}")
            response = model.generate_content(prompt)
            reply = response.text.strip()

            print(f"[LOG] Response von Gemini: {reply}")


            # Antwort in Zwischenablage kopieren und STRG+V simulieren (Umlaute/Sonderzeichen sicher)
            time.sleep(0.3)
            pyperclip.copy(reply)
            pyautogui.hotkey('ctrl', 'v')

        except Exception as e:
            print(f"❌ Fehler: {e}")

    print("[INFO] Eingabewarteschleife aktiv – das Programm ist bereit.")
    with keyboard.Listener(on_press=on_press, on_release=on_release) as listener:
        listener.join()


if __name__ == "__main__":
    main()
