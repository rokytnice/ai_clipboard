# AI Clipboard

A background service that monitors clipboard content and sends it to an LLM (Language Learning Model) for processing, then automatically pastes the result at the cursor position.

## How it Works

**AI Clipboard** is a clipboard monitor that runs in the background with the following workflow:

1. **Hotkey Detection**: The application listens for the keyboard shortcut `Ctrl+Windows` (or `Strg+Windows` in German)

2. **Clipboard Reading**: When the hotkey is triggered, the current clipboard content is captured

3. **LLM Processing**: The clipboard content is sent to an LLM (either Google Gemini or OpenAI) for processing

4. **Response Handling**: The LLM generates a response which is:
   - Logged for debugging purposes
   - Copied to the clipboard
   - Automatically pasted at the current cursor position using `Ctrl+V`

5. **Logging**: Every step is logged for monitoring and debugging:
   - Hotkey detection
   - Clipboard content
   - Request sent to the LLM
   - Response received
   - Paste action

## Architecture

The project supports two LLM backends:

- **`ai_clipboard_gemini.py`**: Uses Google Generative AI (Gemini)
- **`ai_clipboard_openai.py`**: Uses OpenAI API or OpenAI-compatible endpoints

### Launcher Script

- **`ai_clipboard.sh`**: Bash script that:
  - Sets up a Python virtual environment
  - Installs dependencies
  - Runs the chosen Python script (default: Gemini variant)
  - Handles X server permissions for keyboard/mouse control

## Technology Stack

- **Language**: Python (77.2%), Shell (22.8%)
- **Keyboard Monitoring**: `pynput` library for system-wide hotkey detection
- **Clipboard Management**: `pyperclip` for clipboard operations
- **Automation**: `pyautogui` for simulating keystrokes
- **LLM Integration**: 
  - `google-generativeai` for Gemini
  - `langchain-openai` for OpenAI

## Setup & Installation

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Or use the included shell script:

```bash
chmod +x ai_clipboard.sh
./ai_clipboard.sh
```

## Configuration

Set the required environment variables:

**For Gemini:**
```bash
export GEMINI_API_KEY="your-api-key"
export GEMINI_LLM="gemini-1.5-flash-latest"  # Optional, defaults to gemini-1.5-flash-latest
```

**For OpenAI:**
```bash
export OPENAI_API_KEY="your-api-key"
export OPENAI_LLM="gpt-3.5-turbo"  # Optional
export OPENAI_URL="https://api.openai.com/v1/chat/completions"  # Optional
```

## Usage

1. Start the application
2. Copy text to your clipboard
3. Press `Ctrl+Windows` to send it to the LLM
4. The processed response will be automatically pasted at your cursor position
5. Check the logs for debugging information

## Features

- ✅ System-wide hotkey detection
- ✅ Detailed logging for each step
- ✅ Support for multiple LLM backends
- ✅ Automatic clipboard-to-text injection
- ✅ Environment variable configuration
- ✅ Virtual environment management script

## License

[Add license information here]

## Contributing

[Add contribution guidelines here]
