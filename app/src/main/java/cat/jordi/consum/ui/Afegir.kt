@file:OptIn(ExperimentalLayoutApi::class)

package cat.jordi.consum.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.asImageBitmap
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import cat.jordi.consum.R
import cat.jordi.consum.dades.LecturaPlaca
import cat.jordi.consum.dades.Placa
import cat.jordi.consum.dades.missatge
import java.io.File
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.dades.Aparell
import cat.jordi.consum.dades.Categoria
import cat.jordi.consum.dades.Eprel
import cat.jordi.consum.dades.FitxaEprel
import cat.jordi.consum.dades.Font
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Via(@StringRes val nomId: Int) { QR(R.string.afegir_via_qr), PLACA(R.string.afegir_via_placa), MODEL(R.string.afegir_via_model) }

@Composable
fun PantallaAfegir(vm: ConsumViewModel) {
    var via by remember { mutableStateOf(Via.QR) }
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
            Via.MODEL -> AmbModel(acaba)
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
    var nom by remember { mutableStateOf("") }
    var escrit by remember { mutableStateOf("") }
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
        abast.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Eprel.fitxa(numero) } }
            buscant = false
            r.onSuccess { fitxa = it; nom = context.getString(it.categoria.nomId) }
                .onFailure { error = it.missatge(context) ?: context.getString(R.string.afegir_error_eprel) }
        }
    }

    val escaner = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let(::busca) }

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
    error?.let { Text(it, color = Desconegut, fontSize = 14.sp) }
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

@Composable
private fun AmbModel(acaba: (Aparell) -> Unit) {
    val context = LocalContext.current
    var categoria by remember { mutableStateOf<Categoria?>(null) }
    var nom by remember { mutableStateOf("") }
    var kwh by remember { mutableStateOf("") }
    var tocat by remember { mutableStateOf(false) }

    Targeta {
        TitolTargeta(stringResource(R.string.afegir_quin_aparell))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Categoria.entries.forEach { c ->
                FilterChip(
                    selected = c == categoria,
                    onClick = {
                        categoria = c
                        if (nom.isBlank() || Categoria.entries.any { context.getString(it.nomId) == nom }) nom = context.getString(c.nomId)
                        if (!tocat) kwh = num(c.kwhAnyTipic)
                    },
                    label = { Text(stringResource(c.nomId)) },
                )
            }
        }
    }
    categoria?.let { c ->
        Targeta {
            OutlinedTextField(nom, { nom = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.afegir_nom_i_model)) }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                kwh, { kwh = it; tocat = true }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.afegir_consum_kwh_any)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = { Text(stringResource(if (tocat) R.string.afegir_escrit_per_tu else R.string.afegir_valor_tipic)) },
            )
            Spacer(Modifier.height(10.dp))
            val valor = kwh.replace(".", "").replace(',', '.').toDoubleOrNull()
            Button(
                onClick = {
                    acaba(Aparell(nom = nom.ifBlank { context.getString(c.nomId) }, categoria = c, font = if (tocat) Font.MANUAL else Font.TIPIC, kwhAny = valor ?: c.kwhAnyTipic, model = ""))
                },
                Modifier.fillMaxWidth(), enabled = valor != null,
            ) { Text(stringResource(R.string.afegir_afegeix_lo)) }
        }
    }
}

/** Les categories per triar, com a la pestanya Model. */
@Composable
private fun TriaCategoria(triada: Categoria?, enTriar: (Categoria) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Categoria.entries.forEach { c -> FilterChip(selected = c == triada, onClick = { enTriar(c) }, label = { Text(stringResource(c.nomId)) }) }
    }
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
    var w by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf<Categoria?>(null) }
    var nom by remember { mutableStateOf("") }
    var hores by remember { mutableStateOf("") }
    var veureText by remember { mutableStateOf(false) }

    fun llegeix(uri: Uri) {
        llegint = true
        error = null
        lectura = null
        abast.launch {
            val r = withContext(Dispatchers.Default) {
                runCatching {
                    val b = Placa.carrega(context.contentResolver, uri)
                    b to Placa.potencia(Placa.llegeix(context, b))
                }
            }
            llegint = false
            r.onSuccess { (b, l) ->
                foto = b
                lectura = l
                w = l.w?.let { num(it) } ?: ""
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
        if (si) camera.launch(uriFoto) else error = context.getString(R.string.afegir_sense_permis_camera)
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::llegeix) }

    Targeta {
        TitolTargeta(stringResource(R.string.afegir_placa_titol))
        Nota(stringResource(R.string.afegir_placa_nota))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera.launch(uriFoto)
                else permis.launch(Manifest.permission.CAMERA)
            }, enabled = !llegint) {
                Icon(Icons.Outlined.PhotoCamera, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.afegir_fes_la_foto))
            }
            OutlinedButton(onClick = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !llegint) {
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
    error?.let { Text(it, color = Desconegut, fontSize = 14.sp) }
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
                Text(stringResource(R.string.afegir_sense_potencia), fontSize = 14.sp, color = Desconegut)
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
                // Les hores que donen el consum típic d'aquest tipus d'aparell amb aquesta potència
                w.replace(".", "").replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { p ->
                    hores = num(c.kwhAnyTipic * 1000 / 365 / p, 1)
                }
            }
        }
        categoria?.let { c ->
            Targeta {
                OutlinedTextField(nom, { nom = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.afegir_nom)) }, singleLine = true)
                OutlinedTextField(
                    w, { w = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.afegir_potencia)) }, singleLine = true, suffix = { Text("W") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    hores, { hores = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.afegir_hores_al_dia)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text(stringResource(R.string.afegir_hores_ajuda)) },
                )
                val pw = w.replace(".", "").replace(',', '.').toDoubleOrNull()
                val ph = hores.replace(',', '.').toDoubleOrNull()
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
