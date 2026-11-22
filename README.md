<div align="center">

# FSB for Android

**Check any X (Twitter) account for shadowbans, right from your phone. Built with Material 3 Expressive.**
The official Android app for [FSB Sentinel](https://visiuun.com/fsb/).

[![Latest release](https://img.shields.io/github/v/release/visiuun/fsb-android?style=flat-square&label=download&color=1d9bf0)](https://github.com/visiuun/fsb-android/releases/latest)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white)](#compatibility)
[![License: CC BY-NC 4.0](https://img.shields.io/badge/license-CC%20BY--NC%204.0-lightgrey?style=flat-square)](LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/visiuun/fsb-android/build.yml?style=flat-square)](https://github.com/visiuun/fsb-android/actions)

### [⬇ Download the latest APK](https://github.com/visiuun/fsb-android/releases/latest)

</div>

---

## Features

- **Live shadowban tests** for any account: search ban, search suggestion ban, ghost ban, reply deboosting and the sensitive profile flag, with a visibility score and the full **check history** as a chart. Tests that couldn't finish are shown as *Unknown*, never as clean
- **Explore** the global board of every checked profile. Filter by clean, shadowbanned, sensitive or unknown, and sort by most recent, views, favorites or followers. Or tap **Random**
- **Search** by handle, display name, profile link or numeric X ID
- **Posts and media**: recent posts, reposts and quoted posts in a feed or a gallery grid. Photos open full screen with pinch to zoom, and videos play inline
- **Download media** in original quality to `Downloads/FSB`, one file or a whole profile at once, named `[artist] - filename_twitter.ext`
- **Timeline insights**: average likes, reposts, replies and views, engagement rate, link exposure and media richness, plus the best and worst posts
- **Sign in with your FSB account** (Discord or X) to sync **favorites** (up to 6), **track accounts** with email alerts when their status changes, see your own X account's status, and get higher rate limits
- **Make it yours**: system, light, dark and **OLED black** themes, Material You colours, blur for sensitive media
- **Share links**: share an x.com / twitter.com profile or post to FSB, or open visiuun.com/fsb links, to jump straight to the check
- **Automatic updates** straight from this repository's releases

## How to use

### Install

1. Open the **[latest release](https://github.com/visiuun/fsb-android/releases/latest)** on your phone and download the `FSB-x.y.z.apk` file.
2. Open the downloaded file. If Android asks, allow your browser or file manager to **install unknown apps**.
3. Tap **Install**. If you already have an earlier version, it installs as an update and keeps your settings.

### Sign in

Signing in is optional. Everything except favorites and tracking works without an account.

1. Go to **Account → Sign in to FSB**. The app shows a short code and opens visiuun.com in your browser.
2. Sign in there with Discord or X, check that the code matches, and tap **Approve**.
3. The app finishes signing in by itself. It never sees your password.

Your sign-in is stored encrypted on the phone (Android Keystore) and can be revoked at any time from [visiuun.com/fsb/apps](https://visiuun.com/fsb/apps) or with **Sign out**.

### Updates

The app checks this repository's **Releases** when it starts (at most every 6 hours) and offers new versions in a dialog. Tap **Update** and Android will ask you to confirm the install. You can turn this off or check manually in *Account → Updates*. The first time, Android asks you to allow FSB to install apps.

## Privacy

- **No ads, analytics or trackers.**
- Network requests go only to:
  - [visiuun.com/fsb](https://visiuun.com/fsb/) through the documented [FSB API](https://visiuun.com/fsb/docs/) for checks, profiles, search and your account
  - Twitter's media CDNs (`pbs.twimg.com`, `video.twimg.com`) to show and download images and videos
  - [GitHub](https://github.com/visiuun/fsb-android/releases) to check for updates (can be turned off)

## Compatibility

| | |
|---|---|
| **Android** | 8.0 (API 26) and newer |
| **Devices** | Phones, tablets and foldables (layouts adapt to wide screens) |
| **APK** | One universal APK for every CPU type |
| **Google services** | Not needed |

## Build it yourself

You need a **JDK 17 or newer**. Everything else (Gradle, the Android Gradle Plugin, libraries and the Android SDK platform) is downloaded on the first build.

```bash
git clone https://github.com/visiuun/fsb-android.git
cd fsb-android
./gradlew assembleRelease        # Windows: gradlew.bat assembleRelease
```

The APK ends up in `app/build/outputs/apk/release/`. Or open the folder in **Android Studio** and press *Run*.

<details>
<summary><b>Signing your own release builds</b></summary>

Without a key, release builds are signed with the debug key, which is fine for trying things out. To sign with your own key, create `keystore.properties` in the project root (it is git-ignored):

```properties
storeFile=app/release.jks
storePassword=your-store-password
keyAlias=your-alias
keyPassword=your-key-password
```

Or set the environment variables `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`.

Note: Android only installs an update over an existing app if both are signed with the same key. Builds you sign yourself can't update the official release, and vice versa.

</details>

<details>
<summary><b>Project structure</b></summary>

```
app/src/main/java/dev/vis/fsb/
├── data/   FSB API v1 client & parsing, models, encrypted token store, downloader, updater
└── ui/     Jetpack Compose screens: explore, search, favorites, profile, media viewer, sign-in, account
    └── theme/   colour schemes, OLED black, status colours
```

Built with Kotlin, Jetpack Compose and Material 3 Expressive, plus [OkHttp](https://square.github.io/okhttp/), [Coil](https://coil-kt.github.io/coil/) and [Media3](https://developer.android.com/media/media3).

</details>

## Contributing

Bug reports, ideas and pull requests are welcome. Please [open an issue](https://github.com/visiuun/fsb-android/issues) first for larger changes.

## Credits

- Shadowban data by **[FSB Sentinel](https://visiuun.com/fsb/)**.
- Shapes, motion and colour from Google's **Material 3 Expressive**.
- Not affiliated with X Corp.

## License

**[CC BY-NC 4.0](LICENSE)**: you're free to use, change, fork and share this project for anything **non-commercial**, as long as you **credit it** with "FSB for Android by visiuun" and a link to this repository, and say what you changed. Commercial use of any kind (selling it, paywalls, ads) is not allowed.

© 2026 visiuun
