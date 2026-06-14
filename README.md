# Local LLM Commit Assist

A macOS desktop app that uses an OpenAI-compatible local LLM to turn Git changes into commit messages and pull request descriptions. Review the generated text, manage branches, commit and push changes, and create GitHub or Azure DevOps pull requests without leaving the app.

<img width="400" height="443" alt="image" src="https://github.com/user-attachments/assets/a79a6eee-b970-4d11-b36e-13666ec98015" />


## Features

### AI-assisted commits

- Select individual changed files or stage everything with **Select All**.
- Generate a commit summary and description from only the selected changes.
- Preview the staged diff before committing.
- Edit or copy the generated message.
- Commit locally or commit and push in one step.
- Automatically prompt to publish a branch before pushing when it has no upstream.

### Project and branch management

- Save and switch between multiple local Git repositories.
- Open the selected repository in Finder or Terminal.
- View and switch local branches from the project header.
- Create, publish, fetch, and delete branches.
- Optionally delete a published branch from `origin` as well.
- Choose whether to bring or leave uncommitted changes when switching branches.
- Refresh repository state automatically when the app regains focus.

### Pull requests

- Generate an editable PR title and description from the branch commit history and diff.
- Select the target branch from local branches.
- Create pull requests on GitHub or Azure DevOps.
- Configure default reviewers for both providers; Azure reviewers can be required or optional.
- Copy the complete PR title and description to the clipboard.
- Open the created PR from its numbered result link.

### PR attachments

- Drag and drop images or videos, choose multiple files, or paste an image from the clipboard.
- Preview attachment names and sizes, and remove files before submission.
- Supports `png`, `jpg`, `jpeg`, `gif`, `webp`, `mp4`, and `mov`.
- Validates provider limits before upload: GitHub `10 MB`, Azure DevOps `25 MB`.
- Uploads attachments and inserts their Markdown references into the PR description.

### Azure DevOps automation

- Detect work item IDs in branch names and link them to the PR.
- Optionally move linked work items to **Code Review** when the PR is created.
- Automatically tag PRs based on the project name.
- Upload attachments directly to the Azure DevOps pull request.

### Local LLM reliability

- Works with OpenAI-compatible local servers such as [LM Studio](https://lmstudio.ai/), [Ollama](https://ollama.com/), and [LocalAI](https://localai.io/).
- Tests the configured server and model from Settings.
- Discovers model context-window metadata when the provider exposes it.
- Supports a per-model context-window override for providers that do not report one.
- Compacts large diffs and commit histories to fit the model context window.
- Retries context-limit failures with progressively smaller prompt budgets.
- Surfaces detailed API errors that can be copied to the clipboard.

### Settings and security

- Persists the LLM server, model, provider, reviewer, and workflow settings locally.
- Encrypts GitHub and Azure DevOps tokens before storing them in Java Preferences.
- Uses a focused dark Material 3 interface.

## Requirements

- macOS
- JDK 17 or newer
- Git available on `PATH`
- An OpenAI-compatible local LLM server
- A GitHub or Azure DevOps personal access token for PR creation
- An `origin` remote for publish, push, fetch, remote branch deletion, and PR workflows

## Getting Started

1. Start your local LLM server and load a model.
2. Run the app from the repository:

   ```shell
   ./gradlew :composeApp:run
   ```

3. Open **Settings** and configure:
   - **Server Address**, for example `http://localhost:1234/v1`
   - **Model Name**, if your server requires one
   - **Context Window (tokens)** only when automatic discovery is unavailable or incorrect
4. Select **Test Connection**, then save the LLM settings.
5. Configure GitHub or Azure DevOps under **Pull Request** if you plan to create PRs.
6. Return to the main screen, select **Add Project**, and choose a Git repository.

## Workflows

### Generate a commit message

1. Select a project and refresh its changed files.
2. Choose the files to include.
3. Select **Generate Commit Message**.
4. Review the diff, summary, and description.
5. Select **Commit**, optionally enabling **Push** first.

Only the selected files are staged for the generated message and commit.

### Create a pull request

1. Publish the current branch and push its commits.
2. Open the **Create PR** tab.
3. Choose the target branch under **Merge Into**.
4. Select **Generate PR Description**.
5. Review the title and description, then add any attachments.
6. Select **Create PR** and open the resulting PR link.

The repository remote must match the provider selected in Settings.

## Provider Setup

### GitHub

Configure a GitHub personal access token and, optionally, reviewer usernames. The token must be able to create pull requests and upload attachment files to the repository.

### Azure DevOps

Configure your Azure DevOps username and PAT. Optional settings can link work items found in branch names, update their status, add project-based tags, and assign required or optional reviewers by UUID.

## Build the macOS App

Create a distributable `.dmg`:

```shell
./gradlew :composeApp:packageDmg
```

The package is written to:

```text
composeApp/build/compose/binaries/main/dmg/
```

Override the package version when needed:

```shell
./gradlew :composeApp:packageDmg -PappVersion=1.2.0
```

## Tests

Run the JVM test suite:

```shell
./gradlew :composeApp:jvmTest
```

The tests cover Git operations, settings persistence and token encryption, LLM request parsing and context-window handling, and GitHub/Azure DevOps PR behavior.

## Project Structure

```text
composeApp/src/jvmMain/kotlin/com/maderskitech/localllmcommitassist/
  main.kt                  App entry point and window configuration
  App.kt                   Screen navigation and application state
  AppIcon.kt               Programmatic window icon
  data/
    GitService.kt          Git staging, commits, branches, remotes, and sync
    LlmService.kt          OpenAI-compatible client, parsing, and prompt budgets
    PrAttachment.kt        Attachment types and provider limits
    PrService.kt           GitHub and Azure DevOps PR APIs
    SettingsRepository.kt  Persisted application settings
    TokenEncryption.kt     Local token encryption
  ui/
    MainScreen.kt          Commit, branch, and pull request workflows
    SettingsScreen.kt      LLM and PR provider settings
    Theme.kt               Material 3 dark theme
  viewmodel/
    MainViewModel.kt       UI state and workflow orchestration
```

## Tech Stack

- Kotlin Multiplatform and Compose for Desktop
- Material 3
- Ktor Client with CIO
- kotlinx.serialization
- Kotlin coroutines
- Java Preferences API
