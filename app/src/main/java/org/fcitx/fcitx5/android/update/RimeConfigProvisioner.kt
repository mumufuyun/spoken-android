/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import android.content.Context
import android.os.Environment
import org.fcitx.fcitx5.android.BuildConfig
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object RimeConfigProvisioningPolicy {
    enum class Decision {
        READY,
        WAIT,
        DOWNLOAD
    }

    fun decide(
        installedVersion: String?,
        requiredVersion: String,
        healthy: Boolean,
        matchingDownloadPending: Boolean
    ): Decision = when {
        healthy && installedVersion != null &&
            UpdateVersion.compare(installedVersion, requiredVersion) >= 0 -> Decision.READY
        matchingDownloadPending -> Decision.WAIT
        else -> Decision.DOWNLOAD
    }
}

object RimeConfigProvisioner {
    private const val ArchiveName = "rime-ice-t9-phone-main.zip"
    private const val ReleasePage =
        "https://github.com/Rizumu85/rime-ice-t9-phone/releases"
    private val running = AtomicBoolean()
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "RimeConfigProvisioning").apply { isDaemon = true }
    }
    private val PollDelaysSeconds = longArrayOf(0, 2, 4, 8, 15, 30, 60)
    private const val INITIAL_DELAY_MILLIS = 1500L

    fun ensureRequiredConfig(context: Context) {
        val appContext = context.applicationContext
        // Delay the first pass so IME service binding and its first frame win the
        // FcitxDaemon lock on cold start; provisioning is not on the critical path.
        executor.schedule(
            {
                runCatching { ensureRequiredConfigInternal(appContext) }
                    .onFailure { Timber.w(it, "Unable to schedule required Rime configuration") }
            },
            INITIAL_DELAY_MILLIS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun ensureRequiredConfigInternal(context: Context) {
        if (BuildConfig.PERFORMANCE_HARNESS) return
        val version = BuildConfig.RIME_CONFIG_BASELINE_VERSION
        val installedVersion = RimeConfigVersionStore.currentVersion(context)
        val healthy = RimeConfigVersionStore.isHealthy(context)
        val preliminaryDecision = RimeConfigProvisioningPolicy.decide(
            installedVersion = installedVersion,
            requiredVersion = version,
            healthy = healthy,
            matchingDownloadPending = false
        )
        if (preliminaryDecision == RimeConfigProvisioningPolicy.Decision.READY) return
        val decision = RimeConfigProvisioningPolicy.decide(
            installedVersion = installedVersion,
            requiredVersion = version,
            healthy = healthy,
            matchingDownloadPending = UpdateDownloader.hasPending(
                context,
                UpdateComponent.RIME_CONFIG,
                version
            )
        )

        if (!running.compareAndSet(false, true)) return
        try {
            if (decision == RimeConfigProvisioningPolicy.Decision.DOWNLOAD) {
                // Single-APK product: the baseline config archive ships in assets. Install it
                // locally and only fall back to the network when the bundled copy fails.
                if (installBundled(context)) {
                    running.set(false)
                    return
                }
                UpdateDownloader.enqueue(
                    context = context,
                    artifact = requiredArtifact(),
                    options = UpdateDownloader.Options(
                        automatic = true,
                        expectedSha256 = BuildConfig.RIME_CONFIG_BASELINE_SHA256
                    )
                )
            }
            schedulePoll(context, attempt = 0)
        } catch (error: Throwable) {
            running.set(false)
            throw error
        }
    }

    private fun installBundled(context: Context): Boolean {
        val result = runCatching {
            RimeConfigInstaller.install(
                context = context,
                pending = UpdateDownloader.Pending(
                    component = UpdateComponent.RIME_CONFIG,
                    version = BuildConfig.RIME_CONFIG_BASELINE_VERSION,
                    automatic = true,
                    expectedSha256 = BuildConfig.RIME_CONFIG_BASELINE_SHA256
                ),
                openArchive = { context.assets.open("rime/$ArchiveName") }
            )
        }.onFailure { Timber.w(it, "Unable to install bundled Rime configuration") }
        return result.getOrNull()?.isSuccess == true
    }

    private fun schedulePoll(context: Context, attempt: Int) {
        executor.schedule(
            {
                // Every terminal exit resets `running` exactly once; the reschedule path
                // returns false and keeps the flag because the next poll owns it. Any
                // escaping failure (e.g. IOException while hashing the completed archive)
                // must not strand the flag, or this process loses self-healing.
                val finished = try {
                    pollOnce(context, attempt)
                } catch (error: Throwable) {
                    Timber.w(error, "Rime configuration provisioning poll failed")
                    true
                }
                if (finished) running.set(false)
            },
            PollDelaysSeconds[attempt],
            TimeUnit.SECONDS
        )
    }

    /** Returns true when provisioning reaches a terminal state for this process. */
    private fun pollOnce(context: Context, attempt: Int): Boolean {
        if (isReady(context)) {
            UpdateDownloader.cancelAutomatic(
                context,
                UpdateComponent.RIME_CONFIG,
                BuildConfig.RIME_CONFIG_BASELINE_VERSION
            )
            return true
        }
        val archive = completedArchive(context)
        if (archive != null) {
            UpdateDownloader.cancelAutomatic(
                context,
                UpdateComponent.RIME_CONFIG,
                BuildConfig.RIME_CONFIG_BASELINE_VERSION
            )
            val installed = RimeConfigInstaller.install(
                context,
                archive,
                BuildConfig.RIME_CONFIG_BASELINE_VERSION,
                BuildConfig.RIME_CONFIG_BASELINE_SHA256
            )
            if (installed.isSuccess) {
                return true
            }
        }

        if (attempt < PollDelaysSeconds.lastIndex) {
            schedulePoll(context, attempt + 1)
            return false
        }

        // The system downloader is the persistent primary path. A direct transfer is a
        // bounded fallback for vendor DownloadManager implementations that never finish
        // redirected release assets even though ordinary app networking still works.
        val fallback = runCatching {
            val downloaded = RimeConfigProvisioningTransport.obtain(
                cacheDir = context.cacheDir,
                version = BuildConfig.RIME_CONFIG_BASELINE_VERSION,
                downloadUrl = BuildConfig.RIME_CONFIG_BASELINE_URL,
                expectedSha256 = BuildConfig.RIME_CONFIG_BASELINE_SHA256
            )
            RimeConfigInstaller.install(
                context,
                downloaded,
                BuildConfig.RIME_CONFIG_BASELINE_VERSION,
                BuildConfig.RIME_CONFIG_BASELINE_SHA256
            ).getOrThrow()
        }
        if (fallback.isSuccess) {
            UpdateDownloader.cancelAutomatic(
                context,
                UpdateComponent.RIME_CONFIG,
                BuildConfig.RIME_CONFIG_BASELINE_VERSION
            )
        } else {
            Timber.w(
                fallback.exceptionOrNull(),
                "Rime configuration provisioning paused"
            )
        }
        return true
    }

    private fun completedArchive(context: Context): java.io.File? {
        val checkpoint = RimeConfigProvisioningTransport.checkpointFile(
            context.cacheDir,
            BuildConfig.RIME_CONFIG_BASELINE_VERSION
        )
        if (
            RimeConfigProvisioningTransport.isVerified(
                checkpoint,
                BuildConfig.RIME_CONFIG_BASELINE_SHA256
            )
        ) {
            return checkpoint
        }
        val downloaded = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.resolve(ArchiveName)
            ?: return null
        return runCatching {
            RimeConfigProvisioningTransport.adopt(
                source = downloaded,
                cacheDir = context.cacheDir,
                version = BuildConfig.RIME_CONFIG_BASELINE_VERSION,
                expectedSize = BuildConfig.RIME_CONFIG_BASELINE_SIZE,
                expectedSha256 = BuildConfig.RIME_CONFIG_BASELINE_SHA256
            )
        }.onFailure {
            Timber.w(it, "Unable to checkpoint DownloadManager Rime archive")
        }.getOrNull()
    }

    private fun requiredArtifact() = UpdateArtifact(
        component = UpdateComponent.RIME_CONFIG,
        version = BuildConfig.RIME_CONFIG_BASELINE_VERSION,
        pageUrl = ReleasePage,
        assets = listOf(
            UpdateArtifact.Asset(
                name = ArchiveName,
                downloadUrl = BuildConfig.RIME_CONFIG_BASELINE_URL
            )
        )
    )

    private fun isReady(context: Context): Boolean {
        val installedVersion = RimeConfigVersionStore.currentVersion(context) ?: return false
        return RimeConfigVersionStore.isHealthy(context) &&
            UpdateVersion.compare(
                installedVersion,
                BuildConfig.RIME_CONFIG_BASELINE_VERSION
            ) >= 0
    }

}
