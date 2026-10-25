package cat.jordi.consum.dades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sin

class AnalisiPicsTest {
    private val t0 = 1_800_000_000L

    /** Una tarda: 100 W de fons, un cotxe de 2,5 kW des del minut 60 i una cuina que puja i baixa des del 150. */
    private fun tarda(): List<Pair<Long, Double>> = (0..180).map { m ->
        var w = 100.0
        if (m >= 60) w += 2500.0
        if (m >= 150) w += 1800.0 + 900.0 * sin(m * 1.3)
        t0 + m * 60L to w
    }

    @Test
    fun cotxeICuina() {
        val serie = tarda()
        val pic = serie.indices.maxBy { serie[it].second }
        val trossos = AnalisiPics.trossos(serie.subList(0, pic + 1), 100.0)
        val estable = trossos.single { it.tipus == TipusTros.ESTABLE }
        assertEquals(t0 + 60 * 60L, estable.desDe)
        assertEquals(2500.0, estable.w, 150.0)
        val variable = trossos.single { it.tipus == TipusTros.VARIABLE }
        assertTrue("la cuina comença cap al minut 150", variable.desDe in t0 + 145 * 60L..t0 + 155 * 60L)
        // Tots els trossos i el fons sumen el pic
        assertEquals(serie[pic].second, 100.0 + trossos.sumOf { it.w }, 1.0)
    }

    @Test
    fun senseProuDades() {
        assertTrue(AnalisiPics.trossos(tarda().take(5), 100.0).isEmpty())
    }

    /**
     * Amb les dades reals de Guíxols (fora del repositori): el pic del 24/10 a les 20:48, com al prototip
     * recerca/pics/prova_salts.py. Només si hi ha el fitxer.
     */
    @Test
    fun picRealDeGuixols() {
        val fitxer = File(System.getProperty("user.home"), "Projectes/Consum-Shelly/maqueta/dades_24h_guixols.json")
        if (!fitxer.exists()) return
        val minuts = Regex("""\[(\d+),\s*([\d.]+),\s*[\d.]+]""").findAll(fitxer.readText())
            .map { it.groupValues[1].toLong() to it.groupValues[2].toDouble() }.toList()
        val pic = minuts.indexOfFirst { it.first == 1792867680L }
        if (pic < 0) return
        val serie = minuts.subList(maxOf(0, pic - 240), pic + 1)
        val trossos = AnalisiPics.trossos(serie, 88.0)
        val estable = trossos.single { it.tipus == TipusTros.ESTABLE }
        assertEquals(2539.0, estable.w, 50.0)
        assertTrue(trossos.any { it.tipus == TipusTros.VARIABLE && it.w in 3000.0..3500.0 })
    }
}
