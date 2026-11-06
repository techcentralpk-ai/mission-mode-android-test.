# Mission Mode Android Private Beta Privacy Notes

Mission Mode is an experimental, locally stored digital wellbeing tool.

* Device permission **Usage Access**: optional until automatic monitoring is enabled. It exposes app foreground events broadly at Android level, but the application uses event data to identify selected supported apps only: Instagram, TikTok, Snapchat and YouTube. Events from other apps are processed only for detecting a transition away from monitored apps, and are not recorded in history.
* **Notifications**: required for background monitoring indicator, purpose reminders and timer expiry. Notification visibility depends on user device settings and Android rules.
* **Display over other apps**: entirely optional, used to show a purpose card when a monitored app is foreground. Some other apps block overlays for fraud prevention. Mission Mode hides its overlay on an unselected app transition.
* **Boot completed**: after a reboot, the app may resume monitoring when the user previously enabled it and permissions remain granted; Android may limit automatic launches. Explicit Force Stop disables this until the user reopens the app.
* No internet permission, remote telemetry, third-party analytics, user accounts or ads. Data is kept locally using Android SharedPreferences and is deleted when the app data is cleared. History can be deleted from the app.
* History records the selected app, stated mission purpose, selected time limit, elapsed time until marked complete, extensions and completion outcome. It is not a log of every app open or verified foreground screen time; it does not record Reels watched or inspect private messages.
* Monitoring, overlays and permissions can be disabled by the user at any time. This private beta is not medical treatment and guarantees no particular screen-time reduction.
