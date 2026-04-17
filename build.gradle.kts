// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Плагин Android Gradle
        classpath("com.android.tools.build:gradle:8.13.2")

        // Плагин Kotlin
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")

        // Плагин Google Services
        classpath("com.google.gms:google-services:4.4.2")
    }
}