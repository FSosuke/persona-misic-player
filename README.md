# Persona Music Player

A Persona 5–styled Android music player for your **local music** and **YouTube Music** — with **no ads**.

## Features

- **Local library** – scans all music on the device (MediaStore): songs, albums, favourites, shuffle.
- **YouTube Music** – search the YouTube Music song catalogue and stream it directly.
- **Ad-free, ReVanced-style** – audio streams are extracted with [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) and played by ExoPlayer, so the official app's ad pipeline is never involved.
  - Background & lock-screen playback (no Premium needed).
  - **Skip non-music parts** via SponsorBlock (`music_offtopic`), like ReVanced.
  - Audio quality choice (best / data saver).
- **YouTube Music account (optional)** – sign in (Customize → Sign in) to get your personal recommendations ("Quick picks", mixes, …) on Home and your YT Music playlists, albums and Liked Music on the playlist board. The login cookie is stored encrypted with the Android Keystore and only sent to music.youtube.com. Playback still goes through NewPipe, so still no ads.
- **Sync with YouTube Music** – hearts become "Liked music", add songs to (or create) YT Music playlists, and plays land in your YouTube history so recommendations keep learning (each can be switched off).
- **Better search** – live suggestions and search history while typing; results split into top result, songs, artists, albums and playlists, each with a full list tab, plus library matches.
- **Downloads** – save YouTube songs or whole YT Music playlists as tagged M4A files into a folder you choose (or create); they play offline, show up in a *Downloads* card on the playlist board and in any other music app.
- **Radio & autoplay** – "Start radio" on any song (local songs are matched on YouTube), and when the queue runs out it keeps going with similar songs.
- **Calendar** – Persona-style calendar: a month heat map of your listening and each day's log split into Early Morning, Daytime, After School, Evening and Late Night.
- **Wrapped** – monthly and yearly recap as a story slideshow: minutes listened, top confidant with arcana, top songs and artists, habits (favourite time, busiest day, streak, new artists) and a calling card to share.
- **Japan tab on Home** – YouTube Music charts for Japan (ranked top songs, trending, top artists) and Japanese new releases (albums, singles, music videos).
- **Your taste, your rules** – "Not interested" / "Hide artist" in every song menu (also sent to YouTube Music when possible) and a *Japanese music only* switch that filters recommendations, radio and autoplay; hidden items can be restored in Customize.
- **Confidants** – every artist you listen to becomes a confidant with a Major Arcana; ranks 1–MAX grow with your plays, with a spinning "RANK UP!" card when they level up and a confidant overview.
- **Sleep timer "Take your time"** – stop after 15–90 min (with fade-out) or at the end of the song.
- **Persona UI** – ransom-note titles in mixed display fonts, star-pattern backgrounds, slanted cards, the red halftone playlist board, the star pause menu (Playlist / Stats / Customize / Share).
  - Player with red slashes, sparkles, "CHANGE" graffiti, torn-paper control band, grinning-note seek bar and chunky P5 icons.
  - **"NEXT" queue** – cut-out cards on a red board, drag to reorder, swipe to remove.
  - **Lyrics** (synced where available, via LRCLIB) on a calling card held by white gloves.
  - **Per-song stats** – times played, real listening time, average per play.
  - **Your own backgrounds** – pick any image for the player/home; it gets inked and tinted P5-style.
- **Home** – Recent, Most Played, Song Log and a 3×3 Speed Dial built from your listening history.
- **Playlists** – create, rename, delete; Favourites built in.
- **Stats** – plays, hours listened, top artists and top songs.
- **Themes** – Persona 5 red, Royal crimson, Persona 4 yellow, Persona 3 blue.

## Tech

Kotlin · Jetpack Compose · Media3 (ExoPlayer + MediaSession) · Room · Coil · OkHttp · NewPipeExtractor.
Min SDK 26 (Android 8), target SDK 35.

## Install & updates

Grab the APK from the **[latest release](../../releases/latest)** and install it. From then on the app
checks GitHub for new releases itself and shows a **"New mission"** card on Home – tap *Update*, then
*Install*. Updates install over the old version, so your library, playlists and login stay.

Each push that bumps `versionName` in `app/build.gradle.kts` publishes a new release automatically
(signed with the release key from the `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` repository secrets).

**Backup:** Customize → *Updates & backup* → Export / Import saves favourites, playlists, history and
theme to a JSON file.

Build locally with Android Studio or:

```sh
./gradlew assembleRelease   # signed with the debug key unless SIGNING_KEYSTORE_PATH is set
```

## Notes

Streaming from YouTube without the official client is against YouTube's Terms of Service; this project is meant for personal use.
If YouTube playback suddenly breaks, YouTube changed something — bump `newpipe` in `gradle/libs.versions.toml` to the newest NewPipeExtractor release.

Persona is a trademark of ATLUS/SEGA. This is an unofficial fan project and contains no game assets.
