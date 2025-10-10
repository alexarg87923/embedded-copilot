# Prompt Testing

A Python-based application for testing AI prompts using the Anthropic API.

## Prerequisites

- Python 3
- Anthropic API Key

## Setup Instructions

### 1. Install Python

If you don't have Python installed, download it from [https://www.python.org/downloads/](https://www.python.org/downloads/)

### 2. Configure API Key

Create a `.env` file in the `./prompt_testing/` directory and add your Anthropic API key:

```
ANTHROPIC_API_KEY=your_api_key_here
```

You can use `.env.EXAMPLE` as a reference.

### 3. Set Up Python Virtual Environment

#### Mac/Linux

```bash
python3 -m venv env
```

#### Windows

```bash
python -m venv env
```

### 4. Activate Virtual Environment

#### Mac/Linux

```bash
source scripts/mac_activate_env.sh
```

#### Windows

```bash
scripts\windows_activate_env.bat
```

### 5. Install Dependencies

```bash
pip install -r requirements.txt
```

### 6. Start the Application

#### Mac/Linux

```bash
./scripts/mac_start_app.sh
```

#### Windows

```bash
scripts\windows_start_app.bat
```

The application will start with auto-reload enabled. Access it at the URL shown in the terminal output.

## Project Structure

- `main.py` - Main application file
- `ai_providers.py` - AI provider integrations
- `requirements.txt` - Python dependencies
- `scripts/` - Helper scripts for different platforms
- `prompts/` - Prompt templates
- `static/` - Static files
