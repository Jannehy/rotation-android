package org.rotationmusic.app.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.rotationmusic.app.R

/** Who is signed in, against which server – and whether this is the demo. */
class Session(context: Context) : ViewModel() {

    enum class Stage { LOADING, SERVER, LOGIN, READY }

    private val app = context.applicationContext
    private val prefs = Prefs(app)

    var stage by mutableStateOf(Stage.LOADING)
        private set
    var source by mutableStateOf<Source?>(null)
        private set
    var me by mutableStateOf<MeInfo?>(null)
        private set
    var viewing by mutableStateOf<Person?>(null)
    var errorRes by mutableStateOf<Int?>(null)
    var isWorking by mutableStateOf(false)
        private set

    /**
     * The recap playlists written during this run, by year.
     *
     * The same button sits on the last story card and at the foot of the
     * recap. Pressing it in one place has to settle the other, and the recap
     * is not fetched again when the story closes – so the answer lives here
     * rather than in either screen.
     */
    val wrappedPlaylists = mutableStateMapOf<Int, WrappedPlaylist>()

    val scope: CoroutineScope get() = viewModelScope
    val isDemo: Boolean get() = source?.isDemo == true
    val settings: Prefs get() = prefs

    /** The palette, observable – so a change repaints without a restart. */
    var accent by mutableStateOf(prefs.accent)
        private set

    fun chooseAccent(id: String) {
        prefs.accent = id
        accent = id
    }
    val serverAddress: String get() = prefs.serverAddress
    val username: String? get() = prefs.username
    private val api: Api? get() = source as? Api

    init {
        restore()
    }

    private fun restore() {
        val address = prefs.serverAddress
        val url = if (address.isBlank()) null else Api.normalize(address)
        if (url == null) {
            stage = Stage.SERVER
            return
        }
        val client = Api(url)
        source = client
        scope.launch {
            try {
                me = client.me()
                stage = Stage.READY
            } catch (e: SourceException.Unauthorized) {
                // The server forgot us – sign in again with the stored password.
                val user = prefs.username
                val password = user?.let { prefs.password(it) }
                if (user != null && password != null) {
                    signIn(user, password, quiet = true)
                    if (stage != Stage.READY) stage = Stage.LOGIN
                } else {
                    stage = Stage.LOGIN
                }
            } catch (e: Exception) {
                stage = Stage.LOGIN
            }
        }
    }

    fun connect(address: String, onDone: (Boolean) -> Unit = {}) {
        val url = Api.normalize(address)
        if (url == null) {
            errorRes = R.string.error_address
            onDone(false)
            return
        }
        isWorking = true
        val client = Api(url)
        scope.launch {
            try {
                client.probe()
                prefs.serverAddress = url.toString()
                source = client
                errorRes = null
                stage = Stage.LOGIN
                onDone(true)
            } catch (e: SourceException) {
                errorRes = e.messageRes()
                onDone(false)
            } finally {
                isWorking = false
            }
        }
    }

    fun signIn(username: String, password: String, quiet: Boolean = false) {
        val client = api ?: return
        if (!quiet) isWorking = true
        scope.launch {
            try {
                client.login(username, password)
                me = client.me()
                prefs.username = username
                prefs.savePassword(username, password)
                errorRes = null
                stage = Stage.READY
            } catch (e: SourceException) {
                if (!quiet) errorRes = e.messageRes()
            } finally {
                isWorking = false
            }
        }
    }

    fun startDemo() {
        val demo = DemoSource.load(app)
        if (demo == null) {
            errorRes = R.string.error_generic
            return
        }
        source = demo
        scope.launch {
            me = demo.me()
            stage = Stage.READY
        }
    }

    fun signOut() {
        viewing = null
        val client = api
        scope.launch { client?.logout() }
        prefs.clearCredentials()
        me = null
        if (isDemo) {
            val url = if (prefs.serverAddress.isBlank()) null else Api.normalize(prefs.serverAddress)
            source = url?.let { Api(it) }
            stage = if (url == null) Stage.SERVER else Stage.LOGIN
        } else {
            stage = Stage.LOGIN
        }
    }

    fun forgetServer() {
        prefs.serverAddress = ""
        prefs.clearCredentials()
        source = null
        me = null
        viewing = null
        stage = Stage.SERVER
    }

    /** Runs a request and turns an expired session into a silent re-sign-in. */
    suspend fun <T> perform(work: suspend () -> T): T? = try {
        work()
    } catch (e: SourceException.Unauthorized) {
        val user = prefs.username
        val password = user?.let { prefs.password(it) }
        if (user != null && password != null) {
            runCatching {
                api?.login(user, password)
                work()
            }.getOrNull()
        } else {
            stage = Stage.LOGIN
            null
        }
    } catch (e: Exception) {
        null
    }
}
