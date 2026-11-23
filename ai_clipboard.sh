#!/bin/bash

# Allow local user to connect to X server (for pynput/pyautogui)
xhost +SI:localuser:$(whoami)

# Set the project directory
project_dir=$(cd "$(dirname "$0")" && pwd) # More robust way to get script's directory
echo "Project Directory: $project_dir"

# Provider auswaehlen: gemini (Standard), openai oder claude
# Ueberschreibbar per Env-Var, z.B.: AI_CLIPBOARD_PROVIDER=claude ./ai_clipboard.sh
provider="${AI_CLIPBOARD_PROVIDER:-claude}"

# Set the virtual environment name
venv_name="myenv" # This is the venv the script manages
venv_path="$project_dir/$venv_name"

# Python command to use for creating venv and running the script (after venv activation)
python_cmd="python3"

# Check if requirements file exists BEFORE trying to use it
requirements_file="$project_dir/ai_clipboard_${provider}_requirements.txt"
if [ ! -f "$requirements_file" ]; then
  echo "Error: Requirements file not found: $requirements_file"
  exit 1
fi

# Create the virtual environment if it doesn't exist
if [ ! -d "$venv_path" ]; then
  echo "Creating virtual environment: $venv_path"
  $python_cmd -m venv "$venv_path"
  if [ $? -ne 0 ]; then
    echo "Error: Failed to create virtual environment."
    exit 1
  fi
fi

# Activate the virtual environment
echo "Activating virtual environment: $venv_path/bin/activate"
source "$venv_path/bin/activate"
if [ $? -ne 0 ]; then
  echo "Error: Failed to activate virtual environment."
  exit 1
fi

# Install dependencies
echo "Installing dependencies from $requirements_file"
# "python3 -m pip" statt "pip" verwenden: robust gegen venvs, deren pip-Launcher-
# Skript einen abweichenden (z.B. verschobenen/kopierten) Erstellungspfad im Shebang hat.
"$venv_path/bin/python3" -m pip install -r "$requirements_file"
if [ $? -ne 0 ]; then
  echo "Error: Failed to install dependencies."
  # Attempt to deactivate even if install fails, before exiting
  if command -v deactivate &> /dev/null
  then
    deactivate
  fi
  exit 1
fi

# Run the Python script using the virtual environment's Python
python_script_name="ai_clipboard_${provider}.py"
echo "Running Python script: $project_dir/$python_script_name"

if [ ! -f "$project_dir/$python_script_name" ]; then
  echo "Error: Python script not found: $project_dir/$python_script_name"
  if command -v deactivate &> /dev/null
  then
    deactivate
  fi
  exit 1
fi

# Absoluten venv-Python verwenden statt "python3" nach "source activate": das
# activate-Skript in diesem venv traegt einen veralteten VIRTUAL_ENV-Pfad
# (aus einer frueheren Kopie unter ~/PycharmProjects/...), wodurch "python3"
# sonst auf das System-Python zurueckfallen wuerde.
"$venv_path/bin/python3" "$project_dir/$python_script_name"

# Deactivate the virtual environment
echo "Deactivating virtual environment."
if command -v deactivate &> /dev/null
then
  deactivate
else
  echo "Warning: 'deactivate' command not found. Virtual environment might still be active in this shell if sourced."
fi