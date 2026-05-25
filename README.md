# tasks

**Sideload-only Android app for editing a hyperlist `todo.hl` synced from your laptop.**

Phone-side companion to [scribe](https://github.com/isene/scribe),
[kastrup](https://github.com/isene/kastrup)'s `z` triage, and any other
tool in the [Fe₂O₃ suite](https://github.com/isene/fe2o3) that reads or
writes `~/.tasks/todo.hl`. Pairs with [Syncthing](https://syncthing.net)
for the actual sync — this app is purely the on-device UI.

## What it does

Reads a hyperlist file from anywhere on the phone via Android's
Storage Access Framework (no file-system permissions needed) and lets
you:

- Browse categories and items in a single screen
- Tap a category to collapse / expand
- `+` per category to add an item; `+` FAB to add a category
- Long-press / kebab-menu on item or category for edit / delete

Saves are atomic-ish (single contiguous SAF `wt` write) so a mid-edit
Syncthing scan never ships a half-written file.

## Why it exists

The full `todo.hl` format is rich; this app only handles what
kastrup and scribe actually produce: tab-indented two-level
hierarchy. One leading tab = category. Two leading tabs = item.
Anything else round-trips unchanged.

## Setup

1. **Laptop side**: install Syncthing (`apt install syncthing`),
   enable the user service (`systemctl --user enable --now
   syncthing.service`), and add `~/.tasks/` as a shared folder.
2. **Phone side**: install Syncthing from F-Droid or Play Store,
   pair with the laptop, accept the shared `tasks` folder, choose a
   local path (e.g. `Documents/tasks/`).
3. **Install this app** (sideload the APK).
4. **First launch**: tap "Pick file", navigate to wherever Syncthing
   put `todo.hl`, select it. The persisted SAF permission survives
   reboots; you only do this once.

## Build

JDK 17+ and the Android SDK with platforms;android-35 and
build-tools;35.0.0:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug
```

APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Sideload
via `adb install` or transfer + tap.

For a signed release build (recommended for actual phone use so the
APK keeps its install identity across rebuilds), generate a keystore
once:

```bash
keytool -genkeypair -keystore ~/.android/tasks-release.jks \
    -alias tasks -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass <pw> -keypass <pw> \
    -dname "CN=tasks, OU=isene, O=isene, L=, ST=, C=NO"
```

Then drop a `key.properties` next to `local.properties`:

```
storeFile=/path/to/tasks-release.jks
storePassword=<pw>
keyAlias=tasks
keyPassword=<pw>
```

…and a `signingConfigs` block in `app/build.gradle.kts` (left out
of the scaffold to keep it credentialless).

## License

Unlicense — public domain.
