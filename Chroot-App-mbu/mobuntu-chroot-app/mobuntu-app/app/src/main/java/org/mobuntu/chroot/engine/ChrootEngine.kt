package org.mobuntu.chroot.engine

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CmdResult(
    val success: Boolean,
    val output: List<String>,
)

object ChrootEngine {

    private const val DISTRO   = "mobuntu"
    private const val BASE_DIR = "/data/local/chroot-distro"
    private const val BIN      = "/data/local/bin/chroot-distro"

    // ── Lifecycle ────────────────────────────────────────────────────────────

    suspend fun install(tarballPath: String): CmdResult = exec(
        "mkdir -p $BASE_DIR",
        "cp \"$tarballPath\" $BASE_DIR/$DISTRO.tar.gz",
        "$BIN add $DISTRO",
        "$BIN install $DISTRO",
    )

    suspend fun mount()     = exec("$BIN mount $DISTRO")
    suspend fun unmount()   = exec("$BIN unmount --force $DISTRO")
    suspend fun login()     = exec("$BIN login $DISTRO")

    suspend fun backup(path: String? = null): CmdResult {
        val cmd = if (path != null) "$BIN backup $DISTRO $path" else "$BIN backup $DISTRO"
        return exec(cmd)
    }

    suspend fun restore(path: String? = null): CmdResult {
        val cmd = if (path != null) "$BIN restore $DISTRO $path" else "$BIN restore --default $DISTRO"
        return exec(cmd)
    }

    suspend fun reinstall() = exec("$BIN reinstall --force $DISTRO")
    suspend fun remove()    = exec("$BIN remove $DISTRO")

    suspend fun command(cmd: String) = exec("$BIN command $DISTRO \"$cmd\"")

    suspend fun runCustom(cmd: String) = exec(cmd)

    // ── Status ───────────────────────────────────────────────────────────────

    suspend fun isMounted(): Boolean = withContext(Dispatchers.IO) {
        val r = Shell.cmd("mountpoint -q $BASE_DIR/$DISTRO/proc && echo yes || echo no").exec()
        r.out.any { it.contains("yes") }
    }

    suspend fun isInstalled(): Boolean = withContext(Dispatchers.IO) {
        val r = Shell.cmd("[ -d $BASE_DIR/$DISTRO ] && echo yes || echo no").exec()
        r.out.any { it.contains("yes") }
    }

    suspend fun isEngineInstalled(): Boolean = withContext(Dispatchers.IO) {
        val r = Shell.cmd("[ -f $BIN ] && echo yes || echo no").exec()
        r.out.any { it.contains("yes") }
    }

    suspend fun installSize(): String = withContext(Dispatchers.IO) {
        val r = Shell.cmd("du -sh $BASE_DIR/$DISTRO 2>/dev/null | cut -f1").exec()
        r.out.firstOrNull()?.trim() ?: "unknown"
    }

    // ── Engine bootstrap ─────────────────────────────────────────────────────
    // Called on first run to extract the bundled chroot-distro script

    suspend fun bootstrapEngine(scriptContent: String): CmdResult = exec(
        "mkdir -p /data/local/bin",
        "cat > $BIN << 'CDEOF'\n$scriptContent\nCDEOF",
        "chmod +x $BIN",
    )

    // ── Internal ─────────────────────────────────────────────────────────────

    private suspend fun exec(vararg cmds: String): CmdResult = withContext(Dispatchers.IO) {
        val result = Shell.cmd(*cmds).exec()
        CmdResult(
            success = result.isSuccess,
            output  = result.out,
        )
    }

    private suspend fun exec(cmd: String): CmdResult = exec(*arrayOf(cmd))
}
