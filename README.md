# Paper2Test-app

Android app for [paper2test.app](https://paper2test.app) (Kotlin, Jetpack Compose, minSdk 24).

- Google sign-in (Credential Manager) → same account as the website
- Join a test by code, link or **QR** (Play services code scanner); shared links `https://paper2test.app/t/CODE` open in the app
- **Scan a paper** with the camera (ML Kit document scanner) → pages upload to the server's background pipeline → share code/link immediately
- Owned bundles with per-test progress; purchases happen on the website (Play policy)
- The exam room, paper review, results, store and settings are the website's own screens shown in a WebView with the session handed over — one implementation, identical behaviour

## Build

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Google Sign-In needs an **Android OAuth client** in the same Google Cloud project as the web client:
package `app.paper2test` + the SHA-1 of the signing key (debug key for local builds, upload key for Play).
The API accepts ID tokens for the web client id configured in `app/build.gradle.kts`.
