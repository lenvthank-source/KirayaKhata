# How to Build the Final APK & Auto-Build on GitHub

This project includes a ready-to-use GitHub Actions workflow (`.github/workflows/build-apk.yml`) so you can either download the APK directly from AI Studio or push to GitHub for automated cloud builds.

---

## Option 1: Fastest Way (Directly in Google AI Studio)

If you just want to install the final APK or Play Store AAB on your phone right now:
1. Open the **Settings menu** in Google AI Studio.
2. Select **Generate APK / AAB**.
3. Wait for the cloud build to finish and download the generated `.apk` file directly.

---

## Option 2: Export to GitHub & Auto-Build Signed Release APK

### Important Note: GitHub Releases vs. GitHub "Packages"
- GitHub's **Packages** tab only hosts Docker containers and code libraries (Maven `.jar`/`.aar`, npm, NuGet) — Android phones cannot directly download and install `.apk` installers from GitHub Packages.
- **GitHub Releases** (right next to/above Packages in the right-hand sidebar of your GitHub repository) is the standard place for final downloadable `.apk` files.
- Every automated build also saves the `.apk` under **Actions → Artifacts** for 90 days.

### Step 1: Push the Project to GitHub
1. Use **Push to GitHub** in Google AI Studio (or **Export as ZIP** and upload the unzipped files to a new GitHub repository).
2. Notice `.github/workflows/build-apk.yml` is already included!
   - Even before setting up signing secrets, pushing to `main` will automatically run all unit tests and attach a debug preview APK under **Actions → Artifacts**.

### Step 2: One-Time Signing Setup for Final Release APKs
To make GitHub Actions produce a **Signed Release APK** (which matches `app/build.gradle.kts`'s `release` signing config using key alias `upload`):

1. **Where to run the keystore command**:
   - Open **Command Prompt / PowerShell** on Windows, **Terminal** on macOS/Linux, or the **Terminal** tab inside Android Studio. *(Do not run this on GitHub — run it once on your own computer so only you hold the private key file.)*
   - If you get `"keytool is not recognized"`, install JDK 17 first (for example on Windows PowerShell: `winget install EclipseAdoptium.Temurin.17.JDK`, or on macOS: `brew install openjdk@17`), then reopen your terminal.

2. **Generate your upload keystore once** (enter a password you will remember when prompted):
   ```bash
   keytool -genkeypair -v -keystore my-upload-key.jks -storetype PKCS12 -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```
   *(Keep `my-upload-key.jks` and your password in a safe private folder on your computer — never upload or commit `.jks` files to GitHub!)*

3. **Convert `my-upload-key.jks` to a Base64 text string**:
   - **Windows (PowerShell)**:
     ```powershell
     [Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks")) | Set-Clipboard
     ```
     *(This automatically copies the Base64 text to your clipboard.)*
   - **macOS**:
     ```bash
     base64 -i my-upload-key.jks | pbcopy
     ```
   - **Linux**:
     ```bash
     base64 -w 0 my-upload-key.jks
     ```

4. **Add 3 Secrets to Your GitHub Repository**:
   Go to **GitHub Repo → Settings → Secrets and variables → Actions → New repository secret** and add:
   - `RELEASE_KEYSTORE_BASE64`: Paste the Base64 string from step 3.
   - `STORE_PASSWORD`: Your keystore password.
   - `KEY_PASSWORD`: Your key password (usually the same password you typed above).

### Step 3: Trigger & Download Your Final APK
- **From GitHub Website (1 click)**:
  1. Go to **Actions → Build KirayaKhata APK → Run workflow**.
  2. Keep **Publish APK to GitHub Releases** checked and set your version tag (e.g., `v1.0.0`).
  3. Once finished (~3–4 minutes), open **Releases** on the right sidebar of your repository and tap `KirayaKhata-v1.0.0.apk` to download and install it on your phone.
- **From Git CLI (via version tags)**:
  ```bash
  git tag v1.0.0
  git push origin v1.0.0
  ```

---

## Option 3: Build Locally on Your Computer (Android Studio / Gradle)

If you export the ZIP and open it locally with Android SDK + Gradle `9.3.1`:
1. Set your signing environment variables:
   - `KEYSTORE_PATH=/path/to/my-upload-key.jks`
   - `STORE_PASSWORD=your_store_password`
   - `KEY_PASSWORD=your_key_password`
2. Run:
   ```bash
   gradle :app:assembleRelease
   ```
3. Your final signed APK will be generated at:
   `app/build/outputs/apk/release/app-release.apk`
