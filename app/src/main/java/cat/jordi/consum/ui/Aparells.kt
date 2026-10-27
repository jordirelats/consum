package cat.jordi.consum.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.R
import cat.jordi.consum.dades.Aparell
import cat.jordi.consum.dades.FilaRanquing
import cat.jordi.consum.dades.Funcionament

/**
 * Els aparells (maqueta 13): cada un una sola vegada, amb el que ha gastat de debò aquest mes, de més a menys, i com el
 * coneix l'app. Els que encara no ha vist funcionar, a part: el que diu l'etiqueta no és consum. Tocant-ne un, la fitxa.
 */
@Composable
fun PantallaAparells(vm: ConsumViewModel) {
    val mes = vm.resumMes
    val r = mes?.ranquing
    // El preu mitjà que ha pagat la casa aquest mes; si encara no hi ha dades, el d'ara
    val preu = mes?.preuMitja ?: vm.infoPreu?.ara?.preu ?: vm.config.tarifa.preuFix

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (mes != null) {
            Text(stringResource(R.string.aparells_aquest_mes), fontSize = 14.sp, color = Suau)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(euros(mes.euros.total), fontSize = 40.sp, fontWeight = FontWeight.Light)
                Text("  ${num(mes.total)} kWh", Modifier.padding(bottom = 7.dp), fontSize = 14.sp, color = Suau)
            }
            Text(consumIFixos(mes.euros.consum, mes.euros.fixos), fontSize = 13.sp, color = Suau)
        }

        val consells = buildList {
            vm.fonsW?.takeIf { it >= 1 }?.let { w ->
                add(stringResource(R.string.aparells_consell_fons, num(w), num(w * 24 * 365 / 1000 * preu)))
            }
            vm.consellLinia?.let { c ->
                add(stringResource(R.string.aparells_consell_linia, vm.config.nomLinia, c.percentCar, euros(c.estalviMes)))
            }
            val perCompletar = vm.aparells.count { it.perCompletar }
            if (perCompletar > 0) add(pluralStringResource(R.plurals.aparells_consell_tipics, perCompletar, perCompletar))
        }
        if (consells.isNotEmpty()) {
            Targeta(color = DesconegutFluix) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = Desconegut, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    TitolTargeta(stringResource(R.string.aparells_que_pots_fer), Modifier.padding(top = 6.dp))
                }
                consells.forEach { Text(it, Modifier.padding(top = 2.dp), fontSize = 13.sp, lineHeight = 18.sp) }
            }
        }

        if (vm.aparells.isEmpty()) {
            Targeta {
                TitolTargeta(stringResource(R.string.aparells_cap_aparell))
                Nota(stringResource(R.string.aparells_comenca_questionari))
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { vm.pantalla = Pantalla.QUESTIONARI }) {
                    Icon(Icons.Outlined.Checklist, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.aparells_revisem_casa))
                }
            }
        } else {
            val mesurats = r?.aparells.orEmpty()
            if (mesurats.isNotEmpty()) Targeta {
                TitolTargeta(stringResource(R.string.aparells_teus))
                Nota(stringResource(R.string.aparells_teus_nota))
                mesurats.forEach { f -> FilaAparell(vm, f) }
            }
            // Els sempre encesos van dins del que la casa gasta sempre
            val sempre = vm.aparells.filter { it.funciona == Funcionament.SEMPRE }
            if (sempre.isNotEmpty()) Targeta {
                TitolTargeta(stringResource(R.string.aparells_sempre_encesos))
                Nota(stringResource(R.string.aparells_sempre_encesos_nota))
                sempre.forEach { a ->
                    FilaConsum(
                        icona(a.categoria), SempreFluix, Sempre, a.nom, stringResource(a.font.nomId), "${num(a.wTipic)} W",
                        modifier = Modifier.clickable { vm.obreAparell(a) },
                    )
                }
            }
            val ambConsum = mesurats.mapNotNull { it.aparell?.id }.toSet()
            // Un aparell que es diu com la línia és la línia mateixa: si gasta, ja surt a dalt
            val senseMesurar = vm.aparells.filter {
                it.funciona != Funcionament.SEMPRE && it.id !in ambConsum && !(vm.config.teLinia && it.nom.equals(vm.config.nomLinia, ignoreCase = true))
            }
            if (senseMesurar.isNotEmpty()) Targeta {
                TitolTargeta(stringResource(R.string.aparells_sense_mesurar))
                Nota(stringResource(R.string.aparells_sense_mesurar_nota))
                senseMesurar.forEach { a -> FilaSenseMesurar(vm, a) }
            }
            TextButton(onClick = { vm.pantalla = Pantalla.QUESTIONARI }) { Text(stringResource(R.string.aparells_torna_questionari)) }
        }
        Spacer(Modifier.height(80.dp))
    }
}

/** Com coneix l'app un aparell, en poques paraules (trobat sol, ensenyat, après de la pinça 2, après, aprenent), i si ja és segur. */
@Composable
fun estatAparell(vm: ConsumViewModel, f: FilaRanquing): Pair<String, Boolean> {
    val r = f.aparell?.let { vm.referencies[it.id] ?: vm.referenciesLinia[it.id] }
    return when {
        f.compressorW != null -> stringResource(R.string.estat_trobat_sol) to true
        f.aparell == null -> stringResource(R.string.ranquing_linia_mesurada) to true
        f.ensenyats > 0 -> stringResource(R.string.estat_ensenyat) to true
        f.linia -> stringResource(R.string.estat_apres_pinca) to true
        r?.apresa == true -> stringResource(R.string.estat_apres) to true
        else -> stringResource(R.string.estat_aprenent) to false
    }
}

@Composable
private fun FilaAparell(vm: ConsumViewModel, f: FilaRanquing) {
    val color = if (f.linia) Linia else Primari
    val (estat, ok) = estatAparell(vm, f)
    Row(
        Modifier.fillMaxWidth().then(if (f.aparell != null) Modifier.clickable { vm.obreAparell(f.aparell) } else Modifier).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Bombolla(f.aparell?.let { icona(it.categoria) } ?: Icons.Outlined.Bolt, if (f.linia) LiniaFluix else PrimariFluix, color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(f.nom.ifBlank { stringResource(R.string.pinca2_xip) }, Modifier.weight(1f, fill = false), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (f.linia) XipPinca()
            }
            Row(Modifier.padding(top = 2.dp)) { XipEstat(estat, ok) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${num(f.kwh, 1)} kWh", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(euros(f.euros), fontSize = 12.sp, color = Suau)
        }
    }
}

@Composable
private fun FilaSenseMesurar(vm: ConsumViewModel, a: Aparell) {
    val linia = vm.config.teLinia && a.linia
    Row(
        Modifier.fillMaxWidth().clickable { vm.obreAparell(a) }.padding(vertical = 7.dp).alpha(0.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Bombolla(icona(a.categoria), SempreFluix, Sempre)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(a.nom, Modifier.weight(1f, fill = false), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (linia) XipPinca()
            }
            Text(stringResource(R.string.aparells_sense_mesurar_fila, num(a.kwhAny / 12)), fontSize = 12.sp, color = Suau, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("—", fontSize = 15.sp, color = Suau)
    }
}
