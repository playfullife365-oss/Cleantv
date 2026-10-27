# CleanTV

A clean, ad-free home screen for Android TV and Google TV, styled after Apple TV: a top shelf with the selected
app over a blurred backdrop, a frosted dock for your favourite row, and tiles that lift and tilt as you move.
No sponsored rows, no recommendations, no tracking. It has no `INTERNET` permission,
so it can't go online.

Inspired by [cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT). Written from scratch, with no code
copied.

## Features

- Apple TV-style layout: the top row of apps sits in a frosted dock, and the selected one is shown large in
  the top shelf over a blurred copy of its banner. Moving down scrolls the shelf away.
- Tiles grow, lift, tilt towards the direction you moved and catch a sweep of light; the name appears below.
- Every TV app as a tile with its banner; phone-only apps get their icon instead.
- Reorder tiles: long-press OK (or press Menu) on a tile, then move it left, right, up, down or to the start.
- Hide apps you never use, and bring them back from **Hidden apps**.
- Settings button, and Home always brings you back to the first tile.
- Picks up app installs, updates and removals right away.
- Plain Java on the Android framework, with no libraries. minSdk 26 (Android 8.0).

## Build

Requires JDK 17 and the Android SDK (platform 34).

```sh
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest      # JVM unit tests
```

Every push also builds the APK on GitHub Actions. Download it from the run's **cleantv-launcher** artifact.

## Install on your TV

1. On the TV, turn on Developer options (Settings → About → press OK on *Build* seven times), then turn on
   **USB debugging** or **Network debugging**.
2. From a computer on the same Wi-Fi:
   ```sh
   adb connect <tv-ip>:5555
   adb install -r app-release.apk
   ```
3. Press Home and pick **CleanTV**. If the TV doesn't ask, set it from the command line:
   ```sh
   adb shell cmd package set-home-activity com.cleantv.launcher/.MainActivity
   ```
4. Turn debugging off again when you're done. Port 5555 is unencrypted.

Some Google TV builds keep bringing back their own launcher. If yours does, you can disable it with
`adb shell pm disable-user --user 0 <launcher package>`, but only **after** CleanTV works as Home. Undo it with
`pm enable <launcher package>`. Never uninstall system packages.

## Layout

| Path | What it is |
|---|---|
| `app/src/main/java/com/cleantv/launcher/MainActivity.java` | Home screen: grid, focus, tile menu, hidden apps |
| `AppRepository.java` / `AppEntry.java` | Finds the launchable apps, with their banners and icons |
| `AppOrder.java` / `LayoutStore.java` | Tile order and hidden apps (pure logic, plus SharedPreferences) |
| `TileView.java` | The 16:9 tile and its focus animation (lift, tilt, shine, caption) |
| `Backdrops.java` / `Blur.java` | The blurred backdrop, made cheaply so it runs on older boxes |
| `app/src/main/res/` | Colors, layout, strings. Change `values/integers.xml` → `columns` for more or fewer tiles per row (the dock is the first row) |

## License

MIT
