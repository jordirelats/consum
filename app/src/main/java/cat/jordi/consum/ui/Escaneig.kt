package cat.jordi.consum.ui

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import cat.jordi.consum.dades.CampFactura
import cat.jordi.consum.dades.Contracte
import cat.jordi.consum.dades.Document
import cat.jordi.consum.dades.Factura
import cat.jordi.consum.dades.LecturaFactura
import cat.jordi.consum.dades.Pais
import cat.jordi.consum.dades.Placa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Com es diu cada preu a la pantalla de revisió, i en quina unitat. */
private fun nomCamp(c: CampFactura): Pair<String, String> = when (c) {
    CampFactura.NL_NORMAAL -> "Leveringstarief · Normaal" to "€/kWh"
    CampFactura.NL_DAL -> "Leveringstarief · Dal" to "€/kWh"
    CampFactura.NL_ENKEL -> "Leveringstarief" to "€/kWh"
    CampFactura.NL_EB -> "Energiebelasting (EB)" to "€/kWh"
    CampFactura.NL_VASTE -> "Vaste leveringskosten" to "€/dag"
    CampFactura.NL_NETBEHEER -> "Netbeheerkosten" to "€/dag"
    CampFactura.NL_VERMINDERING -> "Vermindering energiebelasting" to "€/dag"
    CampFactura.NL_INKOOP -> "Inkoopvergoeding" to "€/kWh"
    CampFactura.ES_POTENCIA -> "Potència contractada" to "kW"
    CampFactura.ES_P1 -> "Potència P1" to "€/kW i dia"
    CampFactura.ES_P2 -> "Potència P2 (vall)" to "€/kW i dia"
    CampFactura.ES_PUNTA -> "Energia, punta" to "€/kWh"
    CampFactura.ES_PLA -> "Energia, pla" to "€/kWh"
    CampFactura.ES_VALL -> "Energia, vall" to "€/kWh"
    CampFactura.ES_FIX -> "Energia, preu únic" to "€/kWh"
    CampFactura.ES_IEE -> "Impost elèctric" to "%"
    CampFactura.ES_IVA -> "IVA" to "%"
    CampFactura.ES_COMPTADOR -> "Lloguer del comptador" to "€/mes"
    CampFactura.ES_BO_SOCIAL -> "Finançament del bo social" to "€/dia"
}

/** Els preus que hauria de tenir un document d'aquest país i contracte, per dir quins no s'han trobat. */
private fun esperats(l: LecturaFactura): List<CampFactura> = when (l.pais) {
    Pais.NL -> when (l.contracte) {
        Contracte.FRANGES -> listOf(CampFactura.NL_NORMAAL, CampFactura.NL_DAL)
        Contracte.FIX -> listOf(CampFactura.NL_ENKEL)
        Contracte.DINAMIC -> listOf(CampFactura.NL_INKOOP)
    } + listOf(CampFactura.NL_VASTE, CampFactura.NL_NETBEHEER, CampFactura.NL_VERMINDERING) +
        if (l.contracte == Contracte.DINAMIC) emptyList() else listOf(CampFactura.NL_EB)
    else -> listOf(CampFactura.ES_POTENCIA, CampFactura.ES_P1, CampFactura.ES_P2) + when (l.contracte) {
        Contracte.FRANGES -> listOf(CampFactura.ES_PUNTA, CampFactura.ES_PLA, CampFactura.ES_VALL)
        Contracte.FIX -> listOf(CampFactura.ES_FIX)
        Contracte.DINAMIC -> emptyList()
    } + listOf(CampFactura.ES_IEE, CampFactura.ES_IVA, CampFactura.ES_COMPTADOR, CampFactura.ES_BO_SOCIAL)
}

private fun nomContracte(l: LecturaFactura) = when (l.pais) {
    Pais.NL -> "Països Baixos · " + when (l.contracte) {
        Contracte.FIX -> "preu fix"; Contracte.FRANGES -> "normaal i dal"; Contracte.DINAMIC -> "dinàmic"
    }
    else -> "Espanya · " + when (l.contracte) {
        Contracte.FIX -> "preu fix"; Contracte.FRANGES -> "per franges"; Contracte.DINAMIC -> "PVPC"
    }
}

/**
 * «Omple-ho amb el contracte»: una foto o un PDF del contracte o de la factura. Es llegeix dins del mòbil amb
 * Tesseract, pàgina a pàgina, i el que es troba es passa a [enLlegir] perquè l'usuari ho revisi.
 */
@Composable
fun TargetaEscaneig(enLlegir: (LecturaFactura?) -> Unit) {
    val context = LocalContext.current
    val abast = rememberCoroutineScope()
    var progres by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun llegeix(uri: Uri) {
        error = null
        progres = "Obrint el document…"
        abast.launch {
            val r = withContext(Dispatchers.Default) {
                runCatching {
                    val pagines = Document.pagines(context, uri)
                    val text = pagines.mapIndexed { i, p ->
                        withContext(Dispatchers.Main) { progres = "Llegint la pàgina ${i + 1} de ${pagines.size}…" }
                        Placa.llegeix(context, p, document = true)
                    }.joinToString("\n")
                    // Només a la versió de prova: el text llegit, per afinar el lector amb factures noves
                    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) text.lines().forEach { Log.d("ConsumOCR", it) }
                    Factura.llegeix(text)
                }
            }
            progres = null
            r.onSuccess { l -> if (l == null) error = "No hi he trobat cap preu. Prova amb el PDF, o amb una foto més de prop." else enLlegir(l) }
                .onFailure { error = "No he pogut llegir el document." }
        }
    }

    val uriFoto = remember {
        val f = File(context.cacheDir, "fotos/document.jpg").apply { parentFile?.mkdirs() }
        FileProvider.getUriForFile(context, "${context.packageName}.fitxers", f)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { feta -> if (feta) llegeix(uriFoto) }
    val permis = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { si ->
        if (si) camera.launch(uriFoto) else error = "Sense permís de càmera. Pots triar el PDF."
    }
    val fitxer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::llegeix) }

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = androidx.compose.ui.graphics.Color.White, border = BorderStroke(2.dp, Primari)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, null, tint = Primari, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                TitolTargeta("Omple-ho amb el contracte o la factura", Modifier.padding(top = 6.dp))
            }
            Nota("Fes-ne una foto o tria'n el PDF. L'app hi busca els preus i te'ls ensenya abans de posar-los. Tot es llegeix dins del mòbil.")
            Spacer(Modifier.height(10.dp))
            if (progres != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("  $progres", fontSize = 14.sp, color = Suau)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera.launch(uriFoto)
                        else permis.launch(Manifest.permission.CAMERA)
                    }) {
                        Icon(Icons.Outlined.PhotoCamera, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Foto")
                    }
                    OutlinedButton(onClick = { fitxer.launch(arrayOf("application/pdf", "image/*")) }) { Text("Tria un PDF o una foto") }
                }
            }
            error?.let { Text(it, Modifier.padding(top = 8.dp), color = Desconegut, fontSize = 13.sp) }
        }
    }
}

/** El que s'ha trobat al document: cada preu amb el tros d'on surt, per marcar o desmarcar. */
@Composable
fun RevisioDocument(l: LecturaFactura, enCancellar: () -> Unit, enPosar: (Set<CampFactura>) -> Unit) {
    var triats by remember { mutableStateOf(l.trobats.map { it.camp }.toSet()) }
    val trobats = l.trobats.map { it.camp }.toSet()
    val falten = esperats(l).filter { it !in trobats }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Targeta {
            TitolTargeta("He trobat al document")
            Text("Contracte: ${nomContracte(l)}", fontSize = 13.sp, color = Suau)
            Spacer(Modifier.height(4.dp))
            // En el mateix ordre que el formulari, no en el que surten al document
            l.trobats.sortedBy { it.camp.ordinal }.forEach { p ->
                val (nom, unitat) = nomCamp(p.camp)
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = p.camp in triats, onCheckedChange = { si -> triats = if (si) triats + p.camp else triats - p.camp })
                    Column(Modifier.weight(1f)) {
                        Text(nom, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (p.deduit) "La factura només té dos preus: el del pla també per a la vall" else "«${p.fragment}»",
                            fontSize = 11.sp, color = Suau, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontFamily = if (p.deduit) FontFamily.Default else FontFamily.Monospace,
                        )
                    }
                    Text("${textValor(p.camp, p.valor)} $unitat", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Nota("Sota cada número hi ha el tros del document d'on surt. Desmarca el que no sigui bo.", Modifier.padding(top = 4.dp))
        }
        Targeta {
            TitolTargeta("No ho he trobat")
            Nota(if (falten.isEmpty()) "Res: hi havia tot el que cal." else falten.joinToString(", ") { nomCamp(it).first } + ". Es queda el que ja hi havia.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = enCancellar, Modifier.weight(1f)) { Text("Cancel·la") }
            Button(onClick = { enPosar(triats) }, Modifier.weight(1f), enabled = triats.isNotEmpty()) { Text("Posa'ls") }
        }
    }
}

private fun textValor(c: CampFactura, v: Double) = when (c) {
    CampFactura.ES_POTENCIA -> num(v, 2)
    CampFactura.ES_IEE, CampFactura.ES_IVA -> num(v, 2)
    CampFactura.ES_COMPTADOR -> num(v, 2)
    else -> num(v, 6).trimEnd('0').trimEnd(',')
}
