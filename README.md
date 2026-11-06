# Mission Mode Android V1

A standalone Android experiment to detect when Instagram opens and offer a five-minute **Reply to messages** mission, without MacroDroid or Accessibility. Native source is supplied as a ZIP archive.

## One remaining upload step

1. Download [the Android V1 ZIP from ChatGPT](https://chatgpt.com/) using the attachment linked in our conversation. **Do not extract it.**
2. In this repository, click **Add file → Upload files**.
3. Upload the exact file `Mission_Mode_Android_V1_Source_and_Free_APK_Builder.zip` to the repository **root** and click **Commit changes**. The filename must match.
4. Open the [Actions tab](https://github.com/techcentralpk-ai/mission-mode-android-test./actions), and select **Build Mission Mode test APK**. The build should trigger from the upload; otherwise select **Run workflow**.
5. When the build succeeds, open that run, download artifact `MissionMode-Android-Test-APK`, unzip it, and transfer `app-debug.apk` to your Android phone.
6. Install the APK, allow notifications and (optionally) Android Usage Access, then enable monitoring in Mission Mode. Open Instagram normally and check for an intention reminder.

**Status:** Android code passed Java parser and XML checks, but has not yet been compiled with the Android SDK or tested on a phone. The workflow will perform its first actual compilation after the source ZIP is uploaded.

**Safety:** The source deliberately requests no Internet, Accessibility or overlay permission. Usage Access is broad at the operating-system level, but the app only reacts to Instagram foreground transitions and does not retain histories of other apps. A persistent notification identifies when monitoring is running. The debug APK is for personal device testing, not public distribution. Actual detection and reminder performance may vary by Android version, notification settings, and battery management. The foreground service will require careful Google Play policy review before commercial distribution.

For detailed guidance, see `README.md`, `PRIVACY.md` and `TEST_PLAN.md` inside the source ZIP after extraction.
