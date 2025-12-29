# CaproverForge
Android APK frontend for Caprover Captain admin panel, native android, gh actions

## Build Status
This project uses GitHub Actions to automatically build Android APK files.

### Download APKs
APKs are automatically built and published as artifacts when changes are pushed to the repository. You can download them from the [Actions tab](../../actions) after workflow completion.

### Building Locally
```bash
./gradlew assembleDebug    # Build debug APK
./gradlew assembleRelease  # Build release APK (unsigned)
```

Note: Release APKs are currently unsigned and intended for development/testing purposes only.
