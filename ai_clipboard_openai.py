# Datei: ai_clipboard/ai_clipboard_openai.py

import os
import pyperclip
import time
from pynput import keyboard
import pyautogui
from langchain_openai import ChatOpenAI
import json
import requests

def main():
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("Umgebungsvariable OPENAI_API_KEY ist nicht gesetzt!")

    # Modellname und API-URL aus Umgebungsvariablen
    default_model = "gpt-3.5-turbo"
    model_name = os.getenv("OPENAI_LLM", default_model)
    api_url = os.getenv("OPENAI_URL")

    print(f"[INFO] Verwende das Modell: {model_name}")
    if api_url:
        print(f"[INFO] Verwende API-URL: {api_url}")
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

            prompt = content
            print(f"[LOG] Request an OpenAI-kompatibles Modell: {prompt[:80]}...")

            # --- Request Logging ---
            req_body = {
                "model": model_name,
                "messages": [{"role": "user", "content": prompt}],
                "temperature": 0.7
            }
            req_headers = {
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json"
            }
            if api_url:
                print("[DEBUG] Request URL:", api_url)
            print("[DEBUG] Request Headers:", json.dumps(req_headers, indent=2, ensure_ascii=False))
            print("[DEBUG] Request Body:", json.dumps(req_body, indent=2, ensure_ascii=False))

            # --- Real Request via LangChain (für Antwort und Clipboard) ---
            llm = ChatOpenAI(
                openai_api_key=api_key,
                model=model_name,
                temperature=0.7,
                base_url=api_url if api_url else None,
            )
            response = llm.invoke(prompt)
            reply = response.content.strip() if hasattr(response, 'content') else str(response)

            # --- Parallel: Response Logging (direkt mit requests, für Header/Body) ---
            if api_url:
                r = requests.post(
                    api_url,
                    headers=req_headers,
                    json=req_body,
                    timeout=30
                )
                print("[DEBUG] Response Headers:", json.dumps(dict(r.headers), indent=2, ensure_ascii=False))
                try:
                    print("[DEBUG] Response Body:", json.dumps(r.json(), indent=2, ensure_ascii=False))
                except Exception:
                    print("[DEBUG] Response Body (raw):", r.text)

            print(f"[LOG] Response vom Modell: {reply[:80]}...")
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