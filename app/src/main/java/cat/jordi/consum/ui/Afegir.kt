@file:OptIn(ExperimentalLayoutApi::class)

package cat.jordi.consum.ui

import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import cat.jordi.consum.R
import cat.jordi.consum.dades.Aparell
import cat.jordi.consum.dades.Categoria
import cat.jordi.consum.dades.Document
import cat.jordi.consum.dades.Eprel
import cat.jordi.consum.dades.ErrorUsuari
import cat.jordi.consum.dades.FitxaEprel
import cat.jordi.consum.dades.Font
import cat.jordi.consum.dades.Funcionament
import cat.jordi.consum.dades.Idioma
import cat.jordi.consum.dades.LecturaPlaca
import cat.jordi.consum.dades.Placa
import cat.jordi.consum.dades.VariantsModel
import cat.jordi.consum.dades.missatge
import cat.jordi.consum.dades.variantsModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer

private enum class Via(@StringRes val nomId: Int) {
    QR(R.string.afegir_via_qr), PLACA(R.string.afegir_via_placa), MARCA(R.string.marca_via), SENSE(R.string.afegir_via_sense)
}

@Composable
fun PantallaAfegir(vm: ConsumViewModel) {
    var via by rememberSaveable { mutableStateOf(if (Compartit.rebut is Rebut.Fitxer) Via.PLACA else Via.QR) }
    // El que s'ha compartit amb Consum va a la pestanya que el llegeix, i aquella el recull
    val rebut = Compartit.rebut
    LaunchedEffect(rebut) {
        when (rebut) {
            is Rebut.Registre -> via = Via.QR
            is Rebut.Fitxer -> via = Via.PLACA
            null -> {}
        }
    }
    val acaba = { a: Aparell ->
        vm.afegeixAparell(a)
        vm.pantalla = Pantalla.PRINCIPAL
        vm.pestanya = Pestanya.APARELLS
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Pestanyes(Via.entries, via, { stringResource(it.nomId) }) { via = it }
        when (via) {
            Via.QR -> AmbQr(acaba)
            Via.PLACA -> AmbPlaca(acaba)
            Via.MARCA -> AmbMarca(acaba) { via = Via.PLACA }
            Via.SENSE -> AmbTipus(vm.resumMes?.preuMitja ?: vm.infoPreu?.ara?.preu ?: vm.config.tarifa.preuFix, acaba)
        }
    }
}

@Composable
private fun ColumnScope.AmbQr(acaba: (Aparell) -> Unit) {
    val context = LocalContext.current
    val abast = rememberCoroutineScope()
    var buscant by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fitxa by remember { mutableStateOf<FitxaEprel?>(null) }
    var nom by rememberSaveable { mutableStateOf("") }
    var escrit by rememberSaveable { mutableStateOf("") }
    val teclat = LocalSoftwareKeyboardController.current

    fun busca(text: String) {
        teclat?.hide()
        val numero = Eprel.numero(text)
        if (numero == null) {
            error = context.getString(R.string.afegir_qr_no_etiqueta)
            return
        }
        buscant = true
        error = null
        fitxa = null
        abast.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Eprel.fitxa(numero) } }
            buscant = false
            r.onSuccess { fitxa = it; nom = context.getString(it.categoria.nomId) }
                .onFailure { error = it.missatge(context) ?: context.getString(R.string.afegir_error_eprel) }
        }
    }

    val escaner = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let(::busca) }

    // Un enllaç de l'EPREL compartit amb Consum
    val rebut = Compartit.rebut
    LaunchedEffect(rebut) {
        if (rebut is Rebut.Registre) {
            Compartit.rebut = null
            escrit = rebut.numero
            busca(rebut.numero)
        }
    }

    Targeta {
        TitolTargeta(stringResource(R.string.afegir_qr_titol))
        Nota(stringResource(R.string.afegir_qr_nota))
        Spacer(Modifier.height(10.dp))
        Button(onClick = {
            escaner.launch(
                ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt(context.getString(R.string.afegir_qr_apunta)).setBeepEnabled(false).setOrientationLocked(false)
            )
        }) {
            Icon(Icons.Outlined.QrCodeScanner, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.afegir_escaneja))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                escrit, { escrit = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.afegir_numero_eprel)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { busca(escrit) }, enabled = escrit.isNotBlank()) { Text(stringResource(R.string.afegir_busca)) }
        }
    }
    if (buscant) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
    error?.let { Text(it, color = DesconegutText, fontSize = 14.sp) }
    fitxa?.let { f ->
        Targeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Check, null, tint = Ok, modifier = Modifier.padding(end = 6.dp))
                Text(stringResource(R.string.afegir_trobat_eprel, f.numero), color = Ok, fontSize = 13.sp)
            }
            Text(
                if (f.classe.isNotBlank()) stringResource(R.string.afegir_tipus_i_classe, stringResource(f.categoria.nomId), f.classe)
                else stringResource(f.categoria.nomId),
                Modifier.padding(top = 6.dp), fontSize = 19.sp, fontWeight = FontWeight.Medium,
            )
            Text("${f.marca} ${f.model}", fontSize = 13.sp, color = Suau)
            Spacer(Modifier.height(8.dp))
            Text(
                if (f.kwhAny != null) stringResource(R.string.afegir_kwh_any, num(f.kwhAny)) else stringResource(R.string.afegir_sense_consum_anual),
                fontSize = 17.sp, fontWeight = FontWeight.Medium,
            )
            Nota(f.explicacio.text())
            f.esperaW?.let { Nota(stringResource(R.string.afegir_en_espera, num(it, 1))) }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(nom, { nom = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.afegir_nom_app)) }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { acaba(f.comAparell(nom.ifBlank { context.getString(f.categoria.nomId) })) }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.afegir_afegeix_lo))
            }
        }
    }
}

/** Una cerca a l'EPREL en marxa: els grups on es busca, per ordre, i el model. */
private data class CercaMarca(val grups: List<String>, val variants: VariantsModel)

/**
 * Per marca i model: es busca el model a la web de l'EPREL (`CercaEprelWeb`), si el tipus d'aparell té etiqueta.
 * Si no hi surt, es pot buscar a internet, fer una foto de la placa o afegir-lo amb el consum a mà.
 */
@Composable
private fun ColumnScope.AmbMarca(acaba: (Aparell) -> Unit, aPlaca: () -> Unit) {
    val context = LocalContext.current
    val abast = rememberCoroutineScope()
    val teclat = LocalSoftwareKeyboardController.current
    var categoria by rememberSaveable { mutableStateOf<Categoria?>(null) }
    var marca by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var ajuda by rememberSaveable { mutableStateOf(false) }
    var cerca by remember { mutableStateOf<CercaMarca?>(null) }
    var feina by remember { mutableStateOf<Job?>(null) }
    var llegint by remember { mutableStateOf(false) }
    var fitxes by remember { mutableStateOf<List<FitxaEprel>>(emptyList()) }
    // Amb quina variant del model s'ha trobat, i si s'ha hagut d'escurçar (maqueta 14)
    var trobat by remember { mutableStateOf<TrobatEprel?>(null) }
    var noTrobat by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var nomTrobat by remember { mutableStateOf("") }
    // El nom del formulari a mà: mentre no s'escriu, «<Marca> <Model>» o el tipus d'aparell
    var nom by rememberSaveable { mutableStateOf<String?>(null) }
    var kwh by rememberSaveable { mutableStateOf("") }
    var tocat by rememberSaveable { mutableStateOf(false) }
    val buscant = cerca != null || llegint
    val text = "${marca.trim()} ${model.trim()}".trim()
    val c = categoria
    val grups = c?.let(Eprel::grups).orEmpty()

    fun busca() {
        val v = variantsModel(model)
        if (c == null || grups.isEmpty() || v.models.isEmpty() || buscant) return
        teclat?.hide()
        fitxes = emptyList()
        trobat = null
        noTrobat = false
        error = null
        cerca = CercaMarca(grups, v)
    }

    fun trobats(r: Result<TrobatEprel?>, tipus: Categoria) {
        cerca = null
        r.onFailure { error = it.missatge(context) ?: context.getString(R.string.cerca_no_carrega) }
        r.onSuccess { t ->
            if (t == null) {
                noTrobat = true
            } else {
                trobat = t
                llegint = true
                feina = abast.launch {
                    try {
                        val llegides = withContext(Dispatchers.IO) { t.numeros.map { n -> runCatching { Eprel.fitxa(n) } } }
                        // Els de la marca escrita, primer
                        fitxes = llegides.mapNotNull { it.getOrNull() }
                            .sortedByDescending { marca.isNotBlank() && it.marca.contains(marca.trim(), ignoreCase = true) }
                        nomTrobat = context.getString(tipus.nomId)
                        if (fitxes.isEmpty()) {
                            error = llegides.firstNotNullOfOrNull { it.exceptionOrNull() }?.missatge(context) ?: context.getString(R.string.afegir_error_eprel)
                        }
                    } finally {
                        llegint = false
                    }
                }
            }
        }
    }

    if (ajuda) {
        BackHandler { ajuda = false }
        AjudaModel(text, enTancar = { ajuda = false }, enFoto = { ajuda = false; aPlaca() })
        return
    }
    Targeta {
        TitolTargeta(stringResource(R.string.afegir_quin_aparell))
        TriaCategoria(categoria) { nova ->
            if (nova != categoria) {
                categoria = nova
                cerca = null
                feina?.cancel()
                fitxes = emptyList()
                noTrobat = false
                error = null
                if (!tocat) kwh = num(nova.kwhAnyTipic)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            marca, { marca = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.marca_marca)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            model, {
                model = it
                // El que s'havia trobat era d'un altre model
                fitxes = emptyList()
                trobat = null
                noTrobat = false
                error = null
            },
            Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.marca_model)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = if (grups.isEmpty()) ImeAction.Done else ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { busca() }),
        )
        TextButton(onClick = { ajuda = true }) {
            Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.marca_on_trobo))
        }
        if (grups.isNotEmpty()) {
            Button(onClick = { busca() }, Modifier.fillMaxWidth(), enabled = variantsModel(model).models.isNotEmpty() && !buscant) {
                Icon(Icons.Outlined.Search, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.marca_busca_eprel))
            }
        }
    }
    if (buscant && c != null) {
        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            cerca?.let { cr -> key(cr) { CercaEprelWeb(cr.grups, cr.variants.models, cr.variants.exactes) { trobats(it, c) } } }
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Text("  " + stringResource(R.string.cerca_buscant), fontSize = 14.sp, color = Suau)
        }
    }
    val t = trobat
    if (c != null && t != null && fitxes.isNotEmpty()) {
        Targeta {
            // Si no s'ha hagut d'escurçar: l'ha trobat (potser sense el «/01»); si no, són els que s'hi assemblen
            TitolTargeta(stringResource(if (t.exacte) R.string.marca_trobat else R.string.marca_potser))
            if (t.exacte && !mateixModel(t.model, model)) Text(stringResource(R.string.marca_variant, t.model), fontSize = 13.sp, color = DesconegutText)
            if (!t.exacte) Nota(stringResource(R.string.marca_potser_nota, t.model))
            OutlinedTextField(nomTrobat, { nomTrobat = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text(stringResource(R.string.afegir_nom_app)) }, singleLine = true)
            fitxes.forEach { f ->
                FilaFitxa(f, c) { acaba(f.copy(categoria = c).comAparell(nomTrobat.ifBlank { context.getString(c.nomId) })) }
            }
            Nota(stringResource(R.string.marca_nota_eprel), Modifier.padding(top = 8.dp))
        }
    }
    // Si només n'hi ha de semblants, també s'ha de poder afegir pel tipus («Cap d'aquests?»)
    val aproximat = t != null && !t.exacte && fitxes.isNotEmpty()
    val noSurt = grups.isEmpty() || noTrobat || error != null || aproximat
    // Qui no en sap el model també l'ha de poder afegir, amb el valor típic
    val senseModel = !noSurt && model.isBlank() && fitxes.isEmpty() && !buscant
    if (c != null && (noSurt || senseModel)) {
        Targeta {
            TitolTargeta(stringResource(if (aproximat) R.string.marca_cap_daquests else if (noSurt) R.string.marca_no_surt else R.string.marca_sense_model))
            if (noSurt) {
                val e = error
                when {
                    e != null -> Text(e, color = DesconegutText, fontSize = 14.sp)
                    noTrobat -> Nota(stringResource(R.string.cerca_no_trobat))
                    aproximat -> Nota(stringResource(R.string.marca_cap_daquests_nota))
                    else -> Nota(stringResource(R.string.marca_tipus_sense_etiqueta))
                }
                OpcionsNoSurt(text, aPlaca)
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Separador)
                Nota(stringResource(R.string.marca_a_ma))
            } else {
                Nota(stringResource(R.string.marca_sense_model_nota))
            }
            Spacer(Modifier.height(6.dp))
            val nomTipus = stringResource(c.nomId)
            val nomDefecte = text.ifEmpty { nomTipus }
            OutlinedTextField(nom ?: nomDefecte, { nom = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.afegir_nom_app)) }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                kwh, { kwh = it; tocat = true }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.afegir_consum_kwh_any)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = { Text(stringResource(if (tocat) R.string.afegir_escrit_per_tu else R.string.afegir_valor_tipic)) },
            )
            Spacer(Modifier.height(10.dp))
            val valor = llegeixNum(kwh)
            Button(
                onClick = {
                    acaba(
                        Aparell(
                            nom = (nom ?: nomDefecte).ifBlank { context.getString(c.nomId) }, categoria = c, font = if (tocat) Font.MANUAL else Font.TIPIC,
                            kwhAny = valor ?: c.kwhAnyTipic, marca = marca.trim(), model = model.trim(),
                        )
                    )
                },
                Modifier.fillMaxWidth(), enabled = valor != null,
            ) { Text(stringResource(R.string.afegir_afegeix_lo)) }
        }
    }
}

/** Si dues maneres d'escriure un model són la mateixa (sense espais, ni majúscules). */
private fun mateixModel(a: String, b: String) = a.replace(" ", "").equals(b.trim().replace(" ", ""), ignoreCase = true)

/** Un aparell trobat a l'EPREL: la marca i el model, la classe, el consum de l'etiqueta i «Afegeix-lo». */
@Composable
private fun FilaFitxa(f: FitxaEprel, c: Categoria, afegeix: () -> Unit) {
    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Separador)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${f.marca} · ${f.model}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(c.nomId), fontSize = 12.sp, color = Suau)
        }
        if (f.classe.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Classe(f.classe)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.marca_consum_etiqueta), Modifier.weight(1f), fontSize = 14.sp)
        Text(
            if (f.kwhAny != null) stringResource(R.string.afegir_kwh_any, num(f.kwhAny)) else stringResource(R.string.afegir_sense_consum_anual),
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
    }
    Nota(f.explicacio.text())
    // El tipus és el que s'ha triat: una nevera i un congelador, per exemple, són al mateix grup de l'EPREL
    Button(onClick = afegeix, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.afegir_afegeix_lo)) }
}

/** La classe de l'etiqueta amb el seu color. L'EPREL escriu les classes A+ com «AP», «APP»… */
@Composable
private fun Classe(classe: String) {
    val lletra = if (classe.length > 1 && classe[0] == 'A' && classe.drop(1).all { it == 'P' }) "A" + "+".repeat(classe.length - 1) else classe
    val primera = classe.firstOrNull() ?: ' '
    val fons = when (primera) {
        'A' -> Color(0xFF00A651)
        'B' -> Color(0xFF50B848)
        'C' -> Color(0xFFBFD730)
        'D' -> Color(0xFFFFF200)
        'E' -> Color(0xFFFDB913)
        'F' -> Color(0xFFF37021)
        'G' -> Color(0xFFED1C24)
        else -> Separador
    }
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(fons).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(lletra, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (primera in 'C'..'E' || fons == Separador) Color(0xFF1C2421) else Color.White)
    }
}

/** Què fer si no surt a l'EPREL: buscar-lo a internet, fer una foto de la placa o compartir-hi la fitxa. */
@Composable
private fun OpcionsNoSurt(text: String, enFoto: () -> Unit) {
    val context = LocalContext.current
    if (text.isNotBlank()) {
        OutlinedButton(onClick = { buscaAInternet(context, text) }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Icon(Icons.Outlined.Language, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.marca_busca_internet, text))
        }
    }
    OutlinedButton(onClick = enFoto, Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.marca_foto_placa))
    }
    Row(Modifier.padding(top = 8.dp)) {
        Icon(Icons.Outlined.Share, null, Modifier.padding(top = 2.dp).size(14.dp), tint = Suau)
        Spacer(Modifier.width(6.dp))
        Nota(stringResource(R.string.marca_comparteix))
    }
}

/** El cercador del mòbil; si no n'hi ha cap, el navegador. */
private fun buscaAInternet(context: Context, text: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text))
    } catch (e: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(text)))) }
    }
}

/** On sol ser la placa de característiques de cada tipus d'aparell. */
private val llocsPlaca = listOf(
    Categoria.NEVERA to R.string.ajuda_lloc_nevera,
    Categoria.CONGELADOR to R.string.ajuda_lloc_congelador,
    Categoria.RENTADORA to R.string.ajuda_lloc_rentadora,
    Categoria.ASSECADORA to R.string.ajuda_lloc_assecadora,
    Categoria.RENTAPLATS to R.string.ajuda_lloc_rentaplats,
    Categoria.FORN to R.string.ajuda_lloc_forn,
    Categoria.MICROONES to R.string.ajuda_lloc_microones,
    Categoria.CAMPANA to R.string.ajuda_lloc_campana,
    Categoria.TELEVISOR to R.string.ajuda_lloc_televisor,
    Categoria.AIRE to R.string.ajuda_lloc_aire,
    Categoria.BOMBA_CALOR to R.string.ajuda_lloc_bomba_calor,
    Categoria.TERMO to R.string.ajuda_lloc_termo,
)

/** L'ajuda «On trobo el model?», en lloc del formulari. [text] és la marca i el model escrits, per buscar-los a internet. */
@Composable
private fun ColumnScope.AjudaModel(text: String, enTancar: () -> Unit, enFoto: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.ajuda_titol), Modifier.weight(1f).padding(start = 4.dp), fontSize = 22.sp)
        IconButton(onClick = enTancar) { Icon(Icons.Outlined.Close, stringResource(R.string.ajuda_tanca)) }
    }
    Targeta {
        TitolTargeta(stringResource(R.string.marca_on_trobo))
        Nota(stringResource(R.string.ajuda_placa))
        llocsPlaca.forEachIndexed { i, (categoria, lloc) ->
            if (i > 0) HorizontalDivider(color = Separador)
            Row(Modifier.padding(vertical = 8.dp)) {
                Icon(icona(categoria), null, Modifier.padding(top = 1.dp).size(20.dp), tint = Primari)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(categoria.nomId), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Nota(stringResource(lloc))
                }
            }
        }
    }
    Targeta {
        TitolTargeta(stringResource(R.string.marca_no_surt))
        Nota(stringResource(R.string.marca_sense_etiqueta))
        OpcionsNoSurt(text, enFoto)
    }
}

/** Altres paraules amb què es pot buscar un tipus d'aparell (sinònimes o més concretes), separades per comes. */
private val paraules = mapOf(
    Categoria.NEVERA to R.string.cerca_tipus_nevera, Categoria.CONGELADOR to R.string.cerca_tipus_congelador,
    Categoria.PLACA to R.string.cerca_tipus_placa, Categoria.FORN to R.string.cerca_tipus_forn,
    Categoria.RENTAPLATS to R.string.cerca_tipus_rentaplats, Categoria.RENTADORA to R.string.cerca_tipus_rentadora,
    Categoria.ASSECADORA to R.string.cerca_tipus_assecadora, Categoria.TELEVISOR to R.string.cerca_tipus_televisor,
    Categoria.ORDINADOR to R.string.cerca_tipus_ordinador, Categoria.AIRE to R.string.cerca_tipus_aire,
    Categoria.BOMBA_CALOR to R.string.cerca_tipus_bomba_calor, Categoria.TERMO to R.string.cerca_tipus_termo,
    Categoria.COTXE to R.string.cerca_tipus_cotxe, Categoria.ESTUFA to R.string.cerca_tipus_estufa,
    Categoria.LLUMS to R.string.cerca_tipus_llums, Categoria.CONSOLA to R.string.cerca_tipus_consola,
    Categoria.CAFETERA to R.string.cerca_tipus_cafetera, Categoria.DEPURADORA to R.string.cerca_tipus_depuradora,
)

/** Per comparar sense majúscules ni accents: «Vitroceràmica» i «vitroceramica» són iguals. */
private fun pla(text: String): String =
    Normalizer.normalize(text.lowercase(Idioma.locale), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").trim()

/**
 * Els tipus d'aparell per triar, amb un cercador: la llista és llarga. Un cop triat, es plega i només en queda el
 * triat, amb «Canvia» per tornar-la a obrir.
 */
@Composable
private fun TriaCategoria(triada: Categoria?, enTriar: (Categoria) -> Unit) {
    var obert by rememberSaveable { mutableStateOf(triada == null) }
    var cerca by rememberSaveable { mutableStateOf("") }
    if (triada != null && !obert) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = true, onClick = { obert = true }, label = { Text(stringResource(triada.nomId)) })
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { obert = true }) { Text(stringResource(R.string.cerca_tipus_canvia)) }
        }
        return
    }
    OutlinedTextField(
        cerca, { cerca = it }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.cerca_tipus)) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
        trailingIcon = {
            if (cerca.isNotEmpty()) IconButton(onClick = { cerca = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.cerca_tipus_esborra)) }
        },
    )
    Spacer(Modifier.height(6.dp))
    val context = LocalContext.current
    val buscat = pla(cerca)
    val trobades = if (buscat.isEmpty()) Categoria.entries else Categoria.entries.filter { c ->
        val noms = listOf(context.getString(c.nomId)) + (paraules[c]?.let { context.getString(it).split(',') } ?: emptyList())
        noms.any { pla(it).contains(buscat) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        trobades.forEach { c ->
            FilterChip(selected = c == triada, onClick = { enTriar(c); obert = false; cerca = "" }, label = { Text(stringResource(c.nomId)) })
        }
        // Si no en surt cap, sempre hi ha «Un altre»
        if (trobades.isEmpty()) {
            FilterChip(selected = false, onClick = { enTriar(Categoria.ALTRES); obert = false; cerca = "" }, label = { Text(stringResource(Categoria.ALTRES.nomId)) })
        }
    }
    if (trobades.isEmpty()) Nota(stringResource(R.string.cerca_tipus_cap))
}

/**
 * La placa de característiques: es fa (o es tria) una foto, l'app en llegeix el text dins del mòbil amb
 * Tesseract i en treu la potència. L'usuari la pot corregir i diu quantes hores al dia funciona l'aparell.
 */
@Composable
private fun ColumnScope.AmbPlaca(acaba: (Aparell) -> Unit) {
    val context = LocalContext.current
    val abast = rememberCoroutineScope()
    var foto by remember { mutableStateOf<Bitmap?>(null) }
    var llegint by remember { mutableStateOf(false) }
    var lectura by remember { mutableStateOf<LecturaPlaca?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var w by rememberSaveable { mutableStateOf("") }
    var categoria by rememberSaveable { mutableStateOf<Categoria?>(null) }
    var nom by rememberSaveable { mutableStateOf("") }
    var hores by rememberSaveable { mutableStateOf("") }
    // Mentre l'usuari no les toqui, les hores surten del consum típic del tipus d'aparell i de la potència
    var horesTocades by rememberSaveable { mutableStateOf(false) }
    var veureText by remember { mutableStateOf(false) }
    // El model llegit a la placa, buscat sol a l'EPREL (maqueta 14)
    var cercaPlaca by remember { mutableStateOf<CercaMarca?>(null) }
    var trobatPlaca by remember { mutableStateOf<TrobatEprel?>(null) }
    var fitxesPlaca by remember { mutableStateOf<List<FitxaEprel>>(emptyList()) }
    var placaNoTrobat by remember { mutableStateOf(false) }
    val modelsPlaca = remember(lectura) { lectura?.let { Placa.models(it.text) }.orEmpty() }

    // Amb el tipus triat i un model llegit, es busca a l'EPREL: primer cada model tal com és (o sense el «/01»), i després
    // el primer, escurçat
    LaunchedEffect(categoria, lectura) {
        cercaPlaca = null
        trobatPlaca = null
        fitxesPlaca = emptyList()
        placaNoTrobat = false
        val c = categoria ?: return@LaunchedEffect
        val grupsC = Eprel.grups(c)
        if (grupsC.isEmpty() || modelsPlaca.isEmpty()) return@LaunchedEffect
        val vs = modelsPlaca.map(::variantsModel)
        val exactes = vs.flatMap { it.models.take(it.exactes) }.distinct()
        cercaPlaca = CercaMarca(grupsC, VariantsModel(exactes + vs.first().models.drop(vs.first().exactes), exactes.size))
    }

    fun calculaHores() {
        val c = categoria ?: return
        if (horesTocades) return
        llegeixNum(w)?.takeIf { it > 0 }?.let { p -> hores = num(c.kwhAnyTipic * 1000 / 365 / p, 1) }
    }

    fun llegeix(uri: Uri, compartit: Boolean = false) {
        llegint = true
        error = null
        lectura = null
        abast.launch {
            val r = withContext(Dispatchers.Default) {
                runCatching {
                    // Un fitxer compartit amb Consum pot ser un PDF: se'n llegeix la primera pàgina
                    val b = if (compartit) {
                        Document.pagines(context, uri).firstOrNull() ?: throw ErrorUsuari(R.string.error_pdf)
                    } else {
                        Placa.carrega(context.contentResolver, uri)
                    }
                    b to Placa.potencia(Placa.llegeix(context, b))
                }
            }
            llegint = false
            r.onSuccess { (b, l) ->
                foto = b
                lectura = l
                w = l.w?.let { num(it) } ?: ""
                calculaHores()
            }.onFailure { error = it.missatge(context) ?: context.getString(R.string.afegir_error_foto) }
        }
    }

    val uriFoto = remember {
        val f = File(context.cacheDir, "fotos/placa.jpg").apply { parentFile?.mkdirs() }
        FileProvider.getUriForFile(context, "${context.packageName}.fitxers", f)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { feta -> if (feta) llegeix(uriFoto) }
    // L'app declara el permís de càmera (per l'escàner de QR): si no està concedit, Android no deixa obrir la càmera
    val permis = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { si ->
        if (si) camera.obre(uriFoto) { error = context.getString(R.string.error_sense_app) } else error = context.getString(R.string.afegir_sense_permis_camera)
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { llegeix(it) } }

    // Una foto o un PDF compartits amb Consum, com si s'haguessin triat
    val rebut = Compartit.rebut
    LaunchedEffect(rebut) {
        if (rebut is Rebut.Fitxer) {
            Compartit.rebut = null
            llegeix(rebut.uri, compartit = true)
        }
    }

    Targeta {
        TitolTargeta(stringResource(R.string.afegir_placa_titol))
        Nota(stringResource(R.string.afegir_placa_nota))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera.obre(uriFoto) { error = context.getString(R.string.error_sense_app) }
                else permis.launch(Manifest.permission.CAMERA)
            }, enabled = !llegint) {
                Icon(Icons.Outlined.PhotoCamera, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.afegir_fes_la_foto))
            }
            OutlinedButton(onClick = { galeria.obre(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) { error = context.getString(R.string.error_sense_app) } }, enabled = !llegint) {
                Text(stringResource(R.string.afegir_tria_foto))
            }
        }
    }
    if (llegint) {
        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Text("  " + stringResource(R.string.afegir_llegint_placa), fontSize = 14.sp, color = Suau)
        }
    }
    error?.let { Text(it, color = DesconegutText, fontSize = 14.sp) }
    lectura?.let { l ->
        Targeta {
            foto?.let { Image(it.asImageBitmap(), stringResource(R.string.afegir_foto_placa), Modifier.fillMaxWidth().height(150.dp)) }
            Spacer(Modifier.height(8.dp))
            if (l.w != null) {
                Text(
                    stringResource(if (l.delsAmperes) R.string.afegir_he_llegit_amperes else R.string.afegir_he_llegit, num(l.w)),
                    fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Ok,
                )
                l.fragment?.let { Nota(stringResource(R.string.afegir_a_la_placa, it)) }
            } else {
                Text(stringResource(R.string.afegir_sense_potencia), fontSize = 14.sp, color = DesconegutText)
            }
            TextButton(onClick = { veureText = !veureText }) {
                Text(stringResource(if (veureText) R.string.afegir_amaga_llegit else R.string.afegir_mira_llegit))
            }
            if (veureText) Nota(if (l.text.isBlank()) stringResource(R.string.afegir_res) else l.text)
        }
        Targeta {
            TitolTargeta(stringResource(R.string.afegir_quin_aparell))
            TriaCategoria(categoria) { c ->
                categoria = c
                if (nom.isBlank() || Categoria.entries.any { context.getString(it.nomId) == nom }) nom = context.getString(c.nomId)
                calculaHores()
            }
        }
        categoria?.let { c ->
            if (modelsPlaca.isNotEmpty() && Eprel.grups(c).isNotEmpty()) Targeta {
                TitolTargeta(stringResource(R.string.placa_model_titol))
                Text(stringResource(R.string.placa_model_llegit, trobatPlaca?.model ?: modelsPlaca.first()), fontSize = 14.sp)
                cercaPlaca?.let { cr ->
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        key(cr) {
                            CercaEprelWeb(cr.grups, cr.variants.models, cr.variants.exactes) { r ->
                                cercaPlaca = null
                                r.onFailure { placaNoTrobat = true }
                                r.onSuccess { t ->
                                    if (t == null) placaNoTrobat = true
                                    else abast.launch {
                                        val llegides = withContext(Dispatchers.IO) { t.numeros.map { n -> runCatching { Eprel.fitxa(n) } } }
                                        // Si mentrestant s'ha canviat el tipus (o la foto), ja no és d'aquesta cerca
                                        if (categoria != c || cercaPlaca != null) return@launch
                                        fitxesPlaca = llegides.mapNotNull { it.getOrNull() }
                                        trobatPlaca = t
                                        if (fitxesPlaca.isEmpty()) placaNoTrobat = true
                                    }
                                }
                            }
                        }
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("  " + stringResource(R.string.cerca_buscant), fontSize = 14.sp, color = Suau)
                    }
                }
                val tp = trobatPlaca
                if (tp != null && fitxesPlaca.isNotEmpty()) {
                    Text(stringResource(if (tp.exacte) R.string.marca_trobat else R.string.marca_potser), Modifier.padding(top = 6.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    fitxesPlaca.forEach { f ->
                        // Amb la potència de la placa, si s'ha llegit
                        FilaFitxa(f, c) { acaba(f.copy(categoria = c).comAparell(nom.ifBlank { context.getString(c.nomId) }).copy(potenciaW = llegeixNum(w))) }
                    }
                }
                if (placaNoTrobat) Nota(stringResource(R.string.placa_model_no_surt), Modifier.padding(top = 6.dp))
            }
            Targeta {
                OutlinedTextField(nom, { nom = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.afegir_nom)) }, singleLine = true)
                OutlinedTextField(
                    w, { w = it; calculaHores() }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.afegir_potencia)) }, singleLine = true, suffix = { Text("W") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    hores, { hores = it; horesTocades = true }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.afegir_hores_al_dia)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text(stringResource(R.string.afegir_hores_ajuda)) },
                )
                val pw = llegeixNum(w)
                val ph = llegeixNum(hores)
                val kwh = if (pw != null && ph != null) pw * ph * 365 / 1000 else null
                kwh?.let { Text(stringResource(R.string.afegir_kwh_any_aprox, num(it)), Modifier.padding(top = 4.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { acaba(Aparell(nom = nom.ifBlank { context.getString(c.nomId) }, categoria = c, font = Font.PLACA, kwhAny = kwh ?: c.kwhAnyTipic, potenciaW = pw)) },
                    Modifier.fillMaxWidth(), enabled = pw != null && kwh != null,
                ) { Text(stringResource(R.string.afegir_afegeix_lo)) }
            }
        }
    }
}

/**
 * Un aparell sense etiqueta, placa ni model (maqueta 7): el tipus, com funciona i la potència, la típica del tipus
 * si no se'n sap cap altra. Si està sempre encès, gasta aquesta potència tot l'any; si no, el consum típic del
 * tipus, a escala amb la potència. [preu]: €/kWh, per dir quant costa.
 */
@Composable
private fun ColumnScope.AmbTipus(preu: Double, acaba: (Aparell) -> Unit) {
    val context = LocalContext.current
    var categoria by rememberSaveable { mutableStateOf<Categoria?>(null) }
    var nom by rememberSaveable { mutableStateOf("") }
    var funciona by rememberSaveable { mutableStateOf(Funcionament.JO) }
    var w by rememberSaveable { mutableStateOf("") }
    Targeta {
        TitolTargeta(stringResource(R.string.afegir_quin_aparell))
        TriaCategoria(categoria) { c ->
            categoria = c
            if (nom.isBlank() || Categoria.entries.any { context.getString(it.nomId) == nom }) nom = context.getString(c.nomId)
            funciona = c.funciona
            w = num(c.wTipic)
        }
    }
    val c = categoria ?: return
    Targeta {
        OutlinedTextField(nom, { nom = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.afegir_nom)) }, singleLine = true)
        Text(stringResource(R.string.afegir_sense_com), Modifier.padding(top = 10.dp, bottom = 2.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Funcionament.entries.forEach { f ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (funciona == f) PrimariFluix else Color.Transparent)
                    .selectable(funciona == f, role = Role.RadioButton) { funciona = f },
                verticalAlignment = Alignment.Top,
            ) {
                RadioButton(funciona == f, null, Modifier.padding(12.dp))
                Column(Modifier.padding(top = 12.dp, end = 8.dp, bottom = 8.dp)) {
                    Text(stringResource(f.nomId), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Nota(stringResource(f.textId))
                }
            }
        }
        OutlinedTextField(
            w, { w = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text(stringResource(R.string.afegir_potencia)) },
            singleLine = true, suffix = { Text("W") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Nota(stringResource(R.string.afegir_sense_potencia_nota), Modifier.padding(top = 2.dp))
        val pw = llegeixNum(w)?.takeIf { it > 0 }
        val kwh = pw?.let { if (funciona == Funcionament.SEMPRE) it * 8760 / 1000 else c.kwhAnyTipic * it / c.wTipic }
        kwh?.let {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.afegir_sense_any), Modifier.weight(1f), fontSize = 14.sp)
                Text(stringResource(R.string.afegir_sense_any_valor, num(it), euros(it / 12 * preu)), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        Button(
            onClick = {
                acaba(
                    Aparell(
                        nom = nom.ifBlank { context.getString(c.nomId) }, categoria = c,
                        font = if (pw == c.wTipic) Font.TIPIC else Font.MANUAL, kwhAny = kwh ?: c.kwhAnyTipic, potenciaW = pw, funciona = funciona,
                    )
                )
            },
            Modifier.fillMaxWidth().padding(top = 10.dp), enabled = pw != null,
        ) { Text(stringResource(R.string.afegir_afegeix_lo)) }
    }
}
