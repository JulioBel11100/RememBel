# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

RememBel is a single-module Android app (Kotlin + Jetpack Compose) that records audio continuously
in 15-minute chunks and lets the user recompose/retrieve any past time interval. See `README.md` for
the full feature/product description. Everything runs on-device: no network calls, no analytics, no
backend.

## Commands

All commands run from the repo root via the Gradle wrapper.

```bash
./gradlew assembleDebug              # build debug APK
./gradlew installDebug                # build and install on a connected device/emulator
./gradlew testDebugUnitTest           # run JVM unit tests (app/src/test)
./gradlew connectedAndroidTest        # run instrumented tests on a connected device (app/src/androidTest)
./gradlew lint                        # Android lint
```

To run a single test class/method with Gradle:

```bash
./gradlew testDebugUnitTest --tests "com.example.remembel.ExampleUnitTest"
```

There is only one module (`:app`); `settings.gradle.kts` includes nothing else.

### Known quirk: mismatched test package

`app/src/test` and `app/src/androidTest` still live under `com/example/remembel` (the old package
name before the rename to `com.juliobel11100.remembel` — see commit "Renombrar paquete..."). Main
source lives under `com/juliobel11100/remembel`. Only trivial example tests exist currently. If you
add real tests, put them under `com.juliobel11100.remembel` and consider migrating/removing the
stale `com.example.remembel` example test files rather than adding to them.

## Architecture

### Recording engine (the core of the app)

- **`RecordingService`** — a foreground service (`foregroundServiceType="microphone"`) that owns the
  actual `MediaRecorder`. It records in wall-clock-aligned 15-minute chunks: on each cut it stops the
  current `MediaRecorder`, immediately starts a new one, and schedules the next cut via a `Handler`
  postDelayed to the next quarter-hour boundary (not a fixed 15-min timer from start). Chunk files are
  named `yyyy-MM-dd_HH-mm.m4a` and live in `getExternalFilesDir(null)/grabaciones`. Chunks older than
  7 days are deleted opportunistically whenever recording starts/cuts (`limpiarGrabacionesAntiguas`).
  The service is driven by `Intent` actions (`ACCION_HORARIO_INICIAR/DETENER/STANDBY`, or no action for
  the plain "constant" mode) rather than by binding.
- **`RecordingService.estaGrabando`** (companion `StateFlow<Boolean>`) is the single source of truth
  for "is it currently recording." UI (`MainActivity`), the Quick Settings tile
  (`GrabacionTileService`), and anything else that needs to reflect recording state all collect this
  flow instead of tracking their own state — keep it that way when adding new surfaces.
- **`AudioRecuperador.kt`** (`recuperarIntervalo`) — the retrieval engine. It does NOT assume a
  perfect 15-minute grid: it lists the real chunk files, reads each one's *actual* duration via
  `MediaExtractor` (chunks can be short if recording was just stopped), figures out which chunks
  overlap the requested `[inicioMs, finMs)` window, and stitches the trimmed pieces into one output
  file with `MediaMuxer`. Output goes to `cacheDir/recuperado_temporal.m4a` (single temp file, reused/
  overwritten each retrieval — not safe for concurrent retrievals).
- **`ConfiguracionGrabacion`** — plain `SharedPreferences` wrapper (no DataStore/Room). Holds the
  recording mode (`ModoGrabacion`: `CONSTANTE`, `HORARIO_FIJO`, `DURACION_LIMITADA`), fixed-schedule
  start/end times (stored as minutes-of-day), limited-duration minutes, whether recording was active
  before the last stop (used to resume after reboot), and whether the onboarding guide has been seen.

### Scheduling for non-constant modes

- **`AlarmScheduler`** — sole owner of `AlarmManager` alarms. Schedules exact alarms when allowed
  (falls back to inexact `setAndAllowWhileIdle` if `canScheduleExactAlarms()` is false on API 31+,
  rather than crashing). Used for `HORARIO_FIJO` (daily start/stop alarms, rescheduled each time they
  fire so they recur daily) and `DURACION_LIMITADA` (one stop alarm N minutes out).
- **`GrabacionReceiver`** — receives the alarm broadcasts and forwards them to `RecordingService` via
  `startService` with the corresponding `ACCION_HORARIO_*` action. Also reschedules the next day's
  fixed-schedule alarms here.
- **`ArranqueAutomaticoReceiver`** — `BOOT_COMPLETED` receiver. Does **not** start `RecordingService`
  directly: Android 15+ blocks starting a `microphone`-type foreground service while a `BOOT_COMPLETED`
  broadcast is being processed, even through an Activity trampoline. Instead, for `CONSTANTE` (only if
  `ConfiguracionGrabacion.leerEstabaActivo` was true) and `HORARIO_FIJO` (after re-arming the daily
  alarms) it posts a notification the user must tap to actually resume recording — tapping a
  notification is a genuine user gesture and is exempt from the restriction. Deliberately does *not*
  offer to resume `DURACION_LIMITADA` (a countdown resuming silently after reboot would be surprising,
  so the user has to re-trigger it).
- **`GrabacionTrampolinActivity`** — an invisible, no-history activity that exists purely because
  Android 14+ requires a visible activity in the call stack to start a microphone-type foreground
  service from a background context (alarm receiver, tile). It's used both by the Quick Settings tile
  (no explicit action → toggles based on `estaGrabando.value`) and could be reused by alarms if needed.
- **`GrabacionTileService`** — Quick Settings tile; on Android 14+ it routes through
  `GrabacionTrampolinActivity` via `startActivityAndCollapse`, on older versions it starts/stops the
  service directly.

### UI

- Single-Activity, all Compose, no Navigation library — `MainActivity` swaps between an in-memory
  `Pantalla` enum (`PRINCIPAL`, `BIBLIOTECA`, `RECUPERAR`) using `AnimatedContent` for the transition.
  No ViewModel layer; screen state is local `remember`/`rememberSaveable` state plus direct reads from
  `ConfiguracionGrabacion` and `RecordingService.estaGrabando`.
- **`PantallaPrincipal`** (in `MainActivity.kt`) — main screen: start/stop, mode selection, and
  playback of whatever was just recovered (`MediaPlayer`, speed 0.75x–2x, ±10s skip, waveform via
  `FormaDeOnda.kt`), save-to-library dialog. Two buttons ("Recuperar audio" / "Biblioteca") navigate
  to the other screens. Shows `PantallaGuia` (onboarding) on first launch, gated by
  `ConfiguracionGrabacion.leerGuiaVista`.
- **`PantallaRecuperar`** — combines custom-interval retrieval (day + start/end time pickers, calls
  `AudioRecuperador.recuperarIntervalo` indirectly by building a `PistaDisponible` and handing it back
  to `PantallaPrincipal` via `onSeleccionarPista`) with the "Pendientes de guardar" list
  (`listarPistasDisponibles` — whole raw sessions not yet saved to the library, shown with days left
  before the 7-day auto-delete). Both paths reuse the same recovery/playback flow in `PantallaPrincipal`.
- **`PantallaBiblioteca`** — file-manager screen for saved audio (`getFilesDir()`-based library
  folder, separate from the raw rolling `grabaciones` chunks); create/rename/move/delete folders and
  files, share via `FileProvider` (`res/xml/file_paths.xml`), plus its own playback controls with
  waveform. Re-reads the directory by bumping a `refrescar` counter used as a `remember` key, rather
  than any reactive file-watching.
- `window.setFlags(FLAG_SECURE, FLAG_SECURE)` in `MainActivity.onCreate` blocks screenshots/screen
  recording of the app — required by the privacy-by-design goal; don't remove without checking
  `privacidad.md`.

### Storage model

Two separate directories, don't conflate them:
- `getExternalFilesDir(null)/grabaciones` — raw rolling chunks, app-private but on external storage,
  named by timestamp, auto-deleted after 7 days. Never shown directly to the user.
- Library folder (see `obtenerCarpetaBiblioteca` in `PantallaBiblioteca.kt`) — user-curated, permanent,
  user-named files/folders the user explicitly saved via "Guardar en biblioteca".

## Build configuration notes

- Kotlin 2.2.10, AGP 9.2.1, Gradle 9.4.1, Compose BOM 2026.02.01 (see `gradle/libs.versions.toml`).
- `compileSdk`/`targetSdk` 36, `minSdk` 26.
- `applicationId` / package namespace: `com.juliobel11100.remembel`.
- Release build has `optimization { enable = false }` in `app/build.gradle.kts` — R8/minification is
  currently off for release builds.

## Language convention

Identifiers (classes, functions, variables) and UI strings are in Spanish throughout the codebase
(`RecordingService` is the one notable exception). Match this when adding new code — don't mix in
English identifiers for new Kotlin symbols in this module.
