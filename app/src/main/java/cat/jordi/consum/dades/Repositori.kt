package cat.jordi.consum.dades

import androidx.annotation.StringRes
import cat.jordi.consum.R
import cat.jordi.consum.shelly.Lectura
import cat.jordi.consum.shelly.ShellyLocal
import cat.jordi.consum.shelly.ShellyNuvol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.max

enum class Periode(@StringRes val nomId: Int) { DIA(R.string.periode_dia), SETMANA(R.string.periode_setmana), MES(R.string.periode_mes) }

/** Una barra de l'historial: una hora o un dia. */
data class Barra(val etiqueta: String, val hora: Hora)

/** Els euros d'un període: cada kWh al preu de la seva hora, i els costos fixos dels dies del període a part. */
data class Euros(val sempre: Double, val linia: Double, val desconegut: Double, val fixos: Double) {
    val consum get() = sempre + linia + desconegut
    val total get() = consum + fixos
}

/** El consum d'un període (kWh), repartit. [fonsW] és el consum que no s'apaga mai. */
data class Resum(
    val barres: List<Barra>, val sempre: Double, val linia: Double, val desconegut: Double, val fonsW: Double?, val euros: Euros,
) {
    val total get() = sempre + linia + desconegut

    /** El que ha pagat de mitjana cada kWh de la casa, sense els fixos. */
    val preuMitja get() = if (total > 0.05) euros.consum / total else null
}

/** Una hora amb el seu preu (€/kWh, amb impostos; null si és dinàmic i encara no se sap) i la seva franja. */
data class HoraPreu(val ts: Long, val hora: Int, val preu: Double?, val franja: Franja)

data class InfoPreu(
    val tarifa: Tarifa,
    val ara: HoraPreu,
    /** L'hora en què s'acaba la franja d'ara (null si el preu no va per franges). */
    val canviaA: Int?,
    val avui: List<HoraPreu>,
    /** Les 24 hores a partir d'ara, per trobar la més barata. */
    val properes: List<HoraPreu>,
)

/** El consum del mes repartit entre els aparells après; el residu és el que encara no s'explica. */
data class Repartiment(val kwh: Map<Long, Double>, val euros: Map<Long, Double>, val residuKwh: Double, val residuEuros: Double)

/** Què costaria la línia de la segona pinça si gastés a les hores barates. */
data class ConsellLinia(val estalviMes: Double, val percentCar: Int)

class Repositori(val config: Config, private val bd: BaseDades) {
    private val zona get() = ZoneId.systemDefault()
    private val sincronitzant = Mutex()
    private val intentsPreus = mutableMapOf<LocalDate, Long>()

    private fun ara() = System.currentTimeMillis() / 1000

    /** La lectura del moment: primer el Shelly de casa; si no hi arriba, el núvol. */
    suspend fun lectura(): Lectura? = withContext(Dispatchers.IO) {
        if (config.host.isNotBlank()) {
            runCatching { ShellyLocal(config.host).lectura() }.getOrNull()?.let { return@withContext it }
        }
        if (config.teNuvol) {
            runCatching { ShellyNuvol(config.servidorNuvol, config.clauNuvol, config.idDispositiu).lectura() }.getOrNull()
        } else null
    }

    /**
     * Baixa del Shelly els minuts que falten (només a casa). Retorna quants n'ha afegit.
     * Llança una excepció si el Shelly no respon.
     */
    suspend fun sincronitza(): Int = withContext(Dispatchers.IO) {
        sincronitzant.withLock {
            val shelly = ShellyLocal(config.host)
            var n = 0
            val canals = if (config.teLinia) listOf(0, 1) else listOf(0)
            for (canal in canals) {
                val desDe = bd.darrerTs(canal)?.plus(60) ?: (ara() - 14 * 86400)
                shelly.minuts(canal, desDe) { files ->
                    bd.insereixMinuts(canal, files)
                    n += files.size
                }
            }
            n
        }
    }

    /**
     * Amb un contracte dinàmic, baixa els preus de mercat dels dies que tenen dades i encara no tenen preu,
     * i els de demà quan ja s'han publicat. Un dia que falla no es torna a provar fins al cap de mitja hora.
     */
    suspend fun actualitzaPreus() = withContext(Dispatchers.IO) {
        val t = config.tarifa
        if (!t.dinamica) return@withContext
        val avui = LocalDate.now(zona)
        var dia = bd.primerTs()?.let { Instant.ofEpochSecond(it).atZone(zona).toLocalDate() }
            ?.coerceAtLeast(avui.minusDays(35)) ?: avui.minusDays(1)
        while (!dia.isAfter(avui.plusDays(1))) {
            val falten = bd.preus(t.pais, iniciDia(dia), iniciDia(dia.plusDays(1))).size < 23
            val provat = intentsPreus[dia]?.let { ara() - it < 1800 } == true
            if (falten && !provat) {
                intentsPreus[dia] = ara()
                runCatching { bd.desaPreus(t.pais, Mercat.preus(t.pais, dia)) }
            }
            dia = dia.plusDays(1)
        }
    }

    /** El preu de cada hora segons la tarifa, amb els preus de mercat desats si és dinàmica. */
    private inner class Preus(desDe: Long, finsA: Long) {
        val tarifa = config.tarifa
        private val mercat = if (tarifa.dinamica) bd.preus(tarifa.pais, desDe - 86400, finsA + 86400) else emptyMap()
        private val mitja = mercat.values.average().takeIf { !it.isNaN() }

        fun mercatDe(ts: Long) = mercat[(ts / 3600) * 3600]

        /** Si és dinàmica i no tenim el preu d'aquella hora, fa servir la mitjana dels que tenim. */
        fun de(ts: Long): Double =
            tarifa.preuKwh(ts, zona, ::mercatDe) ?: tarifa.preuKwh(ts, zona) { mitja } ?: tarifa.preuFix

        fun euros(hores: List<Hora>, dies: Double) = Euros(
            hores.sumOf { it.sempre * de(it.ts) }, hores.sumOf { it.linia * de(it.ts) },
            hores.sumOf { it.desconegut * de(it.ts) }, tarifa.fixosDia() * dies,
        )
    }

    /** El fons en Wh per minut, calculat amb els últims 3 dies. */
    private fun fonsWh(): Double = bd.fons(ara() - 3 * 86400, ara() + 60) ?: 0.0

    fun fonsW(): Double? = bd.fons(ara() - 3 * 86400, ara() + 60)?.times(60)

    fun serie24h(): List<Punt> = bd.serie(ara() - 86400, ara() + 60)

    private fun iniciDia(data: LocalDate) = data.atStartOfDay(zona).toEpochSecond()

    /** El consum d'avui: kWh i euros (sense els fixos). */
    fun avui(): Pair<Double, Double> {
        val desDe = iniciDia(LocalDate.now(zona))
        val hores = bd.perHores(desDe, ara() + 60, fonsWh())
        return hores.sumOf { it.total } to Preus(desDe, ara()).euros(hores, 0.0).consum
    }

    fun resum(periode: Periode): Resum {
        val fons = fonsWh()
        val avui = LocalDate.now(zona)
        val (barres, euros) = when (periode) {
            Periode.DIA -> {
                val desDe = (ara() / 3600 - 23) * 3600
                val llista = bd.perHores(desDe, ara() + 60, fons)
                val hores = llista.associateBy { it.ts }
                (0 until 24).map { i ->
                    val ts = desDe + i * 3600L
                    val h = Instant.ofEpochSecond(ts).atZone(zona).hour
                    Barra("$h", hores[ts] ?: Hora(ts, 0.0, 0.0, 0.0))
                } to Preus(desDe, ara()).euros(llista, 1.0)
            }
            Periode.SETMANA, Periode.MES -> {
                val dies = if (periode == Periode.SETMANA) 7 else 30
                val primer = avui.minusDays(dies - 1L)
                val llista = bd.perHores(iniciDia(primer), ara() + 60, fons)
                val perDia = llista.groupBy { Instant.ofEpochSecond(it.ts).atZone(zona).toLocalDate() }
                (0 until dies).map { i ->
                    val dia = primer.plusDays(i.toLong())
                    val hores = perDia[dia].orEmpty()
                    val etiqueta = if (periode == Periode.SETMANA)
                        dia.dayOfWeek.getDisplayName(TextStyle.SHORT, Idioma.locale).removeSuffix(".")
                    else dia.dayOfMonth.toString()
                    Barra(etiqueta, Hora(iniciDia(dia), hores.sumOf { it.sempre }, hores.sumOf { it.linia }, hores.sumOf { it.desconegut }))
                } to Preus(iniciDia(primer), ara()).euros(llista, dies.toDouble())
            }
        }
        return Resum(
            barres, barres.sumOf { it.hora.sempre }, barres.sumOf { it.hora.linia }, barres.sumOf { it.hora.desconegut },
            fons.takeIf { it > 0 }?.times(60), euros,
        )
    }

    /** El consum d'aquest mes, des del dia 1, amb els fixos dels dies que han passat. */
    fun resumMes(): Resum {
        val fons = fonsWh()
        val avui = LocalDate.now(zona)
        val desDe = iniciDia(avui.withDayOfMonth(1))
        val hores = bd.perHores(desDe, ara() + 60, fons)
        return Resum(
            emptyList(), hores.sumOf { it.sempre }, hores.sumOf { it.linia }, hores.sumOf { it.desconegut },
            fons.takeIf { it > 0 }?.times(60), Preus(desDe, ara()).euros(hores, avui.dayOfMonth.toDouble()),
        )
    }

    fun infoPreu(): InfoPreu {
        val ara = ara()
        val hora = (ara / 3600) * 3600
        val avuiInici = iniciDia(LocalDate.now(zona))
        val p = Preus(avuiInici, avuiInici + 2 * 86400)
        fun hp(ts: Long) = HoraPreu(
            ts, Instant.ofEpochSecond(ts).atZone(zona).hour,
            if (p.tarifa.dinamica && p.mercatDe(ts) == null) null else p.de(ts), p.tarifa.franja(ts, zona),
        )
        val properes = (0 until 24).map { hp(hora + it * 3600L) }
        val franja = properes.first().franja
        val canvia = if (franja == Franja.UNICA || franja == Franja.MERCAT) null
        else properes.drop(1).firstOrNull { it.franja != franja }?.hora
        return InfoPreu(p.tarifa, properes.first(), canvia, (0 until 24).map { hp(avuiInici + it * 3600L) }, properes)
    }

    /**
     * Si la línia de la segona pinça gastés a les hores barates del mes (el terç més barat),
     * quant s'estalviaria. Null si no hi ha línia o la diferència és petita.
     */
    fun consellLinia(): ConsellLinia? {
        if (!config.teLinia) return null
        val desDe = iniciDia(LocalDate.now(zona).withDayOfMonth(1))
        val hores = bd.perHores(desDe, ara() + 60, fonsWh())
        val p = Preus(desDe, ara())
        val preus = hores.map { p.de(it.ts) }.sorted()
        if (preus.size < 24) return null
        val barat = preus.take(preus.size / 3).average()
        val car = preus[preus.size * 2 / 3]
        val kwh = hores.sumOf { it.linia }
        if (kwh < 0.5) return null
        val estalvi = hores.sumOf { it.linia * (p.de(it.ts) - barat) }
        val percentCar = (hores.filter { p.de(it.ts) >= car }.sumOf { it.linia } / kwh * 100).toInt()
        return ConsellLinia(estalvi, percentCar).takeIf { estalvi >= 0.3 }
    }

    fun aparells() = bd.aparells()
    fun desaAparell(a: Aparell) = bd.desaAparell(a)
    fun esborraAparell(id: Long) = bd.esborraAparell(id)

    // ---------- Entrenament ----------

    /** L'aparell que es diu com la línia de la segona pinça es mesura directament: no s'entrena ni s'endevina. */
    private fun esLinia(a: Aparell) = config.teLinia && a.nom.equals(config.nomLinia, ignoreCase = true)

    fun aparellsAEntrenar() = bd.aparells().filterNot(::esLinia)

    fun referencies(): Map<Long, Referencia> {
        val valids = aparellsAEntrenar().map { it.id }.toSet()
        return bd.mostres().filter { it.aparellId in valids }.groupBy { it.aparellId }
            .mapNotNull { (id, m) -> Referencia.de(id, m) }.associateBy { it.aparellId }
    }

    fun explicador() = Explicador(referencies().values.toList())

    fun desaMostra(aparellId: Long, dp: Double, durada: Long?) = bd.desaMostra(aparellId, ara(), dp, durada, "directe")

    fun tornaAEntrenar(aparellId: Long) = bd.esborraMostres(aparellId)

    fun preguntesPendents() = bd.preguntes(EstatPregunta.PENDENT)

    fun pregunta(id: Long) = bd.pregunta(id)

    /** Respon una pregunta. Si és un aparell, el salt de la pregunta passa a ser una mostra seva. */
    fun respon(preguntaId: Long, aparellId: Long?) {
        val p = bd.pregunta(preguntaId) ?: return
        if (aparellId != null) {
            bd.desaMostra(aparellId, p.ts, p.dp, null, "pregunta")
            bd.respon(preguntaId, EstatPregunta.RESPOSTA, aparellId)
        } else {
            bd.respon(preguntaId, EstatPregunta.IGNORADA, null)
        }
    }

    /**
     * Mira si ara hi ha un consum que les referències no expliquen. Si dura 10 minuts o més (amb una
     * potència semblant), en fa una pregunta, només una per a cada consum. [restaW] és el total menys la línia.
     */
    fun comprovaInesperat(restaW: Double): Pregunta? {
        val fons = fonsW() ?: return null
        val residu = explicador().explica(restaW - fons).residu
        val c = config
        if (residu < c.llindarAvis) {
            c.inesperatDes = 0
            c.inesperatPregunta = 0
            return null
        }
        val semblant = c.inesperatDes != 0L && abs(residu - c.inesperatW) <= 0.3 * max(residu, c.inesperatW)
        if (!semblant) {
            c.inesperatDes = ara()
            c.inesperatW = residu
            c.inesperatPregunta = 0
            return null
        }
        if (ara() - c.inesperatDes < 10 * 60 || c.inesperatPregunta != 0L) return null
        val id = bd.creaPregunta(c.inesperatDes, residu)
        c.inesperatPregunta = id
        return bd.pregunta(id)
    }

    /** Reparteix el consum sense identificar del mes entre els aparells après, minut a minut, al preu de cada hora. */
    fun repartimentMes(): Repartiment {
        val desDe = iniciDia(LocalDate.now(zona).withDayOfMonth(1))
        val explicador = explicador()
        val fons = fonsWh() * 60
        val p = Preus(desDe, ara())
        val kwh = mutableMapOf<Long, Double>()
        val euros = mutableMapOf<Long, Double>()
        var residuKwh = 0.0
        var residuEuros = 0.0
        for (punt in bd.serie(desDe, ara() + 60)) {
            val exces = max(punt.wTotal - punt.wLinia, 0.0) - fons
            if (exces <= 0) continue
            val e = explicador.explica(exces)
            val preu = p.de(punt.ts)
            for ((id, w) in e.aparells) {
                kwh[id] = (kwh[id] ?: 0.0) + w / 60_000
                euros[id] = (euros[id] ?: 0.0) + w / 60_000 * preu
            }
            residuKwh += e.residu / 60_000
            residuEuros += e.residu / 60_000 * preu
        }
        return Repartiment(kwh, euros, residuKwh, residuEuros)
    }
}
