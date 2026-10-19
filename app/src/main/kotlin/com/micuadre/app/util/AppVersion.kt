package com.micuadre.app.util

/**
 * Semantic version (MAJOR.MINOR.PATCH) with the same `versionCode` derivation the Gradle build uses
 * (see `app/build.gradle.kts`, which reads `version.properties`). Keep both in sync; a unit test
 * checks that BuildConfig agrees with this class.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) {

    val code: Int get() = major * 10_000 + minor * 100 + patch

    companion object {
        private val PATTERN = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""")

        /** Parses `MAJOR.MINOR.PATCH` (minor and patch 0..99); returns null when malformed. */
        fun parse(name: String): AppVersion? {
            val (major, minor, patch) = PATTERN.matchEntire(name)?.destructured ?: return null
            val parts = listOf(major, minor, patch).map { it.toIntOrNull() ?: return null }
            if (parts[1] > 99 || parts[2] > 99) return null
            return AppVersion(parts[0], parts[1], parts[2])
        }

        /** Text shown in Settings: the plain name for release, "name (buildType)" otherwise. */
        fun label(versionName: String, buildType: String): String =
            if (buildType == "release") versionName else "$versionName ($buildType)"
    }
}
