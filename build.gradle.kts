plugins {
    id("com.android.application") version "8.9.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    // Layout snapshots on the JVM (no emulator): see app/src/test/.../ui/LayoutSnapshotTest.kt
    id("app.cash.paparazzi") version "1.3.5" apply false
}
