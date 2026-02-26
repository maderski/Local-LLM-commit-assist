# Local LLM Commit Assist

A desktop app that connects to a local LLM (via its OpenAI-compatible API) to automatically generate git commit messages and pull request descriptions from your changes.

<img width="673" height="671" alt="image" src="https://github.com/user-attachments/assets/05f3385f-b6a8-4d8e-a62c-a59911c34921" />


## Features

### Commit Tab
- **Project management** — Add multiple git repositories and switch between them from a dropdown
- **Current branch display** — See which branch you're working on at a glance
- **Automatic staging** — Automatically runs `git add` if no files are staged
- **AI-generated commit messages** — Sends your diff to a local LLM and gets back a summary and description
- **Commit & Push** — Commit directly from the app, with an optional push checkbox
- **Copy to clipboard** — Copy the generated message for use elsewhere

### Create PR Tab
- **AI-generated PR descriptions** — Generates a title, summary, and bullet-point list of changes from your commit history
- **Target branch selection** — Pick the target branch from a dropdown of your local branches
- **PR title & description editing** — Review and edit the generated content before creating the PR
- **Multi-platform support** — Create pull requests on **GitHub** or **Azure DevOps**
- **PR URL link** — Get a clickable link to your newly created PR

### General
- **Tab-based navigation** — Switch between the Commit and Create PR workflows with tabs
- **Configurable LLM settings** — Set the server address and model name, with a test connection button
- **PR platform settings** — Configure your PR platform (GitHub or Azure DevOps) and authentication token
- **Dark theme** — Modern dark UI with clear visual hierarchy

## Requirements

- **Java 17+** — Required to run and build the app
- **A local LLM server** with an OpenAI-compatible API (e.g., [LM Studio](https://lmstudio.ai/), [Ollama](https://ollama.com/), [LocalAI](https://localai.io/))
- **Git** — Installed and available on your PATH
- **A GitHub or Azure DevOps personal access token** (only needed for PR creation)

## Getting Started

1. Start your local LLM server
2. Run the app:
   ```shell
   ./gradlew :composeApp:run
   ```
3. Go to **Settings** and enter your LLM server address (e.g., `http://localhost:1234/v1`)
4. Click **Test Connection** to verify
5. Back on the main screen, click **Add Project** and select a git repository

### Generating Commit Messages

1. Make some changes in your repo
2. Click **Generate Commit Message** — the app will stage files, load the diff, and generate a message
3. Edit the summary/description if needed
4. Click **Commit** (or check **Push** first to commit and push in one step)

### Creating Pull Requests

1. Switch to the **Create PR** tab
2. Select the target branch from the dropdown
3. Click **Generate PR Description** — the app analyzes your commits and generates a title and description
4. Review and edit the title and description as needed
5. Click **Create PR** to create the pull request on your configured platform
6. Click the resulting PR URL to view it in your browser

> **Note:** To create PRs, go to **Settings** and configure your PR platform (GitHub or Azure DevOps) and enter your personal access token.

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
    PrService.kt           — GitHub and Azure DevOps PR creation
  viewmodel/
    MainViewModel.kt       — Main screen state and business logic
  ui/
    Theme.kt               — Dark color scheme and typography
    MainScreen.kt          — Main screen UI (Commit & Create PR tabs)
    SettingsScreen.kt      — Settings screen UI
```

## Tech Stack

- Kotlin Multiplatform + Compose for Desktop
- Ktor Client (CIO) for HTTP
- kotlinx.serialization for JSON
- Material 3 design components
