package com.crownedpixel.vault.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crownedpixel.vault.BuildConfig
import com.crownedpixel.vault.data.AppSource
import com.crownedpixel.vault.data.AppStatus
import com.crownedpixel.vault.data.Dates
import com.crownedpixel.vault.data.GitHubApi
import com.crownedpixel.vault.data.LibraryApp
import com.crownedpixel.vault.data.LibraryTransfer
import com.crownedpixel.vault.data.Preferences
import com.crownedpixel.vault.data.ReleaseAsset
import com.crownedpixel.vault.data.ReleaseInfo
import com.crownedpixel.vault.data.RepoCandidate
import com.crownedpixel.vault.data.RepoKind
import com.crownedpixel.vault.data.TokenStore
import com.crownedpixel.vault.data.TrackedRepo
import com.crownedpixel.vault.data.UpdateCheck
import com.crownedpixel.vault.data.text
import com.crownedpixel.vault.data.VaultStore
import com.crownedpixel.vault.data.Versions
import com.crownedpixel.vault.data.WorkflowCommitter
import com.crownedpixel.vault.install.ApkInstaller
import com.crownedpixel.vault.install.InstallEvents
import com.crownedpixel.vault.install.InstalledApps
import com.crownedpixel.vault.work.UpdateScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

enum class Screen { ONBOARDING, SIGN_IN, LIBRARY, DETAIL, UPDATES, ADD, WRAP, BUILD, SETTINGS }

enum class SignInMode { DEVICE_FLOW, TOKEN }

data class SignInState(
    val mode: SignInMode,
    val busy: Boolean = false,
    val userCode: String = "",
    val verificationUri: String = "https://github.com/login/device",
    val tokenValue: String = "",
    val error: String? = null,
)

data class WrapState(
    val repoValue: String = "",
    val appName: String = "",
    val siteUrl: String? = null,
    val detecting: Boolean = false,
    val busy: Boolean = false,
    val percent: Int = 0,
    val stage: String = "",
    val error: String? = null,
)

data class BuildState(
    val candidates: List<RepoCandidate> = emptyList(),
    val loading: Boolean = false,
    val selected: String? = null,
    val busy: Boolean = false,
    val percent: Int = 0,
    val stage: Int = 0,
    val error: String? = null,
)

enum class PickerKind(val title: String) {
    MINE("My repositories"),
    STARRED("Starred repositories with APK releases"),
}

data class PickerState(
    val title: String,
    val kind: PickerKind,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val items: List<RepoCandidate> = emptyList(),
    val error: String? = null,
)

data class VaultUiState(
    val screen: Screen = Screen.LIBRARY,
    val signedIn: Boolean = false,
    val login: String? = null,
    val apps: List<LibraryApp> = emptyList(),
    val selected: String? = null,
    val preferences: Preferences = Preferences(),
    val refreshing: Boolean = false,
    val notice: String? = null,
    val addValue: String = "",
    val addError: String? = null,
    val adding: Boolean = false,
    val signIn: SignInState = SignInState(
        mode = if (BuildConfig.GITHUB_CLIENT_ID.isBlank()) SignInMode.TOKEN else SignInMode.DEVICE_FLOW,
    ),
    val wrap: WrapState = WrapState(),
    val build: BuildState = BuildState(),
    val picker: PickerState? = null,
    /** Which tab the detail screen was reached from, so back and the tab bar agree. */
    val detailOrigin: Screen = Screen.LIBRARY,
    val exportWithToken: Boolean = false,
    val transferBusy: Boolean = false,
) {
    val detail: LibraryApp? get() = apps.firstOrNull { it.id == selected } ?: apps.firstOrNull()

    val pendingUpdates: List<LibraryApp>
        get() = apps.filter { it.status == AppStatus.UPDATE || it.status == AppStatus.UPDATING }

    val updateCount: Int get() = apps.count { it.status == AppStatus.UPDATE }

    val headerLabel: String
        get() = when (screen) {
            Screen.LIBRARY -> "Library"
            Screen.DETAIL -> "Release feed"
            Screen.UPDATES -> "Updates"
            Screen.ADD -> "Add repository"
            Screen.WRAP -> "Wrap website"
            Screen.BUILD -> "Actions build"
            Screen.SETTINGS -> "Settings"
            else -> ""
        }
}

sealed interface VaultEvent {
    data class OpenUrl(val url: String) : VaultEvent
    data class Launch(val intent: Intent) : VaultEvent
    data object RequestInstallPermission : VaultEvent
    data object PickImportFile : VaultEvent
}

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()
    private val store = VaultStore(context)
    private val tokens = TokenStore(context)

    private val _state = MutableStateFlow(VaultUiState())
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    val events = MutableSharedFlow<VaultEvent>(extraBufferCapacity = 8)

    private var repos: List<TrackedRepo> = emptyList()
    private val releases = mutableMapOf<String, List<ReleaseInfo>>()
    private val progress = mutableMapOf<String, Pair<Float, String>>()
    private val installing = mutableSetOf<String>()
    private val installQueue = ArrayDeque<String>()
    private var activeInstall: String? = null
    private val pendingInstalls = mutableMapOf<String, String>() // package name -> slug
    private var deviceCode: GitHubApi.DeviceCode? = null
    private var lastRefreshAt = 0L

    init {
        repos = store.readRepos()
        val preferences = store.readPreferences()
        val token = tokens.readToken()
        _state.value = _state.value.copy(
            screen = if (preferences.onboarded) Screen.LIBRARY else Screen.ONBOARDING,
            preferences = preferences,
            signedIn = token != null,
            login = tokens.login,
        )
        rebuild()
        observeInstallResults()
        refresh()
    }

    private fun token(): String? = tokens.readToken()

    // ---------------------------------------------------------------- derived state

    private fun rebuild(mutate: (VaultUiState) -> VaultUiState = { it }) {
        val preferences = _state.value.preferences
        val apps = repos.map { repo ->
            val cached = releases[repo.slug].orEmpty()
            val newest = cached.firstOrNull { release ->
                release.apkAssets.isNotEmpty() && (preferences.includePrereleases || !release.prerelease)
            }
            val latestVersion = newest?.version ?: repo.latestVersion
            val latestMillis = Dates.epochMillis(newest?.timestamp ?: repo.latestPublishedAt)
            val installedVersion = InstalledApps.installedVersion(context, repo.packageName)
                ?: repo.installedTag?.takeIf { repo.packageName == null }?.let { Versions.normalize(it) }
            val installedMillis = InstalledApps.lastUpdateTime(context, repo.packageName)
            val inFlight = progress[repo.slug]
            val status = when {
                inFlight != null -> AppStatus.UPDATING
                installedVersion == null -> AppStatus.NOT_INSTALLED
                UpdateCheck.isNewer(latestVersion, latestMillis, installedVersion, installedMillis) ->
                    AppStatus.UPDATE
                else -> AppStatus.CURRENT
            }
            LibraryApp(
                tracked = repo,
                installedVersion = installedVersion,
                latestVersion = latestVersion,
                publishedLabel = Dates.short(newest?.timestamp ?: repo.latestPublishedAt),
                status = status,
                progress = inFlight?.first ?: 0f,
                progressStage = inFlight?.second.orEmpty(),
                releases = cached,
            )
        }
        _state.value = mutate(_state.value.copy(apps = apps))
    }

    private fun persist() {
        store.writeRepos(repos)
    }

    private fun notice(message: String?) {
        _state.value = _state.value.copy(notice = message)
    }

    fun dismissNotice() = notice(null)

    // ---------------------------------------------------------------- navigation

    fun goTo(screen: Screen) {
        _state.value = _state.value.copy(screen = screen, notice = null)
        when (screen) {
            Screen.BUILD -> loadBuildCandidates()
            // Opening the queue should not show a stale answer, but tapping the tab repeatedly
            // must not spend the hourly API budget either.
            Screen.UPDATES -> refreshIfStale()
            else -> Unit
        }
    }

    fun openDetail(slug: String) {
        val from = _state.value.screen.takeIf { it == Screen.UPDATES } ?: Screen.LIBRARY
        _state.value = _state.value.copy(
            screen = Screen.DETAIL,
            selected = slug,
            detailOrigin = from,
        )
        viewModelScope.launch { refreshOne(slug) }
    }

    fun back(): Boolean {
        val current = _state.value.screen
        return when (current) {
            Screen.DETAIL -> { goTo(_state.value.detailOrigin); true }
            Screen.WRAP, Screen.BUILD -> { goTo(Screen.ADD); true }
            Screen.UPDATES, Screen.ADD, Screen.SETTINGS -> { goTo(Screen.LIBRARY); true }
            Screen.SIGN_IN -> { goTo(Screen.ONBOARDING); true }
            else -> false
        }
    }

    fun completeOnboarding(signIn: Boolean) {
        val preferences = _state.value.preferences.copy(onboarded = true)
        store.writePreferences(preferences)
        _state.value = _state.value.copy(preferences = preferences)
        if (signIn) beginSignIn() else goTo(Screen.LIBRARY)
    }

    // ---------------------------------------------------------------- refresh

    /** Refreshes only when the last read is old enough to be worth another round of API calls. */
    private fun refreshIfStale() {
        if (_state.value.refreshing) return
        if (System.currentTimeMillis() - lastRefreshAt < STALE_AFTER_MS) return
        refresh()
    }

    fun refresh() {
        if (repos.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            val token = token()
            val limiter = Semaphore(4)
            val failures = coroutineScope {
                repos.map { repo ->
                    async {
                        limiter.withPermit {
                            runCatching { fetchReleases(repo, token) }.exceptionOrNull()
                        }
                    }
                }.map { it.await() }
            }.filterNotNull()
            repos = adoptInstalledPackages(
                repos.map { repo ->
                    val newest = releases[repo.slug]?.firstOrNull { it.apkAssets.isNotEmpty() }
                    if (newest == null) {
                        repo
                    } else {
                        repo.copy(
                            latestTag = newest.tag,
                            latestVersion = newest.version,
                            latestPublishedAt = newest.timestamp,
                        )
                    }
                },
            )
            persist()
            lastRefreshAt = System.currentTimeMillis()
            rebuild { it.copy(refreshing = false) }
            failures.firstOrNull()?.let { error ->
                notice(error.message ?: "Could not reach GitHub.")
            }
        }
    }

    private suspend fun refreshOne(slug: String) {
        val repo = repos.firstOrNull { it.slug == slug } ?: return
        runCatching { fetchReleases(repo, token()) }
            .onFailure { notice(it.message) }
        rebuild()
    }

    /**
     * An app installed by hand — sideloaded from the browser, or installed before it was tracked —
     * has no package name on record, so it reads as "not installed" forever. Match those against
     * what is actually on the device, off the main thread: the sweep is not free.
     */
    private suspend fun adoptInstalledPackages(candidates: List<TrackedRepo>): List<TrackedRepo> {
        if (candidates.none { it.packageName == null }) return candidates
        return withContext(Dispatchers.Default) {
            val index = InstalledApps.installedIndex(context)
            candidates.map { repo ->
                if (repo.packageName != null) {
                    repo
                } else {
                    InstalledApps.match(index, repo.repo, repo.displayName)
                        ?.let { repo.copy(packageName = it) }
                        ?: repo
                }
            }
        }
    }

    private suspend fun fetchReleases(repo: TrackedRepo, token: String?) {
        val fetched = GitHubApi.releases(repo.owner, repo.repo, token)
        releases[repo.slug] = fetched
    }

    // ---------------------------------------------------------------- library actions

    fun launch(slug: String) {
        val repo = repos.firstOrNull { it.slug == slug } ?: return
        val intent = InstalledApps.launchIntent(context, repo.packageName)
        if (intent == null) {
            notice("Vault does not know which package this repository installs yet.")
        } else {
            viewModelScope.launch { events.emit(VaultEvent.Launch(intent)) }
        }
    }

    fun openOnGitHub(slug: String) {
        viewModelScope.launch { events.emit(VaultEvent.OpenUrl("https://github.com/$slug")) }
    }

    fun remove(slug: String) {
        repos = repos.filterNot { it.slug == slug }
        releases.remove(slug)
        persist()
        rebuild { it.copy(screen = Screen.LIBRARY, selected = null) }
    }

    fun updateAll() {
        _state.value.apps.filter { it.status == AppStatus.UPDATE }.forEach { install(it.id) }
    }

    /**
     * Queues an update. Installs run strictly one at a time: the platform installer shows its
     * confirmation in its own activity, and once that is in front, Android blocks Vault — now a
     * background app — from launching the confirmation for a second session. Committing several
     * at once therefore leaves every session after the first waiting for a prompt that never
     * appears.
     */
    fun install(slug: String) {
        if (!installing.add(slug)) return
        if (!ApkInstaller.canInstall(context)) {
            installing.remove(slug)
            viewModelScope.launch { events.emit(VaultEvent.RequestInstallPermission) }
            notice("Allow Vault to install unknown apps, then try again.")
            return
        }
        installQueue.addLast(slug)
        setProgress(slug, 0f, QUEUED)
        pumpInstalls()
    }

    private fun pumpInstalls() {
        if (activeInstall != null) return
        val next = installQueue.removeFirstOrNull() ?: return
        activeInstall = next
        runInstall(next)
    }

    /** Clears an install that has reached its end, and lets the queue move on. */
    private fun finishInstall(slug: String, message: String? = null) {
        installing.remove(slug)
        installQueue.remove(slug)
        progress.remove(slug)
        if (activeInstall == slug) activeInstall = null
        pendingInstalls.entries.removeAll { it.value == slug }
        rebuild()
        message?.let { notice(it) }
        pumpInstalls()
    }

    private fun runInstall(slug: String) {
        viewModelScope.launch {
            var downloaded: File? = null
            try {
                val repo = repos.firstOrNull { it.slug == slug }
                    ?: throw IOException("That repository is no longer tracked.")
                setProgress(slug, 0f, "Downloading APK")
                val token = token()
                val release = releases[repo.slug]?.firstOrNull { candidate ->
                    candidate.apkAssets.isNotEmpty() &&
                        (_state.value.preferences.includePrereleases || !candidate.prerelease)
                } ?: GitHubApi.latestReleaseWithApk(
                    repo.owner,
                    repo.repo,
                    token,
                    _state.value.preferences.includePrereleases,
                ) ?: throw IOException("No release with an APK asset yet.")

                val asset = chooseAsset(release.apkAssets)
                    ?: throw IOException("This release has no APK for your device.")

                val file = ApkInstaller.download(
                    context = context,
                    url = if (token != null) asset.apiUrl.ifBlank { asset.browserDownloadUrl } else asset.browserDownloadUrl,
                    token = token,
                    expectedSize = asset.size,
                ) { fraction -> setProgress(slug, fraction * 0.88f, "Downloading APK") }
                downloaded = file

                setProgress(slug, 0.9f, "Verifying signature")
                val archive = InstalledApps.archiveInfo(context, file)
                    ?: throw IOException("The downloaded file is not a valid APK.")
                val packageName = archive.packageName

                if (!InstalledApps.signatureMatchesInstalled(context, file, packageName)) {
                    throw IOException(
                        "Signature mismatch — this build was signed with a different key than the " +
                            "installed app. Uninstall it first if you trust the new publisher.",
                    )
                }

                repos = repos.map {
                    if (it.slug == slug) {
                        it.copy(
                            packageName = packageName,
                            installedTag = release.tag,
                            latestTag = release.tag,
                            latestVersion = release.version,
                            latestPublishedAt = release.timestamp,
                        )
                    } else {
                        it
                    }
                }
                persist()
                pendingInstalls[packageName] = slug

                setProgress(slug, 0.96f, "Awaiting confirmation")
                ApkInstaller.install(context, file, packageName)
            } catch (error: Exception) {
                downloaded?.delete()
                finishInstall(slug, error.message ?: "The install could not be started.")
            }
        }
    }

    /**
     * The confirmation dialog belongs to another process, so Vault is paused while it is up.
     * Coming back means it has been dealt with one way or another; if no result reached us, the
     * queue would otherwise stall behind it forever.
     */
    fun onResumed() {
        rebuild()
        viewModelScope.launch {
            delay(RESUME_GRACE_MS)
            val slug = activeInstall ?: return@launch
            val committed = (progress[slug]?.first ?: 0f) >= 0.95f
            if (committed) finishInstall(slug)
        }
    }

    /** Prefers an ABI-specific asset that matches the device, then a universal one. */
    private fun chooseAsset(assets: List<ReleaseAsset>): ReleaseAsset? {
        if (assets.size == 1) return assets.first()
        val abis = android.os.Build.SUPPORTED_ABIS?.toList().orEmpty()
        abis.forEach { abi ->
            val needle = abi.lowercase().replace("-", "")
            assets.firstOrNull { it.name.lowercase().replace("-", "").contains(needle) }?.let { return it }
        }
        return assets.firstOrNull { it.name.contains("universal", ignoreCase = true) }
            ?: assets.firstOrNull { !it.name.contains("debug", ignoreCase = true) }
            ?: assets.firstOrNull()
    }

    private fun setProgress(slug: String, fraction: Float, stage: String) {
        progress[slug] = fraction to stage
        rebuild()
    }

    private fun observeInstallResults() {
        viewModelScope.launch {
            InstallEvents.results.collect { result ->
                val slug = result.packageName?.let { pendingInstalls[it] } ?: activeInstall
                if (slug != null) {
                    if (result.success) {
                        repos = repos.map { repo ->
                            if (repo.slug == slug && repo.packageName == null) {
                                repo.copy(packageName = result.packageName)
                            } else {
                                repo
                            }
                        }
                        persist()
                    }
                    finishInstall(slug, result.message.takeIf { !result.success })
                } else {
                    rebuild()
                    if (!result.success) notice(result.message)
                }
            }
        }
    }

    // ---------------------------------------------------------------- add repository

    fun onAddValueChange(value: String) {
        _state.value = _state.value.copy(addValue = value, addError = null)
    }

    fun submitAdd() {
        val raw = _state.value.addValue.trim()
        val slug = normalizeSlug(raw)
        if (slug == null) {
            _state.value = _state.value.copy(
                addError = "Enter as owner/repository — e.g. harlowe/ledgerline",
            )
            return
        }
        if (repos.any { it.slug.equals(slug, ignoreCase = true) }) {
            _state.value = _state.value.copy(addError = "Already tracked.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(adding = true, addError = null)
            try {
                val owner = slug.substringBefore('/')
                val name = slug.substringAfter('/')
                val token = token()
                val repository = GitHubApi.repository(owner, name, token)
                val fetched = GitHubApi.releases(owner, name, token)
                val newest = fetched.firstOrNull { it.apkAssets.isNotEmpty() }
                if (newest == null) {
                    _state.value = _state.value.copy(
                        adding = false,
                        addError = "No release here publishes an .apk asset. Try Wrap or Actions build below.",
                    )
                    return@launch
                }
                addRepository(repository, owner, name, AppSource.RELEASES, fetched)
                _state.value = _state.value.copy(adding = false, addValue = "", addError = null)
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    adding = false,
                    addError = error.message ?: "Could not reach GitHub.",
                )
            }
        }
    }

    private fun addRepository(
        repository: JSONObject?,
        owner: String,
        name: String,
        source: AppSource,
        fetched: List<ReleaseInfo>,
    ) {
        val newest = fetched.firstOrNull { it.apkAssets.isNotEmpty() }
        val tracked = TrackedRepo(
            owner = owner,
            repo = name,
            displayName = TrackedRepo.prettyName(name),
            description = repository?.text("description").orEmpty(),
            source = source,
            latestTag = newest?.tag,
            latestVersion = newest?.version,
            latestPublishedAt = newest?.timestamp,
            addedAt = System.currentTimeMillis(),
        )
        repos = repos + tracked
        releases[tracked.slug] = fetched
        persist()
        rebuild { it.copy(screen = Screen.DETAIL, selected = tracked.slug) }
    }

    private fun normalizeSlug(raw: String): String? {
        val cleaned = raw
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        return if (Regex("^[\\w.-]+/[\\w.-]+$").matches(cleaned)) cleaned else null
    }

    // ---------------------------------------------------------------- pickers

    fun openMinePicker() = openPicker(PickerKind.MINE)

    fun openStarredPicker() = openPicker(PickerKind.STARRED)

    /** Pull-to-refresh on the picker: re-reads GitHub while the current list stays on screen. */
    fun refreshPicker() {
        val current = _state.value.picker ?: return
        if (current.refreshing) return
        loadPicker(current.kind, reopen = false)
    }

    private fun openPicker(kind: PickerKind) {
        loadPicker(kind, reopen = true)
    }

    private fun loadPicker(kind: PickerKind, reopen: Boolean) {
        val token = token()
        if (token == null) {
            notice("Sign in to list your repositories.")
            goTo(Screen.SETTINGS)
            return
        }
        val existing = _state.value.picker
        _state.value = _state.value.copy(
            picker = if (reopen || existing == null) {
                PickerState(title = kind.title, kind = kind)
            } else {
                existing.copy(refreshing = true, error = null)
            },
        )
        viewModelScope.launch {
            try {
                val source = when (kind) {
                    PickerKind.MINE -> GitHubApi.myRepositories(token)
                    PickerKind.STARRED -> GitHubApi.starredRepositories(token)
                }
                val limiter = Semaphore(4)
                val candidates = coroutineScope {
                    source.take(60).map { repository ->
                        async {
                            val slug = repository.text("full_name")
                            val owner = slug.substringBefore('/')
                            val name = slug.substringAfter('/')
                            val release = limiter.withPermit {
                                runCatching {
                                    GitHubApi.releases(owner, name, token, perPage = 5)
                                        .firstOrNull { it.apkAssets.isNotEmpty() }
                                }.getOrNull()
                            }
                            if (release == null) {
                                null
                            } else {
                                RepoCandidate(
                                    slug = slug,
                                    description = repository.text("description")
                                        .ifBlank { "Latest APK release ${release.tag}" },
                                    tag = release.tag.ifBlank { "release" },
                                    buildable = true,
                                )
                            }
                        }
                    }.map { it.await() }
                }.filterNotNull().filterNot { candidate ->
                    repos.any { it.slug.equals(candidate.slug, ignoreCase = true) }
                }
                _state.value = _state.value.copy(
                    picker = PickerState(
                        title = kind.title,
                        kind = kind,
                        loading = false,
                        items = candidates,
                    ),
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    picker = PickerState(
                        title = kind.title,
                        kind = kind,
                        loading = false,
                        items = _state.value.picker?.items.orEmpty(),
                        error = error.message ?: "Could not reach GitHub.",
                    ),
                )
            }
        }
    }

    fun closePicker() {
        _state.value = _state.value.copy(picker = null)
    }

    fun pickFromPicker(slug: String) {
        closePicker()
        _state.value = _state.value.copy(addValue = slug, addError = null)
        submitAdd()
    }

    // ---------------------------------------------------------------- sign in

    fun beginSignIn() {
        val mode = if (BuildConfig.GITHUB_CLIENT_ID.isBlank()) SignInMode.TOKEN else SignInMode.DEVICE_FLOW
        _state.value = _state.value.copy(
            screen = Screen.SIGN_IN,
            signIn = SignInState(mode = mode),
        )
        if (mode == SignInMode.DEVICE_FLOW) requestDeviceCode()
    }

    private fun requestDeviceCode() {
        viewModelScope.launch {
            _state.value = _state.value.copy(signIn = _state.value.signIn.copy(busy = true, error = null))
            try {
                val code = GitHubApi.requestDeviceCode(BuildConfig.GITHUB_CLIENT_ID, DEVICE_SCOPES)
                deviceCode = code
                _state.value = _state.value.copy(
                    signIn = _state.value.signIn.copy(
                        busy = false,
                        userCode = code.userCode,
                        verificationUri = code.verificationUri,
                    ),
                )
                pollForToken(code)
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    signIn = _state.value.signIn.copy(
                        busy = false,
                        error = error.message ?: "GitHub did not issue a device code.",
                    ),
                )
            }
        }
    }

    private fun pollForToken(code: GitHubApi.DeviceCode) {
        viewModelScope.launch {
            var interval = code.intervalSeconds.coerceAtLeast(5)
            val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L
            while (System.currentTimeMillis() < deadline && _state.value.screen == Screen.SIGN_IN) {
                delay(interval * 1000L)
                when (val poll = runCatching { GitHubApi.pollDeviceToken(BuildConfig.GITHUB_CLIENT_ID, code.deviceCode) }
                    .getOrElse { GitHubApi.DevicePoll.Pending }) {
                    is GitHubApi.DevicePoll.Success -> {
                        adoptToken(poll.token)
                        return@launch
                    }

                    is GitHubApi.DevicePoll.SlowDown -> interval = poll.intervalSeconds
                    is GitHubApi.DevicePoll.Pending -> Unit
                    is GitHubApi.DevicePoll.Failed -> {
                        _state.value = _state.value.copy(
                            signIn = _state.value.signIn.copy(error = poll.message),
                        )
                        return@launch
                    }
                }
            }
        }
    }

    fun onSignInTokenChange(value: String) {
        _state.value = _state.value.copy(signIn = _state.value.signIn.copy(tokenValue = value, error = null))
    }

    fun submitSignInToken() {
        val value = _state.value.signIn.tokenValue.trim()
        if (value.isBlank()) {
            _state.value = _state.value.copy(
                signIn = _state.value.signIn.copy(error = "Paste a personal access token to continue."),
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(signIn = _state.value.signIn.copy(busy = true, error = null))
            try {
                GitHubApi.currentUser(value)
                adoptToken(value)
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    signIn = _state.value.signIn.copy(
                        busy = false,
                        error = error.message ?: "GitHub rejected that token.",
                    ),
                )
            }
        }
    }

    /** Called by the sign-in screen's confirm button; polls immediately instead of waiting. */
    fun confirmDeviceCode() {
        val code = deviceCode ?: return requestDeviceCode()
        viewModelScope.launch {
            _state.value = _state.value.copy(signIn = _state.value.signIn.copy(busy = true))
            when (val poll = runCatching { GitHubApi.pollDeviceToken(BuildConfig.GITHUB_CLIENT_ID, code.deviceCode) }
                .getOrElse { GitHubApi.DevicePoll.Failed(it.message ?: "GitHub could not be reached.") }) {
                is GitHubApi.DevicePoll.Success -> adoptToken(poll.token)
                is GitHubApi.DevicePoll.Failed -> _state.value = _state.value.copy(
                    signIn = _state.value.signIn.copy(busy = false, error = poll.message),
                )
                else -> _state.value = _state.value.copy(
                    signIn = _state.value.signIn.copy(
                        busy = false,
                        error = "GitHub has not seen the code yet — enter it, then try again.",
                    ),
                )
            }
        }
    }

    private suspend fun adoptToken(value: String) {
        tokens.writeToken(value)
        val account = runCatching { GitHubApi.currentUser(value) }.getOrNull()
        tokens.login = account?.login
        val preferences = _state.value.preferences.copy(onboarded = true)
        store.writePreferences(preferences)
        _state.value = _state.value.copy(
            screen = Screen.LIBRARY,
            signedIn = true,
            login = account?.login,
            preferences = preferences,
            signIn = SignInState(mode = _state.value.signIn.mode),
        )
        refresh()
    }

    fun signOut() {
        tokens.clear()
        _state.value = _state.value.copy(signedIn = false, login = null)
        notice("Signed out. Public repositories still work, at 60 checks an hour.")
    }

    fun openDeviceVerification() {
        viewModelScope.launch { events.emit(VaultEvent.OpenUrl(_state.value.signIn.verificationUri)) }
    }

    fun openTokenPage() {
        viewModelScope.launch {
            events.emit(VaultEvent.OpenUrl("https://github.com/settings/tokens/new?scopes=repo,workflow&description=Vault"))
        }
    }

    // ---------------------------------------------------------------- settings

    fun setInterval(hours: Int) {
        val preferences = _state.value.preferences.copy(checkIntervalHours = hours)
        applyPreferences(preferences)
    }

    fun togglePrerelease() = applyPreferences(
        _state.value.preferences.copy(includePrereleases = !_state.value.preferences.includePrereleases),
    )

    fun toggleWifiOnly() = applyPreferences(
        _state.value.preferences.copy(wifiOnly = !_state.value.preferences.wifiOnly),
    )

    fun toggleNotify() = applyPreferences(
        _state.value.preferences.copy(notifyOnRelease = !_state.value.preferences.notifyOnRelease),
    )

    private fun applyPreferences(preferences: Preferences) {
        store.writePreferences(preferences)
        _state.value = _state.value.copy(preferences = preferences)
        UpdateScheduler.schedule(context, preferences)
        rebuild()
    }

    // ---------------------------------------------------------------- moving to another device

    fun setExportWithToken(include: Boolean) {
        _state.value = _state.value.copy(exportWithToken = include)
    }

    /**
     * Writes the library to a file and hands it to the share sheet. The token rides along only
     * when the user has ticked that box, because whoever holds the file then holds the credential.
     */
    fun exportLibrary() {
        if (_state.value.transferBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(transferBusy = true)
            try {
                val includeToken = _state.value.exportWithToken
                val contents = LibraryTransfer.encode(
                    repositories = repos,
                    preferences = _state.value.preferences,
                    token = if (includeToken) token() else null,
                )
                val intent = LibraryTransfer.share(context, contents)
                events.emit(VaultEvent.Launch(intent))
                notice(
                    if (includeToken) {
                        "Export includes your GitHub token — send it somewhere private, and delete it afterwards."
                    } else {
                        "Export written. The other device will ask for a token of its own."
                    },
                )
            } catch (error: Exception) {
                notice(error.message ?: "The library could not be exported.")
            } finally {
                _state.value = _state.value.copy(transferBusy = false)
            }
        }
    }

    fun beginImport() {
        viewModelScope.launch { events.emit(VaultEvent.PickImportFile) }
    }

    /** Merges an exported library into this device, keeping anything already tracked. */
    fun importLibrary(uri: Uri) {
        if (_state.value.transferBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(transferBusy = true)
            try {
                val bundle = LibraryTransfer.decode(LibraryTransfer.read(context, uri))
                val known = repos.map { it.slug.lowercase() }.toSet()
                val fresh = bundle.repositories.filterNot { it.slug.lowercase() in known }
                repos = adoptInstalledPackages(repos + fresh)
                persist()

                bundle.preferences?.let { imported ->
                    val merged = imported.copy(onboarded = true)
                    store.writePreferences(merged)
                    _state.value = _state.value.copy(preferences = merged)
                    UpdateScheduler.schedule(context, merged)
                }

                var signedIn = _state.value.signedIn
                var login = _state.value.login
                bundle.token?.let { imported ->
                    val account = runCatching { GitHubApi.currentUser(imported) }.getOrNull()
                    if (account == null) {
                        notice("The token in that export was rejected by GitHub — sign in on this device.")
                    } else {
                        tokens.writeToken(imported)
                        tokens.login = account.login
                        signedIn = true
                        login = account.login
                    }
                }

                _state.value = _state.value.copy(
                    screen = Screen.LIBRARY,
                    signedIn = signedIn,
                    login = login,
                )
                rebuild()
                notice(
                    when {
                        fresh.isEmpty() -> "Nothing new — every repository in that export is already tracked."
                        fresh.size == 1 -> "Added 1 repository from the export."
                        else -> "Added ${fresh.size} repositories from the export."
                    },
                )
                refresh()
            } catch (error: Exception) {
                notice(error.message ?: "That export could not be read.")
            } finally {
                _state.value = _state.value.copy(transferBusy = false)
            }
        }
    }

    // ---------------------------------------------------------------- wrap a website

    fun onWrapRepoChange(value: String) {
        _state.value = _state.value.copy(
            wrap = _state.value.wrap.copy(repoValue = value, siteUrl = null, error = null),
        )
        val slug = normalizeSlug(value) ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(wrap = _state.value.wrap.copy(detecting = true))
            val owner = slug.substringBefore('/')
            val name = slug.substringAfter('/')
            val url = runCatching { GitHubApi.pagesUrl(owner, name, token()) }.getOrNull()
            if (_state.value.wrap.repoValue == value) {
                _state.value = _state.value.copy(
                    wrap = _state.value.wrap.copy(
                        detecting = false,
                        siteUrl = url ?: "https://$owner.github.io/$name",
                        appName = _state.value.wrap.appName.ifBlank { TrackedRepo.prettyName(name) },
                    ),
                )
            }
        }
    }

    fun onWrapNameChange(value: String) {
        _state.value = _state.value.copy(wrap = _state.value.wrap.copy(appName = value, error = null))
    }

    /**
     * An APK cannot be compiled on the phone, so the wrapper is produced the same way the rest of
     * GitHub builds software: Vault commits a workflow, Actions generates and signs the wrapper,
     * and the resulting release is tracked like any other app.
     */
    fun startWrap() {
        val current = _state.value.wrap
        if (current.busy) return
        val slug = normalizeSlug(current.repoValue)
        if (slug == null) {
            _state.value = _state.value.copy(wrap = current.copy(error = "Enter as owner/repository."))
            return
        }
        if (current.appName.isBlank()) {
            _state.value = _state.value.copy(wrap = current.copy(error = "Give the app a launcher name."))
            return
        }
        val token = token()
        if (token == null) {
            _state.value = _state.value.copy(
                wrap = current.copy(error = "Sign in first — committing a workflow needs write access."),
            )
            return
        }
        viewModelScope.launch {
            val owner = slug.substringBefore('/')
            val name = slug.substringAfter('/')
            try {
                setWrap(busy = true, percent = 5, stage = "Fetching web manifest", error = null)
                val siteUrl = current.siteUrl
                    ?: GitHubApi.pagesUrl(owner, name, token)
                    ?: "https://$owner.github.io/$name"
                val branch = WorkflowCommitter.defaultBranch(owner, name, token)
                setWrap(busy = true, percent = 30, stage = "Generating wrapper")
                WorkflowCommitter.commitWrapperWorkflow(
                    context = context,
                    owner = owner,
                    repo = name,
                    token = token,
                    branch = branch,
                    siteUrl = siteUrl,
                    appName = current.appName.trim(),
                    packageName = WorkflowCommitter.wrapperPackageName(owner, name),
                )
                awaitWorkflow(
                    owner = owner,
                    repo = name,
                    workflow = WorkflowCommitter.WRAPPER_WORKFLOW,
                    token = token,
                    onProgress = { percent, running ->
                        setWrap(
                            busy = true,
                            percent = percent,
                            stage = if (running && percent >= 80) "Signing APK" else "Generating wrapper",
                        )
                    },
                )
                setWrap(busy = true, percent = 96, stage = "Signing APK")
                val fetched = awaitApkRelease(owner, name, token)
                setWrap(busy = false, percent = 0, stage = "")
                addRepository(
                    repository = runCatching { GitHubApi.repository(owner, name, token) }.getOrNull(),
                    owner = owner,
                    name = name,
                    source = AppSource.WRAPPER,
                    fetched = fetched,
                )
            } catch (error: Exception) {
                setWrap(busy = false, percent = 0, stage = "", error = error.message ?: "The wrapper build failed.")
            }
        }
    }

    private fun setWrap(busy: Boolean, percent: Int, stage: String, error: String? = null) {
        _state.value = _state.value.copy(
            wrap = _state.value.wrap.copy(busy = busy, percent = percent, stage = stage, error = error),
        )
    }

    // ---------------------------------------------------------------- build with Actions

    fun loadBuildCandidates() {
        val token = token()
        if (token == null) {
            _state.value = _state.value.copy(
                build = _state.value.build.copy(
                    loading = false,
                    error = "Sign in first — Vault commits the workflow to a repository you own.",
                ),
            )
            return
        }
        if (_state.value.build.candidates.isNotEmpty() || _state.value.build.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(build = _state.value.build.copy(loading = true, error = null))
            try {
                val owned = GitHubApi.myRepositories(token).take(40)
                val limiter = Semaphore(4)
                val candidates = coroutineScope {
                    owned.map { repository ->
                        async {
                            limiter.withPermit {
                                val slug = repository.text("full_name")
                                val owner = slug.substringBefore('/')
                                val name = slug.substringAfter('/')
                                val hasApk = runCatching {
                                    GitHubApi.releases(owner, name, token, perPage = 5)
                                        .any { it.apkAssets.isNotEmpty() }
                                }.getOrDefault(false)
                                if (hasApk) {
                                    null
                                } else {
                                    val kind = runCatching { WorkflowCommitter.detectKind(owner, name, token) }
                                        .getOrDefault(RepoKind.UNKNOWN)
                                    when (kind) {
                                        RepoKind.GRADLE_PROJECT -> RepoCandidate(
                                            slug = slug,
                                            description = "Gradle project detected",
                                            tag = "Buildable",
                                            buildable = true,
                                        )

                                        RepoKind.PAGES_SITE -> RepoCandidate(
                                            slug = slug,
                                            description = "Static website · use Wrap instead",
                                            tag = "Website",
                                            buildable = false,
                                            isWebsite = true,
                                        )

                                        RepoKind.UNKNOWN -> null
                                    }
                                }
                            }
                        }
                    }.map { it.await() }
                }.filterNotNull()
                _state.value = _state.value.copy(
                    build = _state.value.build.copy(
                        loading = false,
                        candidates = candidates,
                        selected = candidates.firstOrNull { it.buildable }?.slug,
                    ),
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    build = _state.value.build.copy(
                        loading = false,
                        error = error.message ?: "Could not list your repositories.",
                    ),
                )
            }
        }
    }

    fun selectBuildRepo(slug: String) {
        if (_state.value.build.busy) return
        _state.value = _state.value.copy(build = _state.value.build.copy(selected = slug, error = null))
    }

    fun startBuild() {
        val current = _state.value.build
        if (current.busy) return
        val slug = current.selected ?: run {
            _state.value = _state.value.copy(build = current.copy(error = "Choose a repository first."))
            return
        }
        val token = token() ?: run {
            _state.value = _state.value.copy(build = current.copy(error = "Sign in first."))
            return
        }
        viewModelScope.launch {
            val owner = slug.substringBefore('/')
            val name = slug.substringAfter('/')
            try {
                setBuild(busy = true, percent = 4, stage = 0, error = null)
                val branch = WorkflowCommitter.defaultBranch(owner, name, token)
                WorkflowCommitter.commitBuildWorkflow(context, owner, name, token, branch)
                setBuild(busy = true, percent = 20, stage = 1)
                awaitWorkflow(
                    owner = owner,
                    repo = name,
                    workflow = WorkflowCommitter.BUILD_WORKFLOW,
                    token = token,
                    onProgress = { percent, _ -> setBuild(busy = true, percent = percent, stage = 1) },
                )
                setBuild(busy = true, percent = 88, stage = 2)
                val fetched = awaitApkRelease(owner, name, token)
                setBuild(busy = false, percent = 0, stage = 0)
                addRepository(
                    repository = runCatching { GitHubApi.repository(owner, name, token) }.getOrNull(),
                    owner = owner,
                    name = name,
                    source = AppSource.ACTIONS_BUILD,
                    fetched = fetched,
                )
            } catch (error: Exception) {
                setBuild(busy = false, percent = 0, stage = 0, error = error.message ?: "The build failed.")
            }
        }
    }

    private fun setBuild(busy: Boolean, percent: Int, stage: Int, error: String? = null) {
        _state.value = _state.value.copy(
            build = _state.value.build.copy(busy = busy, percent = percent, stage = stage, error = error),
        )
    }

    // ---------------------------------------------------------------- workflow polling

    private suspend fun awaitWorkflow(
        owner: String,
        repo: String,
        workflow: String,
        token: String,
        onProgress: (Int, Boolean) -> Unit,
    ) {
        val deadline = System.currentTimeMillis() + WORKFLOW_TIMEOUT_MS
        var percent = 30
        while (System.currentTimeMillis() < deadline) {
            delay(6_000)
            val run = runCatching { GitHubApi.latestRun(owner, repo, workflow, token) }.getOrNull()
            if (run != null && run.status == "completed") {
                if (run.conclusion == "success") return
                throw IOException("The build finished as ${run.conclusion}. Open the run on GitHub for the log.")
            }
            percent = (percent + 5).coerceAtMost(85)
            onProgress(percent, run != null && run.status == "in_progress")
        }
        throw IOException("The workflow is still running — check Actions on GitHub, then track the repository.")
    }

    private suspend fun awaitApkRelease(owner: String, repo: String, token: String): List<ReleaseInfo> {
        val deadline = System.currentTimeMillis() + RELEASE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val fetched = runCatching { GitHubApi.releases(owner, repo, token) }.getOrDefault(emptyList())
            if (fetched.any { it.apkAssets.isNotEmpty() }) return fetched
            delay(6_000)
        }
        throw IOException("The workflow finished but published no APK release.")
    }

    private companion object {
        const val DEVICE_SCOPES = "repo workflow"
        const val STALE_AFTER_MS = 60 * 1000L
        const val RESUME_GRACE_MS = 1_500L
        const val QUEUED = "Queued"
        const val WORKFLOW_TIMEOUT_MS = 15 * 60 * 1000L
        const val RELEASE_TIMEOUT_MS = 3 * 60 * 1000L
    }
}
