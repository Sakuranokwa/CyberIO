package net.liplum.update

import arc.util.Log
import kotlinx.coroutines.*
import mindustry.Vars
import mindustry.ui.dialogs.ModsDialog
import net.liplum.HeadlessOnly
import net.liplum.Meta
import net.liplum.Settings
import net.liplum.utils.getMethodBy
import java.lang.reflect.Method
import java.net.URL
import kotlin.coroutines.CoroutineContext

private val ImportModFunc: Method = ModsDialog::class.java.getMethodBy(
    "githubImportMod", String::class.java, Boolean::class.java
)

private fun ModsDialog.ImportMod(repo: String, isJava: Boolean) {
    ImportModFunc(this, repo, isJava)
}

object Updater : CoroutineScope {
    var latestVersion: Version2 = Meta.DetailedVersion
    var accessJob: Job? = null
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO
    const val GitHub = "https://raw.githubusercontent.com/liplum/CyberIO/master/update"
    fun check() {
        if (Settings.ShowUpdate) {
            Log.info("CyberIO update checking...")
            accessJob = launch(
                CoroutineExceptionHandler { _, e ->
                    Log.err("Can't fetch the latest version of CyberIO because of ${e.javaClass} ${e.message}.")
                }
            ) {
                val url = URL(GitHub)
                val bytes = url.readBytes()
                val updateInfo = String(bytes)
                val allInfos = updateInfo.split('\n')
                val versionInfo = allInfos[0]
                latestVersion = runCatching {
                    Version2.valueOf(versionInfo)
                }.getOrDefault(Meta.DetailedVersion)
                Log.info("The latest CyberIO version is $latestVersion")
                HeadlessOnly {
                    if (requireUpdate) {
                        Log.info("Current CyberIO is ${Meta.DetailedVersion} and need to be updated to $latestVersion.")
                    }
                }
            }
        }
    }
    @JvmStatic
    fun updateSelf() {
        val modsDialog = Vars.ui.mods
        modsDialog.show()
        modsDialog.ImportMod(Meta.Repo, true)
    }

    val requireUpdate: Boolean
        get() = latestVersion > Meta.DetailedVersion
}
