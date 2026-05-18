package de.lolo.rssreader.debug

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    private const val APP_TAG = "RSSReaderDebug"
    private const val MAX_LOG_SIZE_BYTES = 256 * 1024L

    @Volatile
    private var logFile: File? = null
    @Volatile
    private var isInitialized = false
    @Volatile
    private var uncaughtHandlerInstalled = false
    @Volatile
    private var debugEnabled = false

    private val timestampFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.GERMANY)

    fun init(context: Context) {
        debugEnabled = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debugEnabled) {
            return
        }
        if (isInitialized) {
            return
        }
        val debugDir = File(context.filesDir, "debug").apply { mkdirs() }
        logFile = File(debugDir, "rss-reader-debug.log")
        rotateIfNeeded()
        installUncaughtExceptionHandler()
        isInitialized = true
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        session(
            "Debug-Logging initialisiert: ${logFile?.absolutePath.orEmpty()} | " +
                "version=${packageInfo.versionName.orEmpty()} ($versionCode)"
        )
        d("App", "Debug-Logging initialisiert: ${logFile?.absolutePath.orEmpty()}")
    }

    fun d(tag: String, message: String) = write("D", tag, message, null)

    fun i(tag: String, message: String) = write("I", tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) = write("W", tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) = write("E", tag, message, throwable)

    fun currentLogFilePath(): String? {
        return if (debugEnabled) {
            logFile?.absolutePath
        } else {
            null
        }
    }

    private fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        if (!debugEnabled) {
            return
        }

        val formatted = buildString {
            append(timestampFormatter.format(Date()))
            append(" [")
            append(level)
            append("] ")
            append(tag)
            append(": ")
            append(message)
            if (throwable != null) {
                append(" | ")
                append(throwable.javaClass.simpleName)
                append(": ")
                append(throwable.message.orEmpty())
            }
        }

        runCatching {
            when (level) {
                "D" -> Log.d(APP_TAG, formatted)
                "I" -> Log.i(APP_TAG, formatted)
                "W" -> Log.w(APP_TAG, formatted, throwable)
                else -> Log.e(APP_TAG, formatted, throwable)
            }
        }

        synchronized(this) {
            runCatching {
                rotateIfNeeded()
                logFile?.appendText(formatted + "\n")
            }
        }
    }

    private fun session(message: String) {
        synchronized(this) {
            runCatching {
                rotateIfNeeded()
                logFile?.appendText(
                    buildString {
                        appendLine()
                        appendLine("=".repeat(88))
                        appendLine(message)
                        appendLine("=".repeat(88))
                    }
                )
            }
        }
    }

    private fun installUncaughtExceptionHandler() {
        if (uncaughtHandlerInstalled) {
            return
        }
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            write(
                level = "E",
                tag = "Crash",
                message = "Unbehandelte Exception im Thread ${thread.name}",
                throwable = throwable
            )
            previousHandler?.uncaughtException(thread, throwable)
        }
        uncaughtHandlerInstalled = true
    }

    private fun rotateIfNeeded() {
        val file = logFile ?: return
        if (file.exists() && file.length() > MAX_LOG_SIZE_BYTES) {
            file.writeText("")
        } else if (!file.exists()) {
            file.createNewFile()
        }
    }
}

