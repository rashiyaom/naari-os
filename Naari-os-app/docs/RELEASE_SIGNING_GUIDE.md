# Naari Companion: Production Release Signing Guide

This guide details how to generate release signing keys, configure Gradle without hardcoding credentials in source code, and assemble production-signed release APKs/AABs.

---

## 1. Security Principles
- **NEVER** commit keystores (`.jks`, `.keystore`) or passwords to git or version control.
- Use environment variables or a local, uncommitted `keystore.properties` file ignored by git.
- Back up your production keystore securely in an encrypted secrets vault.

---

## 2. Generating a Production Release Keystore

Generate a 2048-bit RSA keystore using Java's `keytool`:

```bash
keytool -genkeypair -v \
  -keystore naari-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias naari-key
```

You will be prompted for:
- Keystore password
- Organizational details (Name, Organization, City, Country)
- Key password (or press enter to match keystore password)

---

## 3. Configuring Gradle (`build.gradle.kts`)

Create an untracked `keystore.properties` file in the project root:

```properties
# keystore.properties (DO NOT COMMIT TO GIT)
KEYSTORE_FILE=/path/to/naari-release.jks
KEYSTORE_PASSWORD=YourKeystorePassword
KEY_ALIAS=naari-key
KEY_PASSWORD=YourKeyPassword
```

In `app/build.gradle.kts`:

```kotlin
import java.util.Properties
import java.io.FileInputStream

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("KEYSTORE_FILE"))
                storePassword = keystoreProperties.getProperty("KEYSTORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("KEY_PASSWORD")
            } else {
                // Fallback to environment variables (for CI/CD pipelines)
                storeFile = System.getenv("KEYSTORE_FILE")?.let { file(it) }
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

---

## 4. Building the Production Signed APK

Run:

```bash
./gradlew assembleRelease
```

The output will be placed in:
```
app/build/outputs/apk/release/app-release.apk
```

---

## 5. Verifying Signature and Alignment

Verify that the APK is aligned and signed with APK Signature Scheme v2/v3 using `apksigner`:

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

Expected output:
```
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
```

Calculate the release checksum:
```bash
sha256sum app/build/outputs/apk/release/app-release.apk
```
