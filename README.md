# OTP Forwarder

Android app that forwards extracted SMS codes using locally configured keyword rules.

## Setup

1. Install the debug APK from a tagged [GitHub release](https://github.com/rohandhamapurkar/android-otp-sms-forwarder/releases), or download the artifact from a completed [Actions run](https://github.com/rohandhamapurkar/android-otp-sms-forwarder/actions/workflows/debug-apk.yml).
2. Open the app and grant SMS permissions. Allow notifications to see forwarding failures.
3. Add a rule with a ten-digit destination, message prefix, and one or more keywords. Tap **Add** for each keyword, then **Save**.
4. Enable the rule and the master toggle. On dual-SIM devices, select a default SMS SIM in Android settings.

## Matching behavior

- Keywords match the incoming message body as case-insensitive substrings.
- Each rule has an **OTP length** setting: **Auto (current matching)**, **4 digits**, or **6 digits**. Existing rules retain Auto after upgrading. Fixed-length rules search only for full codes of that length and never truncate longer numbers.
- The matched keyword is removed before extraction, preventing numbers inside that keyword from being used as the code.
- In Auto mode, codes of 4–8 digits near OTP/code/PIN/password words take priority. Otherwise the first standalone six-digit number, then four-digit number, is used. Fixed-length rules keep keyword priority and use only their selected length for the fallback.
- Each enabled rule produces at most one outgoing message. Multiple matching rules can forward to multiple destinations.
- Outgoing text is `Prefix: code`. New rules require a prefix, at least one keyword, and a ten-digit destination number.
- Multipart incoming SMS segments are assembled before matching. Long outgoing messages are split using the device's SMS encoding rules.

The permissive number fallback is retained for compatibility: a matching message without an actual OTP can still yield another four- or six-digit number. Filters match body text rather than authenticate the sender.

## Configuration and status

The master toggle is stored in SharedPreferences; forwarding rules are stored in Room. Older preference-based rules are imported before their old preference values are removed. Incomplete legacy configurations are retained.

Sending uses the device's default SMS subscription. Configure a default SMS SIM on dual-SIM devices. Send-result callbacks report modem/network failures through notifications when notification permission is available. A successful send result does not confirm delivery to the recipient. Debug builds display queued message contents; release builds only display failures.

## Build and test

Open in Android Studio with Android SDK 36 and Java 21 available to Gradle.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

On Linux or macOS, run `./gradlew testDebugUnitTest assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Minimum supported Android API: 23. Target API: 36. The app uses Kotlin, Material Components, Room, coroutines, and Gson.

## GitHub debug APK builds

Push any Git tag to run `.github/workflows/debug-apk.yml`. The workflow runs unit tests, builds and verifies the debug APK, uploads it as a workflow artifact for 30 days, and attaches the APK and SHA-256 checksum to a GitHub release for that tag. No signing secrets are required.

Pushing to `main` alone does not trigger an APK build. Push a new tag pointing to the desired commit, or use the manual Actions run.

You can also run **Release Debug APK** from the Actions tab. Manual runs on a branch upload artifacts without creating a release. The APK supports Android API 23 and later, including API 36 devices.

Each fresh runner generates its own debug signing key. Installing over a build signed with a different key requires uninstalling the old app first, which clears its settings. Debug builds also show queued OTP contents in notifications.

Tests cover extraction, rule matching and formatting, validation, JSON conversion, and multipart assembly. Real SMS send callbacks and configuration migration should also be checked on a device before release.

## Permissions

The app currently requests RECEIVE_SMS, READ_SMS, SEND_SMS, and POST_NOTIFICATIONS (Android 13+). Forwarding requires SMS receive/send access. Notifications provide failure visibility but are not required for matching. SMS charges may apply.
