# Building CLIFrontend

## 1. Prerequisites
- **JDK**: OpenJDK 17 or higher
- **Android SDK**: API Level 34 Platform (`platforms;android-34`)
- **Build Tools**: Android SDK Build-Tools 34.0.0 (`aapt`, `zipalign`, `apksigner`, `d8`)
- **Kotlin**: Kotlin compiler 1.9.24 or Gradle 8.5+

## 2. Automated Build
To compile the release APK directly from the environment:
```bash
/downloads/clifrontend/scripts/build.sh
```
This script:
1. Compiles Android resources using `aapt`
2. Generates the `R.java` symbol tables
3. Compiles Kotlin/Java source code with target JVM 17
4. Compiles bytecode to Android Dalvik bytecode (`classes.dex`) via `d8`
5. Packages resources and DEX into an unaligned APK
6. Aligns the APK using `zipalign -v 4`
7. Signs the release APK using `apksigner` with release key
8. Outputs the final APK to:
   - `/downloads/clifrontend/build/outputs/apk/release/CLIFrontend-release.apk`
   - `/downloads/clifrontend/CLIFrontend.apk`

## 3. Verification
Verify APK signature and alignment:
```bash
apksigner verify --verbose /downloads/clifrontend/CLIFrontend.apk
zipalign -c -v 4 /downloads/clifrontend/CLIFrontend.apk
```
