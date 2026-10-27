package cat.jordi.consum.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.R
import cat.jordi.consum.dades.Aparell
import cat.jordi.consum.dades.FilaRanquing
import cat.jordi.consum.dades.Ranquing

/** La marca dels aparells que són a la línia de la pinça 2 (maqueta 13). */
@Composable
fun XipPinca() {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(LiniaFluix).padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Bolt, null, tint = Linia, modifier = Modifier.size(11.dp))
        Text(stringResource(R.string.pinca2_xip), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Linia)
    }
}

/** Una etiqueta petita: com coneix l'app un aparell (après, ensenyat, trobat sol…). */
@Composable
fun XipEstat(text: String, ok: Boolean = true) {
    Text(
        text, Modifier.clip(RoundedCornerShape(6.dp)).background(if (ok) OkFluix else Separador).padding(horizontal = 6.dp, vertical = 1.dp),
        fontSize = 10.5.sp, color = if (ok) Ok else Suau,
    )
}

/** Tot el consum del període en una barra: el dels aparells que l'app coneix i la resta. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BarraConsum(r: Ranquing) {
    if (r.total <= 0) return
    val parts = listOf(
        Triple(R.string.ranquing_llegenda_aparells, r.delsAparells, Primari),
        Triple(R.string.ranquing_llegenda_sempre, r.sempre, Sempre),
        Triple(R.string.ranquing_llegenda_petits, r.petits + r.liniaAltres + r.senseDetall, Separador),
        Triple(R.string.ranquing_llegenda_sense, r.senseIdentificar, Desconegut),
    )
    val pct = parts.map { (text, kwh, _) -> stringResource(text, num(kwh / r.total * 100)) }
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = pct.joinToString(", ") }) {
        Canvas(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
            var x = 0f
            for ((_, kwh, color) in parts) {
                val ample = (kwh / r.total).toFloat() * size.width
                drawRect(color, Offset(x, 0f), Size(ample, size.height))
                x += ample
            }
        }
        FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            parts.zip(pct).forEach { (part, text) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(part.third))
                    Text(" $text", fontSize = 11.sp, color = Suau)
                }
            }
        }
    }
}

/** Qui ha gastat més: els aparells de més a menys, cadascun un cop, amb la barra, els kWh i els euros. */
@Composable
fun LlistaRanquing(r: Ranquing, obre: (Aparell) -> Unit) {
    if (r.aparells.isEmpty()) {
        Nota(stringResource(R.string.ranquing_cap))
        return
    }
    val maxim = r.aparells.first().kwh.coerceAtLeast(0.001)
    r.aparells.forEachIndexed { i, f -> FilaDelRanquing(i + 1, f, f.kwh / maxim, obre) }
}

@Composable
private fun FilaDelRanquing(posicio: Int, f: FilaRanquing, part: Double, obre: (Aparell) -> Unit) {
    val color = if (f.linia) Linia else Primari
    val fons = if (f.linia) LiniaFluix else PrimariFluix
    val sub = when {
        f.compressorW != null -> stringResource(R.string.ranquing_compressor, num(f.compressorW))
        f.aparell == null -> stringResource(R.string.ranquing_linia_mesurada)
        else -> buildList {
            add(pluralStringResource(R.plurals.ranquing_cops, f.cops, f.cops))
            if (f.ensenyats > 0) add(pluralStringResource(R.plurals.ranquing_ensenyats, f.ensenyats, f.ensenyats))
        }.joinToString(" · ")
    }
    Row(
        Modifier.fillMaxWidth().then(if (f.aparell != null) Modifier.clickable { obre(f.aparell) } else Modifier).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$posicio", Modifier.width(16.dp), fontSize = 12.sp, color = Suau)
        Bombolla(if (f.aparell != null) icona(f.aparell.categoria) else Icons.Outlined.Bolt, fons, color)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(f.nom.ifBlank { stringResource(R.string.pinca2_xip) }, Modifier.weight(1f, fill = false), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (f.linia) XipPinca()
            }
            Text(sub, fontSize = 12.sp, color = Suau, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Canvas(Modifier.fillMaxWidth().height(5.dp).padding(top = 2.dp)) {
                drawRoundRect(Separador, Offset.Zero, size, CornerRadius(size.height / 2))
                drawRoundRect(color, Offset.Zero, Size(size.width * part.toFloat().coerceIn(0.01f, 1f), size.height), CornerRadius(size.height / 2))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${num(f.kwh, 1)} kWh", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(euros(f.euros), fontSize = 12.sp, color = Suau)
        }
    }
}

/** La resta: el que no és de cap aparell, separat del rànquing. [fixos]: els costos fixos del període (€). */
@Composable
fun RestaConsum(r: Ranquing, fonsW: Double?, nomLinia: String, fixos: Double?) {
    FilaConsum(
        Icons.Outlined.Power, SempreFluix, Sempre, stringResource(R.string.ranquing_sempre),
        fonsW?.let { stringResource(R.string.ranquing_sempre_text, num(it)) } ?: stringResource(R.string.historial_cal_un_dia),
        "${num(r.sempre, 1)} kWh", subValor = euros(r.sempreEuros),
    )
    if (r.petits >= 0.05) FilaConsum(
        Icons.Outlined.LightMode, SempreFluix, Sempre, stringResource(R.string.ranquing_petits), stringResource(R.string.ranquing_petits_text),
        "${num(r.petits, 1)} kWh", subValor = euros(r.petitsEuros),
    )
    if (r.senseIdentificar >= 0.05) FilaConsum(
        Icons.Outlined.QuestionMark, DesconegutFluix, Desconegut, stringResource(R.string.ranquing_sense_id), stringResource(R.string.ranquing_sense_id_text),
        "${num(r.senseIdentificar, 1)} kWh", subValor = euros(r.senseIdentificarEuros),
    )
    if (r.liniaAltres >= 0.05) FilaConsum(
        Icons.Outlined.Bolt, LiniaFluix, Linia, stringResource(R.string.ranquing_linia_altres, nomLinia), stringResource(R.string.ranquing_linia_altres_text),
        "${num(r.liniaAltres, 1)} kWh", subValor = euros(r.liniaAltresEuros),
    )
    if (r.senseDetall >= 0.05) FilaConsum(
        Icons.Outlined.CloudQueue, SempreFluix, Sempre, stringResource(R.string.ranquing_sense_detall), stringResource(R.string.ranquing_sense_detall_text),
        "${num(r.senseDetall, 1)} kWh", subValor = euros(r.senseDetallEuros),
    )
    if (fixos != null) FilaConsum(
        Icons.Outlined.Euro, SempreFluix, Sempre, stringResource(R.string.historial_costos_fixos), stringResource(R.string.historial_no_depenen_us), euros(fixos),
    )
}
