package cat.jordi.consum.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cat.jordi.consum.Actualitzacio
import cat.jordi.consum.Avisos
import cat.jordi.consum.ConsumApp
import cat.jordi.consum.R
import cat.jordi.consum.Vigilancia
import cat.jordi.consum.VersioNova
import cat.jordi.consum.dades.Aparell
import cat.jordi.consum.dades.Avui
import cat.jordi.consum.dades.ConsellLinia
import cat.jordi.consum.dades.Explicador
import cat.jordi.consum.dades.InfoPreu
import cat.jordi.consum.dades.Periode
import cat.jordi.consum.dades.Pregunta
import cat.jordi.consum.dades.Punt
import cat.jordi.consum.dades.Referencia
import cat.jordi.consum.dades.Repartiment
import cat.jordi.consum.dades.Resum
import cat.jordi.consum.dades.Tarifa
import cat.jordi.consum.dades.TextRecurs
import cat.jordi.consum.dades.mediana
import cat.jordi.consum.dades.missatge
import cat.jordi.consum.shelly.Lectura
import cat.jordi.consum.shelly.Origen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Pantalla { CONNECTA, PRINCIPAL, AFEGIR, QUESTIONARI, PREU }

enum class Pestanya(@StringRes val nomId: Int) {
    ARA(R.string.pestanya_ara), APARELLS(R.string.pestanya_aparells), ENTRENA(R.string.pestanya_entrena), HISTORIAL(R.string.pestanya_historial)
}

class ConsumViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as ConsumApp).repo
    val config get() = repo.config

    var pantalla by mutableStateOf(if (repo.config.configurat) Pantalla.PRINCIPAL else Pantalla.CONNECTA)
    var pestanya by mutableStateOf(Pestanya.ARA)

    /** La lectura en directe; null si ara mateix no s'arriba al Shelly ni pel núvol. */
    var lectura by mutableStateOf<Lectura?>(null)
        private set
    var haLlegit by mutableStateOf(false)
        private set

    var serie by mutableStateOf<List<Punt>>(emptyList())
        private set
    var avuiKwh by mutableStateOf(0.0)
        private set
    var avuiEuros by mutableStateOf(0.0)
        private set

    /** Una part del consum d'avui s'ha calculat des de fora i és aproximada. */
    var avuiAproximat by mutableStateOf(false)
        private set
    var infoPreu by mutableStateOf<InfoPreu?>(null)
        private set
    var consellLinia by mutableStateOf<ConsellLinia?>(null)
        private set
    var fonsW by mutableStateOf<Double?>(null)
        private set
    var periode by mutableStateOf(Periode.DIA)
        private set
    var resum by mutableStateOf<Resum?>(null)
        private set
    var resumMes by mutableStateOf<Resum?>(null)
        private set
    var aparells by mutableStateOf<List<Aparell>>(emptyList())
        private set
    var sincronitzant by mutableStateOf(false)
        private set
    var errorSincronitzacio by mutableStateOf<TextRecurs?>(null)
        private set

    // ---------- Entrenament ----------
    var referencies by mutableStateOf<Map<Long, Referencia>>(emptyMap())
        private set
    var explicador by mutableStateOf(Explicador(emptyList()))
        private set
    var repartiment by mutableStateOf<Repartiment?>(null)
        private set
    var preguntes by mutableStateOf<List<Pregunta>>(emptyList())
        private set
    var aparellsAEntrenar by mutableStateOf<List<Aparell>>(emptyList())
        private set

    /** L'aparell que s'està mesurant ara (si n'hi ha) i el missatge del resultat de l'últim. */
    var mesurant by mutableStateOf<Long?>(null)
        private set
    var missatgeEntrena by mutableStateOf<TextRecurs?>(null)
        private set

    /** De quin aparell és el missatge, perquè surti a sota seu (null: és general). */
    var missatgeDe by mutableStateOf<Long?>(null)
        private set

    /** Els aparells que l'usuari ha marcat com a encesos, i des de quan. */
    var encesos by mutableStateOf<Map<Long, Long>>(emptyMap())
        private set

    /** Les lectures dels últims dos minuts (instant, W sense la línia), per mesurar els salts. */
    private val viu = ArrayDeque<Pair<Long, Double>>()

    // ---------- Actualitzacions ----------
    var versioNova by mutableStateOf<VersioNova?>(null)
        private set
    var progresActualitzacio by mutableStateOf<Float?>(null)
        private set
    var missatgeActualitzacio by mutableStateOf<TextRecurs?>(null)
        private set

    private var bucles: Job? = null

    init {
        refresca()
        comprovaVersio(forcat = false)
    }

    /** Mira a GitHub si hi ha una versió nova: cada cop que s'obre l'app, com a molt un cop per hora; amb el botó, sempre. */
    fun comprovaVersio(forcat: Boolean) {
        val ara = System.currentTimeMillis()
        if (!forcat && ara - config.darreraComprovacio < 3_600_000L) return
        if (forcat) missatgeActualitzacio = null
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Actualitzacio.comprova(getApplication()) } }
            config.darreraComprovacio = ara
            r.onSuccess {
                versioNova = it
                if (forcat && it == null) missatgeActualitzacio = TextRecurs(R.string.vm_ultima_versio)
            }.onFailure {
                if (forcat) missatgeActualitzacio = TextRecurs(R.string.vm_no_he_pogut_mirar, it.missatge(getApplication<Application>()).orEmpty())
            }
        }
    }

    /** Baixa la versió nova i obre l'instal·lador. El primer cop cal permetre que Consum instal·li apps. */
    fun actualitza() {
        val v = versioNova ?: return
        val context = getApplication<Application>()
        if (!Actualitzacio.potInstallar(context)) {
            missatgeActualitzacio = TextRecurs(R.string.vm_permet_installar)
            return
        }
        viewModelScope.launch {
            progresActualitzacio = 0f
            val r = withContext(Dispatchers.IO) {
                runCatching { Actualitzacio.baixa(context, v) { p -> viewModelScope.launch { progresActualitzacio = p } } }
            }
            progresActualitzacio = null
            r.onSuccess { Actualitzacio.installa(context, it) }
                .onFailure { missatgeActualitzacio = TextRecurs(R.string.vm_no_s_ha_pogut_baixar, it.missatge(context).orEmpty()) }
        }
    }

    /** Només es llegeix en directe mentre l'app és a la pantalla. */
    fun enPrimerPla(si: Boolean) {
        bucles?.cancel()
        bucles = null
        ConsumApp.oberta = si
        // Amb l'app oberta, Android deixa engegar la vigilància; si cal, continuarà amb l'app tancada
        if (si) Vigilancia.actualitza(getApplication())
        if (si) comprovaVersio(forcat = false)
        if (!si || !config.configurat) return
        bucles = viewModelScope.launch {
            launch {
                var comprovat = 0L
                while (isActive) {
                    val l = repo.lectura()
                    lectura = l
                    haLlegit = true
                    if (l != null) {
                        val ara = System.currentTimeMillis()
                        val resta = resta(l)
                        viu.addLast(ara to resta)
                        while (viu.isNotEmpty() && viu.first().first < ara - 120_000) viu.removeFirst()
                        // Amb l'app oberta, el consum inesperat es mira cada minut
                        if (ara - comprovat > 60_000) {
                            comprovat = ara
                            comprovaInesperat(resta)
                        }
                    }
                    delay(if (l?.origen == Origen.NUVOL) 3000L else 1000L)
                }
            }
            launch {
                while (isActive) {
                    sincronitzaAra()
                    delay(5 * 60_000L)
                }
            }
        }
    }

    // Hi pot haver dues sincronitzacions alhora (la periòdica i el botó): es compten.
    private var enCurs = 0

    private suspend fun sincronitzaAra() {
        enCurs++
        sincronitzant = true
        errorSincronitzacio = runCatching { repo.sincronitza() }.exceptionOrNull()?.let { TextRecurs(R.string.vm_error_sincronitzacio) }
        runCatching { repo.actualitzaPreus() }
        enCurs--
        sincronitzant = enCurs > 0
        refrescaAra()
    }

    fun sincronitza() {
        viewModelScope.launch { sincronitzaAra() }
    }

    private suspend fun refrescaAra() {
        val p = periode
        val dades = withContext(Dispatchers.IO) {
            Dades(
                repo.serie24h(), repo.avui(), repo.fonsW(), repo.resum(p), repo.resumMes(), repo.aparells(),
                repo.infoPreu(), repo.consellLinia(),
                repo.referencies(), repo.repartimentMes(), repo.preguntesPendents(), repo.aparellsAEntrenar(),
            )
        }
        serie = dades.serie
        avuiKwh = dades.avui.kwh
        avuiEuros = dades.avui.euros
        avuiAproximat = dades.avui.aproximat
        infoPreu = dades.preu
        consellLinia = dades.consell
        referencies = dades.referencies
        explicador = Explicador(dades.referencies.values.toList())
        repartiment = dades.repartiment
        preguntes = dades.preguntes
        aparellsAEntrenar = dades.aEntrenar
        fonsW = dades.fons
        resum = dades.resum
        resumMes = dades.mes
        aparells = dades.aparells
    }

    private class Dades(
        val serie: List<Punt>, val avui: Avui, val fons: Double?, val resum: Resum, val mes: Resum,
        val aparells: List<Aparell>, val preu: InfoPreu, val consell: ConsellLinia?,
        val referencies: Map<Long, Referencia>, val repartiment: Repartiment, val preguntes: List<Pregunta>,
        val aEntrenar: List<Aparell>,
    )

    fun refresca() {
        viewModelScope.launch { refrescaAra() }
    }

    fun triaPeriode(p: Periode) {
        periode = p
        refresca()
    }

    fun desaAparells(llista: List<Aparell>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { llista.forEach(repo::desaAparell) }
            refrescaAra()
        }
    }

    /** Si ja n'hi ha un amb el mateix nom que encara té el valor típic (del qüestionari), el substitueix. */
    fun afegeixAparell(a: Aparell) {
        val existent = aparells.firstOrNull { it.perCompletar && it.nom.equals(a.nom, ignoreCase = true) }
        desaAparells(listOf(a.copy(id = existent?.id ?: 0)))
    }

    fun esborraAparell(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.esborraAparell(id) }
            refrescaAra()
        }
    }

    /** El consum sense la línia de la segona pinça, que ja es mesura a part. */
    fun resta(l: Lectura) = (l.total.w - if (config.teLinia) l.linia?.w ?: 0.0 else 0.0).coerceAtLeast(0.0)

    private suspend fun comprovaInesperat(resta: Double) {
        val p = withContext(Dispatchers.IO) { repo.comprovaInesperat(resta) } ?: return
        Avisos.avisa(getApplication(), p, aparellsAEntrenar, referencies)
        refrescaAra()
    }

    private fun medianaDes(desDe: Long, finsA: Long = Long.MAX_VALUE) =
        viu.filter { it.first in desDe..finsA }.map { it.second }.takeIf { it.size >= 2 }?.let(::mediana)

    /**
     * L'usuari diu que acaba d'encendre (o apagar) un aparell. Es compara la potència dels 10 segons d'abans
     * amb la dels 8 segons després d'esperar-ne 20, perquè l'aparell arribi al seu règim. El salt és una mostra.
     */
    fun ensenya(aparell: Aparell, encen: Boolean) {
        if (mesurant != null) return
        val ara = System.currentTimeMillis()
        val abans = medianaDes(ara - 10_000)
        missatgeDe = aparell.id
        if (abans == null) {
            missatgeEntrena = TextRecurs(R.string.vm_entrena_poques_lectures)
            return
        }
        mesurant = aparell.id
        missatgeEntrena = null
        viewModelScope.launch {
            delay(28_000)
            val despres = medianaDes(System.currentTimeMillis() - 8_000)
            val salt = if (despres == null) null else if (encen) despres - abans else abans - despres
            missatgeEntrena = when {
                salt == null -> TextRecurs(R.string.vm_entrena_no_arribo)
                salt < 15 -> TextRecurs(R.string.vm_entrena_cap_canvi, num(salt))
                else -> {
                    val durada = if (encen) null else encesos[aparell.id]?.let { (ara - it) / 1000 }
                    withContext(Dispatchers.IO) { repo.desaMostra(aparell.id, salt, durada) }
                    // Només canvia d'encès a apagat (o al revés) si s'ha vist el salt
                    encesos = if (encen) encesos + (aparell.id to ara) else encesos - aparell.id
                    val n = (referencies[aparell.id]?.mostres ?: 0) + 1
                    TextRecurs(
                        if (encen) R.string.vm_entrena_desat_encen else R.string.vm_entrena_desat_apaga,
                        aparell.nom, num(salt), n, Referencia.PER_APRENDRE,
                    )
                }
            }
            mesurant = null
            refrescaAra()
        }
    }

    fun respon(pregunta: Pregunta, aparellId: Long?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.respon(pregunta.id, aparellId) }
            refrescaAra()
        }
    }

    fun tornaAEntrenar(aparellId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.tornaAEntrenar(aparellId) }
            refrescaAra()
        }
    }

    fun desaTarifa(t: Tarifa) {
        config.tarifa = t
        pantalla = Pantalla.PRINCIPAL
        viewModelScope.launch {
            refrescaAra()
            runCatching { repo.actualitzaPreus() }
            refrescaAra()
        }
    }

    /** Després de canviar la configuració: torna a començar les lectures. */
    fun configuracioDesada() {
        pantalla = Pantalla.PRINCIPAL
        lectura = null
        haLlegit = false
        enPrimerPla(true)
    }
}
