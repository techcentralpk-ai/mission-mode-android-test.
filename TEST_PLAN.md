# Android V0.4 test plan

Test first on your own phone, with no financial app open. Use a stable debug APK build and preserve the previous build if you need to roll back.

1. Upgrade the existing app. Verify Home has Instagram, TikTok, Snapchat and YouTube, purpose options and a custom-duration picker.
2. Enable monitoring. Verify on-device guided requests for notifications, Usage Access and optional floating overlay. Check that declining floating overlay leaves notifications functioning.
3. Check the persistent `Mission Mode monitoring enabled` notification. Dismiss Mission Mode from recent-apps and open Instagram. Test launch from launcher, Recents, and return from another app, at least ten times each; record misses and any false duplicate prompts.
4. Force Stop Mission Mode in Settings and verify monitoring no longer runs. Reopen Mission Mode to re-enable or recover. Distinguish force stop from swiping the app out of Recents.
5. Restart phone; verify whether the service returns. On Vivo, allow Autostart and unrestricted background activity if needed. Do not claim restart is always guaranteed.
6. On Settings, enable TikTok, Snapchat and YouTube separately. Open each installed native app normally. Verify correct app name, title and purpose cards. Disable one app and ensure it no longer triggers.
7. In the floating popup, choose different purposes and presets, type 1 and 90 minutes, and adjust with plus/minus. Start missions and verify the selected app, purpose and duration are correctly saved.
8. In Home, test all app selections, reason choices, timer presets and typed custom time. Ensure each starts the correct native app.
9. Verify the countdown notification decreases. Check expiration in a foreground app with optional overlay enabled, and confirm extension (+2 min) and Finished / Distracted outcomes update session state.
10. Verify Insights Today and Last 7 Days count recorded missions accurately by purpose and by app. Test with multiple deliberately completed missions on different dates if possible. Verify empty-state behavior.
11. Verify banking or other sensitive apps are never covered after switching away from the selected social app. Test leaving an overlay visible, then switching rapidly to an unrelated app.
12. Verify battery consumption and delayed alarms with the phone locked, Do Not Disturb enabled and Notifications denied. Note Android may delay inexact alarms.

## Not claimed

This is a private beta. It does not read messages, count Reels, block app access, record all app launches in history, or infer true minutes spent inside social apps. The charts reflect only manually started and completed missions.
