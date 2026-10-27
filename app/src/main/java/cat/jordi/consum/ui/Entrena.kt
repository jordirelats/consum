@file:OptIn(ExperimentalLayoutApi::class)

package cat.jordi.consum.ui

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.R
import cat.jordi.consum.dades.Aparell
import cat.jordi.consum.dades.Funcionament
import cat.jordi.consum.dades.Idioma
import cat.jordi.consum.dades.SenseNom
import cat.jordi.consum.dades.VegadaSenseNom
import cat.jordi.consum.dades.Pregunta
import cat.jordi.consum.dades.Punt
import cat.jordi.consum.dades.Referencia
import cat.jordi.consum.dades.Repetit
import cat.jordi.consum.dades.endevina
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun hora(ts: Long) = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
fun PantallaEntrena(vm: ConsumViewModel) {
    val context = LocalContext.current
    val avisos = rememberPermisAvisos()
    // Mentre es mesura (28 s), la pantalla no s'apaga: amb la pantalla apagada no arriben lectures
    val vista = LocalView.current
    val mesurant = vm.mesurant != null
    DisposableEffect(mesurant) {
        vista.keepScreenOn = mesurant
        onDispose { vista.keepScreenOn = false }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!avisos.actius) {
            Targeta(color = DesconegutFluix) {
                TitolTargeta(stringResource(R.string.entrena_avisos_titol))
                Nota(stringResource(R.string.entrena_avisos_nota))
                Spacer(Modifier.height(8.dp))
                Button(onClick = avisos.demana) { Text(stringResource(R.string.entrena_activa_avisos)) }
            }
        }

        // El que més gasta i encara no té nom (maqueta 15): d'un toc
        vm.senseNom?.takeIf { it.vegades.isNotEmpty() }?.let { TargetaSenseNom(vm, it) }
        vm.preguntes.forEach { p -> key(p.id) { TargetaPregunta(vm, p) } }
        // El que es repeteix sempre igual (maqueta 12): preguntat un cop
        vm.repetits.forEach { r -> key("r${r.id}") { TargetaRepetit(vm, r) } }

        // Ensenyar-li una estona passada (maqueta 8): quan no hi eres a temps de tocar el botó
        Targeta {
            TitolTargeta(stringResource(R.string.entrena_passat_titol))
            Nota(stringResource(R.string.entrena_passat_text))
            Button(
                onClick = { vm.oblidaEstona(); vm.pantalla = Pantalla.PASSAT },
                Modifier.fillMaxWidth().padding(top = 8.dp), enabled = vm.aparellsAEntrenar.isNotEmpty(),
            ) { Text(stringResource(R.string.entrena_passat_boto)) }
        }

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
            // Només s'entrenen els que engega l'usuari: els que estan sempre encesos o s'engeguen sols, no cal
            val perEntrenar = vm.aparellsAEntrenar.filter { it.funciona == Funcionament.JO }
            if (vm.aparells.isEmpty()) Nota(stringResource(R.string.entrena_primer_afegeix), Modifier.padding(top = 8.dp))
            perEntrenar.forEach { a -> FilaEntrena(vm, a, vm.referencies[a.id]) }
            if (vm.aparells.any { it.funciona != Funcionament.JO }) Nota(stringResource(R.string.entrena_nota_sense_entrenar), Modifier.padding(top = 8.dp))
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
    // Si s'engega sol, l'app n'aprèn l'interval de potència i no n'avisa més
    var sol by remember { mutableStateOf(false) }
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
                SuggestionChip(onClick = { vm.respon(p, a.id, sol) }, label = { Text(a.nom) })
            }
            if (!totes && ordenats.size > 5) SuggestionChip(onClick = { totes = true }, label = { Text(stringResource(R.string.entrena_un_altre)) })
            SuggestionChip(onClick = { vm.respon(p, null) }, label = { Text(stringResource(R.string.entrena_no_ho_se)) })
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp).toggleable(sol, role = Role.Switch) { sol = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.funciona_sol), fontSize = 14.sp)
                Text(stringResource(R.string.entrena_sol_text), fontSize = 12.sp, color = Suau)
            }
            Switch(sol, null)
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

/**
 * Una cosa que es repeteix sempre igual i encara no se sap què és (maqueta 12): quant dura i cada quant, quan passa,
 * la potència, com és, i cada cop de l'última setmana en una ratlla. Amb una resposta, l'app n'aprèn tots els cops.
 */
@Composable
private fun TargetaRepetit(vm: ConsumViewModel, r: Repetit) {
    var totes by remember { mutableStateOf(false) }
    // Si sembla que s'engega sol (de nit, molts cops al dia), l'app n'aprendrà l'interval i no n'avisarà
    var sol by remember(r.id) { mutableStateOf(r.sembla(ZoneId.systemDefault())) }
    val minuts = num(r.minuts)
    val titol = when {
        r.cadaMinuts >= 20 * 60 -> stringResource(R.string.repetit_un_cop_al_dia, minuts)
        r.cadaMinuts >= 90 -> stringResource(R.string.repetit_cada_hores, minuts, num(r.cadaMinuts / 60))
        else -> stringResource(R.string.repetit_cada_minuts, minuts, num(r.cadaMinuts))
    }
    Targeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pastilla(pluralStringResource(R.plurals.repetit_cops, r.cops.size, r.cops.size), Desconegut, DesconegutFluix)
            Spacer(Modifier.width(8.dp))
            Text(titol, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.repetit_detall, stringResource(r.quan(ZoneId.systemDefault()).textId), num(r.wMin), num(r.wMax), num(r.kwh, 1)),
                Modifier.weight(1f), fontSize = 13.sp, lineHeight = 18.sp, color = Suau,
            )
        }
        r.forma?.let { f ->
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                DibuixForma(f, Desconegut)
                Text("  " + stringResource(f.textId), fontSize = 13.sp, color = Suau)
            }
        }
        TiraSetmana(r.cops.map { it.first }, Desconegut, stringResource(R.string.repetit_tira, r.cops.size))
        Text(stringResource(R.string.repetit_que_es), Modifier.padding(top = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        val ordenats = endevina(r.w, vm.aparellsAEntrenar, vm.referencies, vm.aparellsAEntrenar.size, r.minuts, r.forma)
        FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (if (totes) ordenats else ordenats.take(4)).forEach { a ->
                SuggestionChip(onClick = { vm.responRepetit(r, a.id, sol) }, label = { Text(a.nom) })
            }
            if (!totes && ordenats.size > 4) SuggestionChip(onClick = { totes = true }, label = { Text(stringResource(R.string.entrena_un_altre)) })
            SuggestionChip(onClick = { vm.responRepetit(r, null, false) }, label = { Text(stringResource(R.string.entrena_no_ho_se)) })
        }
        if (vm.aparellsAEntrenar.isEmpty()) Nota(stringResource(R.string.entrena_primer_afegeix), Modifier.padding(top = 4.dp))
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp).toggleable(sol, role = Role.Switch) { sol = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.funciona_sol), fontSize = 14.sp)
                Text(stringResource(R.string.entrena_sol_text), fontSize = 12.sp, color = Suau)
            }
            Switch(sol, null)
        }
    }
}

/**
 * El que més gasta i encara no té nom (maqueta 15): les vegades més grosses dels últims 7 dies que l'app no sap de qui són,
 * per dir-ho d'un toc. A Guíxols, les dues càrregues del cotxe del dissabte 24 eren gairebé tot el 61 % sense identificar.
 */
@Composable
private fun TargetaSenseNom(vm: ConsumViewModel, s: SenseNom) {
    Targeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.QuestionMark, null, tint = Desconegut, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.sense_nom_titol), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Nota(stringResource(R.string.sense_nom_text, num(s.kwh, 1), num(s.part * 100)), Modifier.padding(top = 4.dp))
        s.vegades.forEach { x -> key(x.vegada.ts) { FilaSenseNom(vm, x) } }
        if (s.vegades.size > 1) {
            Row(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(PrimariFluix).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Check, null, tint = Primari, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.sense_nom_nota), fontSize = 12.5.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun FilaSenseNom(vm: ConsumViewModel, x: VegadaSenseNom) {
    val v = x.vegada
    var totes by remember { mutableStateOf(false) }
    val zona = ZoneId.systemDefault()
    val dia = Instant.ofEpochSecond(v.ts).atZone(zona).format(DateTimeFormatter.ofPattern(stringResource(R.string.format_dia_curt), Idioma.locale))
    Column(Modifier.padding(top = 10.dp)) {
        HorizontalDivider(color = Separador)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sense_nom_quan, dia, hora(v.ts)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.sense_nom_detall, durada(v.minuts), num(v.w / 1000, 1)), fontSize = 12.sp, color = Suau)
            }
            FormaVegada(x.punts, v.ts, v.fi + 60)
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("${num(v.kwh, 1)} kWh", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(euros(x.euros), fontSize = 12.sp, color = Suau)
            }
        }
        FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (if (totes) x.candidats else x.candidats.take(3)).forEach { a ->
                SuggestionChip(onClick = { vm.responVegada(v, a.id) }, label = { Text(a.nom) })
            }
            if (!totes && x.candidats.size > 3) SuggestionChip(onClick = { totes = true }, label = { Text(stringResource(R.string.entrena_un_altre)) })
            SuggestionChip(onClick = { vm.ignoraVegada(v) }, label = { Text(stringResource(R.string.entrena_no_ho_se)) })
        }
        if (x.candidats.isEmpty()) Nota(stringResource(R.string.entrena_primer_afegeix))
    }
}

/** La forma d'una vegada, petita i sense eixos: la potència de la resta de la casa, amb la vegada pintada a sota. */
@Composable
private fun FormaVegada(punts: List<Pair<Long, Double>>, desDe: Long, finsA: Long) {
    if (punts.size < 2) return
    Canvas(Modifier.width(84.dp).height(34.dp)) {
        val t0 = punts.first().first.toFloat()
        val t1 = punts.last().first.toFloat().coerceAtLeast(t0 + 1)
        val ymax = punts.maxOf { it.second }.toFloat().coerceAtLeast(1f) * 1.08f
        fun x(ts: Long) = (ts - t0) / (t1 - t0) * size.width
        fun y(w: Double) = size.height - (w.toFloat() / ymax).coerceIn(0f, 1f) * size.height
        val linia = Path()
        val area = Path()
        punts.forEachIndexed { i, (ts, w) ->
            if (i == 0) linia.moveTo(x(ts), y(w)) else linia.lineTo(x(ts), y(w))
        }
        val dins = punts.filter { it.first in desDe until finsA }
        if (dins.size >= 2) {
            area.moveTo(x(dins.first().first), size.height)
            dins.forEach { (ts, w) -> area.lineTo(x(ts), y(w)) }
            area.lineTo(x(dins.last().first), size.height)
            area.close()
            drawPath(area, Desconegut.copy(alpha = 0.15f))
        }
        drawPath(linia, Desconegut, style = Stroke(1.6.dp.toPx()))
    }
}

/** «6 h 7 min» o «45 min». */
@Composable
fun durada(minuts: Int): String =
    if (minuts >= 60) stringResource(R.string.durada_h_min, minuts / 60, minuts % 60) else stringResource(R.string.durada_min, minuts)
