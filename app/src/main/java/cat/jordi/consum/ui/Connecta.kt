package cat.jordi.consum.ui

import android.app.LocaleConfig
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.Actualitzacio
import cat.jordi.consum.R
import cat.jordi.consum.dades.Funcionament
import cat.jordi.consum.Vigilancia
import cat.jordi.consum.dades.Idioma
import cat.jordi.consum.dades.Pais
import cat.jordi.consum.dades.missatge
import cat.jordi.consum.shelly.InfoShelly
import cat.jordi.consum.shelly.ShellyLocal
import cat.jordi.consum.shelly.ShellyNuvol
import cat.jordi.consum.shelly.Trobat
import cat.jordi.consum.shelly.descobreix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** El primer cop, i després com a configuració: on és el Shelly, les pinces, el núvol i el preu. */
@Composable
fun PantallaConnecta(vm: ConsumViewModel) {
    val config = vm.config
    val context = LocalContext.current
    val abast = rememberCoroutineScope()
    var host by rememberSaveable { mutableStateOf(config.host) }
    var info by remember { mutableStateOf<InfoShelly?>(null) }
    var provant by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var nomLinia by rememberSaveable { mutableStateOf(config.nomLinia) }
    var servidor by rememberSaveable { mutableStateOf(config.servidorNuvol) }
    var clau by remember { mutableStateOf(config.clauNuvol) }
    var idNuvol by rememberSaveable { mutableStateOf(config.idDispositiu) }
    var provantNuvol by remember { mutableStateOf(false) }
    var nuvol by remember { mutableStateOf<String?>(null) }
    var nuvolOk by remember { mutableStateOf(false) }
    val trobats = remember { mutableStateListOf<Trobat>() }

    DisposableEffect(Unit) {
        val atura = descobreix(context, config.host) { t -> if (trobats.none { it.host == t.host }) trobats += t }
        onDispose { atura() }
    }

    val teclat = LocalSoftwareKeyboardController.current

    fun prova() {
        teclat?.hide()
        provant = true
        error = null
        info = null
        abast.launch {
            val r = withContext(Dispatchers.IO) { runCatching { ShellyLocal(host.trim()).info() } }
            provant = false
            r.onSuccess { info = it; idNuvol = it.idDispositiu }
                .onFailure { error = context.getString(R.string.connecta_no_hi_arribo) }
        }
    }

    // Per configurar-la des de fora de casa: n'hi ha prou que el núvol respongui.
    fun provaNuvol() {
        teclat?.hide()
        provantNuvol = true
        nuvol = null
        nuvolOk = false
        abast.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { ShellyNuvol(netejaServidor(servidor), clau.trim(), netejaId(idNuvol)).lectura() }
            }
            provantNuvol = false
            r.onSuccess { l ->
                nuvolOk = l != null
                nuvol = if (l != null) context.getString(R.string.connecta_nuvol_respon, num(l.total.w))
                else context.getString(R.string.connecta_nuvol_no_coneix)
            }.onFailure {
                nuvol = if (it.message?.contains("401") == true) context.getString(R.string.connecta_clau_dolenta)
                else context.getString(R.string.connecta_no_arribo_nuvol, it.missatge(context).orEmpty())
            }
        }
    }

    val potDesar = info != null || nuvolOk || (host.trim() == config.host && config.configurat)

    // Els camps es desplacen; el botó de desar queda fix a baix.
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Targeta {
                TitolTargeta(stringResource(R.string.connecta_titol_shelly))
                if (trobats.isEmpty()) Nota(stringResource(R.string.connecta_buscant))
                trobats.forEach { t ->
                    TextButton(onClick = { host = t.host; info = null }) {
                        Icon(Icons.Outlined.Wifi, null)
                        Spacer(Modifier.width(8.dp))
                        Text("${t.nom} · ${t.host}")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        host, { host = it; info = null }, Modifier.weight(1f), label = { Text(stringResource(R.string.connecta_adreca)) },
                        placeholder = { Text("192.168.1.40") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = ::prova, enabled = host.isNotBlank() && !provant) { Text(stringResource(R.string.connecta_prova)) }
                }
                if (provant) CircularProgressIndicator(Modifier.padding(top = 8.dp))
                info?.let {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Check, null, tint = Ok)
                        Text("  " + stringResource(R.string.connecta_info_shelly, it.nom, it.model, it.versio), fontSize = 13.sp, color = Ok)
                    }
                }
                error?.let { Text(it, Modifier.padding(top = 8.dp), color = DesconegutText, fontSize = 13.sp) }
            }

            Targeta {
                TitolTargeta(stringResource(R.string.connecta_titol_pinces))
                Text(stringResource(R.string.connecta_pinca_1), fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    nomLinia, { nomLinia = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.connecta_pinca_2)) },
                    placeholder = { Text(stringResource(R.string.connecta_pinca_2_exemple)) }, singleLine = true,
                    supportingText = { Text(stringResource(R.string.connecta_pinca_2_ajuda)) },
                )
                // Quins aparells hi ha a la línia (maqueta 13): es tria aquí, un cop
                // Ni els sempre encesos ni un que es digui com la línia (és la línia mateixa)
                val candidats = vm.aparells.filter { it.funciona != Funcionament.SEMPRE && !it.nom.equals(nomLinia.trim(), ignoreCase = true) }
                if (nomLinia.isNotBlank() && config.teLinia && candidats.isNotEmpty()) {
                    Text(stringResource(R.string.connecta_linia_aparells), Modifier.padding(top = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    candidats.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().toggleable(a.linia, role = Role.Checkbox) { vm.posaALinia(a, it) }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(a.linia, null)
                            Text(a.nom, fontSize = 14.sp)
                        }
                    }
                    Nota(stringResource(R.string.connecta_linia_aparells_nota))
                }
            }

            Targeta {
                TitolTargeta(stringResource(R.string.connecta_titol_nuvol))
                Nota(stringResource(R.string.connecta_nuvol_nota))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    servidor, { servidor = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.connecta_servidor)) },
                    placeholder = { Text("shelly-00-eu.shelly.cloud") }, singleLine = true,
                )
                OutlinedTextField(
                    clau, { clau = it; nuvolOk = false }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.connecta_clau)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    // Que el teclat no la recordi ni la proposi
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    idNuvol, { idNuvol = it; nuvolOk = false }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.connecta_id)) },
                    placeholder = { Text("a1b2c3d4e5f6") }, singleLine = true,
                    supportingText = { Text(stringResource(R.string.connecta_id_ajuda)) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = ::provaNuvol,
                        enabled = servidor.isNotBlank() && clau.isNotBlank() && idNuvol.isNotBlank() && !provantNuvol,
                    ) { Text(stringResource(R.string.connecta_prova_nuvol)) }
                    if (provantNuvol) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(24.dp), strokeWidth = 2.dp)
                }
                nuvol?.let { Text(it, Modifier.padding(top = 6.dp), color = if (nuvolOk) Ok else Desconegut, fontSize = 13.sp) }
            }

            Targeta {
                TitolTargeta(stringResource(R.string.connecta_titol_preu))
                Text(resumTarifa(config.tarifa), fontSize = 14.sp)
                if (config.configurat) {
                    TextButton(onClick = { vm.pantalla = Pantalla.PREU }) { Text(stringResource(R.string.connecta_canvia_preu)) }
                } else {
                    Nota(stringResource(R.string.connecta_preu_despres))
                }
            }
            if (config.configurat) TargetaAvisPotencia(vm)
            if (config.configurat) TargetaAvisInesperat(vm)
            if (config.teNuvol) TargetaNuvolHores(vm)
            // Abans d'Android 13 no hi ha idioma per app: fa com el mòbil
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) TargetaIdioma()
            Targeta {
                TitolTargeta(stringResource(R.string.connecta_actualitzacions))
                val versio = remember { Actualitzacio.versioActual(context) }
                Text(stringResource(R.string.connecta_versio_actual, versio), fontSize = 14.sp)
                val nova = vm.versioNova
                val progres = vm.progresActualitzacio
                when {
                    progres != null -> {
                        Nota(stringResource(R.string.connecta_baixant, nova?.versio.orEmpty()), Modifier.padding(top = 6.dp))
                        LinearProgressIndicator(progress = { progres }, Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    nova != null -> {
                        Text(stringResource(R.string.connecta_versio_nova, nova.versio), Modifier.padding(top = 4.dp), fontSize = 14.sp, color = Primari)
                        Button(onClick = vm::actualitza, Modifier.padding(top = 6.dp)) { Text(stringResource(R.string.connecta_actualitza)) }
                        Nota(stringResource(R.string.connecta_samsung), Modifier.padding(top = 6.dp))
                    }
                    else -> TextButton(onClick = { vm.comprovaVersio(forcat = true) }) { Text(stringResource(R.string.connecta_mira_noves)) }
                }
                vm.missatgeActualitzacio?.let { Nota(it.text(), Modifier.padding(top = 4.dp)) }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.padding(12.dp)) {
            Button(
                onClick = {
                    config.host = host.trim()
                    info?.let { config.model = it.model }
                    config.idDispositiu = netejaId(idNuvol)
                    // Amb la pinça 2 nova (o sense), les vegades es tornen a buscar de cap a peus (maqueta 13)
                    if (config.nomLinia.isBlank() != nomLinia.isBlank()) vm.tornaABuscarVegades()
                    config.nomLinia = nomLinia
                    config.servidorNuvol = servidor
                    config.clauNuvol = clau
                    vm.configuracioDesada()
                    vm.sincronitza()
                },
                Modifier.fillMaxWidth(), enabled = potDesar,
            ) { Text(stringResource(if (config.configurat) R.string.connecta_desa else R.string.connecta_comenca)) }
            if (!potDesar) {
                Nota(stringResource(R.string.connecta_prova_primer), Modifier.align(Alignment.CenterHorizontally))
            }

        }
    }
}

/**
 * La potència contractada i l'avís quan se'n passa del 90 %: al moment (la vigilància, amb una notificació fixa)
 * o cada 15 minuts. Els canvis es desen de seguida.
 */
@Composable
private fun TargetaAvisPotencia(vm: ConsumViewModel) {
    val context = LocalContext.current
    val config = vm.config
    val tarifa = config.tarifa
    var kw by rememberSaveable { mutableStateOf(tarifa.limitKw?.let { num(it, 2) } ?: "") }
    var avis by remember { mutableStateOf(config.avisPotencia) }
    var alMoment by remember { mutableStateOf(config.avisAlMoment) }
    val permis = rememberPermisAvisos { Vigilancia.actualitza(context) }
    fun aplica() = Vigilancia.actualitza(context)

    Targeta {
        TitolTargeta(stringResource(R.string.connecta_titol_potencia))
        OutlinedTextField(
            kw, { nou ->
                kw = nou
                val valor = llegeixNum(nou, milers = false)?.takeIf { it > 0 }
                if (valor != null || nou.isBlank()) {
                    config.tarifa = config.tarifa.ambLimit(valor)
                    aplica()
                }
            },
            Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.connecta_potencia_contractada)) },
            suffix = { Text("kW") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Nota(
            stringResource(if (tarifa.pais == Pais.ES) R.string.connecta_potencia_es else R.string.connecta_potencia_nl),
            Modifier.padding(top = 4.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp)
                .toggleable(avis, role = Role.Switch) { avis = it; config.avisPotencia = it; aplica() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.connecta_avisa_90), Modifier.weight(1f), fontSize = 14.sp)
            Switch(avis, null)
        }
        Nota(stringResource(R.string.connecta_colors_grafica))
        if (avis) {
            Text(stringResource(R.string.connecta_app_tancada), Modifier.padding(top = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            for ((opcio, titol, text) in listOf(
                Triple(true, R.string.connecta_al_moment, R.string.connecta_al_moment_text),
                Triple(false, R.string.connecta_cada_15, R.string.connecta_cada_15_text),
            )) {
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(alMoment == opcio, role = Role.RadioButton) { alMoment = opcio; config.avisAlMoment = opcio; aplica() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    RadioButton(alMoment == opcio, null, Modifier.padding(12.dp))
                    Column(Modifier.padding(top = 12.dp)) {
                        Text(stringResource(titol), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Nota(stringResource(text))
                    }
                }
            }
            if (!permis.actius) {
                Nota(stringResource(R.string.connecta_cal_permis), Modifier.padding(top = 4.dp))
                TextButton(onClick = permis.demana) { Text(stringResource(R.string.entrena_activa_avisos)) }
            }
        }
    }
}

/**
 * Els avisos de consum inesperat (maqueta 7): si n'hi ha, a partir de quant i durant quant. Mentre es tria, diu
 * quants avisos hauria donat la setmana passada amb el que l'app sap ara.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetaAvisInesperat(vm: ConsumViewModel) {
    val config = vm.config
    var actiu by remember { mutableStateOf(config.avisInesperat) }
    var llindar by remember { mutableStateOf(config.llindarInesperat) }
    var minuts by remember { mutableStateOf(config.minutsInesperat) }
    LaunchedEffect(actiu, llindar, minuts) { vm.ajustaInesperat(actiu, llindar, minuts) }

    Targeta {
        TitolTargeta(stringResource(R.string.connecta_titol_inesperat))
        Row(
            Modifier.fillMaxWidth().toggleable(actiu, role = Role.Switch) { actiu = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.connecta_avisa_inesperat), Modifier.weight(1f), fontSize = 14.sp)
            Switch(actiu, null)
        }
        if (!actiu) return@Targeta
        Text(stringResource(R.string.connecta_inesperat_quant), Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (w in listOf(500.0, 1000.0, 2000.0, 3000.0)) {
                FilterChip(llindar == w, { llindar = w }, label = { Text("${num(w / 1000, if (w < 1000) 1 else 0)} kW") })
            }
        }
        Text(stringResource(R.string.connecta_inesperat_durant), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (m in listOf(10, 30, 60)) {
                FilterChip(minuts == m, { minuts = m }, label = {
                    Text(if (m == 60) stringResource(R.string.connecta_inesperat_hora) else stringResource(R.string.connecta_inesperat_minuts, m))
                })
            }
        }
        vm.fonsW?.let { Nota(stringResource(R.string.connecta_inesperat_normal, num(it)), Modifier.padding(top = 2.dp)) }
        val previsio = vm.previsioAvisos
        val text = when {
            vm.previsioSenseDades -> stringResource(R.string.connecta_inesperat_sense_dades)
            previsio == null -> null
            previsio.isEmpty() -> stringResource(R.string.connecta_inesperat_cap)
            else -> {
                // Com a molt 3, i si n'hi ha més, quants
                val moments = previsio.take(3).map { (ts, w) -> momentAvis(ts, w) } +
                    listOfNotNull((previsio.size - 3).takeIf { it > 0 }?.let { pluralStringResource(R.plurals.connecta_inesperat_mes, it, it) })
                val llista = if (moments.size == 1) moments[0]
                else moments.dropLast(1).joinToString(", ") + stringResource(R.string.comu_i) + moments.last()
                pluralStringResource(R.plurals.connecta_inesperat_previsio, previsio.size, previsio.size, llista)
            }
        }
        text?.let {
            Text(
                it, Modifier.padding(top = 8.dp).fillMaxWidth().background(PrimariFluix.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(10.dp),
                fontSize = 13.sp, lineHeight = 18.sp,
            )
        }
    }
}

/**
 * Omplir el que falta amb el consum per hores del núvol (maqueta 11): les vacances de més de 7 dies i el d'abans
 * d'instal·lar l'app. Es pot apagar; apagada, aquells dies queden sense dades.
 */
@Composable
private fun TargetaNuvolHores(vm: ConsumViewModel) {
    var actiu by remember { mutableStateOf(vm.config.nuvolHores) }
    LaunchedEffect(Unit) { vm.miraNuvol() }
    Targeta {
        TitolTargeta(stringResource(R.string.connecta_titol_nuvol_hores))
        Row(
            Modifier.fillMaxWidth().toggleable(actiu, role = Role.Switch) { actiu = it; vm.ajustaNuvol(it) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.connecta_omple_nuvol), Modifier.weight(1f), fontSize = 14.sp)
            Switch(actiu, null)
        }
        Nota(stringResource(R.string.connecta_nuvol_hores_text), Modifier.padding(top = 2.dp))
        if (!actiu) return@Targeta
        val r = vm.resumNuvol
        when {
            vm.omplintNuvol -> Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("  " + stringResource(R.string.connecta_nuvol_omplint), fontSize = 13.sp, color = Suau)
            }
            r != null && r.first > 0 -> {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(R.string.connecta_nuvol_recuperat), Modifier.weight(1f), fontSize = 14.sp)
                    Text(pluralStringResource(R.plurals.connecta_nuvol_dies_kwh, r.first, r.first, num(r.second)), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                r.third?.let { ts ->
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                        Text(stringResource(R.string.connecta_nuvol_des_de), Modifier.weight(1f), fontSize = 14.sp)
                        val d = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).toLocalDate()
                        Text(d.format(DateTimeFormatter.ofPattern(stringResource(R.string.format_dia_mes), Idioma.locale)), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            r != null -> Nota(stringResource(R.string.connecta_nuvol_res), Modifier.padding(top = 6.dp))
        }
        Text(
            stringResource(R.string.connecta_nuvol_quadra), Modifier.padding(top = 8.dp).fillMaxWidth()
                .background(PrimariFluix.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(10.dp),
            fontSize = 13.sp, lineHeight = 18.sp,
        )
    }
}

/** «dissabte a les 12:46 (+3,0 kW)»: quan va començar un avís i quants kW de més hi havia. */
@Composable
private fun momentAvis(ts: Long, w: Double): String {
    val d = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault())
    return stringResource(
        R.string.connecta_inesperat_moment, d.dayOfWeek.getDisplayName(TextStyle.FULL, Idioma.locale),
        "%d:%02d".format(d.hour, d.minute), "${num(w / 1000, 1)} kW",
    )
}

/**
 * L'idioma de l'app: com el mòbil o una de les llengües que té traduïdes (les carpetes values-xx).
 * En triar-ne un, Android torna a crear l'activitat.
 */
@OptIn(ExperimentalLayoutApi::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun TargetaIdioma() {
    val context = LocalContext.current
    val gestor = remember { context.getSystemService(LocaleManager::class.java) } ?: return
    val llengues = remember {
        val l = LocaleConfig(context).supportedLocales ?: LocaleList.getEmptyLocaleList()
        (0 until l.size()).map { l[it] }
    }
    var triada by remember { mutableStateOf(gestor.applicationLocales.let { if (it.isEmpty) null else it[0] }) }
    // La triada pot venir de la configuració d'Android amb el país (nl-NL): si no hi és igual, per la llengua
    val marcada = triada?.let { t -> llengues.firstOrNull { it == t } ?: llengues.firstOrNull { it.language == t.language } }

    fun tria(l: Locale?) {
        triada = l
        gestor.applicationLocales = if (l == null) LocaleList.getEmptyLocaleList() else LocaleList(l)
    }

    Targeta {
        TitolTargeta(stringResource(R.string.connecta_idioma))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = triada == null, onClick = { tria(null) }, label = { Text(stringResource(R.string.connecta_idioma_mobil)) })
            llengues.forEach { l ->
                FilterChip(
                    selected = l == marcada, onClick = { tria(l) },
                    label = { Text(l.getDisplayName(l).replaceFirstChar { it.titlecase(l) }) },
                )
            }
        }
    }
}

private fun netejaServidor(s: String) = s.trim().removePrefix("https://").removeSuffix("/")

/** L'ID que fa servir el núvol és la MAC en minúscules: accepta "shellyemg3-dcb4…" o "DC:B4:…". */
private fun netejaId(s: String) = s.trim().lowercase().substringAfterLast('-').replace(":", "")
