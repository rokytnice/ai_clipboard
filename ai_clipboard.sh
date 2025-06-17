#!/bin/bash

# Allow local user to connect to X server (for pynput/pyautogui)
xhost +SI:localuser:$(whoami)

# Set the project directory
# Using $(dirname "$0") is often more robust if the script can be called from other directories
# For simplicity, if you always run it from the project root, pwd is fine.
# project_dir=$(pwd)
project_dir=$(cd "$(dirname "$0")" && pwd) # More robust way to get script's directory
echo "Project Directory: $project_dir"

# Set the virtual environment name
venv_name="myenv" # This is the venv the script manages
venv_path="$project_dir/$venv_name"

# Python command to use for creating venv and running the script (after venv activation)
# This will resolve to the system python3 initially, then the venv's python3 after activation.
python_cmd="python3"

# Check if requirements file exists BEFORE trying to use it
requirements_file="$project_dir/ai_clipboard_requirements.txt"
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
pip install -r "$requirements_file"
if [ $? -ne 0 ]; then
  echo "Error: Failed to install dependencies."
  deactivate
  exit 1
fi

# Run the Python script using the virtual environment's Python
echo "Running Python script: $project_dir/main.py"
# Now, python_cmd (or just 'python3' or 'python') will refer to the venv's interpreter
$python_cmd "$project_dir/main.py"
# Or simply:
# python3 "$project_dir/main.py"

# Deactivate the virtual environment
echo "Deactivating virtual environment."
deactivate#!/bin/bash



xhost +SI:localuser:$(whoami)


# Set the project directory (adjust as needed)
project_dir=$(pwd)
echo "Project Directory: $project_dir"
# Set the Python interpreter (adjust if needed)
python_interpreter="/usr/bin/python3"

# Set the virtual environment name
venv_name="myenv"

# Create the virtual environment
if [ ! -d "$venv_name" ]; then
  python3 -m venv "$venv_name"
fi

# Activate the virtual environment
source "$venv_name/bin/activate"

# Install dependencies (replace with your ai_clipboard_requirements.txt)
pip install -r "$project_dir/ai_clipboard_requirements.txt"

# Check if ai_clipboard_requirements.txt exists
if [ ! -f "$project_dir/ai_clipboard_requirements.txt" ]; then
  echo "Error: requirements.txt not found in $project_dir."
  deactivate
  exit 1
fi

# Run the Python script
$python_interpreter "$project_dir/main.py"

# Deactivate the virtual environment
deactivate
