package cat.jordi.consum.dades

import androidx.annotation.StringRes
import cat.jordi.consum.R
import cat.jordi.consum.shelly.Lectura
import cat.jordi.consum.shelly.Origen
import cat.jordi.consum.shelly.ShellyLocal
import cat.jordi.consum.shelly.ShellyNuvol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import kotlin.math.max

enum class Periode(@StringRes val nomId: Int) {
    DIA(R.string.periode_dia), SETMANA(R.string.periode_setmana), MES(R.string.periode_mes), ANY(R.string.periode_any)
}

/** Una estona passada: la potència minut a minut (amb un marge a cada costat) i els trams que hi ha. */
data class Estona(val desDe: Long, val finsA: Long, val punts: List<Punt>, val trams: List<TramPassat>, val aproximada: Boolean)

/** Una barra de l'historial: una hora o un dia. */
data class Barra(val etiqueta: String, val hora: Hora)

/** Els euros d'un període: cada kWh al preu de la seva hora, i els costos fixos dels dies del període a part. */
data class Euros(val sempre: Double, val linia: Double, val desconegut: Double, val fixos: Double) {
    val consum get() = sempre + linia + desconegut
    val total get() = consum + fixos
}

/**
 * El consum d'un període (kWh), repartit. [fonsW] és el consum que no s'apaga mai. [aproximat]: una part s'ha
 * calculat des de fora i es corregirà a casa.
 */
data class Resum(
    val barres: List<Barra>, val sempre: Double, val linia: Double, val desconegut: Double, val fonsW: Double?, val euros: Euros,
    val aproximat: Boolean = false,
    /** El període de calendari que es mira (maqueta 10): [primerDia] i, sense incloure'l, [fiDia]. */
    val primerDia: LocalDate? = null, val fiDia: LocalDate? = null,
    /** Si és el període d'ara (si no, l'Historial el pinta de color sèpia). */
    val actual: Boolean = true,
    /** El total del període d'abans, per comparar; null si l'app encara no en tenia totes les dades. */
    val anteriorKwh: Double? = null,
    /** El primer dia amb dades de l'app; si el període és anterior, no en té cap. */
    val primerDiaDades: LocalDate? = null,
) {
    val ambDades get() = primerDiaDades != null && (fiDia == null || fiDia.isAfter(primerDiaDades))

    val total get() = sempre + linia + desconegut

    /** El que ha pagat de mitjana cada kWh de la casa, sense els fixos. */
    val preuMitja get() = if (total > 0.05) euros.consum / total else null
}

/** El consum d'avui (kWh i euros sense els fixos). [aproximat]: una part s'ha calculat des de fora. */
data class Avui(val kwh: Double, val euros: Double, val aproximat: Boolean)

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

/**
 * El consum del mes repartit entre els aparells après; el residu és el que encara no s'explica. Els aparells de la línia
 * de la pinça 2 (maqueta 12) s'hi reparteixen la línia, i el que no expliquen és [liniaAltresKwh].
 */
data class Repartiment(
    val kwh: Map<Long, Double>, val euros: Map<Long, Double>, val residuKwh: Double, val residuEuros: Double,
    val liniaAltresKwh: Double = 0.0, val liniaAltresEuros: Double = 0.0,
)

/** Què costaria la línia de la segona pinça si gastés a les hores barates. */
data class ConsellLinia(val estalviMes: Double, val percentCar: Int)

class Repositori(val config: Config, private val bd: BaseDades) {
    private val zona get() = ZoneId.systemDefault()
    private val sincronitzant = Mutex()
    private val intentsPreus = java.util.concurrent.ConcurrentHashMap<LocalDate, Long>()

    private fun ara() = System.currentTimeMillis() / 1000

    /**
     * La lectura del moment: primer el Shelly de casa; si no hi arriba, el núvol. De passada, en desa el
     * comptador d'energia ([desaComptador]).
     */
    suspend fun lectura(): Lectura? = withContext(Dispatchers.IO) {
        val l = (if (config.host.isNotBlank()) runCatching { ShellyLocal(config.host).lectura() }.getOrNull() else null)
            ?: if (config.teNuvol) {
                runCatching { ShellyNuvol(config.servidorNuvol, config.clauNuvol, config.idDispositiu).lectura() }.getOrNull()
            } else null
        if (l != null) runCatching { desaComptador(l) }
        l
    }

    /**
     * Des de fora de casa, el núvol només dona els comptadors d'energia, que avancen cada minut. L'energia
     * entre dues lectures es reparteix a parts iguals pels minuts d'entremig, que queden com a aproximats fins
     * que el mòbil torna a casa i es baixen els exactes del Shelly. Amb una lectura de casa només es desa el
     * comptador, perquè els minuts exactes ja vindran amb la sincronització. D'un en un: l'app i el treball de
     * fons poden llegir alhora.
     */
    @Synchronized
    private fun desaComptador(l: Lectura) {
        val wh = l.whTotal ?: return
        val minut = l.ts / 60 * 60
        val abans = config.comptador
        if (abans != null && minut <= abans.ts) return
        config.comptador = Comptador(minut, wh, l.whLinia)
        if (abans == null || l.origen == Origen.LOCAL) return
        val minuts = ((minut - abans.ts) / 60).toInt()
        // Més d'una setmana sense cap lectura: no es reparteix, i es torna a començar
        if (minuts > 7 * 24 * 60) return
        fun reparteix(canal: Int, ara: Double?, aleshores: Double?) {
            // Si el comptador ha anat enrere, s'ha posat a zero: es torna a començar
            if (ara == null || aleshores == null || ara < aleshores) return
            val perMinut = (ara - aleshores) / minuts
            bd.insereixAproximats(canal, List(minuts) { i -> Minut(abans.ts + i * 60L, perMinut, perMinut * 60, perMinut * 60) })
        }
        reparteix(0, wh, abans.wh)
        if (config.teLinia) reparteix(1, l.whLinia, abans.whLinia)
    }

    /**
     * Baixa del Shelly els minuts que falten (només a casa), també els que ara són aproximats. Retorna quants n'ha
     * afegit. Llança una excepció si el Shelly no respon. Sense l'adreça del Shelly (només núvol) no fa res.
     */
    suspend fun sincronitza(): Int = withContext(Dispatchers.IO) {
        if (config.host.isBlank()) return@withContext 0
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
            // Amb les dades noves: la nevera, l'interval dels aparells que s'engeguen sols, els aparells de la línia i el
            // que es repeteix (maqueta 12)
            actualitzaNevera()
            completaIntervals()
            aprenLinia()
            actualitzaRepetits()
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

    /**
     * El fons en Wh per minut, calculat amb els últims 3 dies: amb els minuts exactes, o, si encara no n'hi ha
     * prou (qui només mira el núvol), amb els aproximats.
     */
    private fun fonsMinut(): Double? =
        bd.fons(ara() - 3 * 86400, ara() + 60, nomesExactes = true) ?: bd.fons(ara() - 3 * 86400, ara() + 60, nomesExactes = false)

    private fun fonsWh(): Double = fonsMinut() ?: 0.0

    fun fonsW(): Double? = fonsMinut()?.times(60)

    fun serie24h(): List<Punt> = bd.serie(ara() - 86400, ara() + 60)

    private fun iniciDia(data: LocalDate) = data.atStartOfDay(zona).toEpochSecond()

    /** L'energia per hores locals, també a les zones que no van per hores en punt UTC (l'Índia, el Nepal...). */
    private fun perHores(desDe: Long, finsA: Long, fonsWh: Double) = bd.perHores(desDe, finsA, fonsWh, desfasHora(zona, ara()))

    /** El consum d'avui: kWh i euros (sense els fixos), i si una part és aproximada. */
    fun avui(): Avui {
        val desDe = iniciDia(LocalDate.now(zona))
        val hores = perHores(desDe, ara() + 60, fonsWh())
        return Avui(hores.sumOf { it.total }, Preus(desDe, ara()).euros(hores, 0.0).consum, bd.teAproximats(desDe, ara() + 60))
    }

    /**
     * El resum d'un període de calendari (maqueta 10): el dia, la setmana de dilluns a diumenge, el mes o l'any on cau
     * [dia]. Les barres són les hores del dia (23 o 25 el dia del canvi d'hora), els dies de la setmana o del mes, o els
     * mesos de l'any. Els fixos compten els dies amb dades fins avui. També el total del període d'abans, per comparar.
     */
    fun resum(periode: Periode, dia: LocalDate = LocalDate.now(zona)): Resum {
        val fons = fonsWh()
        val avui = LocalDate.now(zona)
        val primer = iniciPeriode(periode, dia)
        val fi = fiPeriode(periode, dia)
        val desDe = iniciDia(primer)
        val finsA = minOf(iniciDia(fi), ara() + 60)
        val llista = if (finsA > desDe) perHores(desDe, finsA, fons) else emptyList()
        fun suma(hores: List<Hora>, ts: Long) =
            Hora(ts, hores.sumOf { it.sempre }, hores.sumOf { it.linia }, hores.sumOf { it.desconegut }, hores.sumOf { it.nuvol })
        val barres = when (periode) {
            Periode.DIA -> {
                val hores = llista.associateBy { it.ts }
                (desDe until iniciDia(fi) step 3600L).map { ts ->
                    Barra("${Instant.ofEpochSecond(ts).atZone(zona).hour}", hores[ts] ?: Hora(ts, 0.0, 0.0, 0.0))
                }
            }
            Periode.SETMANA, Periode.MES -> {
                val perDia = llista.groupBy { Instant.ofEpochSecond(it.ts).atZone(zona).toLocalDate() }
                generateSequence(primer) { it.plusDays(1) }.takeWhile { it.isBefore(fi) }.map { d ->
                    val etiqueta = if (periode == Periode.SETMANA)
                        d.dayOfWeek.getDisplayName(TextStyle.SHORT, Idioma.locale).removeSuffix(".")
                    else d.dayOfMonth.toString()
                    Barra(etiqueta, suma(perDia[d].orEmpty(), iniciDia(d)))
                }.toList()
            }
            Periode.ANY -> {
                val perMes = llista.groupBy { Instant.ofEpochSecond(it.ts).atZone(zona).monthValue }
                (1..12).map { m ->
                    Barra(primer.withMonth(m).month.getDisplayName(TextStyle.NARROW_STANDALONE, Idioma.locale).uppercase(Idioma.locale), suma(perMes[m].orEmpty(), iniciDia(primer.withMonth(m))))
                }
            }
        }
        // Els fixos: els dies del període que tenen dades, fins avui
        val primerDades = bd.primerTs()?.let { Instant.ofEpochSecond(it).atZone(zona).toLocalDate() }
        val diesFixos = if (primerDades == null) 0.0
        else maxOf(0L, ChronoUnit.DAYS.between(maxOf(primer, primerDades), minOf(fi, avui.plusDays(1)))).toDouble()
        val euros = Preus(desDe, finsA).euros(llista, diesFixos)
        // El període d'abans, si l'app ja en tenia totes les dades
        val anterior = mou(periode, primer, -1)
        val anteriorKwh = if (primerDades != null && !anterior.isBefore(primerDades)) bd.kwhEntre(iniciDia(anterior), desDe) else null
        return Resum(
            barres, barres.sumOf { it.hora.sempre }, barres.sumOf { it.hora.linia }, barres.sumOf { it.hora.desconegut },
            fons.takeIf { it > 0 }?.times(60), euros, finsA > desDe && bd.teAproximats(desDe, finsA),
            primer, fi, !avui.isBefore(primer) && avui.isBefore(fi), anteriorKwh, primerDades,
        )
    }

    /** El consum d'aquest mes, des del dia 1, amb els fixos dels dies que han passat. */
    fun resumMes(): Resum {
        val fons = fonsWh()
        val avui = LocalDate.now(zona)
        val desDe = iniciDia(avui.withDayOfMonth(1))
        val hores = perHores(desDe, ara() + 60, fons)
        return Resum(
            emptyList(), hores.sumOf { it.sempre }, hores.sumOf { it.linia }, hores.sumOf { it.desconegut },
            fons.takeIf { it > 0 }?.times(60), Preus(desDe, ara()).euros(hores, avui.dayOfMonth.toDouble()),
        )
    }

    fun infoPreu(): InfoPreu {
        val ara = ara()
        val hora = iniciHora(ara, zona)
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
        // Les hores d'avui fins a l'inici de demà: els dies del canvi d'hora en tenen 23 o 25
        val dema = iniciDia(LocalDate.now(zona).plusDays(1))
        return InfoPreu(p.tarifa, properes.first(), canvia, (avuiInici until dema step 3600L).map { hp(it) }, properes)
    }

    /**
     * Si la línia de la segona pinça gastés a les hores barates del mes (el terç més barat),
     * quant s'estalviaria. Null si no hi ha línia o la diferència és petita.
     */
    fun consellLinia(): ConsellLinia? {
        if (!config.teLinia) return null
        val desDe = iniciDia(LocalDate.now(zona).withDayOfMonth(1))
        val hores = perHores(desDe, ara() + 60, fonsWh())
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

    /** Un aparell que és a la línia de la segona pinça (maqueta 12): s'aprèn de la línia, i no es busca a la resta. */
    private fun aLaLinia(a: Aparell) = config.teLinia && a.linia && !esLinia(a)

    /**
     * Els aparells que poden ser un consum de la resta de la casa: per aprendre'ls, endevinar-los i respondre les
     * preguntes. Ni la línia de la segona pinça ni els seus aparells (es mesuren), ni els que estan sempre encesos (van
     * dins del fons).
     */
    fun aparellsAEntrenar() = bd.aparells().filterNot { esLinia(it) || aLaLinia(it) || it.funciona == Funcionament.SEMPRE }

    /** Els aparells de la línia de la segona pinça que poden ser un consum. */
    fun aparellsLinia() = bd.aparells().filter { aLaLinia(it) && it.funciona != Funcionament.SEMPRE }

    /**
     * Posa un aparell a la línia de la pinça 2, o el treu. En treure'l, fora el que n'havia après de la línia: si no, el
     * que era d'un altre aparell (marcat per error) explicaria consums de la resta de la casa.
     */
    @Synchronized
    fun posaALinia(a: Aparell, si: Boolean) {
        bd.desaAparell(a.copy(linia = si))
        if (!si) bd.esborraMostres(a.id, "linia")
    }

    /** Les referències dels aparells de la línia, apreses de la línia. */
    fun referenciesLinia(): Map<Long, Referencia> {
        val aparells = aparellsLinia()
        val sols = aparells.filter { it.funciona == Funcionament.SOL }.map { it.id }.toSet()
        return bd.mostres().filter { m -> aparells.any { it.id == m.aparellId } }.groupBy { it.aparellId }
            .mapNotNull { (id, m) -> Referencia.de(id, m, ambInterval = id in sols) }.associateBy { it.aparellId }
    }

    /** Qui explica la línia: null si no s'hi ha dit cap aparell. */
    fun explicadorLinia(): Explicador? = if (aparellsLinia().isEmpty()) null else Explicador(referenciesLinia().values.toList())

    /**
     * Aprèn sola els aparells de la línia (maqueta 12): cada episodi acabat de la línia, de 10 minuts o més, és una mostra
     * de l'aparell de la línia que s'hi assembla clarament ([triaLinia]). Ningú més no hi gasta, i per això no cal
     * preguntar-ho. A Guíxols, la bomba de calor de l'aigua: 486–492 W durant uns 70 minuts. Com a molt cada mitja hora.
     * D'un en un (la sincronització i els canvis de l'usuari): si no, un episodi faria dues mostres.
     */
    @Synchronized
    fun aprenLinia(forca: Boolean = false) {
        if (!config.teLinia) return
        if (!forca && ara() - config.liniaMirada < 30 * 60) return
        config.liniaMirada = ara()
        val aparells = aparellsLinia()
        if (aparells.isEmpty()) return
        val minuts = bd.minutsW(linia = true, ara() - 8 * 86400, ara() + 60)
        val darrer = minuts.lastOrNull()?.ts ?: return
        val sols = aparells.filter { it.funciona == Funcionament.SOL }.map { it.id }.toSet()
        var refs = referenciesLinia()
        for (ep in Episodis.troba(minuts)) {
            // Només els que ja han acabat, els llargs i un sol cop cadascun
            if (ep.fi > darrer - 5 * 60 || ep.minuts < 10 || bd.hiHaMostra(ep.ts, "linia")) continue
            val a = triaLinia(ep, aparells, refs) ?: continue
            bd.desaMostra(a.id, ep.ts, ep.w, ep.durada, "linia", if (a.id in sols) ep.interval else null, ep.variacio, ep.arrencada)
            refs = referenciesLinia()
        }
    }

    /**
     * Busca a la resta de la casa el que es repeteix sempre igual (maqueta 12) i que encara no se sap què és, per
     * preguntar-ho un cop. Amb l'última setmana de dades exactes, com a molt cada 6 hores. Els que ja s'han respost o
     * ignorat no es tornen a preguntar. Amb el mateix bloqueig que [responRepetit], perquè una resposta no torni com a
     * pendent.
     */
    @Synchronized
    fun actualitzaRepetits(forca: Boolean = false) {
        if (!forca && ara() - config.repetitsMirats < 6 * 3600) return
        config.repetitsMirats = ara()
        val minuts = bd.minutsW(linia = false, ara() - 7 * 86400, ara() + 60)
        // Amb menys d'un dia no es pot dir que res es repeteixi
        if (minuts.size < 24 * 60) return
        val e = explicador()
        val vistos = bd.repetits().filter { it.estat != EstatPregunta.PENDENT }
        val nous = Episodis.repetits(Episodis.troba(minuts)).map(Episodis::repetit).filter { r ->
            vistos.none { Episodis.mateix(it, r.w, r.minuts) } && e.explica(r.w).residu > 0.25 * r.w
        }.sortedByDescending { it.kwh }.take(3)
        bd.substitueixRepetits(nous)
    }

    fun repetitsPendents() = bd.repetits(EstatPregunta.PENDENT)

    /**
     * Respon què és una cosa que es repeteix: cada cop (els 12 més recents) passa a ser una mostra de l'aparell, amb la
     * seva durada i la seva firma. Si s'engega sol, amb l'interval de potència del grup. Torna false si ja estava resposta.
     */
    @Synchronized
    fun responRepetit(id: Long, aparellId: Long?, sol: Boolean = false): Boolean {
        val r = bd.repetits(EstatPregunta.PENDENT).firstOrNull { it.id == id } ?: return false
        if (aparellId == null) {
            bd.responRepetit(id, EstatPregunta.IGNORADA, null)
            return true
        }
        var a = bd.aparells().firstOrNull { it.id == aparellId } ?: return false
        if (sol && a.funciona != Funcionament.SOL) {
            a = a.copy(funciona = Funcionament.SOL)
            bd.desaAparell(a)
        }
        val interval = if (a.funciona == Funcionament.SOL) r.wMin * 0.9 to r.wMax * 1.1 else null
        for ((ts, w, minuts) in r.cops.takeLast(12)) {
            if (!bd.teMostra(aparellId, ts, "repetit")) bd.desaMostra(aparellId, ts, w, minuts * 60L, "repetit", interval, r.variacio, r.arrencada)
        }
        bd.responRepetit(id, EstatPregunta.RESPOSTA, aparellId)
        return true
    }

    fun referencies(): Map<Long, Referencia> {
        val aparells = aparellsAEntrenar()
        val sols = aparells.filter { it.funciona == Funcionament.SOL }.map { it.id }.toSet()
        val refs = bd.mostres().filter { m -> aparells.any { it.id == m.aparellId } }.groupBy { it.aparellId }
            .mapNotNull { (id, m) -> Referencia.de(id, m, ambInterval = id in sols) }.associateBy { it.aparellId }
        // El compressor de la nevera, trobat sol a les dades: per a la nevera que s'engega sola i no té mostres
        val w = config.neveraW.takeIf { it > 0 } ?: return refs
        val nevera = listOf(Categoria.NEVERA, Categoria.CONGELADOR).firstNotNullOfOrNull { cat ->
            aparells.firstOrNull { it.categoria == cat && it.funciona == Funcionament.SOL && it.id !in refs }
        } ?: return refs
        return refs + (nevera.id to Referencia(nevera.id, w, 0, null, automatica = true))
    }

    // ---------- Les dades per hores del núvol (maqueta 11) ----------

    private val omplintNuvol = Mutex()

    /**
     * Omple el que falta amb el consum per hores del núvol: els dies sense totes les dades del Shelly (unes vacances de
     * més de 7 dies, o d'abans d'instal·lar l'app), d'avui cap enrere, fins a un any o fins que el núvol ja no en té (3
     * dies seguits sense res abans de la primera dada del Shelly). Una petició per dia i per pinça, a menys d'una per
     * segon, i com a molt [maxDies] per vegada: la vegada següent continua. Els dies de fa més d'una setmana ja mirats no
     * es tornen a demanar; els recents sí, perquè el núvol els va completant. Només les hores ja passades: l'hora d'ara
     * la fa el comptador. Torna quants dies ha omplert.
     */
    suspend fun omplNuvol(maxDies: Int = 40): Int = withContext(Dispatchers.IO) {
        val c = config
        if (!c.nuvolHores || !c.teNuvol) return@withContext 0
        omplintNuvol.withLock {
            val nuvol = ShellyNuvol(c.servidorNuvol, c.clauNuvol, c.idDispositiu)
            val avui = LocalDate.now(zona)
            val limit = avui.minusDays(365)
            val primerExacte = bd.primerExacte()?.let { Instant.ofEpochSecond(it).atZone(zona).toLocalDate() }
            val mirats = c.nuvolMirats.toMutableSet()
            var demanats = 0
            var omplerts = 0
            var buitsSeguits = 0
            var dia = avui
            fun omple(canal: Int, hores: List<Pair<Long, Double>>, exactes: Map<Long, Double>) {
                val minuts = hores.filter { (ts, _) -> ts + 3600 <= ara() }
                    .flatMap { (ts, wh) -> minutsDelNuvol(ts, wh, exactes.filterKeys { it >= ts && it < ts + 3600 }) }
                bd.insereixNuvol(canal, minuts)
            }
            suspend fun demana(canal: Int, d: LocalDate): List<Pair<Long, Double>> {
                if (demanats++ > 0) delay(1100)
                return nuvol.consumHores(canal, d)
            }
            try {
                while (dia.isAfter(limit) && demanats < maxDies && dia.toEpochDay() > c.nuvolSenseAbans) {
                    val vell = dia.isBefore(avui.minusDays(7))
                    val e = dia.toEpochDay()
                    val desDe = iniciDia(dia)
                    val finsA = minOf(iniciDia(dia.plusDays(1)), ara())
                    val exactes = bd.minutsExactes(0, desDe, finsA)
                    val complet = exactes.size >= (finsA - desDe) / 60 - 5
                    if (complet || (vell && e in mirats)) {
                        if (vell) mirats += e
                        dia = dia.minusDays(1)
                        continue
                    }
                    val hores = demana(0, dia)
                    if (hores.isEmpty()) {
                        if (vell) mirats += e
                        // Abans de la primera dada del Shelly, 3 dies seguits sense res: el núvol ja no en té més
                        if ((primerExacte == null || dia.isBefore(primerExacte)) && ++buitsSeguits >= 3) {
                            c.nuvolSenseAbans = e
                            break
                        }
                    } else {
                        buitsSeguits = 0
                        omple(0, hores, exactes)
                        if (c.teLinia) omple(1, demana(1, dia), bd.minutsExactes(1, desDe, finsA))
                        if (vell) mirats += e
                        omplerts++
                    }
                    dia = dia.minusDays(1)
                }
            } finally {
                c.nuvolMirats = mirats.filter { it > limit.toEpochDay() }.toSet()
                c.nuvolDarrer = ara()
            }
            omplerts
        }
    }

    /** Apaga l'opció: fora els minuts del núvol per hores, i la vegada que s'encengui es tornarà a mirar tot. */
    fun oblidaNuvol() {
        bd.esborraNuvol()
        config.nuvolMirats = emptySet()
        config.nuvolSenseAbans = Long.MIN_VALUE
    }

    fun resumNuvol() = bd.resumNuvol()

    /** Busca el compressor de la nevera a les dades de l'última setmana, com a molt un cop al dia. */
    fun actualitzaNevera() {
        if (ara() - config.neveraMirada < 20 * 3600) return
        val linia = config.teLinia
        val punts = bd.serie(ara() - 7 * 86400, ara() + 60).filterNot { it.aprox }
            .map { it.ts to (it.wTotal - if (linia) it.wLinia else 0.0) }
        config.neveraW = potenciaCompressor(punts, zona) ?: 0.0
        config.neveraMirada = ara()
    }

    /**
     * Els aparells que s'engeguen sols aprenen l'interval de potència de l'episodi de cada pregunta resposta, quan
     * ja n'hi ha les dades exactes (a casa). Des de fora, o si l'episodi va ser curt, es queda amb el salt.
     */
    fun completaIntervals() {
        val sols = bd.aparells().filter { it.funciona == Funcionament.SOL }.map { it.id }.toSet()
        if (sols.isEmpty()) return
        val fons = fonsW() ?: return
        val linia = config.teLinia
        for (m in bd.mostres()) {
            if (m.aparellId !in sols || m.font != "pregunta" || m.wMin != null) continue
            // L'episodi ha d'haver tingut temps d'acabar, i les dades per minut només duren una setmana al Shelly
            if (m.ts > ara() - 20 * 60 || m.ts < ara() - 8 * 86400) continue
            val serie = bd.serie(m.ts, m.ts + 3 * 3600)
            if (serie.isEmpty() || serie.any { it.aprox }) continue
            val exces = serie.map { it.wTotal - (if (linia) it.wLinia else 0.0) - fons }
            intervalEpisodi(exces, m.dp)?.let { (lo, hi) -> bd.desaInterval(m.id, lo, hi) }
        }
    }

    fun explicador() = Explicador(referencies().values.toList())

    fun desaMostra(aparellId: Long, dp: Double, durada: Long?) = bd.desaMostra(aparellId, ara(), dp, durada, "directe")

    fun tornaAEntrenar(aparellId: Long) = bd.esborraMostres(aparellId)

    fun preguntesPendents() = bd.preguntes(EstatPregunta.PENDENT)

    fun pregunta(id: Long) = bd.pregunta(id)

    /**
     * Respon una pregunta. Si és un aparell, el salt de la pregunta passa a ser una mostra seva. Torna false si ja
     * estava resposta (des de la notificació i des de l'app, o amb dos tocs): no es fa una mostra repetida.
     */
    @Synchronized
    fun respon(preguntaId: Long, aparellId: Long?, sol: Boolean = false): Boolean {
        val p = bd.pregunta(preguntaId)?.takeIf { it.estat == EstatPregunta.PENDENT } ?: return false
        if (aparellId != null) {
            // «S'engega sol»: a partir d'ara se n'aprèn l'interval i no s'avisa quan s'engegui
            if (sol) bd.aparells().firstOrNull { it.id == aparellId && it.funciona != Funcionament.SOL }
                ?.let { bd.desaAparell(it.copy(funciona = Funcionament.SOL)) }
            bd.desaMostra(aparellId, p.ts, p.dp, null, "pregunta")
            bd.respon(preguntaId, EstatPregunta.RESPOSTA, aparellId)
            completaIntervals()
        } else {
            bd.respon(preguntaId, EstatPregunta.IGNORADA, null)
        }
        return true
    }

    /**
     * Mira si ara hi ha un consum que les referències no expliquen: si passa del llindar que ha triat l'usuari durant
     * els minuts que ha triat, en fa una pregunta (amb avís). [restaW] és el total menys la línia.
     */
    @Synchronized
    fun comprovaInesperat(restaW: Double): Pregunta? {
        val c = config
        if (!c.avisInesperat) return null
        val fons = fonsW() ?: return null
        val residu = explicador().explica(restaW - fons).residu
        val d = DetectorInesperat(c.llindarInesperat, c.minutsInesperat, c.inesperatDes, c.inesperatAlt, c.inesperatPregunta != 0L, c.darrerAvisInesperat)
        val avisa = d.mira(ara(), residu)
        c.inesperatDes = d.des
        c.inesperatAlt = d.alt
        c.darrerAvisInesperat = d.darrerAvis
        if (!d.avisat) c.inesperatPregunta = 0
        if (!avisa) return null
        val id = bd.creaPregunta(d.des, residu)
        c.inesperatPregunta = id
        return bd.pregunta(id)
    }

    /**
     * Quins avisos de consum inesperat hauria donat la setmana passada amb un llindar i uns minuts, amb el que l'app
     * sap ara: quan va començar cada episodi i quants W sense explicar hi havia. Null si encara no hi ha un dia de
     * dades exactes.
     */
    fun simulaAvisos(llindarW: Double, minuts: Int): List<Pair<Long, Double>>? {
        val serie = bd.serie(ara() - 7 * 86400, ara() + 60).filterNot { it.aprox }
        if (serie.size < 24 * 60) return null
        val fons = fonsW() ?: return null
        val e = explicador()
        val d = DetectorInesperat(llindarW, minuts)
        val linia = config.teLinia
        return serie.mapNotNull { p ->
            val r = e.explica(max(p.wTotal - (if (linia) p.wLinia else 0.0), 0.0) - fons).residu
            if (d.mira(p.ts, r)) d.des to r else null
        }
    }

    /**
     * Els moments punta d'un període de calendari: el minut de més consum del dia, o, per a la setmana i el mes, el de
     * cada dia, i els [quants] més alts. De l'any, cap: seria llegir-ne tots els minuts.
     */
    fun pics(periode: Periode, quants: Int = 3, dia: LocalDate = LocalDate.now(zona)): List<Pic> {
        if (periode == Periode.ANY) return emptyList()
        val desDe = iniciDia(iniciPeriode(periode, dia))
        // Els minuts que no són del Shelly (del núvol) són mitjanes: no poden ser un moment punta
        val serie = bd.serie(desDe, minOf(iniciDia(fiPeriode(periode, dia)), ara() + 60)).filterNot { it.aprox }
        if (serie.isEmpty()) return emptyList()
        val maxims = if (periode == Periode.DIA) listOf(serie.maxBy { it.wTotal })
        else serie.groupBy { Instant.ofEpochSecond(it.ts).atZone(zona).toLocalDate() }.values.map { dia -> dia.maxBy { it.wTotal } }
        return maxims.sortedByDescending { it.wTotal }.take(if (periode == Periode.DIA) 1 else quants)
            .map { Pic(it.ts, it.wTotal, bd.pmax(it.ts)) }
    }

    /**
     * Què hi havia encès en un pic: la línia de la segona pinça, el fons i els trossos d'[AnalisiPics], cadascun amb
     * els aparells après que l'expliquen (si en queda poc per explicar) o, si no, com a desconegut per preguntar-ne.
     */
    fun analitzaPics(pics: List<Pic>): List<AnalisiPic> {
        if (pics.isEmpty()) return emptyList()
        // El que és comú a tots els pics es llegeix un sol cop
        val fons = fonsW() ?: 0.0
        val refs = referencies()
        val aparells = aparells()
        val aEntrenar = aparellsAEntrenar()
        val linia = explicadorLinia()
        val refsLinia = referenciesLinia()
        return pics.map { analitzaPic(it, fons, refs, aparells.associateBy { a -> a.id }, aEntrenar, linia, refsLinia) }
    }

    private fun analitzaPic(
        pic: Pic, fons: Double, refs: Map<Long, Referencia>, tots: Map<Long, Aparell>, aEntrenar: List<Aparell>,
        explicadorLinia: Explicador?, refsLinia: Map<Long, Referencia>,
    ): AnalisiPic {
        val linia = config.teLinia
        val serie = bd.serie(pic.ts - AnalisiPics.FINESTRA_S, pic.ts + 1)
        // Amb minuts aproximats (des de fora, trams plans de mitjanes) no es poden veure salts: tot queda sense identificar
        val trossos = if (serie.any { it.aprox }) emptyList()
        else AnalisiPics.trossos(serie.map { it.ts to max(it.wTotal - (if (linia) it.wLinia else 0.0), 0.0) }, fons)
        val elements = mutableListOf<ElementPic>()
        val alPic = serie.lastOrNull()
        val wLinia = if (linia) alPic?.wLinia ?: 0.0 else 0.0
        if (wLinia > 0) {
            // Els aparells de la línia que l'expliquen (maqueta 12); el que quedi, la línia
            val e = explicadorLinia?.explica(wLinia)
            e?.aparells?.forEach { (id, w) -> tots[id]?.let { a -> elements += AparellPic(a, w, null, refsLinia[id]?.apresa == true) } }
            val resta = e?.residu ?: wLinia
            if (resta > 0) elements += LiniaPic(resta)
        }
        if (fons > 0) elements += SempreEncesPic(minOf(fons, pic.w - wLinia))
        // Un aparell només pot ser en un tros: primer els estables i el que ja hi era, que són més segurs
        val usats = mutableSetOf<Long>()
        for (tros in trossos.sortedBy { it.tipus == TipusTros.VARIABLE }) {
            // El que puja i baixa es compara amb la seva potència típica, i els aparells que l'expliquen s'emporten tot el tros
            val variable = tros.tipus == TipusTros.VARIABLE
            val objectiu = if (variable) tros.mostra else tros.w
            // Primer els aparells de la forma que toca (maqueta 12): el que puja i baixa, els que polsen; el que és estable, els
            // que no. Si no l'expliquen, tots.
            val lliures = refs.values.filter { it.aparellId !in usats }
            val explicat = { e: Explicacio -> e.aparells.isNotEmpty() && e.residu <= 0.25 * objectiu }
            val e = Explicador(lliures.filter { (it.forma == Forma.POLSA) == variable }).explica(objectiu).takeIf(explicat)
                ?: Explicador(lliures).explica(objectiu)
            if (explicat(e)) {
                usats += e.aparells.map { it.first }
                val escala = if (variable) tros.w / e.aparells.sumOf { it.second } else 1.0
                e.aparells.forEach { (id, w) ->
                    tots[id]?.let { a -> elements += AparellPic(a, w * escala, tros.desDe.takeIf { tros.tipus != TipusTros.JA_HI_ERA }, refs[id]?.apresa == true) }
                }
            } else {
                elements += DesconegutPic(tros, endevina(tros.mostra, aEntrenar.filter { it.id !in usats }, refs, forma = if (variable) Forma.POLSA else null))
            }
        }
        // El que queda (el que els aparells no expliquen del tot): sense identificar
        val resta = pic.w - elements.sumOf { it.w }
        if (resta > 50) elements += SenseIdentificarPic(resta)
        // La gràfica: des d'una mica abans del primer tros que s'encén (com a molt 90 minuts abans del pic) fins a mitja hora després
        val primer = trossos.filter { it.tipus != TipusTros.JA_HI_ERA }.minOfOrNull { it.desDe } ?: pic.ts
        val inici = minOf(primer, pic.ts - 30 * 60) - 15 * 60
        val finestra = bd.serie(maxOf(inici, pic.ts - 90 * 60L - 15 * 60), pic.ts + 30 * 60)
        return AnalisiPic(pic, elements.sortedByDescending { it.w }, trossos, finestra)
    }

    /**
     * Una estona passada, per ensenyar-hi un aparell: la potència de l'estona (amb un quart d'hora més a cada costat,
     * per a la gràfica) i els trams que hi ha. Si una part és aproximada (des de fora), no s'hi busca res.
     */
    fun estona(desDe: Long, finsA: Long): Estona {
        val serie = bd.serie(desDe - 15 * 60, finsA + 15 * 60)
        val dins = serie.filter { it.ts in desDe until finsA }
        val aproximada = dins.any { it.aprox }
        val linia = config.teLinia
        val trams = if (aproximada) emptyList()
        else EstonaPassada.trams(dins.map { it.ts to max(it.wTotal - (if (linia) it.wLinia else 0.0), 0.0) })
        return Estona(desDe, finsA, serie, trams, aproximada)
    }

    /**
     * Els trams que l'usuari ha marcat d'una estona passada, com a mostres de l'aparell. Si l'engega l'usuari, els salts
     * en encendre's i en apagar-se (com en directe); si s'engega sol, l'interval de cada tram. Dues vegades la mateixa
     * estona no fan mostres repetides.
     */
    @Synchronized
    fun ensenyaEstona(aparellId: Long, trams: List<TramPassat>) {
        val a = bd.aparells().firstOrNull { it.id == aparellId } ?: return
        for (t in trams) {
            val durada = t.fi - t.ts + 60
            if (a.funciona == Funcionament.SOL) {
                if (bd.teMostra(aparellId, t.ts, "passat")) continue
                val id = bd.desaMostra(aparellId, t.ts, mediana(t.exces), durada, "passat")
                val s = t.exces.sorted()
                bd.desaInterval(id, s[s.size / 10] * 0.8, s.last() * 1.2)
            } else {
                val (puja, baixa) = t.mostres
                if (!bd.teMostra(aparellId, t.ts, "passat")) bd.desaMostra(aparellId, t.ts, puja, durada, "passat")
                if (!bd.teMostra(aparellId, t.fi, "passat")) bd.desaMostra(aparellId, t.fi, baixa, durada, "passat")
            }
        }
    }

    /** L'usuari diu quin aparell era un tros d'un pic: és una mostra de l'aparell, com si l'hagués ensenyat en directe. */
    @Synchronized
    fun respon(aparellId: Long, tros: Tros) {
        // Dos tocs seguits (o el mateix pic a la llista i obert) no fan dues mostres
        if (!bd.teMostra(aparellId, tros.desDe, "pic")) bd.desaMostra(aparellId, tros.desDe, tros.mostra, null, "pic")
    }

    /** Els aparells que expliquen ara el consum (com a molt 3), per dir-ho a l'avís de potència: nom i W. */
    fun encesAra(l: Lectura): List<Pair<String, Double>> {
        val wLinia = if (config.teLinia) (l.linia?.w ?: 0.0).coerceAtLeast(0.0) else 0.0
        val fons = fonsW() ?: return emptyList()
        val noms = aparells().associate { it.id to it.nom }
        val llista = explicador().explica(l.total.w - wLinia - fons).aparells.mapNotNull { (id, w) -> noms[id]?.let { it to w } }.toMutableList()
        if (wLinia >= AnalisiPics.LLINDAR) {
            // Els aparells de la línia que l'expliquen; el que quedi, amb el nom de la línia
            val e = explicadorLinia()?.explica(wLinia)
            e?.aparells?.forEach { (id, w) -> noms[id]?.let { llista += it to w } }
            val resta = e?.residu ?: wLinia
            if (resta >= AnalisiPics.LLINDAR) llista += config.nomLinia to resta
        }
        return llista.sortedByDescending { it.second }.take(3)
    }

    /**
     * Reparteix el consum sense identificar del mes entre els aparells après, minut a minut, al preu de cada hora; i la
     * línia de la pinça 2 entre els seus aparells. Amb la durada de la firma ([ajustaDurades]): un aparell no es queda una
     * estona molt més llarga del que dura.
     */
    fun repartimentMes(): Repartiment {
        val desDe = iniciDia(LocalDate.now(zona).withDayOfMonth(1))
        val refs = referencies()
        val explicador = Explicador(refs.values.toList())
        val refsLinia = referenciesLinia()
        val linia = if (aparellsLinia().isEmpty()) null else Explicador(refsLinia.values.toList())
        val fons = fonsWh() * 60
        val p = Preus(desDe, ara())
        val serie = bd.serie(desDe, ara() + 60)
        val kwh = mutableMapOf<Long, Double>()
        val euros = mutableMapOf<Long, Double>()
        fun suma(punt: Punt, e: Explicacio): Double {
            val preu = p.de(punt.ts)
            for ((id, w) in e.aparells) {
                kwh[id] = (kwh[id] ?: 0.0) + w / 60_000
                euros[id] = (euros[id] ?: 0.0) + w / 60_000 * preu
            }
            return preu
        }
        var residuKwh = 0.0
        var residuEuros = 0.0
        // La resta de la casa, el que passa del fons; un minut aproximat és la mitjana d'una estona: no es pot dir quins
        // aparells hi havia, i va sencer al residu
        val resta = serie.filter { it.wTotal - it.wLinia - fons > 0 }
        val explicacions = ajustaDurades(
            resta.map { pt ->
                val exces = max(pt.wTotal - pt.wLinia, 0.0) - fons
                pt.ts to if (pt.aprox) Explicacio(emptyList(), exces) else explicador.explica(exces)
            }, refs,
        )
        resta.zip(explicacions).forEach { (pt, e) ->
            val preu = suma(pt, e)
            residuKwh += e.residu / 60_000
            residuEuros += e.residu / 60_000 * preu
        }
        // La línia, entre els seus aparells (maqueta 12)
        var liniaKwh = 0.0
        var liniaEuros = 0.0
        if (linia != null) {
            val ambLinia = serie.filter { it.wLinia > 0 }
            val e = ajustaDurades(ambLinia.map { pt -> pt.ts to if (pt.aprox) Explicacio(emptyList(), pt.wLinia) else linia.explica(pt.wLinia) }, refsLinia)
            ambLinia.zip(e).forEach { (pt, el) ->
                val preu = suma(pt, el)
                liniaKwh += el.residu / 60_000
                liniaEuros += el.residu / 60_000 * preu
            }
        }
        return Repartiment(kwh, euros, residuKwh, residuEuros, liniaKwh, liniaEuros)
    }
}
