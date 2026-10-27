package cat.jordi.consum.dades

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * El consum, repartit per vegades (maqueta 13): cada vegada que una cosa s'encén i s'apaga (un [Episodi]) és d'un sol
 * aparell, o de cap. Abans es repartia minut a minut combinant referències, i barrejava coses: després d'ensenyar el
 * rentaplats amb una estona passada (0,36 kWh), el rentaplats es quedava 1,9 kWh del mes i l'estufa passava de 5,7 a
 * 7,8 kWh sense tocar-la. Prototip: recerca/deteccio/repartiment_episodis.py.
 */

/** Com se sap de qui és una vegada. */
enum class Com {
    /** L'usuari ho ha dit: una estona passada, una pregunta, un moment punta, el que es repeteix, «L'he encès». */
    ENSENYAT,

    /** S'assembla a la firma d'un aparell, i només a la d'un. */
    RECONEGUT,
}

/**
 * El que l'usuari ha dit d'una estona d'un canal (0, la resta de la casa; 1, la línia de la pinça 2): que era l'aparell
 * [aparellId] o, amb [no], que no ho era («No era ell»). Un instant ([desDe] = [finsA]) és quan es va encendre: val per a
 * la vegada que comença a menys de 3 minuts. Una estona val per a les vegades que cobreix almenys a la meitat.
 */
data class Etiqueta(val canal: Int, val desDe: Long, val finsA: Long, val aparellId: Long, val no: Boolean = false)

/** Una vegada desada: un episodi d'un canal, amb l'aparell (si se sap) i com se sap. */
data class Vegada(
    val id: Long, val canal: Int, val ts: Long, val fi: Long, val minuts: Int, val w: Double, val kwh: Double,
    val variacio: Double, val arrencada: Double, val aparellId: Long? = null, val com: Com? = null,
) {
    val forma get() = Forma.de(variacio, arrencada)

    companion object {
        fun de(canal: Int, e: Episodi) = Vegada(0, canal, e.ts, e.fi, e.minuts, e.w, e.kwh, e.variacio, e.arrencada)
    }
}

/**
 * Quant val una etiqueta per a una vegada (0: no hi té res a veure). Un instant, si la vegada comença a menys de 3 minuts
 * (una vegada llarga no s'emporta el que algú ha respost d'una cosa que s'hi va encendre a sobre). Una estona, el que se
 * superposen (s), si cobreix almenys la meitat de la vegada.
 */
private fun encavalca(v: Vegada, e: Etiqueta): Long {
    val fi = v.fi + 60
    if (e.desDe == e.finsA) return if (kotlin.math.abs(e.desDe - v.ts) <= 180) 1 else 0
    val comu = max(0, min(fi, e.finsA + 1) - max(v.ts, e.desDe))
    return if (comu * 2 >= fi - v.ts) comu else 0
}

/**
 * Les etiquetes per canal, ordenades per l'inici, per trobar de pressa les d'una vegada (n'hi pot haver milers, i vegades,
 * desenes de milers).
 */
class IndexEtiquetes(etiquetes: List<Etiqueta>) {
    private val perCanal = etiquetes.groupBy { it.canal }.mapValues { (_, l) -> l.sortedBy { it.desDe } }
    private val llargada = etiquetes.maxOfOrNull { it.finsA - it.desDe } ?: 0

    /** Les que poden tocar una vegada: les que comencen des de [llargada] + 3 minuts abans fins que s'acaba. */
    fun de(v: Vegada): List<Etiqueta> {
        val l = perCanal[v.canal] ?: return emptyList()
        val desDe = v.ts - 180 - llargada
        var lo = 0
        var hi = l.size
        while (lo < hi) {
            val mig = (lo + hi) / 2
            if (l[mig].desDe < desDe) lo = mig + 1 else hi = mig
        }
        val sortida = mutableListOf<Etiqueta>()
        var i = lo
        while (i < l.size && l[i].desDe <= v.fi + 60) sortida += l[i++]
        return sortida
    }
}

/**
 * De qui és una vegada. Primer, el que ha dit l'usuari (l'etiqueta que s'hi encavalca més), tret dels aparells de
 * [exclosos] (els que ara estan sempre encesos: van dins del fons). Si no, l'aparell de [candidats]
 * amb una referència apresa que hi encaixa: la potència (dins de l'interval, amb un 15 % de marge, o a menys de ×1,3 del
 * valor), la durada (si se sap, entre un terç i el triple) i que no en polsi un i l'altre no. Si n'encaixa més d'un, el que
 * s'hi assembla clarament més ([distancia], ×1,4); si no, de ningú. Els aparells que l'usuari ha dit que no eren, no.
 */
fun assigna(
    v: Vegada, candidats: List<Aparell>, refs: Map<Long, Referencia>, etiquetes: List<Etiqueta>, exclosos: Set<Long> = emptySet(),
): Pair<Long?, Com?> {
    val seves = etiquetes.filter { it.canal == v.canal && it.aparellId !in exclosos }.map { it to encavalca(v, it) }.filter { it.second > 0 }
    seves.filter { !it.first.no }.maxByOrNull { it.second }?.let { return it.first.aparellId to Com.ENSENYAT }
    val no = seves.filter { it.first.no }.map { it.first.aparellId }.toSet()
    val encaixen = candidats.filter { a ->
        val r = refs[a.id] ?: return@filter false
        if (a.id in no) return@filter false
        val potencia = if (r.wMin != null) v.w in r.min * 0.85..r.max * 1.15 else v.w in r.w / 1.3..r.w * 1.3
        val durada = r.duradaMin?.let { d -> v.minuts.toDouble() in (d / 3 - 1)..(d * 3 + 1) } ?: true
        val f = v.forma
        val forma = r.forma == null || f == null || (f == Forma.POLSA) == (r.forma == Forma.POLSA)
        potencia && durada && forma
    }
    if (encaixen.isEmpty()) return null to null
    if (encaixen.size == 1) return encaixen[0].id to Com.RECONEGUT
    val d = encaixen.map { it to distancia(v.w, v.minuts.toDouble(), v.forma, it, refs[it.id]) }.sortedBy { it.second }
    return if (d[1].second - d[0].second >= ln(1.4)) d[0].first.id to Com.RECONEGUT else null to null
}

/** Un aparell al rànquing d'un període: quant ha gastat, quantes vegades i quantes n'ha ensenyat l'usuari. */
data class FilaRanquing(
    val aparell: Aparell?, val nom: String, val kwh: Double, val euros: Double, val cops: Int, val ensenyats: Int,
    /** És a la línia de la pinça 2 (o és la línia mateixa, si no s'hi ha dit cap aparell). */
    val linia: Boolean,
    /** La nevera: el compressor trobat sol a les dades, sense vegades. */
    val compressorW: Double? = null,
)

/**
 * Tot el consum d'un període, sense barrejar res (maqueta 13): els aparells de més a menys, i la resta a part. La suma de
 * tot és el total.
 */
data class Ranquing(
    val aparells: List<FilaRanquing>,
    val total: Double,
    /** El que la casa gasta sempre (el fons, també el dels aparells sempre encesos). */
    val sempre: Double, val sempreEuros: Double,
    /** El que passa del fons però no és cap vegada de 250 W o més: llums, tele, carregadors. */
    val petits: Double, val petitsEuros: Double,
    /** Vegades que l'app no sap de qui són. */
    val senseIdentificar: Double, val senseIdentificarEuros: Double,
    /** A la línia de la pinça 2, el que no és cap dels seus aparells. */
    val liniaAltres: Double, val liniaAltresEuros: Double,
    /** Des de fora de casa o del núvol per hores: sense el detall de cada minut, no se sap de qui és. */
    val senseDetall: Double, val senseDetallEuros: Double,
) {
    val delsAparells get() = aparells.sumOf { it.kwh }
}
