# CaproverForge
Android APK frontend for Caprover Captain admin panel, native android, gh actions

## Target Platform
Built against **Android 17 (API level 37)**: `compileSdk` and `targetSdk` are both 37, `minSdk` is 24 (Android 7.0).

Toolchain: Android Gradle Plugin 9.4.1, Gradle 9.6.0, JDK 17+, build-tools 37.0.0.

## Build Status
This project uses GitHub Actions to automatically build Android APK files.

### Download APKs
APKs are automatically built and published as artifacts when changes are pushed to the repository. You can download them from the [Actions tab](../../actions) after workflow completion.

### Building Locally
Requires JDK 17+ and an Android SDK with `platforms;android-37.0` and `build-tools;37.0.0` installed (point `sdk.dir` in `local.properties` or `ANDROID_HOME` at it).

```bash
./gradlew assembleDebug    # Build debug APK
./gradlew assembleRelease  # Build release APK (unsigned)
```

Note: Release APKs are currently unsigned and intended for development/testing purposes only.
