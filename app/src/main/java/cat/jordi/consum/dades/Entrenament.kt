package cat.jordi.consum.dades

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/**
 * Una mostra de l'entrenament: el salt de potència (W) que va fer un aparell en encendre's o en apagar-se,
 * mesurat per l'app quan l'usuari ho va marcar o quan va respondre una pregunta. [durada] és el temps que va
 * estar encès (s), si es coneix.
 */
data class Mostra(val id: Long, val aparellId: Long, val ts: Long, val dp: Double, val durada: Long?)

/** El que l'app sap d'un aparell: la potència típica (la mediana de les mostres) i quantes mostres en té. */
data class Referencia(val aparellId: Long, val w: Double, val mostres: Int, val duradaMin: Double?) {
    /** Amb tres mostres ja es considera après; amb menys, encara aprenent. */
    val apresa get() = mostres >= 3

    companion object {
        const val PER_APRENDRE = 3

        fun de(aparellId: Long, mostres: List<Mostra>): Referencia? {
            if (mostres.isEmpty()) return null
            val durades = mostres.mapNotNull { it.durada }
            return Referencia(aparellId, mediana(mostres.map { it.dp }), mostres.size, durades.takeIf { it.isNotEmpty() }?.let { mediana(it.map(Long::toDouble)) / 60 })
        }
    }
}

fun mediana(xs: List<Double>): Double {
    val s = xs.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

enum class EstatPregunta { PENDENT, RESPOSTA, IGNORADA }

/** Un consum que l'app no ha sabut explicar: des de quan i quants W de més hi havia. */
data class Pregunta(val id: Long, val ts: Long, val dp: Double, val estat: EstatPregunta, val aparellId: Long?)

/** Quins aparells expliquen un excés de potència i quant en queda sense explicar (W). */
data class Explicacio(val aparells: List<Pair<Long, Double>>, val residu: Double)

/**
 * Explica un excés de potència sobre el fons amb les referències apreses: busca la combinació d'1 a 3 aparells
 * que s'hi acosta més sense passar-se'n (amb un marge del 15 %, o 40 W). Si la suma es passa una mica, es
 * retalla; el que no s'explica queda com a residu. És aproximat: dos aparells de la mateixa potència es confonen.
 */
class Explicador(referencies: List<Referencia>) {
    private class Combinacio(val suma: Double, val membres: List<Referencia>)

    private val combinacions: List<Combinacio>

    init {
        val r = referencies.filter { it.w > 0 }.sortedByDescending { it.w }.take(20)
        val llista = mutableListOf(Combinacio(0.0, emptyList()))
        for (i in r.indices) {
            llista += Combinacio(r[i].w, listOf(r[i]))
            for (j in i + 1 until r.size) {
                llista += Combinacio(r[i].w + r[j].w, listOf(r[i], r[j]))
                for (k in j + 1 until r.size) llista += Combinacio(r[i].w + r[j].w + r[k].w, listOf(r[i], r[j], r[k]))
            }
        }
        // De més petita a més gran; a igual suma, la de menys aparells queda l'última, que és la que trobarà la cerca
        combinacions = llista.sortedWith(compareBy<Combinacio> { it.suma }.thenBy { -it.membres.size })
    }

    fun explica(exces: Double): Explicacio {
        if (exces <= 0 || combinacions.size == 1) return Explicacio(emptyList(), max(exces, 0.0))
        val sostre = exces + max(40.0, 0.15 * exces)
        // La combinació més gran que no passa del sostre (cerca binària)
        var lo = 0
        var hi = combinacions.lastIndex
        while (lo < hi) {
            val mig = (lo + hi + 1) / 2
            if (combinacions[mig].suma <= sostre) lo = mig else hi = mig - 1
        }
        val tria = combinacions[lo]
        if (tria.membres.isEmpty()) return Explicacio(emptyList(), exces)
        val factor = if (tria.suma > exces) exces / tria.suma else 1.0
        return Explicacio(tria.membres.map { it.aparellId to it.w * factor }, max(exces - tria.suma, 0.0))
    }
}

/** Els aparells que més s'assemblen a un consum desconegut, per proposar-los a la pregunta. */
fun endevina(w: Double, aparells: List<Aparell>, referencies: Map<Long, Referencia>, quants: Int = 2): List<Aparell> =
    aparells.sortedBy { a -> abs(ln(max(referencies[a.id]?.w ?: a.potenciaW ?: a.categoria.wTipic, 1.0) / max(w, 1.0))) }.take(quants)
