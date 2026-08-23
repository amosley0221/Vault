# Vault — GitHub APK Manager

Vault installs and updates Android apps straight from GitHub releases, with no store in between.
Track any repository — your own, starred, or anyone's public one — and Vault watches its release
feed, downloads the `.apk` asset, checks the signing certificate, and hands it to the platform
installer. Two extra paths cover repositories that publish no APK at all: wrapping a GitHub Pages
site into a WebView APK, and committing a build workflow so GitHub Actions compiles and publishes
the APK for you.

Native Kotlin + Jetpack Compose, built to the "Crowned Pixel" design handoff in [`design/`](design).

## Install it on a phone

Every push to this repository builds the APK and refreshes a rolling release, so there is always
one stable link:

**https://github.com/amosley0221/Vault/releases/download/latest/vault.apk**

1. Open that link in the phone's browser and let the download finish.
2. Tap the downloaded file. Android asks whether this browser may install unknown apps — allow it
   once, then confirm the install.
3. Tagged versions (`v0.2.0`, …) publish their own release as well; the newest one is always at
   `https://github.com/amosley0221/Vault/releases/latest`.

`vault.apk.sha256` sits next to the APK in each release if you want to verify the download, and
every build prints its signing certificate in the workflow log.

Android 8.0 (API 26) or newer.

## What it does

| Screen | Behaviour |
| --- | --- |
| Library | Every tracked repository with its installed → latest version and status badge. |
| Detail | Release feed, stat strip, and the install/update action with live download progress. |
| Updates | Everything with a pending update; "Update all" runs them together. |
| Add | `owner/repo` (or a pasted GitHub URL), plus pickers over your own and starred repos filtered to those whose releases carry an APK. |
| Wrap website | Turns a GitHub Pages site into a launcher app. |
| Actions build | Commits a build workflow to an Android repository you own. |
| Settings | Account, 6/12/24h background cadence, pre-releases, Wi-Fi-only, notifications. |

Under the hood:

- **Releases** — `GET /repos/{owner}/{repo}/releases`; a repository is installable when a release
  asset ends in `.apk`. Assets are matched against the device ABI, falling back to a universal one.
- **Versions** — the release tag is compared against the installed `PackageInfo.versionName`.
- **Install** — the `PackageInstaller` session API. Before committing a session Vault compares the
  APK's signing certificate with the installed app's; a mismatch stops the install rather than
  letting the system fail it halfway.
- **Auth** — GitHub's device-authorization flow when the build carries an OAuth client id (see
  below), otherwise a personal access token you paste in. Either way the token is encrypted with an
  AES-GCM key generated in the Android keystore, and app backup is disabled.
- **Background checks** — a WorkManager periodic job on the chosen cadence, constrained to
  unmetered networks when "Wi-Fi only" is on, posting one silent notification per new release.

## The two "no APK published" paths

A phone cannot compile an APK, so both of these do the honest thing: Vault commits a workflow to
the repository (Contents API) and GitHub Actions does the building. The workflows ship inside the
app at [`app/src/main/assets/workflows/`](app/src/main/assets/workflows).

- **Wrap website** — `vault-web-wrapper.yml` generates a small WebView project pointed at the Pages
  site, takes the icon from the site's web manifest when there is one, signs it, and publishes it as
  a release. Re-run it only when the name or icon changes; the site itself updates on its own.
- **Actions build** — `vault-android-build.yml` runs the repository's own Gradle build, zipaligns
  and signs the output, and publishes it as `vault-build-<n>`.

Both keep their signing key in `.vault/` inside the target repository so later builds stay
upgrade-compatible on the device, and both defer to `VAULT_KEYSTORE_*` repository secrets when you
set them. Committing a workflow needs `workflow` scope on your token.

## Building it yourself

```sh
./gradlew assembleRelease        # dist at app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug          # installs alongside the release build
```

Requires JDK 17 and the Android SDK (compileSdk 35, build-tools 35).

### Signing

Release builds are signed with `keystore/vault-ci.jks`, a convenience key committed on purpose so
that every clone and every CI run produces an APK that installs and upgrades cleanly. Its password
is in `keystore/vault-ci.properties` — it is not a secret and proves nothing about provenance.

To sign with your own key, set the repository secrets `VAULT_KEYSTORE_BASE64`,
`VAULT_KEYSTORE_PASSWORD`, `VAULT_KEY_ALIAS`, `VAULT_KEY_PASSWORD`, or pass
`-PvaultKeystoreFile=… -PvaultKeystorePassword=… -PvaultKeyAlias=… -PvaultKeyPassword=…` locally.
Android refuses upgrades across signing keys, so switching keys means users reinstall once.

### GitHub sign-in

The device-authorization flow needs an OAuth app client id. Create one under
**Settings → Developer settings → OAuth Apps** with device flow enabled, then either set the
`VAULT_GITHUB_CLIENT_ID` repository secret (CI picks it up) or build with
`-PvaultGithubClientId=Iv1.…`. Without it, the sign-in screen asks for a personal access token
instead — same capabilities, one more step for the user.

## Where this departs from the handoff

The design is followed as specified — tokens, copy, layout, states — with three deviations, each
forced by what an Android app can actually do:

1. **Sign-in copy.** The prototype says "read-only access". Vault asks for `repo workflow` so the
   Wrap and Actions-build screens can work, and the screen says so rather than claiming read-only.
   The token screen appears in builds without an OAuth client id.
2. **Wrap and Actions build run on CI.** The prototype simulates local generation; the real
   equivalents commit a workflow and follow the run. The three-stage progress language is kept and
   now reflects real stages.
3. **Extras the prototype had no state for.** Untrack / Open on GitHub / Launch on the detail
   screen, an empty library state, and an inline notice strip for GitHub errors and rate limits.

Monogram squares stand in for app icons, as specified.

## Layout

```
app/src/main/java/com/crownedpixel/vault/
  data/        GitHub client, models, storage, keystore-backed token store, workflow committer
  install/     download, signature check, PackageInstaller session, install results
  ui/          design tokens, components, view model, screens
  work/        WorkManager release polling and notifications
app/src/main/assets/workflows/   workflows Vault commits into other repositories
.github/workflows/release.yml    builds this APK and publishes the download
design/                          the original handoff, prototype, and screenshots
```

Fonts are Cinzel and Jost, bundled under the SIL Open Font License ([`licenses/`](licenses)).
