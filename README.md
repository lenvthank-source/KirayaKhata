# KirayaKhata

**Offline-first Android ledger for landlords and property owners** — track monthly rent, sub-meter electricity readings, main government meter reconciliation, maintenance, extra charges, and carry-forward balances with zero subscriptions or payment-gateway clutter.

![Platform](https://img.shields.io/badge/Platform-Android%20(API%2024%2B)-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20M3-4285F4?logo=jetpackcompose&logoColor=white)
![Build](https://img.shields.io/badge/Gradle-9.3.1-02303A?logo=gradle&logoColor=white)

---

## Key Features

### Monthly Rent & Carry-Forward Ledger
- **Automatic Balance Carry-Forward**: Unpaid dues (`Due +`) and overpayments (`Advance -`) automatically roll forward from month to month.
- **Quick Month & Year Scroller**: Tap the month header to jump straight to any month and year.
- **Instant Filters**: Filter entries by `All`, `Due`, `Settled`, `Advance`, and `Unrecorded`.
- **Maintenance & Extra Charges**: Configure recurring default maintenance per tenant plus one-off monthly extra charges with custom notes.

### Sub-Meter, Direct Govt Meter & Reconciliation Charts
- **Per-Tenant Meter Modes**:
  - **Sub-Meter**: Calculate electricity charges via a fixed `₹/kWh` rate or split proportionally from the main government electricity bill.
  - **Direct Govt Meter**: Mark tenants who have their own government connection and pay their DISCOM bill directly (no sub-meter billing needed).
- **Main vs. Sub-Meter Reconciliation**: Compare the **Main Govt Meter** against the sum of all tenant sub-meters to track common area usage and line loss in `kWh`.
- **Visual Consumption Graphs**: Custom Material 3 charts showing per-tenant monthly `kWh` usage and a combined reconciliation comparison chart.

### Free Offline Bill OCR (+ Optional Personal API Key)
- **On-Device ML Kit OCR (100% Free & Offline)**: Scan electricity bills from your camera, gallery photos, or PDF documents (`PdfRenderer` + ML Kit Text Recognition) with no external API required.
- **Optional Gemini AI OCR**: Optionally save your own personal Gemini API key in **Settings** (stored locally on-device only) for enhanced bill parsing, with automatic offline ML Kit fallback.

### Single-House or Multi-House Management
- **Clean Single-House Mode (Default)**: Streamlined interface without extra property chips or section headers.
- **Multi-House Toggle in Settings**: Enable **Multi-House Management** anytime to organize tenants across multiple houses/buildings, track per-house main meters, and filter Ledger, Tenants, and Main Meter views by property.
- **Tenant Directory & Passbook**: Search tenants by name or room on the **Tenants** tab, view full historical passbooks, track vacated tenants, and inspect individual usage history.

### Indian DISCOM Quick Check
- Built-in directory of Indian electricity boards (Delhi, Maharashtra, UP, Bihar, Rajasthan, Haryana, South & East regions).
- One-tap consumer number copy and direct link to your DISCOM's official bill-view portal — no BBPS or third-party payment integrations.

### Backup, Restore & Themes
- **Phone Storage Export / Import**: Export a portable SQLite `.db` snapshot anywhere on your device and restore offline anytime (with automatic Room v1 → v2 schema migration).
- **Google Drive Backup (`appDataFolder`)**: Optional WhatsApp-style private backup to your own Google Drive account, with manual, daily, or weekly WorkManager schedules.
- **Theme Memory Map**: Choose between **Dark**, **Light (White)**, and **System Default** in Settings, paired with semantic color cues for dues, settled rent, electricity, and balances.

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language & UI** | Kotlin, Jetpack Compose, Material Design 3, Edge-to-Edge (`NavigationBar` + `NavigationRail`) |
| **Architecture** | MVVM with `ViewModel`, `StateFlow`, and Kotlin Coroutines |
| **Local Storage** | Room (SQLite + KSP) & Jetpack DataStore Preferences |
| **OCR & PDF** | Google ML Kit Text Recognition (`text-recognition`), Android `PdfRenderer`, OkHttp |
| **Backup & Sync** | WorkManager, Google Play Services Auth, Credential Manager, Drive REST `appDataFolder` |
| **Testing** | JUnit 4, Robolectric, Coroutines Test |

---

## Download APK

Pre-built Android `.apk` files are automatically built by GitHub Actions:
- **GitHub Releases**: Check the **Releases** section on the right sidebar for tagged `.apk` downloads ready to install on Android phones.
- **Actions Artifacts**: Every push to `main` attaches a tested `.apk` build artifact under the **Actions** tab.

---

## Building Locally

### Prerequisites
- **JDK 17**
- **Android SDK** (`compileSdk` 36, `minSdk` 24, `targetSdk` 36)
- **Gradle 9.3.1** (or Android Studio Ladybug / Meerkat+)

### Run Tests
```bash
gradle :app:testDebugUnitTest
```

### Build Debug APK
```bash
gradle :app:assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Build Signed Release APK
Set your upload keystore environment variables (matching `app/build.gradle.kts` with key alias `upload`):
```bash
export KEYSTORE_PATH=/path/to/my-upload-key.jks
export STORE_PASSWORD=your_store_password
export KEY_PASSWORD=your_key_password

gradle :app:assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

For full instructions on generating an upload keystore, setting up GitHub Actions secrets (`RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`), and auto-publishing APKs to **GitHub Releases**, see [`BUILD_AND_GITHUB_APK.md`](BUILD_AND_GITHUB_APK.md).

---

## Project Structure

```text
├── .github/workflows/build-apk.yml   # Automated test, APK build & GitHub Release workflow
├── BUILD_AND_GITHUB_APK.md           # Keystore signing & GitHub CI release guide
└── app/src/main/java/com/example/
    ├── MainActivity.kt               # Edge-to-edge activity entry point
    ├── model/                        # Domain models & enums
    ├── data/                         # Room entities, DAOs, migrations & repository
    ├── domain/                       # Billing math, reconciliation, charts & DISCOM catalog
    ├── ocr/                          # Offline ML Kit, PDF renderer & optional Gemini OCR
    ├── backup/                       # DataStore settings, .db export/import & Drive sync
    └── ui/                           # Compose screens, ViewModel, charts & M3 theme
```

---

## Privacy & Data Ownership

- **100% Local-First**: All tenant, rent, meter, and ledger records live privately in your device's Room SQLite database.
- **No Middleman Servers**: No analytics trackers, no ads, and no payment-gateway integrations.
- **User-Controlled Credentials**: Any optional Gemini API key or Google Drive backup connection is stored locally on your device and used strictly when you trigger a scan or backup.
