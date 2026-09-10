# All Convert — Cloud APK Build

This project builds the Android APK on GitHub Actions. The phone does not need Android SDK or Gradle installed.

## Build

1. Upload this project to a GitHub repository.
2. Open **Actions**.
3. Select **All Convert - Build APK**.
4. Press **Run workflow**.
5. Wait for the job to finish.
6. Open the completed run and download **All-Convert-debug-APK** from Artifacts.

The workflow installs:
- Java 17
- Android SDK Platform 35
- Android Build Tools 35.0.0
- Gradle 8.9

The project uses Android Gradle Plugin 8.7.3, which is compatible with Gradle 8.9.
