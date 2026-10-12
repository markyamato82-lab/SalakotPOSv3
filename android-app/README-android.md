# SalakotPOS Android app

Wraps SalakotPOS in an Android app (Capacitor) and adds **native printing**:

- **Network printers (port 9100):** almost any brand (Xprinter, Rongta, Gprinter, Epson, etc.).
- **Bluetooth printers:** paired in Android Settings.
- **Cash drawer:** kicks open after a cash payment when the drawer is wired to the receipt printer.
- **Find printers:** scans the Wi-Fi for the printer port.
- **Exports:** Excel/CSV go to Android's share sheet (Drive, Gmail, Files).

The app still loads the latest SalakotPOS from the database every time it opens, so day-to-day
updates (menu, screens, features) need no new APK. Only changes to native printing need a rebuild.

## How the APK gets built

GitHub builds it automatically (see `.github/workflows/android.yml`). Each build appears on the
repo's **Releases** page as `SalakotPOS-1.0.N.apk`.

### One-time: add the signing key (so updates install over the previous app)

In the repo: **Settings -> Secrets and variables -> Actions -> New repository secret**, add four:

| Secret name | Value |
| --- | --- |
| `SALAKOT_KEYSTORE_BASE64` | (provided separately) |
| `SALAKOT_KEYSTORE_PASSWORD` | (provided separately) |
| `SALAKOT_KEY_ALIAS` | `salakot` |
| `SALAKOT_KEY_PASSWORD` | (same as the keystore password) |

Without these the workflow still builds a *debug* APK you can install, but a later signed build
won't install over it (you'd uninstall first). Keep the key safe: it's the only way to ship updates
that replace the installed app.

## Installing on a tablet

1. On the tablet, open the repo's Releases page and download the `.apk`.
2. Tap it. If Android warns, allow installing from this source, then Install.
3. Open SalakotPOS and sign in with your PIN.
4. First time per tablet: when you print, allow the Nearby devices / local network prompts.

## Building locally (optional)

Needs Node 22 and JDK 21.

```sh
npm ci
npx cap sync android
cd android && ./gradlew assembleDebug
# APK: android/app/build/outputs/apk/debug/app-debug.apk
```
