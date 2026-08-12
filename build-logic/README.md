# Addiyon build logic

This included build owns reusable Gradle conventions for the keyboard products and shared modules.
Versions come from `../gradle/libs.versions.toml`.

| Plugin | Purpose |
| --- | --- |
| `addiyon.android.application` | Android application plugin plus shared compile SDK, minimum SDK, target SDK, and Java compatibility |
| `addiyon.android.library` | Android library plugin plus shared compile SDK, minimum SDK, and Java compatibility |
| `addiyon.android.compose` | Compose compiler plugin and Compose build feature for Android applications or libraries |
| `addiyon.kotlin.jvm` | Kotlin/JVM plugin with the shared Java 11 toolchain |
| `addiyon.language-dictionaries` | One SQLite generation task per configured language plus the aggregate database metadata manifest |

Application IDs, namespaces, signing, versioning, release behavior, and
product dependencies remain in each application module.

The dictionary convention retains `generateDictionaryDbs` as the aggregate task. A module registers
each language through `LanguageDictionariesExtension`, including its word, optional morphology
lexeme, and n-gram inputs, output DB, normalization mode, and precomputed prefix length. Source
`.dat` files live outside `src/main/assets`;
the convention writes the database and manifest to `build/generated/dictionaryAssets`, which is the
only dictionary directory packaged into the app. Asset merge and lint tasks depend on the aggregate
task automatically.
