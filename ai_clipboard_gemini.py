# Datei: ai_clipboard/ai_clipboard_gemini.py

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

    # NEUE LOGIK: Lese den Modellnamen aus der Umgebungsvariable GOOGLE_LLM.
    # Falls sie nicht gesetzt ist, wird ein Standardmodell verwendet.
    default_model = "gemini-1.5-flash-latest"
    model_name = os.getenv("GOOGLE_LLM", default_model)
    model = genai.GenerativeModel(model_name)

    print(f"[INFO] Verwende das Modell: {model_name}")
    print("[INFO] LLM-Hintergrunddienst läuft.")
    print("[INFO] Warte auf Tastenkombination: Strg+Windows ...")


    # Status der Modifier-Tasten
    current_keys = set()

    def on_press(key):
        try:
            if key == keyboard.Key.ctrl_l or key == keyboard.Key.ctrl_r:
                current_keys.add('ctrl')
            if key == keyboard.Key.cmd_l or key == keyboard.Key.cmd_r:
                current_keys.add('win')
            # Wenn Strg und Windows gedrückt sind, auslösen
            if current_keys == {'ctrl', 'win'}:
                print("[INFO] Hotkey erkannt: Strg+Windows")
                content = pyperclip.paste().strip()
                print(f"[LOG] Clipboard-Inhalt: {content}")
                handle_clipboard(content)
        except Exception as e:
            print(f"[ERROR] Fehler beim Erfassen der Tastendrücke: {e}")

    def on_release(key):
        try:
            if key == keyboard.Key.ctrl_l or key == keyboard.Key.ctrl_r:
                current_keys.discard('ctrl')
            if key == keyboard.Key.cmd_l or key == keyboard.Key.cmd_r:
                current_keys.discard('win')
        except Exception as e:
            print(f"[ERROR] Fehler beim Loslassen der Taste: {e}")

    def handle_clipboard(content):
        try:
            if not content:
                print("⚠️ Zwischenablage ist leer.")
                return

            # Der Prompt wurde vereinfacht, um den Text direkt zu verarbeiten.
            # Gemini ist gut darin, den Kontext "verbessere diesen Text" zu verstehen.
            prompt = content
            print(f"[LOG] Request an Gemini: {prompt[:80]}...") # Kürzt lange Prompts im Log
            response = model.generate_content(prompt)
            reply = response.text.strip()

            print(f"[LOG] Response von Gemini: {reply[:80]}...")


            # Antwort in Zwischenablage kopieren und STRG+V simulieren (Umlaute/Sonderzeichen sicher)
            time.sleep(0.3)
            pyperclip.copy(reply)
            pyautogui.hotkey('ctrl', 'v')

        except Exception as e:
            print(f"❌ Fehler: {e}")

    print("[INFO] Eingabewarteschleife aktiv – das Programm ist bereit (Hotkey: Strg+Windows).")
    with keyboard.Listener(on_press=on_press, on_release=on_release) as listener:
        listener.join()

if __name__ == "__main__":
    main()