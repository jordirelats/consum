package cat.jordi.consum.dades

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Un moment punta: el minut de més consum d'una estona (W de mitjana) i, si se sap, el pic instantani d'aquell minut. */
data class Pic(val ts: Long, val w: Double, val wMax: Double?)

/** Com es comporta un tros del consum d'un pic. */
enum class TipusTros {
    /** S'encén en un moment i es manté (el cotxe, un termo). */
    ESTABLE,

    /** Puja i baixa (una placa d'inducció, un forn, un aire). */
    VARIABLE,

    /** Ja era encès a l'inici de la finestra: no se sap des de quan. */
    JA_HI_ERA,
}

/**
 * Un tros del consum d'un pic, abans de posar-hi nom: des de quan hi és i quants W hi posa en el moment del pic.
 * [mostra] és la potència típica del tros (la del salt si és estable, la mediana si puja i baixa), la que es fa
 * servir per aprendre l'aparell si l'usuari diu què era.
 */
data class Tros(val tipus: TipusTros, val desDe: Long, val w: Double, val mostra: Double)

/**
 * Descompon un pic en trossos amb les dades per minut de les hores d'abans. Amb una sola pinça és aproximat: el que
 * s'encén alhora o puja i baixa barrejat no es pot separar.
 * 1. Una escala: el nivell només canvia quan la potència (la mediana de 5 minuts) se n'aparta [LLINDAR] W o més
 *    durant [SEGUITS] minuts seguits.
 * 2. Els trossos estables: les pujades de l'escala que després varien poc durant 5 minuts o més, i que cap
 *    baixada semblant (±30 %) no ha tret abans del pic.
 * 3. El que ja hi havia a l'inici de la finestra, per sobre del fons, si cap baixada semblant no l'ha tret.
 * 4. La resta, fins al pic, és el que puja i baixa: des de quan la mediana hi és per sobre dels altres trossos.
 */
object AnalisiPics {
    const val LLINDAR = 400.0
    private const val SEGUITS = 3
    /** Les hores d'abans del pic que es miren. */
    const val FINESTRA_S = 4 * 3600L
    /** Per sota d'això, un tros no compta. */
    private const val MINIM = 300.0

    /** [serie]: la potència de la casa sense la línia de la segona pinça (W), minut a minut, acabant al pic. */
    fun trossos(totaLaSerie: List<Pair<Long, Double>>, fonsW: Double): List<Tros> {
        // Es compta per minuts seguits: si hi ha un forat sense dades de més de 5 minuts, es comença després
        val forat = (1 until totaLaSerie.size).lastOrNull { totaLaSerie[it].first - totaLaSerie[it - 1].first > 5 * 60 }
        val serie = if (forat != null) totaLaSerie.subList(forat, totaLaSerie.size) else totaLaSerie
        if (serie.size < 8) return emptyList()
        val r = serie.map { it.second }
        val t = serie.map { it.first }
        val s = r.indices.map { i -> mediana(r.subList(max(0, i - 2), minOf(r.size, i + 3))) }

        // 1. L'escala
        var nivell = mediana(s.take(5))
        val inicial = nivell
        val canvis = mutableListOf<Pair<Int, Double>>()
        var i = 0
        while (i + SEGUITS <= s.size) {
            val dev = s[i] - nivell
            if (abs(dev) >= LLINDAR && (i until i + SEGUITS).all { j -> abs(s[j] - nivell) >= LLINDAR && (s[j] - nivell) * dev > 0 }) {
                val nou = mediana(s.subList(i, i + SEGUITS))
                canvis += i to nou - nivell
                nivell = nou
                i += SEGUITS
            } else {
                i++
            }
        }

        // 2 i 3. Els estables i el que ja hi havia
        val estables = mutableListOf<Pair<Int, Double>>()
        var abans = max(inicial - fonsW, 0.0)
        for ((k, canvi) in canvis.withIndex()) {
            val (inici, dp) = canvi
            val fi = if (k + 1 < canvis.size) canvis[k + 1].first else r.size
            val tram = r.subList(inici, fi)
            if (dp > 0 && tram.size >= 5 && desviacio(tram) < max(150.0, 0.15 * dp)) {
                estables += canvi
            } else if (dp < 0) {
                // Una baixada treu l'estable més recent de mida semblant; si no n'hi ha, el que ja hi havia
                val e = estables.lastOrNull { abs(it.second + dp) <= 0.3 * it.second }
                if (e != null) estables.remove(e)
                else if (abans >= MINIM && abs(abans + dp) <= 0.3 * abans) abans = 0.0
            }
        }
        val trossos = mutableListOf<Tros>()
        if (abans >= MINIM) trossos += Tros(TipusTros.JA_HI_ERA, t.first(), abans, abans)
        estables.forEach { (inici, dp) -> trossos += Tros(TipusTros.ESTABLE, t[inici], dp, dp) }

        // 4. El que puja i baixa
        val base = fonsW + trossos.sumOf { it.w }
        val resta = r.last() - base
        if (resta >= MINIM) {
            var k = s.lastIndex
            while (k > 0 && s[k - 1] - base >= MINIM) k--
            trossos += Tros(TipusTros.VARIABLE, t[k], resta, mediana(r.subList(k, r.size).map { it - base }))
        }
        return trossos
    }

    private fun desviacio(xs: List<Double>): Double {
        val m = xs.average()
        return sqrt(xs.sumOf { (it - m) * (it - m) } / xs.size)
    }
}

/** Una de les coses que gastaven en un pic. */
sealed interface ElementPic {
    val w: Double
}

/** Un aparell conegut: des de quan era encès (si se sap) i si ja està après o encara aprenent. */
data class AparellPic(val aparell: Aparell, override val w: Double, val desDe: Long?, val apres: Boolean) : ElementPic

data class SempreEncesPic(override val w: Double) : ElementPic

data class LiniaPic(override val w: Double) : ElementPic

/** Un tros que no s'assembla a cap aparell conegut: se'n pregunta què era, proposant-ne els que s'hi assemblen. */
data class DesconegutPic(val tros: Tros, val suggerits: List<Aparell>) : ElementPic {
    override val w get() = tros.w
}

data class SenseIdentificarPic(override val w: Double) : ElementPic

/** Un pic, el que hi havia encès (de més a menys) i la potència de les hores d'abans, per a la gràfica. */
data class AnalisiPic(val pic: Pic, val elements: List<ElementPic>, val trossos: List<Tros>, val finestra: List<Punt>)
