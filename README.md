# Mission Mode Android V0.4 — Private test build

A local-first Android prototype that prompts users to set a purpose and time limit for Instagram, TikTok, Snapchat and YouTube. It uses Android Usage Access and a user-enabled foreground service to notice foreground app transitions, plus optional floating purpose cards via Display over other apps. It does not use Accessibility, read content or require internet access.

## What's new

1. Instagram, TikTok, Snapchat and YouTube are supported. Instagram is enabled by default; the other three can be switched on individually in Settings.
2. Users choose a purpose: Reply to messages, Post content, Research, Learn something, Take a break, Other.
3. Timer options: presets 3, 5, 10, 15, 20 and 30 minutes, plus 1–180 custom minutes (plus/minus, with typed duration in the overlay).
4. The home screen and optional floating pop-up capture purpose, app and duration. History and Insights record your actual *mission intentions*, and report outcome plus session elapsed time.
5. New guided permission setup and monitoring which attempts to remain active after the screen closes, with `START_STICKY` foreground service and a boot receiver. No OS app can recover automatically from explicit **Force stop** until the user launches it again. Vivo/OEM background restrictions also affect reliability.
6. Android's native notification countdown tracks elapsed wall-clock time, and a high-priority expiry notification reminds you to finish or extend; precise heads-up popups aren't guaranteed unless optional overlay permission works.

## Build

GitHub Actions automatically compiles the Java Android project into `app-debug.apk` and uploads an artifact. Open the Actions tab, open the latest successful run and download `MissionMode-Android-V4-Test-APK`. Do not distribute the debug APK publicly.

## Privacy

All data stays in local Android SharedPreferences. Usage Access can expose system-wide app usage information, but this code only checks transitions for supported applications. We don't read notifications or app content. No data network transfer. The chart counts recorded missions, **not** all app openings or the actual time spent in foreground apps.

## Limitations

Android foreground-service and notification policies may restrict background service persistence, overlay visibility, time accuracy, and Google Play approval. Overlays can affect sensitive apps; they're hidden whenever a different app takes focus. The prototype requires user validation on each Android device and additional product and policy QA prior to public distribution.
