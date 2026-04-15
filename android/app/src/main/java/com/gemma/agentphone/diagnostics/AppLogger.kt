package com.gemma.agentphone.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val LOG_DIR = "diagnostics"
    private const val LOG_FILE = "atlas-agent.log"
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileLock = Any()

    fun d(context: Context, tag: String, message: String) {
        Log.d(tag, message)
        append(context, "DEBUG", tag, message, null)
    }

    fun i(context: Context, tag: String, message: String) {
        Log.i(tag, message)
        append(context, "INFO", tag, message, null)
    }

    fun w(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        append(context, "WARN", tag, message, throwable)
    }

    fun e(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        append(context, "ERROR", tag, message, throwable)
    }

    private fun append(
        context: Context,
        level: String,
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        runCatching {
            val dir = File(context.applicationContext.filesDir, LOG_DIR).apply { mkdirs() }
            val logFile = File(dir, LOG_FILE)
            val line = buildString {
                append(timestampFormat.format(Date()))
                append(" [")
                append(level)
                append("] ")
                append(tag)
                append(": ")
                append(message)
                if (throwable != null) {
                    append(" | ")
                    append(throwable::class.java.simpleName)
                    append(": ")
                    append(throwable.localizedMessage ?: "no message")
                }
                append('\n')
            }
            synchronized(fileLock) {
                logFile.appendText(line)
            }
        }
    }
}
