# Persona Music Player

A Persona 5–styled Android music player for your **local music** and **YouTube Music** — with **no ads**.

## Features

- **Local library** – scans all music on the device (MediaStore): songs, albums, favourites, shuffle.
- **YouTube Music** – search the YouTube Music song catalogue and stream it directly.
- **Ad-free, ReVanced-style** – audio streams are extracted with [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) and played by ExoPlayer, so the official app's ad pipeline is never involved.
  - Background & lock-screen playback (no Premium needed).
  - **Skip non-music parts** via SponsorBlock (`music_offtopic`), like ReVanced.
  - Audio quality choice (best / data saver).
- **Persona UI** – ransom-note titles, star-pattern backgrounds, slanted cards, the red halftone playlist board, the star pause menu (Playlist / Stats / Customize / Share).
- **Home** – Recent, Most Played, Song Log and a 3×3 Speed Dial built from your listening history.
- **Playlists** – create, rename, delete; Favourites built in.
- **Stats** – plays, hours listened, top artists and top songs.
- **Themes** – Persona 5 red, Royal crimson, Persona 4 yellow, Persona 3 blue.

## Tech

Kotlin · Jetpack Compose · Media3 (ExoPlayer + MediaSession) · Room · Coil · OkHttp · NewPipeExtractor.
Min SDK 26 (Android 8), target SDK 35.

## Install

Every push builds an APK via GitHub Actions (**Actions → Build APK → Artifacts → `persona-music-player-apk`**).
Unzip and install `app-release.apk` (or `app-debug.apk`) on your phone.

> CI signs the release APK with a throwaway debug key, so when updating you may have to uninstall the previous version first.

Build locally with Android Studio or:

```sh
./gradlew assembleDebug
```

## Notes

Streaming from YouTube without the official client is against YouTube's Terms of Service; this project is meant for personal use.
If YouTube playback suddenly breaks, YouTube changed something — bump `newpipe` in `gradle/libs.versions.toml` to the newest NewPipeExtractor release.

Persona is a trademark of ATLUS/SEGA. This is an unofficial fan project and contains no game assets.
