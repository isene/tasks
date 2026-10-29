# tasks

> **Archived.** The tasks app is now the Tasks tab of
> [gluon](https://github.com/isene/nomad/tree/master/apps/gluon), the
> phone app for mail, calendar and tasks.

<img src="img/tasks.svg" align="right" width="150">

**Sideload-only Android app for editing a hyperlist `todo.hl` synced from your laptop.**

![Kotlin](https://img.shields.io/badge/language-Kotlin-7f52ff)
![Min SDK](https://img.shields.io/badge/minSdk-33%20(Android%2013)-3ddc84)
![License](https://img.shields.io/badge/license-Unlicense-green)
![Build](https://img.shields.io/badge/build-gradle%208.10-02303a)

Phone-side companion to [scribe](https://github.com/isene/scribe),
[kastrup](https://github.com/isene/kastrup)'s `z` triage, and any
other tool in the [Fe₂O₃ suite](https://github.com/isene/fe2o3) that
reads or writes `~/.tasks/todo.hl`. Pairs with
[Syncthing](https://syncthing.net) for sync — this app is purely the
on-device UI.

## What it does

Reads a [hyperlist](https://github.com/isene/hyperlist) file from
anywhere on the phone via Android's Storage Access Framework (no
filesystem permissions, no `/sdcard` rummaging) and lets you:

- Browse categories with collapse / expand
- Tap `+` on a category to add an item
- FAB to add a new category
- Long-press / kebab menu on item or category to edit or delete

Saves are atomic-ish (single contiguous SAF `wt` write) so a
mid-edit Syncthing scan never ships a half file. Reload-on-resume +
mtime-based change detection keeps the view fresh after Syncthing
pushes laptop edits down.

## Why it exists

The full hyperlist format is rich; this app handles the subset that
kastrup and scribe actually produce — tab-indented, two levels:

```
	Personal
		Renew passport before September
		Call the dentist
	Work
		Review the Q3 plan
```

Anything deeper round-trips unchanged.

## Setup

### 1. Laptop side (Syncthing)

```bash
sudo apt install syncthing                       # or your distro's equivalent
systemctl --user enable --now syncthing.service
```

Open the GUI at http://localhost:8080 (or whichever port the config
chose), set a password, and add `~/.tasks/` as a shared folder
(label: `tasks`, type: send-receive). Note the device ID under
**Actions → Show ID**.

### 2. Phone side (Syncthing-Fork)

Install [Syncthing-Fork](https://f-droid.org/packages/com.github.catfriend1.syncthingandroid/)
from F-Droid (the original "Syncthing" app on Play Store is
unmaintained). Open it, let it generate a device ID, then:

- Paste the laptop's device ID into the phone, or vice-versa
- Accept the connection notification on both sides
- Accept the `tasks` folder share when it appears on the phone;
  pick a local path like `Documents/tasks/`

For cellular sync, in **Settings → Run conditions**: enable
"Run on Wi-Fi", "Run on metered networks", and "Run on cellular".

### 3. Install the APK

Drop a signed `tasks-vX.Y.Z.apk` into your synced `~/.tasks/`
folder; on the phone, open any file manager → tap the APK →
**Install**.

### 4. First launch

Tap **Pick file**, navigate to wherever Syncthing put `todo.hl`,
select it. The persisted SAF permission survives reboots — you only
do this once.

## Build

JDK 17+ and the Android SDK with `platforms;android-35` and
`build-tools;35.0.0` installed (via `sdkmanager` from
cmdline-tools):

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew assembleDebug    # ad-hoc, debug-signed
JAVA_HOME=/path/to/jdk-21 ./gradlew assembleRelease  # signed, see below
```

APK lands in `app/build/outputs/apk/{debug,release}/`. Transfer via
`adb install` or your sync folder.

### Signing release builds

Generate a keystore once:

```bash
keytool -genkeypair -keystore ~/.android/tasks-release.jks \
    -alias tasks -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass <pw> -keypass <pw> \
    -dname "CN=tasks, OU=isene, O=isene, L=, ST=, C=NO"
```

Drop a `key.properties` next to `settings.gradle.kts` (gitignored):

```
storeFile=/path/to/tasks-release.jks
storePassword=<pw>
keyAlias=tasks
keyPassword=<pw>
```

`./gradlew assembleRelease` will pick it up automatically. Without
the file, release builds fall back to the debug signer.

## Architecture

| Layer | What it does |
|---|---|
| `data/Hyperlist.kt` | Tab-indented parser + serializer; pure Kotlin, no Android deps |
| `data/TaskRepository.kt` | SAF I/O via `ContentResolver`; one contiguous `wt` write per save |
| `viewmodel/TasksViewModel.kt` | Holds `UiState`, persists URI + collapsed-set in `SharedPreferences`, mtime-based external-change detection on resume |
| `ui/TasksScreen.kt` | Single-screen `LazyColumn` + Compose dialogs for add/edit/delete |
| `ui/theme/Theme.kt` | Material 3, dynamic colour on Android 12+ |

No background service, no work-manager, no telemetry. Foreground
activity only.

## Part of the Fe₂O₃ suite

| Tool | Role |
|---|---|
| [kastrup](https://github.com/isene/kastrup) | Messaging hub; `z` triage drops todos here |
| [scribe](https://github.com/isene/scribe) | TUI editor for `~/.tasks/todo.hl` on the laptop |
| [hyperlist](https://github.com/isene/hyperlist) | Vim plugin / format spec |
| [tock](https://github.com/isene/tock) | Calendar TUI, peer of triage flow |

## License

[Unlicense](https://unlicense.org/) — public domain.

## Credits

Built with extensive pair-programming with Claude Code.
