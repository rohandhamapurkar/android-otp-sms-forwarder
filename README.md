# OTP Forwarder

OTP Forwarder is an Android application designed to automatically detect and forward 4 or 6-digit One-Time Passwords (OTPs) from incoming SMS messages to a pre-configured phone number. It allows users to set up specific filters to ensure only relevant messages are forwarded.

## 🚀 Features

- **Master Toggle**: Quickly enable or disable all forwarding and filtering functionality from the main dashboard.
- **Robust OTP Detection**: Uses regex to identify 4 and 6-digit codes. The logic is optimized to avoid "false positives" by ignoring numbers that are part of the filter keyword itself (e.g., a card ending in 4 digits).
- **Keyword Filtering**: Only forwards messages containing specific keywords (e.g., "Bank", "Login", "Verification").
- **Custom Forwarding**: Set a target phone number and an optional prefix.
- **Contextual Info**: Toggle whether to include the matched filter name in the forwarded message (e.g., "Prefix (Bank Name): OTP").
- **Visual Feedback**: Triggers a system notification whenever an OTP is successfully intercepted and forwarded.
- **Permission Management**: Built-in UI to track and request necessary SMS and notification permissions.
- **Persistent Configuration**: Settings are saved locally using `SharedPreferences`.

## 🛠 Tech Stack

- **Language**: Kotlin
- **Min SDK**: 23 (Android 6.0)
- **Target SDK**: 36 (Android 15)
- **UI Framework**: Material 3 (MD3) for modern components and theming.
- **Dependencies**:
  - `Gson`: For JSON serialization of filter lists.
  - `Material Components`: Using Material 3 for the `MaterialSwitch` and Chips.
  - `AndroidX Core & AppCompat`: For backward compatibility.

## 📦 Project Structure

```text
app/src/main/
├── java/com/example/otpforwarder/
│   ├── MainActivity.kt    # Main UI and configuration management
│   ├── SmsReceiver.kt     # Background receiver for SMS processing
│   └── OtpUtils.kt        # Centralized logic for OTP extraction
├── res/
│   ├── layout/
│   │   └── activity_main.xml # App dashboard layout
│   └── values/
│       ├── strings.xml     # App strings
│       └── themes.xml      # Material 3 styling
└── AndroidManifest.xml     # App metadata and permission declarations
```

## ⚙️ Setup & Usage

1. **Clone the repository**:
   ```bash
   git clone https://github.com/yourusername/otpforwarder.git
   ```
2. **Open in Android Studio**:
   - Open the project and wait for Gradle sync to complete.
3. **Build and Run**:
   - Deploy the app to your Android device.
4. **Configuration**:
   - Launch the app and grant the required SMS and Notification permissions.
   - Enter the **Target Phone Number** where OTPs should be sent.
   - Add **Filter Keywords** (e.g., "HDFC", "Google", "Amazon"). The app will only process messages containing these words.
   - Customize the **Prefix Text** (optional).
   - Click **Save Configuration**.

## 🛡 Permissions

To function correctly, the app requires the following permissions:
- `RECEIVE_SMS`: To detect incoming messages.
- `READ_SMS`: To process message content.
- `SEND_SMS`: To forward the extracted OTP to the target number.
- `POST_NOTIFICATIONS`: To provide status updates (Android 13+).

> **Note**: Due to Android's "SmsAndCallLogPolicy", manual configuration or specific play store exceptions might be required for production distribution.

## 🧪 Testing

The project includes unit tests for the robust OTP extraction logic:
- `OtpExtractionTest.kt`: Verifies the regex pattern and filter-exclusion logic against various SMS formats, ensuring 6-digit codes are prioritized and false positives are avoided.

## 📄 License

This project is for educational purposes. Please ensure compliance with local privacy laws and telecommunication regulations before use.
