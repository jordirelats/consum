@file:OptIn(ExperimentalLayoutApi::class)

package cat.jordi.consum.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import cat.jordi.consum.dades.Aparell
import cat.jordi.consum.dades.Pregunta
import cat.jordi.consum.dades.Punt
import cat.jordi.consum.dades.Referencia
import cat.jordi.consum.dades.endevina
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun teAvisos(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun hora(ts: Long) = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
fun PantallaEntrena(vm: ConsumViewModel) {
    val context = LocalContext.current
    var avisos by remember { mutableStateOf(teAvisos(context)) }
    val demana = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { avisos = it || teAvisos(context) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!avisos) {
            Targeta(color = DesconegutFluix) {
                TitolTargeta("Avisos de consum inesperat")
                Nota("Si s'encén alguna cosa que no reconec i dura més de 10 minuts, t'ho preguntaré amb una notificació.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) demana.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("Activa els avisos") }
            }
        }

        vm.preguntes.forEach { p -> TargetaPregunta(vm, p) }

        Targeta {
            TitolTargeta("Ensenya-li en directe")
            Nota("Encén o apaga un aparell i toca el botó de seguida. L'app mesura el salt en mig minut; millor si mentrestant no s'encén res més.")
            Spacer(Modifier.height(6.dp))
            val l = vm.lectura
            Pastilla(
                if (l != null) "Ara ${num(vm.resta(l))} W" + (if (vm.config.teLinia) " sense la pinça 2" else "") else "Sense lectura del Shelly",
                if (l != null) Primari else Desconegut, if (l != null) PrimariFluix else DesconegutFluix, punt = if (l != null) Ok else Desconegut,
            )
            if (vm.missatgeDe == null) {
                vm.missatgeEntrena?.let { Text(it, Modifier.padding(top = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Primari) }
            }
            if (vm.aparellsAEntrenar.isEmpty()) Nota("Primer afegeix els aparells, a la pestanya Aparells.", Modifier.padding(top = 8.dp))
            vm.aparellsAEntrenar.forEach { a -> FilaEntrena(vm, a, vm.referencies[a.id]) }
        }
        Nota(
            "Funciona millor a casa, on el Shelly dona una lectura cada segon. Des de fora, pel núvol, n'arriba una cada 5 segons.",
            Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FilaEntrena(vm: ConsumViewModel, a: Aparell, r: Referencia?) {
    val encès = a.id in vm.encesos
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Bombolla(icona(a.categoria), PrimariFluix, Primari)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.nom, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    when {
                        r == null -> "encara cap mostra"
                        r.apresa -> "${num(r.w)} W · après (${r.mostres} mostres)"
                        else -> "${num(r.w)} W · ${r.mostres} de ${Referencia.PER_APRENDRE} mostres"
                    },
                    fontSize = 12.sp, color = if (r?.apresa == true) Ok else Suau,
                )
            }
            when {
                vm.mesurant == a.id -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  Mesurant…", fontSize = 13.sp, color = Suau)
                }
                encès -> Button(onClick = { vm.ensenya(a, false) }, enabled = vm.mesurant == null && vm.lectura != null) { Text("L'he apagat") }
                else -> OutlinedButton(onClick = { vm.ensenya(a, true) }, enabled = vm.mesurant == null && vm.lectura != null) { Text("L'he encès") }
            }
        }
        // El resultat de la mesura, a sota de l'aparell que s'ha mesurat
        if (vm.missatgeDe == a.id && vm.mesurant == null) {
            vm.missatgeEntrena?.let { Text(it, Modifier.padding(start = 50.dp, bottom = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Primari) }
        }
    }
}

@Composable
private fun TargetaPregunta(vm: ConsumViewModel, p: Pregunta) {
    var totes by remember { mutableStateOf(false) }
    Targeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pastilla("Pregunta", Desconegut, DesconegutFluix)
            Spacer(Modifier.width(8.dp))
            Text("Què era això?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Text("Des de les ${hora(p.ts)} · +${num(p.dp)} W que no reconeixia", Modifier.padding(top = 6.dp), fontSize = 14.sp)
        val punts = vm.serie.filter { it.ts in (p.ts - 15 * 60)..(p.ts + 60 * 60) }
        if (punts.size > 5) MiniGrafica(punts, p.ts)
        val ordenats = endevina(p.dp, vm.aparellsAEntrenar, vm.referencies, vm.aparellsAEntrenar.size)
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (if (totes) ordenats else ordenats.take(5)).forEach { a ->
                SuggestionChip(onClick = { vm.respon(p, a.id) }, label = { Text(a.nom) })
            }
            if (!totes && ordenats.size > 5) SuggestionChip(onClick = { totes = true }, label = { Text("Un altre…") })
            SuggestionChip(onClick = { vm.respon(p, null) }, label = { Text("No ho sé") })
        }
    }
}

/** La potència (sense la línia) al voltant d'un consum desconegut. */
@Composable
private fun MiniGrafica(punts: List<Punt>, inici: Long) {
    Canvas(Modifier.fillMaxWidth().height(70.dp).padding(top = 6.dp)) {
        val t0 = punts.first().ts.toFloat()
        val t1 = punts.last().ts.toFloat().coerceAtLeast(t0 + 1)
        val ymax = punts.maxOf { it.wTotal - it.wLinia }.toFloat().coerceAtLeast(1f) * 1.1f
        fun x(ts: Long) = (ts - t0) / (t1 - t0) * size.width
        fun y(w: Double) = size.height - (w.toFloat() / ymax).coerceIn(0f, 1f) * size.height
        drawLine(Separador, Offset(x(inici), 0f), Offset(x(inici), size.height), strokeWidth = 2f)
        val camí = Path().apply {
            punts.forEachIndexed { i, p ->
                val w = p.wTotal - p.wLinia
                if (i == 0) moveTo(x(p.ts), y(w)) else lineTo(x(p.ts), y(w))
            }
        }
        drawPath(camí, Desconegut, style = Stroke(2.dp.toPx()))
    }
}
