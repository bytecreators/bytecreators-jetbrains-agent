# ByteCreators AI Coding Agent for JetBrains IDEs

An autonomous AI coding agent plugin for JetBrains IDEs (IntelliJ IDEA, PyCharm, WebStorm, etc.) by **[ByteCreators](https://bytecreators.com)** — using **your own API keys**.

## Features

- 🤖 **Autonomous Agent** - Performs multi-step tasks using tools
- 📁 **File Operations** - Read, write, and search files
- 🔍 **Code Search** - Grep-like search across your codebase
- 💻 **Terminal Access** - Run shell commands (build, test, git)
- 🌊 **Streaming Responses** - See responses as they're generated
- 🔐 **Secure API Keys** - Keys stored in IntelliJ's secure credential store

## Supported LLM Providers

- **OpenAI** - GPT-4o, GPT-4-turbo
- **Anthropic** - Claude 3.5 Sonnet, Claude 4
- **OpenAI-Compatible** - Ollama, LM Studio, or any compatible endpoint

## Installation

### From Source

1. Clone this repository
2. Open in IntelliJ IDEA
3. Run `./gradlew buildPlugin`
4. Install from `build/distributions/bytecreators-ai-agent-*.zip`

### Development

```bash
# Run plugin in sandbox IDE
./gradlew runIde

# Build distributable plugin
./gradlew buildPlugin
```

## Configuration

1. Open your JetBrains IDE
2. Go to **Settings > Tools > ByteCreators AI Coding Agent**
3. Select your LLM provider
4. Enter your API key
5. Choose a model (optional)

## Usage

1. Open the **ByteCreators AI Agent** tool window (right sidebar or `Ctrl+Alt+A`)
2. Type your request in the chat
3. Press **Ctrl+Enter** to send
4. Watch the agent work through your request

### Example Prompts

- "What files are in this project?"
- "Find all TODO comments in the codebase"
- "Create a new file called `utils.kt` with a function to format dates"
- "Run the tests and tell me if they pass"
- "Explain what the `main` function does"

## Project Structure

```
src/main/kotlin/com/bytecreators/aiagent/
├── actions/          # IDE actions (shortcuts, menus)
├── agent/            # Core agent logic
├── llm/              # LLM provider implementations
├── settings/         # Configuration & secure storage
├── tools/            # Agent tools (file, search, terminal)
└── ui/               # Chat panel UI
```

## Requirements

- JetBrains IDE 2025.1+
- JDK 21+
- API key for your chosen LLM provider

## Links

- **Website**: [bytecreators.com](https://bytecreators.com)
- **Support**: support@bytecreators.com

## License

MIT License - see [LICENSE](LICENSE) file for details.

Copyright (c) 2026 ByteCreators
