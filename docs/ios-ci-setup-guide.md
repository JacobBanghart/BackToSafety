# iOS CI Setup Guide

This guide covers everything needed to get the iOS release workflow running.
It is split into two parts: **what you do**, and **what the Account Holder does**.

---

## Part A — You do this first (on your Mac)

### Generate a Certificate Signing Request (CSR)

You need to create this file and send it to the Account Holder before they
can complete Part B Step 1.

1. Open **Keychain Access** (press `Cmd + Space`, type `Keychain Access`, press Enter)
2. In the menu bar click **Keychain Access → Certificate Assistant → Request a Certificate from a Certificate Authority...**
3. Fill in the form:
   - **User Email Address**: your Apple ID email
   - **Common Name**: `BackToSafety` (or your name — doesn't matter much)
   - **CA Email Address**: leave this blank
   - Select **Saved to disk**
4. Click **Continue**
5. Save the file as `CertificateSigningRequest.certSigningRequest` somewhere easy to find (e.g. Desktop)
6. Send that file to the Account Holder — they need it in Part B Step 1

---

## Part B — Account Holder does this (send them this section)

This takes about 10 minutes. Follow each step exactly.

---

### Step 1 — Create the Distribution Certificate

> You will need the `CertificateSigningRequest.certSigningRequest` file sent to you
> before starting this step.

1. Go to **developer.apple.com** and sign in
2. Click **Account** in the top menu
3. On the left sidebar click **Certificates, IDs & Profiles**
4. Click **Certificates** on the left
5. Click the **+** button in the top right
6. Scroll down to the **Software** section, select **Apple Distribution**, click **Continue**
7. Click **Choose File** and select the `CertificateSigningRequest.certSigningRequest` file
8. Click **Continue**, then **Download**
9. Send the downloaded `.cer` file back

---

### Step 2 — Create the Provisioning Profile

1. Still on **developer.apple.com → Certificates, IDs & Profiles**
2. Click **Profiles** on the left sidebar
3. Click the **+** button in the top right
4. Under **Distribution** select **App Store Connect**, click **Continue**
5. Under **App ID** select **com.backtosafety.app**, click **Continue**
6. Select the **Apple Distribution** certificate you just created, click **Continue**
7. Name it `BackToSafety AppStore`, click **Generate**
8. Click **Download** and send that `.mobileprovision` file back

---

### Step 3 — Create an API Key

1. Go to **appstoreconnect.apple.com** and sign in
2. Click your name or icon in the top right → **Users and Access**
3. Click **Integrations** in the top navigation bar
4. Click **App Store Connect API** on the left
5. Click the **+** button
6. Set the name to `CI Key` and the Role to **App Manager**, click **Generate**
7. Click **Download API Key** — ⚠️ you can only download this once, do not skip it
8. Send back:
   - The downloaded `.p8` file
   - The **Key ID** shown in the table next to the key name
   - The **Issuer ID** shown at the top of the page above the table

---

### Step 4 — Create the App in App Store Connect

1. Still on **appstoreconnect.apple.com**, click the **Apps** icon (grid of squares, top left)
2. Click the **+** button → **New App**
3. Fill in the form:
   - **Platforms**: iOS
   - **Name**: Back to Safety
   - **Primary Language**: English (U.S.)
   - **Bundle ID**: select **com.backtosafety.app** from the dropdown
     - If it does not appear in the list, message me before continuing
   - **SKU**: `backtosafety`
4. Click **Create**
5. Let me know when this is done

---

## Part C — You do this after receiving files from Account Holder

### Install the Distribution Certificate

1. Double-click the `.cer` file the Account Holder sent you
2. Keychain Access will open and import it automatically
3. It should appear under **My Certificates** as `Apple Distribution: <team name>`

### Export the Certificate as .p12

The CI workflow needs the certificate in `.p12` format with a password.

1. Open **Keychain Access**
2. Click **My Certificates** in the left sidebar
3. Find `Apple Distribution: <team name>`, right-click it → **Export**
4. Save as `dist_cert.p12`, set a strong password, click **OK**

### Base64-encode the files for GitHub Secrets

Run these commands in Terminal, they copy each value straight to your clipboard:

```bash
# Distribution certificate
base64 -i dist_cert.p12 | pbcopy
# Paste into GitHub secret: IOS_CERTIFICATE_BASE64

# Provisioning profile
base64 -i app.mobileprovision | pbcopy
# Paste into GitHub secret: IOS_PROVISIONING_PROFILE_BASE64

# App Store Connect API key
base64 -i AuthKey_XXXXXXXXXX.p8 | pbcopy
# Paste into GitHub secret: ASC_API_KEY_BASE64
```

### Add GitHub Secrets

Go to your GitHub repo → **Settings → Secrets and variables → Actions → New repository secret**
and add each of these:

| Secret name | Value |
|-------------|-------|
| `IOS_CERTIFICATE_BASE64` | base64 output from dist_cert.p12 |
| `IOS_CERTIFICATE_PASSWORD` | password you chose when exporting the .p12 |
| `IOS_PROVISIONING_PROFILE_BASE64` | base64 output from the .mobileprovision file |
| `ASC_API_KEY_ID` | Key ID from Account Holder (Step 3) |
| `ASC_API_KEY_ISSUER_ID` | Issuer ID from Account Holder (Step 3) |
| `ASC_API_KEY_BASE64` | base64 output from the .p8 file |

### Update the Workflow with Your Team ID

1. Find your 10-character Apple Team ID at **developer.apple.com → Account → Membership details**
2. Open `.github/workflows/ios-release.yml`
3. Find the line `REPLACE_WITH_YOUR_TEAM_ID` and replace it with your Team ID

### Install the Self-Hosted Runner on Your Mac

1. Go to your GitHub repo → **Settings → Actions → Runners → New self-hosted runner**
2. Select **macOS** as the operating system
3. Follow the commands shown on screen to download and configure the runner
4. After configuration, install it as a background service so it starts automatically:

```bash
./svc.sh install
./svc.sh start
```

---

## Part D — Triggering a build

Once everything above is done, push a version tag to trigger the workflow:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The runner on your Mac will pick it up, build the IPA, and submit it to App Store Connect.
The build will appear in **App Store Connect → TestFlight** within a few minutes of upload.
