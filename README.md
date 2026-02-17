# Local LLM Commit Assist

A desktop app that connects to a local LLM (via its OpenAI-compatible API) to automatically generate git commit messages from your staged changes.

<img width="897" height="894" alt="image" src="https://github.com/user-attachments/assets/05f3385f-b6a8-4d8e-a62c-a59911c34921" />


## Features

- **Project management** — Add multiple git repositories and switch between them from a dropdown
- **Automatic staging** — Automatically runs `git add` if no files are staged
- **AI-generated commit messages** — Sends your diff to a local LLM and gets back a summary and description
- **Commit & Push** — Commit directly from the app, with an optional push checkbox
- **Copy to clipboard** — Copy the generated message for use elsewhere
- **Configurable LLM settings** — Set the server address and model name, with a test connection button
- **Dark theme** — Modern dark UI with clear visual hierarchy

## Requirements

- **Java 17+** — Required to run and build the app
- **A local LLM server** with an OpenAI-compatible API (e.g., [LM Studio](https://lmstudio.ai/), [Ollama](https://ollama.com/), [LocalAI](https://localai.io/))
- **Git** — Installed and available on your PATH

## Getting Started

1. Start your local LLM server
2. Run the app:
   ```shell
   ./gradlew :composeApp:run
   ```
3. Go to **Settings** and enter your LLM server address (e.g., `http://localhost:1234/v1`)
4. Click **Test Connection** to verify
5. Back on the main screen, click **Add Project** and select a git repository
6. Make some changes in your repo
7. Click **Generate Commit Message** — the app will stage files, load the diff, and generate a message
8. Edit the summary/description if needed
9. Click **Commit** (or check **Push** first to commit and push in one step)

## Build macOS App

To build a distributable `.dmg`:

```shell
./gradlew :composeApp:packageDmg
```

The output will be in `composeApp/build/compose/binaries/main/dmg/`.

## Project Structure

```
composeApp/src/jvmMain/kotlin/com/maderskitech/localllmcommitassist/
  main.kt                  — App entry point and window configuration
  App.kt                   — Navigation between screens
  AppIcon.kt               — Programmatic window icon
  data/
    SettingsRepository.kt  — Persisted settings (Java Preferences API)
    GitService.kt          — Git operations via ProcessBuilder
    LlmService.kt          — OpenAI-compatible API client (Ktor)
  viewmodel/
    MainViewModel.kt       — Main screen state and business logic
  ui/
    Theme.kt               — Dark color scheme and typography
    MainScreen.kt          — Main screen UI
    SettingsScreen.kt      — Settings screen UI
```

## Tech Stack

- Kotlin Multiplatform + Compose for Desktop
- Ktor Client (CIO) for HTTP
- kotlinx.serialization for JSON
- Material 3 design components
