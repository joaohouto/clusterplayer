package com.joaohouto.clusterplayer.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.util.regex.Pattern

data class LrcLine(
    val timeMs: Long,
    val text: String
)

class LrcParser {
    companion object {
        private const val TAG = "LrcParser"

        // Matches timestamps like:
        // [01:23.45], [01:23:45], [01:23,45], [01:23.456], [01:23], [102:15.00]
        private val TIME_TAG_PATTERN = Pattern.compile(
            "\\[\\s*(\\d+):(\\d{2})(?:[.:,](\\d{1,3}))?\\s*]"
        )

        // Matches [offset:+500] or [offset:-200]
        private val OFFSET_TAG_PATTERN = Pattern.compile(
            "\\[offset:\\s*([+-]?\\d+)\\s*]",
            Pattern.CASE_INSENSITIVE
        )

        // Matches inline karaoke timestamps like <00:12.34> or <12:34>
        private val INLINE_TAG_PATTERN = Pattern.compile("<[^>]+>")

        suspend fun findAndParseLrc(audioFilePath: String): List<LrcLine>? = withContext(Dispatchers.IO) {
            try {
                if (audioFilePath.isBlank()) return@withContext null
                val audioFile = File(audioFilePath)
                if (!audioFile.exists()) return@withContext null

                val baseName = audioFile.nameWithoutExtension
                val parentDir = audioFile.parentFile ?: return@withContext null

                // 1. Direct fast check in parent directory (case-insensitive)
                val directLrc = File(parentDir, "$baseName.lrc")
                val directLrcUpper = File(parentDir, "$baseName.LRC")
                val foundDirect = when {
                    directLrc.isFile -> directLrc
                    directLrcUpper.isFile -> directLrcUpper
                    else -> null
                }

                if (foundDirect != null) {
                    return@withContext parseLrcFile(foundDirect)
                }

                // 2. Check in subdirectories "Lyrics" or "lyrics" (common organization pattern)
                val lyricsDir = File(parentDir, "Lyrics").takeIf { it.isDirectory }
                    ?: File(parentDir, "lyrics").takeIf { it.isDirectory }

                if (lyricsDir != null) {
                    val subLrc = File(lyricsDir, "$baseName.lrc")
                    val subLrcUpper = File(lyricsDir, "$baseName.LRC")
                    val foundSub = when {
                        subLrc.isFile -> subLrc
                        subLrcUpper.isFile -> subLrcUpper
                        else -> null
                    }
                    if (foundSub != null) {
                        return@withContext parseLrcFile(foundSub)
                    }
                }

                // 3. Fallback: listFiles case-insensitive lookup
                val lrcFile = parentDir.listFiles()?.firstOrNull {
                    it.isFile && it.name.equals("$baseName.lrc", ignoreCase = true)
                } ?: lyricsDir?.listFiles()?.firstOrNull {
                    it.isFile && it.name.equals("$baseName.lrc", ignoreCase = true)
                } ?: return@withContext null

                parseLrcFile(lrcFile)
            } catch (e: Exception) {
                Log.w(TAG, "Error finding/parsing LRC for $audioFilePath: ${e.message}")
                null
            }
        }

        fun parseLrcFile(file: File): List<LrcLine> {
            val content = readLrcContent(file)
            return parseLrcString(content)
        }

        private fun readLrcContent(file: File): String {
            return try {
                val bytes = file.readBytes()
                if (bytes.isEmpty()) return ""

                // Detect and remove UTF-8 BOM if present (0xEF, 0xBB, 0xBF)
                val textUtf8 = if (bytes.size >= 3 &&
                    bytes[0] == 0xEF.toByte() &&
                    bytes[1] == 0xBB.toByte() &&
                    bytes[2] == 0xBF.toByte()
                ) {
                    String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
                } else {
                    String(bytes, Charsets.UTF_8)
                }

                // If UTF-8 contains replacement chars (\uFFFD), fallback to ISO-8859-1 for legacy encodings
                if (textUtf8.contains('\uFFFD')) {
                    try {
                        String(bytes, Charset.forName("ISO-8859-1"))
                    } catch (e: Exception) {
                        textUtf8
                    }
                } else {
                    textUtf8
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error reading file ${file.name}: ${e.message}")
                ""
            }
        }

        fun parseLrcString(content: String): List<LrcLine> {
            if (content.isBlank()) return emptyList()

            var fileOffsetMs = 0L
            val rawEntries = mutableListOf<LrcLine>()

            content.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEach

                // Check for [offset:+/-xxx] tag
                val offsetMatcher = OFFSET_TAG_PATTERN.matcher(line)
                if (offsetMatcher.matches()) {
                    fileOffsetMs = offsetMatcher.group(1)?.toLongOrNull() ?: 0L
                    return@forEach
                }

                // Find all time tags in this line (e.g. [00:12.34][00:45.67]repeated lyrics)
                val timeMatcher = TIME_TAG_PATTERN.matcher(line)
                val timestamps = mutableListOf<Long>()
                var lastMatchEnd = 0

                while (timeMatcher.find()) {
                    // Ensure matches are contiguous at the beginning of the line
                    val prefix = line.substring(lastMatchEnd, timeMatcher.start()).trim()
                    if (prefix.isEmpty()) {
                        val minutes = timeMatcher.group(1)?.toLongOrNull() ?: 0L
                        val seconds = timeMatcher.group(2)?.toLongOrNull() ?: 0L
                        val fractionStr = timeMatcher.group(3)

                        val millis = when {
                            fractionStr == null -> 0L
                            fractionStr.length == 1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
                            fractionStr.length == 2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
                            else -> fractionStr.take(3).toLongOrNull() ?: 0L
                        }

                        val totalTimeMs = minutes * 60000L + seconds * 1000L + millis
                        timestamps.add(totalTimeMs)
                        lastMatchEnd = timeMatcher.end()
                    } else {
                        break
                    }
                }

                if (timestamps.isNotEmpty()) {
                    var lyricsText = if (lastMatchEnd < line.length) {
                        line.substring(lastMatchEnd).trim()
                    } else {
                        ""
                    }

                    // Clean inline karaoke tags (<00:12.34>)
                    if (lyricsText.contains('<')) {
                        lyricsText = INLINE_TAG_PATTERN.matcher(lyricsText).replaceAll("").trim()
                    }

                    // Standardize multiple spaces
                    if (lyricsText.contains("  ")) {
                        lyricsText = lyricsText.replace(Regex("\\s+"), " ")
                    }

                    for (rawTime in timestamps) {
                        // In official LRC specification:
                        // + offset shifts time up (lyrics appear earlier -> rawTime - offset)
                        // - offset shifts time down (lyrics appear later -> rawTime - offset)
                        val adjustedTime = (rawTime - fileOffsetMs).coerceAtLeast(0L)
                        rawEntries.add(LrcLine(adjustedTime, lyricsText))
                    }
                }
            }

            return rawEntries.sortedBy { it.timeMs }
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
            return if (activeText.isNullOrBlank()) null else activeText
        }
    }
}
