@file:OptIn(ExperimentalLayoutApi::class)

package cat.jordi.consum.ui

import androidx.activity.compose.rememberLauncherForActivityResult
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

private enum class Via(val nom: String) { QR("QR etiqueta"), PLACA("Placa"), MODEL("Model") }

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
        Pestanyes(Via.entries, via, { it.nom }) { via = it }
        when (via) {
            Via.QR -> AmbQr(acaba)
            Via.PLACA -> Targeta {
                TitolTargeta("Foto de la placa")
                Nota("Aviat: fotografiaràs la placa de característiques i l'app en llegirà la potència. Mentrestant, escriu-la a la pestanya Model.")
            }
            Via.MODEL -> AmbModel(acaba)
        }
    }
}

@Composable
private fun ColumnScope.AmbQr(acaba: (Aparell) -> Unit) {
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
            error = "Aquest QR no és d'una etiqueta energètica europea."
            return
        }
        buscant = true
        error = null
        abast.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Eprel.fitxa(numero) } }
            buscant = false
            r.onSuccess { fitxa = it; nom = it.categoria.nom }
                .onFailure { error = it.message ?: "No he pogut llegir l'EPREL" }
        }
    }

    val escaner = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let(::busca) }

    Targeta {
        TitolTargeta("Escaneja el QR de l'etiqueta energètica")
        Nota("Les etiquetes noves (des del 2021) porten un QR a dalt a la dreta. L'app en treu el consum de la base de dades europea (EPREL).")
        Spacer(Modifier.height(10.dp))
        Button(onClick = {
            escaner.launch(
                ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("Apunta al QR de l'etiqueta energètica").setBeepEnabled(false).setOrientationLocked(false)
            )
        }) {
            Icon(Icons.Outlined.QrCodeScanner, null)
            Spacer(Modifier.width(8.dp))
            Text("Escaneja")
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                escrit, { escrit = it }, Modifier.weight(1f), label = { Text("O el número EPREL") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { busca(escrit) }, enabled = escrit.isNotBlank()) { Text("Busca") }
        }
    }
    if (buscant) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
    error?.let { Text(it, color = Desconegut, fontSize = 14.sp) }
    fitxa?.let { f ->
        Targeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Check, null, tint = Ok, modifier = Modifier.padding(end = 6.dp))
                Text("Trobat a l'EPREL (${f.numero})", color = Ok, fontSize = 13.sp)
            }
            Text(
                "${f.categoria.nom}${if (f.classe.isNotBlank()) " · classe ${f.classe}" else ""}",
                Modifier.padding(top = 6.dp), fontSize = 19.sp, fontWeight = FontWeight.Medium,
            )
            Text("${f.marca} ${f.model}", fontSize = 13.sp, color = Suau)
            Spacer(Modifier.height(8.dp))
            Text(f.kwhAny?.let { "${num(it)} kWh l'any" } ?: "Sense consum anual", fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Nota(f.explicacio)
            f.esperaW?.let { Nota("En espera: ${num(it, 1)} W") }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(nom, { nom = it }, Modifier.fillMaxWidth(), label = { Text("Nom a l'app") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { acaba(f.comAparell(nom.ifBlank { f.categoria.nom })) }, Modifier.fillMaxWidth()) {
                Text("Afegeix-lo")
            }
        }
    }
}

@Composable
private fun AmbModel(acaba: (Aparell) -> Unit) {
    var categoria by remember { mutableStateOf<Categoria?>(null) }
    var nom by remember { mutableStateOf("") }
    var kwh by remember { mutableStateOf("") }
    var tocat by remember { mutableStateOf(false) }

    Targeta {
        TitolTargeta("Quin aparell és?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Categoria.entries.forEach { c ->
                FilterChip(
                    selected = c == categoria,
                    onClick = {
                        categoria = c
                        if (nom.isBlank() || Categoria.entries.any { it.nom == nom }) nom = c.nom
                        if (!tocat) kwh = num(c.kwhAnyTipic)
                    },
                    label = { Text(c.nom) },
                )
            }
        }
    }
    categoria?.let { c ->
        Targeta {
            OutlinedTextField(nom, { nom = it }, Modifier.fillMaxWidth(), label = { Text("Nom (i model, si el saps)") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                kwh, { kwh = it; tocat = true }, Modifier.fillMaxWidth(), label = { Text("Consum, kWh l'any") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = { Text(if (tocat) "El que has escrit tu" else "És un valor típic: canvia'l si el saps") },
            )
            Spacer(Modifier.height(10.dp))
            val valor = kwh.replace(".", "").replace(',', '.').toDoubleOrNull()
            Button(
                onClick = {
                    acaba(Aparell(nom = nom.ifBlank { c.nom }, categoria = c, font = if (tocat) Font.MANUAL else Font.TIPIC, kwhAny = valor ?: c.kwhAnyTipic, model = ""))
                },
                Modifier.fillMaxWidth(), enabled = valor != null,
            ) { Text("Afegeix-lo") }
        }
    }
}
