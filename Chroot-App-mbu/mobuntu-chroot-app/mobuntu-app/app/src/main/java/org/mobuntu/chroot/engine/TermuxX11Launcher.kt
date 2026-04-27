package org.mobuntu.chroot.engine

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mobuntu.chroot.settings.AppSettings

object TermuxX11Launcher {

    private const val CHROOT_ROOT = "/data/local/chroot-distro/mobuntu"
    private const val TX11_PKG    = "com.termux.x11"
    private const val DISPLAY     = ":0"

    suspend fun launch(settings: AppSettings): CmdResult = withContext(Dispatchers.IO) {

        // Resolve Termux:X11 APK path for app_process classloader
        val classpathResult = Shell.cmd(
            "/system/bin/pm path $TX11_PKG | cut -d: -f2"
        ).exec()

        val classpath = classpathResult.out.firstOrNull()?.trim()
            ?: return@withContext CmdResult(
                success = false,
                output  = listOf("ERROR: Termux:X11 (com.termux.x11) is not installed."),
            )

        val xstartup = buildXstartup(settings.ui)

        // For chroot + Termux:X11 we must:
        // 1. Disable SELinux enforcement (X11 socket needs cross-context access)
        // 2. Set TMPDIR to chroot's /tmp so X11 socket lands in the right namespace
        // 3. Set XKB_CONFIG_ROOT so xkbcomp finds keymaps inside the chroot
        // 4. Launch via app_process — this is the supported technique documented
        //    in termux/termux-x11 README for chroot environments
        val script = buildString {
            appendLine("setenforce 0")
            appendLine("export TMPDIR=$CHROOT_ROOT/tmp")
            appendLine("export XKB_CONFIG_ROOT=$CHROOT_ROOT/usr/share/X11/xkb")
            appendLine("export DISPLAY=$DISPLAY")
            appendLine("export CLASSPATH=$classpath")
            append("/system/bin/app_process / ")
            append("--nice-name=termux-x11 ")
            append("com.termux.x11.CmdEntryPoint ")
            append("$DISPLAY ")
            append("-xstartup \"$xstartup\" ")
            if (settings.resolution != "device") {
                append("-screen ${settings.resolution}x24 ")
            }
            append("-dpi ${settings.dpi} ")
            if (settings.extraArgs.isNotBlank()) {
                append(settings.extraArgs)
            }
        }

        val result = Shell.cmd(script).exec()
        CmdResult(success = result.isSuccess, output = result.out)
    }

    fun isInstalled(packageManager: android.content.pm.PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(TX11_PKG, 0)
            true
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun buildXstartup(ui: String): String = when (ui) {
        "phosh"                  -> "dbus-launch --exit-with-session phosh"
        "ubuntu-desktop-minimal" -> "dbus-launch --exit-with-session gnome-session"
        "plasma-mobile"          -> "dbus-launch --exit-with-session startplasma-x11"
        "lomiri"                 -> "dbus-launch --exit-with-session lomiri"
        else                     -> "dbus-launch --exit-with-session phosh"
    }
}
