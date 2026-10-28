@file:OptIn(ExperimentalLayoutApi::class)

package cat.jordi.consum.ui

import android.app.LocaleConfig
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.Actualitzacio
import cat.jordi.consum.R
import cat.jordi.consum.Vigilancia
import cat.jordi.consum.dades.ASPECTE_CLAR
import cat.jordi.consum.dades.ASPECTE_FOSC
import cat.jordi.consum.dades.ASPECTE_MOBIL
import cat.jordi.consum.dades.Com
import cat.jordi.consum.dades.Funcionament
import cat.jordi.consum.dades.Idioma
import cat.jordi.consum.dades.Pais
import cat.jordi.consum.dades.PeriodeExport
import cat.jordi.consum.dades.TipusExport
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

/**
 * La Configuració (maqueta 18): una llista de seccions, cadascuna amb el valor actual en una línia, i cada secció una pantalla.
 * Els canvis es desen sols, tret de la Connexió (l'adreça, les pinces i el núvol), que té el seu «Desa». El primer cop (sense
 * cap Shelly), només la Connexió, amb «Comença».
 */
@Composable
fun PantallaConnecta(vm: ConsumViewModel) {
    if (!vm.config.configurat) {
        SeccioConnexio(vm)
        return
    }
    when (vm.seccioConfig) {
        null -> LlistaSeccions(vm)
        SeccioConfig.CONNEXIO -> SeccioConnexio(vm)
        SeccioConfig.PREU -> SeccioPreu(vm)
        SeccioConfig.AVISOS -> SeccioAvisos(vm)
        SeccioConfig.DADES -> SeccioDades(vm)
        SeccioConfig.ASPECTE -> SeccioAspecte(vm)
        SeccioConfig.SOBRE -> SeccioSobre(vm)
    }
}

/** Una pantalla de la Configuració: la columna que es desplaça. */
@Composable
private fun Pagina(contingut: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        contingut()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LlistaSeccions(vm: ConsumViewModel) {
    val config = vm.config
    val context = LocalContext.current
    val tarifa = config.tarifa
    val versio = remember { Actualitzacio.versioActual(context) }
    val connexio = listOfNotNull(
        if (config.host.isNotBlank()) stringResource(R.string.config_shelly_a, config.host) else stringResource(R.string.config_nomes_nuvol),
        stringResource(R.string.config_nuvol_ok).takeIf { config.teNuvol },
    ).joinToString(" · ")
    val preu = listOfNotNull(resumTarifa(tarifa), tarifa.limitKw?.let { "${num(it, 2)} kW" }).joinToString(" · ")
    val avisos = listOfNotNull(
        if (config.avisPotencia && tarifa.limitKw != null) stringResource(if (config.avisAlMoment) R.string.config_avis_90_al_moment else R.string.config_avis_90_cada15) else null,
        if (config.avisInesperat) stringResource(R.string.config_inesperat_valor, num(config.llindarInesperat / 1000, if (config.llindarInesperat < 1000) 1 else 0), textMinuts(config.minutsInesperat)) else null,
    ).ifEmpty { listOf(stringResource(R.string.config_avisos_apagats)) }.joinToString(" · ")
    val dades = listOfNotNull(
        if (config.teNuvol) stringResource(if (config.nuvolHores) R.string.config_nuvol_hores_si else R.string.config_nuvol_hores_no) else null,
        stringResource(R.string.config_exportar),
    ).joinToString(" · ")
    val aspecte = stringResource(
        when (vm.aspecte) {
            ASPECTE_CLAR -> R.string.connecta_aspecte_clar
            ASPECTE_FOSC -> R.string.connecta_aspecte_fosc
            else -> R.string.connecta_aspecte_mobil
        }
    ) + " · " + Idioma.locale.getDisplayLanguage(Idioma.locale).replaceFirstChar { it.titlecase(Idioma.locale) }
    val sobre = listOfNotNull(stringResource(R.string.config_versio, versio), vm.versioNova?.let { stringResource(R.string.config_versio_nova_curt, it.versio) }).joinToString(" · ")
    Pagina {
        Targeta {
            val files = listOf(
                Triple(SeccioConfig.CONNEXIO, Icons.Outlined.Wifi, connexio),
                Triple(SeccioConfig.PREU, Icons.Outlined.Euro, preu),
                Triple(SeccioConfig.AVISOS, Icons.Outlined.NotificationsNone, avisos),
                Triple(SeccioConfig.DADES, Icons.Outlined.Storage, dades),
                Triple(SeccioConfig.ASPECTE, Icons.Outlined.Palette, aspecte),
                Triple(SeccioConfig.SOBRE, Icons.Outlined.Info, sobre),
            )
            files.forEachIndexed { i, (seccio, icona, valor) ->
                if (i > 0) Ratlla()
                FilaSeccio(icona, stringResource(seccio.nomId), valor) { vm.seccioConfig = seccio }
            }
        }
    }
}

@Composable
private fun FilaSeccio(icona: ImageVector, titol: String, valor: String, obre: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = obre).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Cercle(icona, EstatIcona.NEUTRE, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titol, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(valor, fontSize = 11.5.sp, color = Suau, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = Suau)
    }
}

/** «30 min» o «1 hora». */
@Composable
private fun textMinuts(m: Int) = if (m == 60) stringResource(R.string.connecta_inesperat_hora) else stringResource(R.string.connecta_inesperat_minuts, m)

/** La connexió: on és el Shelly, les pinces i el núvol. El primer cop és tota la pantalla, amb «Comença». */
@Composable
private fun SeccioConnexio(vm: ConsumViewModel) {
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Targeta {
                TitolTargeta(stringResource(R.string.connecta_titol_shelly))
                // Els que es troben, en una sola fila que es desplaça de costat: amb l'alçada fixa, la pàgina no es mou mentre
                // en van sortint i un toc no cau en un altre lloc. La nota hi és sempre, a sobre
                Nota(stringResource(R.string.connecta_buscant))
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterStart) {
                    if (trobats.isEmpty()) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            trobats.forEach { t ->
                                AssistChip(
                                    onClick = { host = t.host; info = null },
                                    label = { Text("${t.nom} · ${t.host}") },
                                    leadingIcon = { Icon(Icons.Outlined.Wifi, null, Modifier.size(18.dp)) },
                                )
                            }
                        }
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
                    OpcioAmbAjuda(stringResource(R.string.connecta_linia_aparells), stringResource(R.string.connecta_linia_aparells_nota), apilat = true) {
                        Column {
                            candidats.forEach { a ->
                                Row(
                                    Modifier.fillMaxWidth().toggleable(a.linia, role = Role.Checkbox) { vm.posaALinia(a, it) }.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(a.linia, null)
                                    Text(a.nom, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }

            Targeta {
                TitolTargeta(stringResource(R.string.connecta_titol_nuvol))
                OpcioAmbAjuda(stringResource(R.string.connecta_nuvol_opcional), stringResource(R.string.connecta_nuvol_nota)) {}
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
                nuvol?.let { Text(it, Modifier.padding(top = 6.dp), color = if (nuvolOk) Ok else DesconegutText, fontSize = 13.sp) }
            }
            if (!config.configurat) {
                Targeta {
                    TitolTargeta(stringResource(R.string.connecta_titol_preu))
                    Text(resumTarifa(config.tarifa), fontSize = 14.sp)
                    Nota(stringResource(R.string.connecta_preu_despres))
                }
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

/** El preu de la llum i la potència contractada. */
@Composable
private fun SeccioPreu(vm: ConsumViewModel) {
    val config = vm.config
    val context = LocalContext.current
    val tarifa = config.tarifa
    var kw by rememberSaveable { mutableStateOf(tarifa.limitKw?.let { num(it, 2) } ?: "") }
    // Es desa mig segon després de l'última tecla, no a cada una («5», «5,», «5,7»…), i la vigilància es reinicia un sol cop
    LaunchedEffect(kw) {
        val valor = llegeixNum(kw, milers = false)?.takeIf { it > 0 }
        if (valor == tarifa.limitKw || (valor == null && kw.isNotBlank())) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        vm.desaPotencia(valor)
    }
    Pagina {
        Targeta {
            TitolTargeta(stringResource(R.string.connecta_titol_preu))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(resumTarifa(tarifa), Modifier.weight(1f), fontSize = 14.sp)
                Enllac(stringResource(R.string.connecta_canvia_preu), icona = Icons.Outlined.ChevronRight) { vm.preuDesDeConfig = true; vm.pantalla = Pantalla.PREU }
            }
        }
        Targeta {
            TitolTargeta(stringResource(R.string.connecta_potencia_contractada))
            OutlinedTextField(
                kw, { kw = it },
                Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.connecta_potencia_contractada)) },
                suffix = { Text("kW") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Nota(stringResource(if (tarifa.pais == Pais.ES) R.string.connecta_potencia_es else R.string.connecta_potencia_nl), Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * Els avisos: el de la potència contractada (al moment o cada 15 minuts), el de consum inesperat (a partir de quant i durant
 * quant) i el resum de la setmana. Els canvis es desen de seguida; cada opció té la seva «i».
 */
@Composable
private fun SeccioAvisos(vm: ConsumViewModel) {
    val context = LocalContext.current
    val config = vm.config
    val tarifa = config.tarifa
    var avis by remember { mutableStateOf(config.avisPotencia) }
    var alMoment by remember { mutableStateOf(config.avisAlMoment) }
    val permis = rememberPermisAvisos { Vigilancia.actualitza(context) }
    fun aplica() = Vigilancia.actualitza(context)
    var actiu by remember { mutableStateOf(config.avisInesperat) }
    var llindar by remember { mutableStateOf(config.llindarInesperat) }
    var minuts by remember { mutableStateOf(config.minutsInesperat) }
    LaunchedEffect(actiu, llindar, minuts) { vm.ajustaInesperat(actiu, llindar, minuts) }
    var resum by remember { mutableStateOf(config.resumSetmana) }

    Pagina {
        Targeta {
            TitolTargeta(stringResource(R.string.connecta_potencia_contractada))
            val limit = tarifa.limitKw
            OpcioAmbAjuda(
                stringResource(R.string.connecta_avisa_90), stringResource(R.string.connecta_colors_grafica),
                sota = if (limit != null) stringResource(R.string.connecta_avisa_90_sota, num(limit, 2)) else stringResource(R.string.connecta_sense_potencia),
            ) {
                Switch(avis, { avis = it; config.avisPotencia = it; aplica() })
            }
            if (avis) {
                OpcioAmbAjuda(
                    stringResource(R.string.connecta_app_tancada),
                    stringResource(R.string.connecta_al_moment_text) + " " + stringResource(R.string.connecta_cada_15_text), apilat = true,
                ) {
                    Pestanyes(listOf(true, false), alMoment, { stringResource(if (it) R.string.connecta_al_moment else R.string.connecta_cada_15) }) {
                        alMoment = it; config.avisAlMoment = it; aplica()
                    }
                }
                if (!permis.actius) {
                    Nota(stringResource(R.string.connecta_cal_permis), Modifier.padding(top = 4.dp))
                    Enllac(stringResource(R.string.entrena_activa_avisos), onClick = permis.demana)
                }
            }
        }
        Targeta {
            TitolTargeta(stringResource(R.string.connecta_titol_inesperat))
            val previsio = vm.previsioAvisos
            val sota = when {
                vm.previsioSenseDades -> stringResource(R.string.connecta_inesperat_sense_dades)
                previsio == null -> null
                previsio.isEmpty() -> stringResource(R.string.connecta_inesperat_cap)
                else -> pluralStringResource(R.plurals.connecta_inesperat_previsio_curt, previsio.size, previsio.size)
            }
            OpcioAmbAjuda(
                stringResource(R.string.connecta_avisa_inesperat), vm.fonsW?.let { stringResource(R.string.connecta_inesperat_normal, num(it)) },
                sota = sota,
            ) {
                Switch(actiu, { actiu = it })
            }
            if (actiu) {
                OpcioAmbAjuda(stringResource(R.string.connecta_quan_gasti), null, apilat = true) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (w in listOf(500.0, 1000.0, 2000.0, 3000.0)) {
                            Xip("${num(w / 1000, if (w < 1000) 1 else 0)} kW", triat = llindar == w) { llindar = w }
                        }
                    }
                }
                OpcioAmbAjuda(stringResource(R.string.connecta_inesperat_durant), null, apilat = true) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (m in listOf(10, 30, 60)) {
                            Xip(textMinuts(m), triat = minuts == m) { minuts = m }
                        }
                    }
                }
                // Quins avisos hauria donat la setmana passada, amb el que l'app sap ara
                if (!previsio.isNullOrEmpty()) {
                    val moments = previsio.take(3).map { (ts, w) -> momentAvis(ts, w) } +
                        listOfNotNull((previsio.size - 3).takeIf { it > 0 }?.let { pluralStringResource(R.plurals.connecta_inesperat_mes, it, it) })
                    Nota(moments.joinToString(", "), Modifier.padding(top = 2.dp))
                }
            }
        }
        Targeta {
            TitolTargeta(stringResource(R.string.connecta_resum_titol))
            OpcioAmbAjuda(stringResource(R.string.connecta_resum_dilluns), stringResource(R.string.connecta_resum_text)) {
                Switch(resum, { resum = it; config.resumSetmana = it })
            }
        }
    }
}

/** Les dades: omplir el que falta amb el núvol per hores (maqueta 11), i exportar-les (maqueta 16). */
@Composable
private fun SeccioDades(vm: ConsumViewModel) {
    Pagina {
        if (vm.config.teNuvol) TargetaNuvolHores(vm)
        TargetaExporta(vm)
    }
}

/** L'aspecte (clar, fosc o com el mòbil) i l'idioma. */
@Composable
private fun SeccioAspecte(vm: ConsumViewModel) {
    Pagina {
        Targeta {
            TitolTargeta(stringResource(R.string.connecta_aspecte))
            Pestanyes(listOf(ASPECTE_MOBIL, ASPECTE_CLAR, ASPECTE_FOSC), vm.aspecte, { a ->
                stringResource(
                    when (a) {
                        ASPECTE_CLAR -> R.string.connecta_aspecte_clar
                        ASPECTE_FOSC -> R.string.connecta_aspecte_fosc
                        else -> R.string.connecta_aspecte_mobil
                    }
                )
            }) { vm.triaAspecte(it) }
        }
        // Abans d'Android 13 no hi ha idioma per app: fa com el mòbil
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) TargetaIdioma()
    }
}

/** La versió i les actualitzacions. */
@Composable
private fun SeccioSobre(vm: ConsumViewModel) {
    val context = LocalContext.current
    Pagina {
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
                else -> Enllac(stringResource(R.string.connecta_mira_noves)) { vm.comprovaVersio(forcat = true) }
            }
            vm.missatgeActualitzacio?.let { Nota(it.text(), Modifier.padding(top = 4.dp)) }
        }
        Targeta {
            TitolTargeta(stringResource(R.string.config_sobre))
            Nota(stringResource(R.string.config_sobre_text))
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
        OpcioAmbAjuda(stringResource(R.string.connecta_omple_nuvol), stringResource(R.string.connecta_nuvol_hores_text) + " " + stringResource(R.string.connecta_nuvol_quadra)) {
            Switch(actiu, { actiu = it; vm.ajustaNuvol(it) })
        }
        if (!actiu) return@Targeta
        val r = vm.resumNuvol
        when {
            vm.omplintNuvol -> Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("  " + stringResource(R.string.connecta_nuvol_omplint), fontSize = 13.sp, color = Suau)
            }
            r != null && r.first > 0 -> {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
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
            r != null -> Nota(stringResource(R.string.connecta_nuvol_res), Modifier.padding(top = 4.dp))
        }
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Xip(stringResource(R.string.connecta_idioma_mobil), triat = triada == null) { tria(null) }
            llengues.forEach { l ->
                Xip(l.getDisplayName(l).replaceFirstChar { it.titlecase(l) }, triat = l == marcada) { tria(l) }
            }
        }
    }
}

private fun netejaServidor(s: String) = s.trim().removePrefix("https://").removeSuffix("/")

/** L'ID que fa servir el núvol és la MAC en minúscules: accepta "shellyemg3-dcb4…" o "DC:B4:…". */
private fun netejaId(s: String) = s.trim().lowercase().substringAfterLast('-').replace(":", "")

/**
 * Exportar les dades (maqueta 16): un fitxer CSV per obrir amb un full de càlcul, del consum de cada hora, de cada vegada o de
 * cada minut, d'aquest mes, de l'any o de tot. Es desa on l'usuari triï, amb el selector de fitxers d'Android.
 */
@Composable
private fun TargetaExporta(vm: ConsumViewModel) {
    var periode by rememberSaveable { mutableStateOf(PeriodeExport.MES) }
    var tipus by rememberSaveable { mutableStateOf<TipusExport?>(null) }
    // Les columnes es separen amb punt i coma: el nom de la línia no en pot portar
    val nomLinia = vm.config.nomLinia.ifBlank { stringResource(R.string.pinca2_xip) }.replace(';', ',')
    val capcaleres = mapOf(
        TipusExport.HORES to stringResource(R.string.exporta_cap_hores, nomLinia),
        TipusExport.VEGADES to stringResource(R.string.exporta_cap_vegades),
        TipusExport.MINUTS to stringResource(R.string.exporta_cap_minuts, nomLinia),
    ).mapValues { it.value.split(";") }
    val textCom = mapOf(Com.ENSENYAT to stringResource(R.string.fitxa_ensenyat), Com.RECONEGUT to stringResource(R.string.fitxa_reconegut))
    val desa = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val t = tipus
        if (uri != null && t != null) vm.exporta(t, periode, uri, capcaleres.getValue(t), textCom)
    }
    Targeta {
        TitolTargeta(stringResource(R.string.exporta_titol))
        OpcioAmbAjuda(stringResource(R.string.exporta_quin_periode), stringResource(R.string.exporta_text) + " " + stringResource(R.string.exporta_on), apilat = true) {
            Pestanyes(PeriodeExport.entries, periode, { stringResource(it.nomId) }) { periode = it }
        }
        val avui = java.time.LocalDate.now()
        val sufix = when (periode) {
            PeriodeExport.MES -> "%d-%02d".format(avui.year, avui.monthValue)
            PeriodeExport.ANY -> "${avui.year}"
            PeriodeExport.TOT -> "tot"
        }
        TipusExport.entries.forEach { t ->
            Ratlla()
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !vm.exportant) { tipus = t; desa.launch("consum-${t.fitxer}-$sufix.csv") }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(t.nomId), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(t.textId), fontSize = 11.5.sp, color = Suau)
                }
                Icon(Icons.Outlined.Download, stringResource(R.string.exporta_desa), tint = Primari)
            }
        }
        if (vm.exportant) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
        vm.missatgeExport?.let { Text(it.text(), Modifier.padding(top = 4.dp), fontSize = 13.sp, color = Primari) }
    }
}
