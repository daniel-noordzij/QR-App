# QR App

A small Android app that scans QR codes and barcodes, or reads printed text (OCR), and lets you
copy the text.

- Scans every barcode format ML Kit supports, using the on-device (bundled) model.
- When it finds a code, the camera freezes and shows the code closest to the centre of the frame.
  You can select the text, or tap **Copy**, **Share**, **Open** (http/https links only), or **Scan again**.
- **Read text**: freezes the current camera frame and reads the text in it (on-device OCR, Latin
  script), with the same Copy, Share, Open and Scan again options. Open uses the first http/https
  link in the text.
- Flashlight toggle.
- Scan from an image with the system photo picker, or share an image to QR App from another app.
- Follows the system light/dark theme.

## Privacy

- No history. A scan exists only in memory and is never written to disk.
- No network access: the `INTERNET` and `ACCESS_NETWORK_STATE` permissions are stripped from the
  manifest, so nothing (including ML Kit usage telemetry) can leave the device.
- Backups and device-to-device transfer are disabled.
- Copied text is marked as sensitive, so the clipboard preview and keyboards hide it.

## Download

Get the APK from the [latest release](https://github.com/daniel-noordzij/QR-App/releases/latest).
Requires Android 7.0 (API 24) or newer on an ARM device (practically every phone and tablet;
x86 emulators and Intel devices aren't supported).

## Releases

Every push to `main` runs `.github/workflows/release.yml`. It builds a signed release APK
(versionName `1.<run number>`, versionCode `<run number>`) and publishes it as a new GitHub
Release. The signing key is read from these repository secrets:
`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

## Building locally

```bash
./gradlew assembleRelease
```

Without the signing environment variables, the release APK is built unsigned.
