# Contributing to RMBG

Thank you for your interest in contributing to **RMBG**! We appreciate all contributions, from bug reports and documentation fixes to new features and optimizations.

---

## 📋 Code of Conduct

Please be respectful and constructive in all discussions, issues, and pull requests.

---

## 🛠️ Development Setup

1. **Fork and clone the repository:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/rmbg.git
   cd rmbg
   ```

2. **Open in Android Studio:**
   - Open the project in Android Studio (recommended: Iguana / Jellyfish or newer).
   - Ensure JDK 17 is configured in **Settings -> Build, Execution, Deployment -> Build Tools -> Gradle -> Gradle JDK**.

3. **Verify Build:**
   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew assembleDebug
   ```

---

## 🌿 Git Workflow

1. Create a feature branch from `master`:
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/your-bug-fix
   ```

2. Follow standard commit message conventions:
   - `feat: add new feature`
   - `fix: resolve issue description`
   - `perf: optimize memory or execution speed`
   - `refactor: clean up structure without changing behavior`
   - `docs: update documentation or comments`

3. Ensure all unit tests pass before submitting:
   ```bash
   ./gradlew testDebugUnitTest
   ```

4. Push to your fork and submit a Pull Request to `master`.

---

## 📐 Architecture Guidelines

- **Clean Architecture & Unidirectional Data Flow (UDF)**:
  - Domain models and interfaces reside in `com.rmbg.app.domain`.
  - Machine learning engines implement `BackgroundRemover` in `com.rmbg.app.engine`.
  - UI state is represented as immutable data classes in `com.rmbg.app.presentation`.
  - Jetpack Compose UI components are stateless whenever possible in `com.rmbg.app.ui.components`.
- **Zero Cloud Policy**: Do not introduce remote network calls or HTTP client dependencies. All ML models must execute 100% on-device.
- **Memory & Allocation Efficiency**:
  - Avoid creating unneeded `Bitmap` or `IntArray`/`FloatArray` allocations in hot render loops.
  - Wrap high-cost transformations in `remember` blocks within Compose.
  - Scale large images using `BitmapUtils.decodeSampledBitmap` before processing.

---

## 🐛 Reporting Bugs & Requesting Features

- **Bug Reports**: Open an issue describing the expected behavior, actual behavior, steps to reproduce, and device specifications (OS version, device model).
- **Feature Requests**: Open an issue detailing the use case, proposed UI/UX design, and implementation plan.
