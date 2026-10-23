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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import cat.jordi.consum.R
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
                TitolTargeta(stringResource(R.string.entrena_avisos_titol))
                Nota(stringResource(R.string.entrena_avisos_nota))
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) demana.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text(stringResource(R.string.entrena_activa_avisos)) }
            }
        }

        vm.preguntes.forEach { p -> TargetaPregunta(vm, p) }

        Targeta {
            TitolTargeta(stringResource(R.string.entrena_en_directe))
            Nota(stringResource(R.string.entrena_instruccions))
            Spacer(Modifier.height(6.dp))
            val l = vm.lectura
            Pastilla(
                when {
                    l == null -> stringResource(R.string.entrena_sense_lectura)
                    vm.config.teLinia -> stringResource(R.string.entrena_ara_w_sense_pinca, num(vm.resta(l)))
                    else -> stringResource(R.string.entrena_ara_w, num(vm.resta(l)))
                },
                if (l != null) Primari else Desconegut, if (l != null) PrimariFluix else DesconegutFluix, punt = if (l != null) Ok else Desconegut,
            )
            if (vm.missatgeDe == null) {
                vm.missatgeEntrena?.let { Text(it.text(), Modifier.padding(top = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Primari) }
            }
            if (vm.aparellsAEntrenar.isEmpty()) Nota(stringResource(R.string.entrena_primer_afegeix), Modifier.padding(top = 8.dp))
            vm.aparellsAEntrenar.forEach { a -> FilaEntrena(vm, a, vm.referencies[a.id]) }
        }
        Nota(
            stringResource(R.string.entrena_nota_casa),
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
                        r == null -> stringResource(R.string.entrena_cap_mostra)
                        r.apresa -> pluralStringResource(R.plurals.entrena_apres, r.mostres, num(r.w), r.mostres)
                        else -> stringResource(R.string.entrena_mostres_de, num(r.w), r.mostres, Referencia.PER_APRENDRE)
                    },
                    fontSize = 12.sp, color = if (r?.apresa == true) Ok else Suau,
                )
            }
            when {
                vm.mesurant == a.id -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  " + stringResource(R.string.entrena_mesurant), fontSize = 13.sp, color = Suau)
                }
                encès -> Button(onClick = { vm.ensenya(a, false) }, enabled = vm.mesurant == null && vm.lectura != null) { Text(stringResource(R.string.entrena_he_apagat)) }
                else -> OutlinedButton(onClick = { vm.ensenya(a, true) }, enabled = vm.mesurant == null && vm.lectura != null) { Text(stringResource(R.string.entrena_he_ences)) }
            }
        }
        // El resultat de la mesura, a sota de l'aparell que s'ha mesurat
        if (vm.missatgeDe == a.id && vm.mesurant == null) {
            vm.missatgeEntrena?.let { Text(it.text(), Modifier.padding(start = 50.dp, bottom = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Primari) }
        }
    }
}

@Composable
private fun TargetaPregunta(vm: ConsumViewModel, p: Pregunta) {
    var totes by remember { mutableStateOf(false) }
    Targeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pastilla(stringResource(R.string.entrena_pregunta), Desconegut, DesconegutFluix)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.entrena_que_era), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(stringResource(R.string.entrena_pregunta_text, hora(p.ts), num(p.dp)), Modifier.padding(top = 6.dp), fontSize = 14.sp)
        val punts = vm.serie.filter { it.ts in (p.ts - 15 * 60)..(p.ts + 60 * 60) }
        if (punts.size > 5) MiniGrafica(punts, p.ts)
        val ordenats = endevina(p.dp, vm.aparellsAEntrenar, vm.referencies, vm.aparellsAEntrenar.size)
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (if (totes) ordenats else ordenats.take(5)).forEach { a ->
                SuggestionChip(onClick = { vm.respon(p, a.id) }, label = { Text(a.nom) })
            }
            if (!totes && ordenats.size > 5) SuggestionChip(onClick = { totes = true }, label = { Text(stringResource(R.string.entrena_un_altre)) })
            SuggestionChip(onClick = { vm.respon(p, null) }, label = { Text(stringResource(R.string.entrena_no_ho_se)) })
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
