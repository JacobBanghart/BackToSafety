# Back to Safety Privacy Policy

Effective date: 2026-04-26

Back to Safety is designed to keep sensitive safety information on your device. This policy explains what data the app uses, where it is stored, and what happens when you use app features.

## Summary

- We do not require an account.
- We do not operate a cloud backend for your personal profile data.
- Your emergency profile, contacts, destinations, and incident notes are stored locally on your device.
- The app may open system services (SMS, phone dialer) only when you explicitly trigger those actions.
- The app collects anonymous usage analytics and crash reports to help us improve reliability. No personally identifiable information is included.

## Information We Handle

Back to Safety may handle the following categories of data you provide:

- Profile details (name, photo, physical description, medical and communication notes)
- Emergency contact details (name, phone, relationship)
- Safety destinations and related notes
- Incident and search notes
- App settings and onboarding status

This information is stored in local on-device storage (SQLite on native platforms, equivalent local storage on web).

## Analytics and Crash Reporting

To improve app quality and reliability, Back to Safety collects the following automatically:

**Anonymous usage analytics (PostHog)**

- A randomly generated device identifier (UUID) is created on first launch and stored locally. It is not linked to your name, email, or any other personal information.
- Anonymous events are recorded when you navigate through onboarding, start or complete an emergency search, and change app settings.
- Session recordings may be captured with all text inputs and images masked — we see interaction patterns only, not the content you type or your photos.
- Analytics data is sent to our self-hosted PostHog instance at **posthog.backtosafety.app**. Data does not leave our infrastructure.

**Crash reporting (Sentry)**

- If the app crashes or encounters an unexpected error, a crash report is sent containing the error message, stack trace, device type, OS version, and app version.
- Reports are linked to the same anonymous device UUID used for analytics, so we can correlate issues without knowing who you are.
- Crash data is sent to our self-hosted Sentry instance at **sentry.backtosafety.app**. Data does not leave our infrastructure.

You cannot opt out of analytics and crash reporting in the current version of the app. If you prefer no telemetry, you may block network access to `posthog.backtosafety.app` and `sentry.backtosafety.app` at the network or firewall level.

## Device Permissions

Depending on your actions, the app may request access to:

- Contacts: to import emergency contacts
- Camera: to capture profile photos
- Photos/Media library: to choose profile photos
- SMS capability: to open a prefilled emergency text flow

Permissions are optional and can be denied. Core app behavior may be reduced if permissions are not granted.

## How Data Is Used

Data is used only to provide app features, including:

- Preparing emergency readout details
- Showing profile information during search flows
- Opening messages to alert your emergency circle
- Maintaining your local setup and preferences
- Identifying and fixing crashes and usability issues (analytics/crash data only)

## Data Sharing

We do not sell or rent personal data.

The app does not automatically send your personal profile data to our servers. If you choose to send a text message or make a call, data included in that message/call is processed by your mobile carrier and platform providers.

Anonymous analytics and crash reports are processed by our self-hosted infrastructure only. They are not shared with third-party analytics vendors.

## Data Retention and Deletion

- Personal profile data remains on your device until you modify or delete it.
- You can clear app data using in-app developer reset tools (if enabled) or by uninstalling the app.
- Anonymous analytics events and crash reports are retained on our self-hosted servers for up to 12 months.

## Children and Sensitive Information

Back to Safety may be used to store sensitive personal and health-adjacent information for safety coordination. You are responsible for entering only information you are authorized to manage.

## Security

We use platform-provided local storage mechanisms. Anonymous telemetry is transmitted over HTTPS. No method of storage or transmission is perfectly secure. Keep your device protected with passcode/biometric lock where possible.

## Your Choices

You can:

- Decline optional permissions
- Edit or remove stored records inside the app
- Remove app data by uninstalling the app

## Changes to This Policy

We may update this Privacy Policy from time to time. When updated, we will revise the effective date.

## Contact

For privacy questions or support:

- Email: support@backtosafety.app
- Support URL: https://backtosafety.app/support/
