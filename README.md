# StudyMate — Offline-first Android Study Planner

StudyMate is a Kotlin + Jetpack Compose study planner for Android phones and tablets. It stores study data on-device and includes an explainable, rule-based offline study coach. The core app does not require an account, API key, or internet connection.

## Daily Goal Streak widget

The home-screen widget is a **task-completion streak**, not a study-hours counter.

- Shows the current streak in days and a Monday–Sunday completion row.
- Shows today's checklist progress as completed tasks / planned tasks.
- Shows the number of tasks planned for tomorrow when available.
- Uses a day as a streak day only when at least one task was planned for that date and every planned task for that date is checked complete.
- Keeps the current streak alive during the day while today's tasks are still in progress; the count is based on the previous completed day until today's list is finished.
- Refreshes when Room-backed tasks change, after reminder checks, and periodically through the launcher.
- Tapping the widget opens StudyMate.

In Planner, tap **+ Task** and choose **Today** or **Tomorrow**. This lets you prepare tomorrow's list the previous night. The app schedules a local **8:00 PM** reminder when today's planned tasks are still incomplete. Allow notifications when Android asks.

A date with no planned tasks does not count as a completed streak day. The streak is task-based; there is no fixed 1-hour or 2-hour daily requirement.

## App modules

- **Dashboard:** today's planned-task progress, daily streak, focused minutes, pending tasks, and offline study cue.
- **Planner:** create, prioritize, complete, and delete tasks; assign each new task to today or tomorrow; set Saturday–Friday weekly subject-minute targets.
- **Focus timer:** Focus / Short Break / Long Break, editable durations, pause/resume/reset, ongoing notification, and foreground service.
- **Elapsed-time accuracy:** the service stores an `endElapsedRealtime` deadline and calculates remaining time from `SystemClock.elapsedRealtime()` rather than decrementing a counter.
- **Offline AI Coach:** estimates practical focus/break intervals, subject priorities, and consistency from local sessions and pending tasks. This is a rule-based engine, not a cloud LLM.
- **Insights:** subject-wise focus distribution, total focused time, recorded breaks, and quiz results.
- **CT & Quiz Vault:** save scores, totals, and mistake notes.
- **Digital Notes:** save subject-linked notes locally.
- **Themes:** dark mode by default, with a light-mode switch.
- **Responsive layout:** bottom navigation on phones and a navigation rail on wider screens/tablets.

## Technology

- Kotlin 2.3.21, Android Gradle Plugin 8.13.2, Gradle 8.13
- Jetpack Compose + Material 3
- Room 2.8.5 with KSP
- Preferences DataStore 1.2.0
- Jetpack Glance AppWidget 1.2.0
- Minimum Android 8.0 (API 26), compile/target API 36

## Build from GitHub (recommended)

The included GitHub Actions workflow compiles the debug APK and saves it as a downloadable workflow artifact.

1. Create an empty GitHub repository named `StudyMate`. Do not initialize it with a README, `.gitignore`, or license because this ZIP already contains those project files.
2. Extract `StudyMate-GitHub-Ready-Streak-Widget.zip` on your computer.
3. Open the extracted `StudyMate-Android` folder. Upload the **contents inside this folder** to the root of the new GitHub repository. Include the hidden `.github` folder; it contains the Android build workflow. Do not upload the ZIP file as the only repository file, and do not leave the project nested one extra folder deep.
4. Commit the upload to the `main` branch.
5. Open the repository's **Actions** tab. Select **Build StudyMate APK** and open the latest run. Wait for a green check mark.
6. In the successful run, find **Artifacts** and download `StudyMate-debug-apk`.
7. Extract the downloaded artifact ZIP. It contains `app-debug.apk`.
8. Transfer `app-debug.apk` to your Android phone and install it. Repeat the same installation on your Android tablet. Android may ask you to allow installation from the browser or file manager you used.
9. On each device, long-press the home screen, open **Widgets**, find **StudyMate**, and add **StudyMate Streak**. Resize it to a wide 3×2-style space if your launcher allows it.
10. Open StudyMate, allow notifications, then use Planner → **+ Task** → **Today** or **Tomorrow** to make your daily list.

### Easier upload option using GitHub Desktop

If the browser uploader is awkward, install GitHub Desktop and sign in. Create the empty repository on GitHub, then in GitHub Desktop choose **File → Clone repository**, select the new `StudyMate` repository, and clone it to your computer. Copy every item inside the extracted `StudyMate-Android` folder into the cloned repository folder (including `.github`). GitHub Desktop will show the changes; enter a summary such as `Add StudyMate Android project`, click **Commit to main**, then click **Push origin**. After pushing, follow the Actions steps above.

## Install on both devices

The same debug APK can be installed on both your phone and tablet. StudyMate stores its records locally on each device; the two installations do **not** automatically sync tasks, notes, or streak history with each other.

## Build locally

Open this folder in Android Studio, allow Gradle sync, and run the `app` configuration. Or run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The included launcher downloads Gradle 8.13 the first time it is used. Android SDK platform 36 and build-tools 36.0.0 must also be installed.

## Important notes

1. Android 13+ requires notification permission for visible reminders and timer notifications. If permission is denied, the widget still works, but reminder notifications will not appear.
2. The daily reminder is scheduled for 8:00 PM local time using an inexact repeating alarm. Android and some device manufacturers may defer background reminders due to battery-management settings.
3. The source project is configured for GitHub Actions to build the APK. The build has not been executed in this authoring environment because Android SDK/Gradle dependencies are not installed here; use the Actions result as the actual compile check before installing.
4. The project is an initial clean release and does not yet include a floating overlay, camera/gallery question attachments, or a nightly-report scheduler.

## Package identity

- Debug application ID: `com.saad.studymate.debug`
- Release application ID: `com.saad.studymate`
- Version: `1.0.0`
