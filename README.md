# OTP Forwarder

Android app that forwards extracted SMS codes using locally configured keyword rules.

The main screen shows the installed version below the app title. Tagged GitHub builds use the exact release tag (for example, `Version v1.1.1`); local and manual branch builds use the version configured in Gradle.

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

## Test a rule without sending SMS

Tap **Test** on a saved rule, paste a sample SMS, and tap **Preview**. The result shows the first matched keyword that yields a code, extracted OTP, destination, and exact outgoing message. It distinguishes an empty sample, no keyword match, and a keyword match without a code of the selected length. Editing the sample clears the previous result.

Preview uses the same matching and formatting engine as live forwarding and ignores enable switches so you can test disabled rules. The dialog shows when the rule or master switch is off. It never sends an SMS, changes a saved rule, or saves the sample/result. Close the dialog to discard the preview.

## Configuration and status

The master toggle is stored in SharedPreferences; forwarding rules are stored in Room. Older preference-based rules are imported before their old preference values are removed. Incomplete legacy configurations are retained.

### Keeping rules across updates and reinstalls

Updates signed with the same key preserve rules and settings. On supported Android 10+ uninstall screens, choose **Keep app data** to retain configuration for a later reinstall with the same signer. Availability depends on the device and installer.

Android backup and device transfer are enabled for the rules database (including its journal files) and forwarding preferences only. A reinstall can restore that configuration when Android has a completed backup and restore is enabled for the account/device. Backups run on Android's schedule; this is not an immediate or guaranteed backup. No SMS bodies or OTP history are stored by the app or included in its backup allowlist. Permissions may need to be granted again after reinstall.

If you delete app data and Android has no usable backup, the rules cannot be recovered. The first upgrade from the temporary signing keys used by v1.0.0/v1.1.0 still requires a fresh installation.

Sending uses the device's default SMS subscription. Configure a default SMS SIM on dual-SIM devices. Send-result callbacks report modem/network failures through notifications when notification permission is available. A successful send result does not confirm delivery to the recipient. Debug builds display queued message contents; release builds only display failures.

## Build and test

Open in Android Studio with Android SDK 36 and Java 21 available to Gradle.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

On Linux or macOS, run `./gradlew testDebugUnitTest assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Minimum supported Android API: 23. Target API: 36. The app uses Kotlin, Material Components, Room, coroutines, and Gson.

## GitHub debug APK builds

### CI and release checks

**Android Checks** runs on pushes to `main`, pull requests, and manual runs. It runs the JVM unit test suite, runs lint for both debug and release variants, builds the debug APK, and builds the optimized release APK with R8/resource shrinking. Test, lint, and release mapping reports are retained for 14 days, including reports produced before a failed check. This workflow needs no signing secrets, so pull requests can run it too.

Every release/manual APK workflow first runs these same checks and only builds and publishes the persistent-key debug APK after they pass. The unsigned optimized release APK is checked but is not distributed. Device-only backup/restore and real SMS behavior still need device testing.

Push any Git tag to run `.github/workflows/debug-apk.yml`. The workflow runs unit tests, builds and verifies the debug APK, uploads it as a workflow artifact for 30 days, and attaches the APK and SHA-256 checksum to a GitHub release for that tag.

Pushing to `main` alone does not trigger an APK build. Push a new tag pointing to the desired commit, or use the manual Actions run.

You can also run **Release Debug APK** from the Actions tab. Manual runs on a branch upload artifacts without creating a release. The APK supports Android API 23 and later, including API 36 devices.

### Signing and updates

CI uses a persistent private debug keystore from the repository's `DEBUG_KEYSTORE_BASE64` Actions secret. It restores the key to `.signing/debug.keystore` before building and removes it afterward. Missing signing credentials fail the build instead of silently generating a new key. The keystore is excluded from Git; keep a secure backup and reuse it for all future builds.

For local builds that must update CI-installed APKs, use that same keystore at `.signing/debug.keystore`. Other local checkouts fall back to their usual Android debug key and cannot update a CI build signed with a different key. The shared debug keystore uses alias `androiddebugkey` and the conventional debug password `android`; its private key must remain private.

The first build with persistent signing requires one final uninstall of v1.0.0 or v1.1.0, which used temporary CI keys. This clears settings. Subsequent builds with the persistent key and the same application ID can update the installed app while retaining settings. Increase `versionCode` and update `versionName` in `app/build.gradle.kts` for each new release; Android rejects version downgrades.

Debug builds show queued OTP contents in notifications. APK signing certificate details are included in the workflow summary and artifacts.

Tests cover extraction, rule matching and formatting, validation, JSON conversion, and multipart assembly. Real SMS send callbacks and configuration migration should also be checked on a device before release.

## Permissions

The app currently requests RECEIVE_SMS, READ_SMS, SEND_SMS, and POST_NOTIFICATIONS (Android 13+). Forwarding requires SMS receive/send access. Notifications provide failure visibility but are not required for matching. SMS charges may apply.
