# Embedded Copilot

An Eclipse plugin that integrates AI-powered coding assistance by leveraging the Cline engine. This project uses a decoupled architecture where the Eclipse extension serves as the front-end UI and Cline acts as the backend engine.

## Project Overview

This repository contains three main sub-projects that work together to create an AI-powered Eclipse plugin:

### 1. **embedded-copilot** - Eclipse Extension (Frontend)
The Eclipse plugin that provides the user interface and integration within the Eclipse IDE. This extension communicates with the Cline backend to deliver AI-powered code assistance directly in your development environment.

**Location:** `./embedded-copilot/`  
**Setup Guide:** [embedded-copilot/README.md](./embedded-copilot/README.md)

### 2. **cline** - AI Engine (Backend)
The core AI engine that powers the assistant. We wrap Cline's standalone server and CLI to extend the Cline engine with minimal code duplication. This decoupled architecture allows us to leverage Cline's powerful capabilities while building a custom Eclipse integration.

**Location:** `./cline/`  
**Setup Guide:** [cline/README.md](./cline/README.md)

### 3. **prompt_testing** - Development Tool
A simple chat interface for testing and experimenting with different prompts and system configurations. This tool was essential during early development and remains useful for testing system prompt modifications before integrating them into the main extension.

**Location:** `./prompt_testing/`  
**Setup Guide:** [prompt_testing/README.md](./prompt_testing/README.md)

## Architecture

```
┌──────────────────────────────────┐
│   Eclipse IDE                    │
│  ┌───────────────────────────┐   │
│  │  Embedded Copilot Plugin  │   │  ← Frontend (UI/UX)
│  │  (./embedded-copilot)     │   │
│  └───────────┬───────────────┘   │
│              │                   │
└──────────────┼───────────────────┘
               │
               │ Communication
               │
┌──────────────▼───────────────────┐
│   Cline Engine                   │
│  ┌───────────────────────────┐   │
│  │  Standalone Server + CLI  │   │  ← Backend (AI Engine)
│  │  (./cline)                │   │
│  └───────────────────────────┘   │
└──────────────────────────────────┘
```

## Getting Started

To set up the complete development environment:

1. **Set up Cline Backend**
   - Follow the setup instructions in [cline/README.md](./cline/README.md)
   - This will configure the AI engine that powers the assistant

2. **Set up Eclipse Extension**
   - Follow the setup instructions in [embedded-copilot/README.md](./embedded-copilot/README.md)
   - This will configure the Eclipse plugin that provides the user interface

3. **Optional: Set up Prompt Testing**
   - Follow the setup instructions in [prompt_testing/README.md](./prompt_testing/README.md)
   - Use this tool if you need to test or modify system prompts

## Development Workflow

1. Start the Cline backend (host and core servers)
2. Open Eclipse and run the plugin in development mode
3. The plugin will communicate with the Cline backend to provide AI assistance
4. Use the prompt testing tool to experiment with prompt modifications before integrating them

## Project Status

This project is actively in development. The architecture allows for:
- **Minimal code duplication** by wrapping existing Cline functionality
- **Flexible development** with decoupled frontend and backend
- **Easy testing** of prompts and configurations before deployment

## Contributing

Each sub-project has its own setup requirements and dependencies. Please refer to the individual README files for detailed development instructions.

## License

Please refer to individual project licenses in their respective directories.
