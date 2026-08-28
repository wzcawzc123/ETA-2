package fuck.andes.agent.terminal

import android.content.Context
import fuck.andes.core.AndroidAgentLogger
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

internal enum class AndroguardInstallStage {
    CHECKING,
    INSTALLING,
    VERIFYING,
    COMPLETE,
}

internal data class AndroguardInstallProgress(
    val stage: AndroguardInstallStage,
)

internal sealed interface AndroguardInstallResult {
    data object AlreadyReady : AndroguardInstallResult
    data object EnvironmentNotReady : AndroguardInstallResult
    data class InsufficientSpace(val requiredBytes: Long, val availableBytes: Long) : AndroguardInstallResult
    data object Installed : AndroguardInstallResult
    data class Failed(val stage: AndroguardInstallStage) : AndroguardInstallResult
}

/**
 * 在 Alpine 基础环境通过 pipx 隔离安装 Androguard（Python 逆向/签名工具），
 * 并把入口软链到 /usr/local/bin（默认 PATH，Agent 无需改环境即可调用）。
 * 版本固定、可卸载、失败可回滚。
 */
internal class AlpineAndroguardInstaller(
    private val context: Context,
) {
    private val installMutex = Mutex()

    fun isReady(): Boolean =
        AlpineEnvironmentPaths.androguardReady(AlpineEnvironmentPaths.rootfsDir(context).absolutePath)

    suspend fun install(
        onProgress: suspend (AndroguardInstallProgress) -> Unit = {},
    ): AndroguardInstallResult {
        installMutex.lock()
        return try {
            installLocked(onProgress)
        } finally {
            installMutex.unlock()
        }
    }

    private suspend fun installLocked(
        onProgress: suspend (AndroguardInstallProgress) -> Unit,
    ): AndroguardInstallResult = withContext(Dispatchers.IO) {
        if (isReady()) return@withContext AndroguardInstallResult.AlreadyReady
        val rootfs = AlpineEnvironmentPaths.rootfsDir(context)
        onProgress(AndroguardInstallProgress(AndroguardInstallStage.CHECKING))
        if (!AlpineEnvironmentPaths.commonToolsReady(rootfs.absolutePath)) {
            return@withContext AndroguardInstallResult.EnvironmentNotReady
        }
        val availableBytes = rootfs.parentFile?.usableSpace ?: context.filesDir.usableSpace
        if (availableBytes < MIN_AVAILABLE_BYTES) {
            return@withContext AndroguardInstallResult.InsufficientSpace(
                requiredBytes = MIN_AVAILABLE_BYTES,
                availableBytes = availableBytes,
            )
        }

        coroutineContext.ensureActive()
        onProgress(AndroguardInstallProgress(AndroguardInstallStage.INSTALLING))
        if (!installAndroguard(rootfs)) {
            rollback(rootfs)
            return@withContext AndroguardInstallResult.Failed(AndroguardInstallStage.INSTALLING)
        }

        coroutineContext.ensureActive()
        onProgress(AndroguardInstallProgress(AndroguardInstallStage.VERIFYING))
        if (!verifyAndMark(rootfs)) {
            rollback(rootfs)
            return@withContext AndroguardInstallResult.Failed(AndroguardInstallStage.VERIFYING)
        }

        onProgress(AndroguardInstallProgress(AndroguardInstallStage.COMPLETE))
        AndroguardInstallResult.Installed
    }

    private suspend fun installAndroguard(rootfs: File): Boolean {
        val result = InstallerShellRunner.run(
            command = ANDROGUARD_INSTALL_SCRIPT,
            timeoutSeconds = ANDROGUARD_INSTALL_TIMEOUT_SECONDS,
            environment = TerminalEnvironment.LINUX,
            linuxRootfsPath = rootfs.absolutePath,
        )
        AndroidAgentLogger.info(
            "Androguard profile action=install " +
                "outcome=${if (result.exitCode == 0) "succeeded" else "failed"} " +
                "exitCode=${result.exitCode} outputChars=${result.output.length}",
        )
        return result.exitCode == 0
    }

    private suspend fun verifyAndMark(rootfs: File): Boolean {
        val command = """
            rm -f /${AlpineEnvironmentPaths.ANDROGUARD_MARKER}
            export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${'$'}HOME/.local/bin"
            command -v androguard >/dev/null 2>&1 || exit 81
            androguard --version >/dev/null 2>&1 || exit 82
            cat > /${AlpineEnvironmentPaths.ANDROGUARD_MARKER} <<'ETA_ANDROGUARD_EOF'
            revision=${AlpineEnvironmentPaths.ANDROGUARD_REVISION}
            version=$ANDROGUARD_EXPECTED_VERSION
            tool=androguard
            ETA_ANDROGUARD_EOF
            chmod 0644 /${AlpineEnvironmentPaths.ANDROGUARD_MARKER} || exit 83
        """.trimIndent()
        val result = InstallerShellRunner.run(
            command = command,
            timeoutSeconds = 90,
            environment = TerminalEnvironment.LINUX,
            linuxRootfsPath = rootfs.absolutePath,
        )
        AndroidAgentLogger.info(
            "Androguard profile action=verify " +
                "outcome=${if (result.exitCode == 0) "succeeded" else "failed"} " +
                "exitCode=${result.exitCode} outputChars=${result.output.length}",
        )
        return result.exitCode == 0
    }

    private suspend fun rollback(rootfs: File) {
        InstallerShellRunner.run(
            command = "rm -f /${AlpineEnvironmentPaths.ANDROGUARD_MARKER} 2>/dev/null || true",
            timeoutSeconds = 60,
            environment = TerminalEnvironment.LINUX,
            linuxRootfsPath = rootfs.absolutePath,
        )
    }

    companion object {
        private const val ANDROGUARD_EXPECTED_VERSION = "4.1.4"
        private const val MIN_AVAILABLE_BYTES = 512L * 1024L * 1024L
        private const val ANDROGUARD_INSTALL_TIMEOUT_SECONDS = 900L
        private val ANDROGUARD_INSTALL_SCRIPT = """
            set -e
            export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${'$'}HOME/.local/bin"
            pipx install androguard==$ANDROGUARD_EXPECTED_VERSION || exit 80
            mkdir -p /usr/local/bin
            ln -sf "${'$'}HOME/.local/share/pipx/venvs/androguard/bin/androguard" /usr/local/bin/androguard || exit 84
            command -v androguard >/dev/null 2>&1 || exit 81
        """.trimIndent()
    }
}
