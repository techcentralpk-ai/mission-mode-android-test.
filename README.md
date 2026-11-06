# Mission Mode Android V1

One-app Android test for detecting Instagram opening and prompting a five-minute **Reply to messages** mission. No Accessibility, MacroDroid, internet permission or paid backend.

**Build status:** This is experimental Android source. A successful cloud build produces `app-debug.apk`, not a published app. Android's Usage Access permission is required for automatic detection; an ongoing notification indicates monitoring is enabled. Device testing, battery and notification reliability, and Play policy review remain necessary.

## Build the APK for free

1. Open **Actions** on this repository.
2. Select **Build Mission Mode test APK**.
3. Run the workflow, or inspect the automatically triggered run.
4. If it succeeds, download the **MissionMode-Android-Test-APK** artifact from the run and extract `app-debug.apk`.
5. Install only on your own test device. Enable notifications and Usage Access inside the app; open Instagram normally to test.

The `.github/workflows/build.yml` workflow extracts the compressed project source, checks its checksum, builds it with Android SDK and Gradle, then uploads the APK. The `payload/` files are the compressed source split into chunks for transfer. They contain *no keys or credentials*.

**Important:** Android usage tracking is not proof of Instagram screen time or Reel counts. Reminders may be delayed or suppressed by Android power management. This is for private testing only.
