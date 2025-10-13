# Cline

AI-powered coding assistant that can be run locally through CLI.

## Prerequisites

- **Go** - [https://go.dev/doc/install](https://go.dev/doc/install)

export PATH="$PATH:/Users/alex/go/bin"

- **Node.js** - [https://nodejs.org/en/download](https://nodejs.org/en/download)
- **Claude API Key** from Anthropic

## Setup Instructions

### 1. Install Dependencies

Navigate to the cline directory and install all dependencies:

```bash
cd cline
npm run install:all
```

### 2. Build Protocol Buffers

Generate the protocol buffer files:

```bash
npm run protos
npm run protos-go
```

### 3. Compile CLI and Standalone

Build the CLI tool and standalone version:

```bash
npm run compile-cli
npm run compile-standalone
```

### 4. Configure API Key

Create the secrets configuration file at `~/.cline/data/secrets.json`:

```json
{
  "apiKey": "your_claude_api_key_here"
}
```

### 5. Add CLI to PATH

#### Mac/Linux

1. Open your shell configuration file:
   ```bash
   # For zsh (default on newer macOS)
   nano ~/.zshrc
   
   # For bash
   nano ~/.bashrc
   ```

2. Add the following line (replace `/full/path/to` with your actual path):
   ```bash
   export PATH="$PATH:/full/path/to/cline/cli/bin"
   ```

3. Save the file and reload your configuration:
   ```bash
   # For zsh
   source ~/.zshrc
   
   # For bash
   source ~/.bashrc
   ```

#### Windows

1. Open **System Properties**:
   - Press `Win + X` and select **System**
   - Click **Advanced system settings**
   - Click **Environment Variables**

2. Under **User variables** or **System variables**, find and select **Path**, then click **Edit**

3. Click **New** and add the full path to the CLI binary:
   ```
   C:\full\path\to\cline\cli\bin
   ```

4. Click **OK** to save and close all dialogs

5. Restart your terminal/command prompt for changes to take effect

### 6. Run Cline Host

Open a terminal and start the Cline host server:

```bash
./cli/bin/cline-host --port 26041 --verbose
```

Keep this terminal running.

### 7. Run Cline Core

Open a **second terminal** and start the Cline core:

```bash
cd dist-standalone
node cline-core.js
```

Keep this terminal running as well.

## Usage

Once both the host and core are running without errors, you can use Cline through the CLI:

### Create a New Task

```bash
cline task new "name of task" \
  -s act-mode-api-provider=anthropic \
  -s act-mode-api-model-id=claude-sonnet-4.5
```

### Send a Prompt

```bash
cline task send "your prompt"
```

### Follow Task Progress

```bash
cline task follow
```

### Auto-Approve Commands

```bash
cline task -a true
```

## Project Structure

- `cli/` - CLI tool implementation
- `dist-standalone/` - Standalone compiled version
- `proto/` - Protocol buffer definitions
- `src/` - Source code
- `webview-ui/` - Web interface components

## Troubleshooting

- Ensure both the host and core are running before executing CLI commands
- Verify your API key is correctly configured in `~/.cline/data/secrets.json`
- Check that the CLI binary path is correctly added to your PATH
- If you encounter permission issues on Mac/Linux, you may need to make the CLI binary executable:
  ```bash
  chmod +x ./cli/bin/cline-host
  ```
