package com.joaohouto.clusterplayer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LrcParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testParseLrcFileAndActiveLine() {
        val lrcFile = tempFolder.newFile("song.lrc")
        lrcFile.writeText(
            """
            [00:00.00]Primeira linha instrumental
            [00:05.50]Letra comecando no segundo 5 e meio
            [01:10.00]Refrão no minuto 1 e 10
            [02:00.25]Final da musica
            """.trimIndent()
        )

        val lines = LrcParser.parseLrcFile(lrcFile)
        assertEquals(4, lines.size)
        assertEquals(0L, lines[0].timeMs)
        assertEquals("Primeira linha instrumental", lines[0].text)

        assertEquals(5500L, lines[1].timeMs)
        assertEquals("Letra comecando no segundo 5 e meio", lines[1].text)

        assertEquals(70000L, lines[2].timeMs)
        assertEquals("Refrão no minuto 1 e 10", lines[2].text)

        assertEquals(120250L, lines[3].timeMs)
        assertEquals("Final da musica", lines[3].text)

        // Test getActiveLine
        assertEquals("Primeira linha instrumental", LrcParser.getActiveLine(lines, 2000L))
        assertEquals("Letra comecando no segundo 5 e meio", LrcParser.getActiveLine(lines, 5500L))
        assertEquals("Letra comecando no segundo 5 e meio", LrcParser.getActiveLine(lines, 30000L))
        assertEquals("Refrão no minuto 1 e 10", LrcParser.getActiveLine(lines, 75000L))
        assertEquals("Final da musica", LrcParser.getActiveLine(lines, 130000L))
    }

    @Test
    fun testEmptyLrcFile() {
        val lrcFile = tempFolder.newFile("empty.lrc")
        lrcFile.writeText("")

        val lines = LrcParser.parseLrcFile(lrcFile)
        assertEquals(0, lines.size)
        assertNull(LrcParser.getActiveLine(lines, 1000L))
    }

    @Test
    fun testMultipleTimestampsPerLine() {
        val lrcFile = tempFolder.newFile("multi.lrc")
        lrcFile.writeText(
            """
            [00:10.00][00:40.00]Refrão repetido
            """.trimIndent()
        )

        val lines = LrcParser.parseLrcFile(lrcFile)
        assertEquals(2, lines.size)
        assertEquals(10000L, lines[0].timeMs)
        assertEquals("Refrão repetido", lines[0].text)
        assertEquals(40000L, lines[1].timeMs)
        assertEquals("Refrão repetido", lines[1].text)

        assertEquals("Refrão repetido", LrcParser.getActiveLine(lines, 15000L))
        assertEquals("Refrão repetido", LrcParser.getActiveLine(lines, 42000L))
    }

    @Test
    fun testOffsetHeaderTag() {
        val lrcFile = tempFolder.newFile("offset.lrc")
        lrcFile.writeText(
            """
            [ti:Test Title]
            [offset:+500]
            [00:10.00]Primeira linha
            """.trimIndent()
        )

        val lines = LrcParser.parseLrcFile(lrcFile)
        assertEquals(1, lines.size)
        // With +500 offset, lyrics appear 500ms earlier (10000 - 500 = 9500ms)
        assertEquals(9500L, lines[0].timeMs)
        assertEquals("Primeira linha", lines[0].text)

        assertNull(LrcParser.getActiveLine(lines, 9000L))
        assertEquals("Primeira linha", LrcParser.getActiveLine(lines, 9600L))
    }

    @Test
    fun testSilentGapLineClearsActiveLyrics() {
        val lrcFile = tempFolder.newFile("silence.lrc")
        lrcFile.writeText(
            """
            [00:10.00]Letra antes do solo
            [00:15.00]
            [00:40.00]Letra depois do solo
            """.trimIndent()
        )

        val lines = LrcParser.parseLrcFile(lrcFile)
        assertEquals(3, lines.size)

        // During lyrics
        assertEquals("Letra antes do solo", LrcParser.getActiveLine(lines, 12000L))
        // During guitar solo (empty line timestamp reached)
        assertNull(LrcParser.getActiveLine(lines, 20000L))
        assertNull(LrcParser.getActiveLine(lines, 35000L))
        // Next lyrics
        assertEquals("Letra depois do solo", LrcParser.getActiveLine(lines, 41000L))
    }

    @Test
    fun testDifferentTimestampSeparatorsAndKaraokeTags() {
        val lrcFile = tempFolder.newFile("varied.lrc")
        lrcFile.writeText(
            """
            [01:05:30]Separador dois pontos <01:05.40> limpo
            [02:10,50]Separador vírgula
            """.trimIndent()
        )

        val lines = LrcParser.parseLrcFile(lrcFile)
        assertEquals(2, lines.size)

        assertEquals(65300L, lines[0].timeMs)
        assertEquals("Separador dois pontos limpo", lines[0].text)

        assertEquals(130500L, lines[1].timeMs)
        assertEquals("Separador vírgula", lines[1].text)
    }
}
