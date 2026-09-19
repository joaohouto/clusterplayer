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
}
