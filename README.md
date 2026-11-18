# Phone-Only APK Template

Build an Android app using only your phone. You edit code on GitHub, GitHub builds the APK for free, and you tap one link to download and install it. No computer, no Android Studio, nothing to install.

## First build

Your project files and signing key are already in this repo. To get your first APK:

1. Tap the **Actions** tab, tap **Build APK**, then **Run workflow**, then the green **Run workflow** button. Wait about 5 to 8 minutes.
2. When it turns green, go back to the repo's main page and tap **Releases** (right side, or the **Code** tab on mobile). Open **Latest build** and tap **phone-app.apk** to download it.
3. Open the downloaded file and install it. Android will ask you to allow installs from your browser. Allow it for this one time. Play Protect may show a warning for unknown apps; for your own app, choose to install anyway.

That's it. You now have a working app that shows "It works!".

## Everyday loop

1. Open a file on GitHub, tap the pencil icon, change the code, and tap **Commit changes**.
2. The build starts by itself. Check the **Actions** tab for the green tick.
3. Download the new `phone-app.apk` from **Releases** and install it over the old one. Your app's data stays, because every build uses the same signing key.

To add a new file: **Add file**, then **Create new file**. Type the full path in the name box, like `app/src/main/java/com/example/phoneapk/Notes.kt`. Typing `/` makes folders.

## When the build fails (it will, sometimes)

1. Open **Actions** and tap the run with the red cross.
2. Tap the run's summary page. Near the top is a box titled **Build failed**, with the important error lines already picked out.
3. Copy that box and paste it to your AI chat with this message:

   > My Android (Kotlin, Gradle) build failed on GitHub Actions. Here is the error. Tell me which file to change and give me the **full corrected file**, so I can paste it over the old one.

4. Paste the fixed file in, commit, and the build runs again.

If the box is empty, open the run, tap the **Build the APK** step, and copy the last 60 lines instead.

## What is in here

| File | What it does |
|---|---|
| `.github/workflows/build.yml` | The build instructions GitHub follows. You rarely change this. |
| `app/src/main/java/.../MainActivity.kt` | Your app's code. Start here. |
| `app/src/main/res/values/strings.xml` | The app's name on the home screen. |
| `app/src/main/AndroidManifest.xml` | App permissions. Add things like internet access here. |
| `app/build.gradle.kts` | App settings: package name, version, libraries. |
| `.github/workflows/setup.yml` | Not needed any more. You can delete it. |
| `signing/debug.keystore.b64` | Your signing key, stored as text, so updates install over old builds. |

## Good to know

- **Rename your app:** change `app_name` in `strings.xml`.
- **New package name:** change `namespace` and `applicationId` in `app/build.gradle.kts` to the same value, and move the `phoneapk` folder to match.
- **Adding libraries:** this template uses none, to keep the build simple. When you need one, add a `dependencies { }` block at the bottom of `app/build.gradle.kts` and put the library line inside it.
- **Your signing key is unique to this repo.** It is stored in `signing/debug.keystore.b64`. Keep the repo private. If you delete that file, builds get a new random key and you must uninstall the old app before installing the new one. For Google Play you need a separate release key, which this template doesn't set up.
- **Free limits:** public repos build for free. Private repos use your monthly free GitHub Actions minutes, which are enough for a normal hobby project.
- **Only the newest build is kept.** Each build replaces the one called Latest.
