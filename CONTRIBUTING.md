# Contributing to Groq Voice Typer

First off, thank you for considering contributing to Groq Voice Typer! It's people like you that make the open-source community such an amazing place to learn, inspire, and create.

Please read through these guidelines to ensure a smooth contribution process.

---

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md). Please report any unacceptable behavior to the project maintainers.

---

## How Can I Contribute?

### 1. Reporting Bugs
Before opening a new bug report, please check the existing issues to see if the problem has already been reported. If not, open a new issue and include:
* A clear, descriptive title.
* Steps to reproduce the issue.
* Your Android OS version, device model, and target keyboard app.
* Expected vs. actual behavior.
* Relevant system log snippets (using `adb logcat`).

### 2. Suggesting Enhancements
Feature requests are welcome! When opening a feature request, please describe:
* The core problem you want to solve.
* Your proposed solution or user experience.
* Alternative approaches you've considered.

### 3. Submitting Pull Requests
We accept contributions via Pull Requests (PRs). To ensure a smooth review:
1. **Fork the repository** and create a feature branch off `main` (e.g. `feat/your-feature-name` or `fix/issue-description`).
2. Make your changes in your branch.
3. Ensure the project builds cleanly and there are no compiler warnings.
4. Format your code according to the Kotlin styling guidelines.
5. Reference any related issues in the PR description.
6. Submit your PR and await code review.

---

## Development Setup

To build and run the project locally:

1. Install **Android Studio** (Koala or newer is recommended).
2. Clone your fork of the repository:
   ```bash
   git clone https://github.com/your-username/AI-Voice-typer.git
   ```
3. Open the folder in Android Studio. It will automatically download dependencies and index the project.
4. Connect a physical Android device via USB (with Developer Options & USB Debugging enabled) or start a Virtual Device (AVD).
5. Run `./gradlew assembleDebug` from the terminal, or click the **Run** button in Android Studio to build and deploy.

---

## Coding Standards

To maintain code quality, please follow these guidelines:
* **Language Guidelines**: Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
* **Compose Guidelines**: Use standard Compose state patterns. Ensure any state referenced in gesture coroutines uses `rememberUpdatedState` to avoid capturing stale values.
* **Imports**: Do not use wildcard imports (e.g., `import kotlinx.coroutines.*`). Always declare imports explicitly.
* **File Cleanup**: Any temporary files generated (like audio recordings) must be deleted securely immediately after use. Use `finally` blocks to guarantee file deletion.

---

## Commit Message Guidelines

We follow the **Conventional Commits** specification. This helps automate releases and changelogs. Please format your commit messages as follows:

```
<type>(<scope>): <subject>

[optional body]

[optional footer(s)]
```

### Types
* `feat`: A new feature
* `fix`: A bug fix
* `docs`: Documentation changes
* `style`: Formatting, missing semi-colons, etc. (no production code changes)
* `refactor`: Refactoring production code (without adding features or fixing bugs)
* `test`: Adding missing tests or correcting existing tests
* `build`: Changes that affect the build system or external dependencies (e.g., Gradle config)
* `ci`: Changes to CI configuration files and scripts (e.g., GitHub Actions workflows)

### Examples
* `feat(ime): add optional language support parameter`
* `fix(recorder): prevent MediaRecorder reference leak on preparation failure`
* `build: update compose compiler options for Kotlin 1.9.23`
