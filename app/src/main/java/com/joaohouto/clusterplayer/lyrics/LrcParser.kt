package com.joaohouto.clusterplayer.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

data class LrcLine(
    val timeMs: Long,
    val text: String
)

class LrcParser {
    companion object {
        private const val TAG = "LrcParser"
        // Matches [01:23.45] or [01:23] or [01:23.456]
        private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?](.*)")

        suspend fun findAndParseLrc(audioFilePath: String): List<LrcLine>? = withContext(Dispatchers.IO) {
            try {
                if (audioFilePath.isBlank()) return@withContext null
                val audioFile = File(audioFilePath)
                if (!audioFile.exists()) return@withContext null

                val baseName = audioFile.nameWithoutExtension
                val parentDir = audioFile.parentFile ?: return@withContext null

                // Look for baseName.lrc (case insensitive)
                val lrcFile = parentDir.listFiles()?.firstOrNull {
                    it.isFile && it.name.equals("$baseName.lrc", ignoreCase = true)
                } ?: return@withContext null

                parseLrcFile(lrcFile)
            } catch (e: Exception) {
                Log.w(TAG, "Error finding/parsing LRC for $audioFilePath: ${e.message}")
                null
            }
        }

        fun parseLrcFile(file: File): List<LrcLine> {
            val lines = mutableListOf<LrcLine>()
            file.forEachLine { rawLine ->
                val trimmed = rawLine.trim()
                if (trimmed.isNotEmpty()) {
                    val matcher = TIME_TAG_PATTERN.matcher(trimmed)
                    if (matcher.matches()) {
                        val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                        val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                        val millisString = matcher.group(3)
                        val text = matcher.group(4)?.trim() ?: ""

                        val millis = when {
                            millisString == null -> 0L
                            millisString.length == 1 -> (millisString.toLongOrNull() ?: 0L) * 100
                            millisString.length == 2 -> (millisString.toLongOrNull() ?: 0L) * 10
                            else -> millisString.take(3).toLongOrNull() ?: 0L
                        }

                        val totalTimeMs = (minutes * 60 + seconds) * 1000 + millis
                        if (text.isNotEmpty()) {
                            lines.add(LrcLine(totalTimeMs, text))
                        }
                    }
                }
            }
            return lines.sortedBy { it.timeMs }
        }

        fun getActiveLine(lines: List<LrcLine>, positionMs: Long): String? {
            if (lines.isEmpty()) return null
            var activeText: String? = null
            for (line in lines) {
                if (line.timeMs <= positionMs) {
                    activeText = line.text
                } else {
                    break
                }
            }
            return activeText
        }
    }
}
