# Handoff: Vault — GitHub APK Manager (Android)

## Overview
Vault is an Android app that installs and updates other Android apps directly from GitHub Releases — no app store in between. It tracks repositories (the user's own, starred, or any public repo), detects releases containing `.apk` assets, downloads and installs them, and offers updates when new releases publish. Two extra acquisition paths cover repos with no APK: wrapping a GitHub Pages website into a WebView/TWA wrapper APK, and committing a GitHub Actions workflow to a user-owned repo so CI builds and publishes the APK.

## About the Design Files
The files in `prototype/` are **design references created in HTML** — an interactive prototype showing intended look and behavior, not production code. The task is to **recreate these designs as a native Android app** (recommended: Kotlin + Jetpack Compose; Material 3 scaffolding restyled to the token set below). The prototype's simulated behaviors (fake downloads, fake CI) must become real implementations — see "Real-world implementation notes".

## Fidelity
**High-fidelity.** Colors, typography, spacing, and copy are final. Recreate pixel-perfectly, adapting only where Android platform conventions demand (system back, status bar, install prompts).

## Design Tokens
Brand: "Crowned Pixel" — dark, luxury, sharp. **Zero border-radius everywhere. No gradients. Gold is an accent, never a fill except on hover/CTA states.**

Colors:
- Onyx `#0C0A09` — primary background (~70% of any surface)
- Graphite `#161318` — raised surfaces / input fields
- Bone `#EDE8DC` — primary text
- Stone `#8F8A80` — secondary text, inactive states
- Gold `#C6A75E` — accent: active tab, CTAs, progress, badges
- Gold bright `#E3C57E` — hover on gold text links
- Hairline `rgba(198,167,94,.25)` — all borders/rules; row separators use `rgba(198,167,94,.12)`

Typography:
- Display / numerals / version numbers: **Cinzel** (serif), weights 500–700, letter-spacing .02em
- Body / UI: **Jost** (sans), weights 300–500, line-height 1.6
- Labels: Jost 10–12px, UPPERCASE, letter-spacing .18em, Stone (Gold when active)

Spacing scale: 8 / 16 / 24 / 44px. Buttons: 1px gold outline, transparent fill, gold uppercase label; hover/press fills solid gold with onyx text (200ms ease). Progress bars: 2px tall, gold on `rgba(198,167,94,.15)` track. No shadows; depth via surface steps. No emoji, no icon font in the prototype — app "icons" are 1px-hairline-bordered squares with a Cinzel monogram (real app icons from APK metadata should replace these in the Library once installed).

## Screens

### 1. Onboarding (`01-onboarding.png`)
Vertically centered, 32px side padding. Monogram square (56px, gold border, Cinzel "V"), headline Cinzel 28/600 "Your apps, from the source.", body Jost 300 14px Stone, primary button "CONTINUE WITH GITHUB", ghost text button "BROWSE WITHOUT SIGNING IN", hairline-topped footnote about the "Install unknown apps" permission.

### 2. GitHub sign-in (`02-github-signin.png`)
Device-authorization flow (GitHub OAuth device flow — right choice for a TV/phone client). Context label `github.com/login/device`, title, explanation (read-only scopes), 8-char device code in a hairline box (Cinzel 26px, .3em tracking), confirm button, Back.

### 3. Library / Home (`03-library.png`)
App bar: Cinzel "Vault" left, uppercase screen label right, hairline bottom border. Full-bleed list rows (16×20px padding, hairline separators): 42px monogram square · name (Jost 500 16) + repo slug (12px Stone, ellipsized) · right column with version line (Cinzel 12, "2.3.0 → 2.4.1" when update available) and status badge (9px uppercase chip, hairline border; gold text/border for Update / Updating / Not installed, stone for Current). Footer count "5 repositories tracked". Bottom tab bar: 4 equal tabs (LIBRARY / UPDATES · n / ADD / SETTINGS), 10px uppercase labels, active = gold text + 2px gold top border.

### 4. App detail (`04-app-detail.png`)
"← LIBRARY" back link, 56px monogram + Cinzel 22 name + `github.com/owner/repo`, description paragraph, 3-column stat strip between hairlines (INSTALLED / LATEST (gold) / PUBLISHED, Cinzel values). Action button "UPDATE TO v2.4.1" / "INSTALL v3.1.0"; when up to date, an inert hairline box "UP TO DATE" in Stone. During install: 2px progress bar with stage label (DOWNLOADING APK → ≥88% VERIFYING SIGNATURE) + percent. Below: RELEASES label and the release feed — version (Cinzel 15) + date per release, gold interpunct (·) bulleted notes (Jost 300 13 Stone).

### 5. Updates queue (`05-updates.png`)
Header row: "n UPDATES AVAILABLE" + outlined "UPDATE ALL" button. One hairline-bordered card per pending app: 36px monogram, name, `old → new` version line (Cinzel 11), UPDATE button → replaced by percent + bottom progress bar while running. Cards finish and leave the queue individually. Empty state: Cinzel "All current" + label "EVERY LIBRARY IS UP TO DATE" centered.

### 6. Add repository (`06-add-repo.png`)
Title, explainer, REPOSITORY label + text field (Graphite fill, hairline border → gold on focus, no radius), validation error in gold under the field ("Enter as owner/repository — e.g. harlowe/ledgerline"; also rejects duplicates). "ADD REPOSITORY" button. "OR PULL FROM" section: two hairline rows — "My repositories" (count) and "Starred repositories with APK releases" (count) — these should open pickers filtered to repos whose latest release contains an `.apk` asset. "NO APK PUBLISHED?" section: two cards linking to screens 7 and 8.

### 7. Wrap website (`07-wrap-website.png`)
For repos that are GitHub Pages sites. Fields: repository/URL (with live detection line "Pages site detected · owner.github.io/repo" in gold), APP NAME, icon preview (48px monogram square; use the site's web-manifest icon when present). "GENERATE WRAPPER APK" → progress stages: Fetching web manifest → Generating wrapper → Signing APK; on completion the wrapped app lands in the Library as Current (the site updates itself; the wrapper re-generates only on name/icon change).

### 8. Actions build (`08-actions-build.png`)
For user-owned repos with Android source but no APK releases. Repo picker cards: slug + tag (BUILDABLE in gold; websites tagged WEBSITE in stone, disabled with "use Wrap instead"). Selected card gets a gold border. Three-step pipeline list with Cinzel roman numerals I/II/III: commit `android-build.yml` → Actions compiles & signs → release published; step states QUEUED / RUNNING (gold) / DONE while a 2px progress bar runs. On completion the app appears in Library as Not installed with its first automated release.

### 9. Settings (`09-settings.png`)
ACCOUNT card: monogram, username or "Not signed in", scope subline ("Read-only · repos, releases, stars" / "Public repositories only · 60 checks/hr"), gold SIGN IN/OUT action. BACKGROUND CHECKS: 6H/12H/24H segmented row (selected = gold border+text), footnote on API rate limits. PREFERENCES toggles (36×18px hairline track, 12px square knob sliding 2px→20px, gold when on): Include pre-releases / Download on Wi-Fi only / Notify on new releases. Footer: version line + "Personal access tokens are stored in the Android keystore, never synced."

## Interactions & State
- Navigation: bottom tabs (Library also active on Detail); Detail/Wrap/Build have inline back links.
- App status machine: `notinstalled | update | updating | current`. `updating` runs download progress (prototype simulates ~180ms ticks); on completion installed=latest, status=current. "Update all" starts all pending in parallel.
- Update badge on the tab shows pending count ("UPDATES · 2"), disappears at zero.
- All hovers/presses: 200ms ease; outlined gold buttons fill gold with onyx text.
- Form validation: repo input must match `owner/repo` (also accepts a pasted `https://github.com/...` URL, stripped).

## Real-world implementation notes
- **Releases**: GitHub REST `GET /repos/{owner}/{repo}/releases`; an app is "installable" iff a release asset ends in `.apk`. Version compare via tag semver vs installed `PackageInfo.versionName`.
- **Auth**: OAuth device flow; store token in Android Keystore/EncryptedSharedPreferences. Unauthenticated = 60 req/hr, authenticated = 5000.
- **Install/update**: `PackageInstaller` session API; requires `REQUEST_INSTALL_PACKAGES` + user's "Install unknown apps" grant. Verify APK signature continuity before offering as an update.
- **Background checks**: WorkManager periodic job at the chosen cadence, Wi-Fi-only constraint from Preferences; silent notification channel per repo.
- **Wrap website**: generate a TWA (Trusted Web Activity) wrapper — PWABuilder's open-source CLI/service is the reference; signing needs a locally generated key.
- **Actions build**: commit a workflow via the Contents API to a repo the user owns; workflow builds with Gradle, signs with a repo-scoped key (secrets), publishes a release. Only offered for repos where a Gradle/Android project is detected.
- Obtainium (open source) is the closest existing reference for the core track/install/update loop.

## Assets
- Fonts: Cinzel + Jost (Google Fonts, OFL).
- No icon set shipped — monogram squares are intentional placeholders. If interface icons become unavoidable, use Lucide at 1.5px stroke, Stone (gold when active).

## Files
- `prototype/GitHub APK Manager.dc.html` — the interactive HTML prototype (all 9 screens, simulated flows)
- `prototype/android-frame.jsx` — device-frame scaffold used by the prototype (presentation only; not part of the app)
- `screenshots/01–09` — one PNG per screen, named by screen
