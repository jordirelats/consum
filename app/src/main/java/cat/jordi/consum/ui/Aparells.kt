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
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.R
import cat.jordi.consum.dades.Aparell
import cat.jordi.consum.dades.Font

@Composable
fun PantallaAparells(vm: ConsumViewModel) {
    val mes = vm.resumMes
    // El preu mitjà que ha pagat la casa aquest mes; si encara no hi ha dades, el d'ara
    val preu = mes?.preuMitja ?: vm.infoPreu?.ara?.preu ?: vm.config.tarifa.preuFix
    var obert by remember { mutableStateOf<Aparell?>(null) }

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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Punts(3); Text(stringResource(R.string.aparells_llegenda_mesurat), fontSize = 11.sp, color = Suau)
            Punts(2); Text(stringResource(R.string.aparells_llegenda_etiqueta), fontSize = 11.sp, color = Suau)
            Punts(1); Text(stringResource(R.string.aparells_llegenda_tipic), fontSize = 11.sp, color = Suau)
        }

        val consells = buildList {
            vm.fonsW?.takeIf { it >= 1 }?.let { w ->
                add(stringResource(R.string.aparells_consell_fons, num(w), num(w * 24 * 365 / 1000 * preu)))
            }
            val perCompletar = vm.aparells.count { it.perCompletar }
            vm.consellLinia?.let { c ->
                add(stringResource(R.string.aparells_consell_linia, vm.config.nomLinia, c.percentCar, euros(c.estalviMes)))
            }
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

        if (mes != null) {
            Targeta {
                TitolTargeta(stringResource(R.string.aparells_mesurat_mes))
                if (vm.config.teLinia) {
                    FilaConsum(
                        Icons.Outlined.Bolt, LiniaFluix, Linia, vm.config.nomLinia,
                        stringResource(R.string.aparells_linia_subtitol, num(mes.linia)), euros(mes.euros.linia), 3,
                    )
                }
                FilaConsum(
                    Icons.Outlined.Power, SempreFluix, Sempre, stringResource(R.string.aparells_sempre_ences),
                    "${num(mes.sempre)} kWh", euros(mes.euros.sempre), 3,
                )
                // Els aparells après es queden la seva part del que no és fons; la resta, sense identificar
                val rep = vm.repartiment
                vm.aparells.filter { (rep?.kwh?.get(it.id) ?: 0.0) > 0.0 }.sortedByDescending { rep!!.euros[it.id] }.forEach { a ->
                    val apresa = vm.referencies[a.id]?.apresa == true
                    FilaConsum(
                        icona(a.categoria), PrimariFluix, Primari, a.nom,
                        stringResource(
                            if (apresa) R.string.aparells_aparell_apres else R.string.aparells_aparell_aprenent,
                            num(rep!!.kwh.getValue(a.id), 1), num(a.kwhAny / 12),
                        ),
                        euros(rep.euros.getValue(a.id)), if (apresa) 3 else 2, modifier = Modifier.clickable { obert = a },
                    )
                }
                FilaConsum(
                    Icons.Outlined.QuestionMark, DesconegutFluix, Desconegut, stringResource(R.string.aparells_sense_identificar),
                    stringResource(R.string.aparells_residu, num(rep?.residuKwh ?: mes.desconegut)), euros(rep?.residuEuros ?: mes.euros.desconegut), 1,
                )
                FilaConsum(
                    Icons.Outlined.Euro, SempreFluix, Sempre, stringResource(R.string.aparells_costos_fixos),
                    stringResource(R.string.aparells_fixos_subtitol, quinsFixos(vm.config.tarifa)), euros(mes.euros.fixos),
                )
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
            // Els que ja tenen referència surten amb el que gasten de debò, a «Mesurat aquest mes»
            val perEntrenar = vm.aparells.filter { (vm.repartiment?.kwh?.get(it.id) ?: 0.0) == 0.0 }
            if (perEntrenar.isNotEmpty()) Targeta {
                TitolTargeta(stringResource(R.string.aparells_segons_etiqueta))
                Nota(stringResource(R.string.aparells_nota_etiqueta, num(preu, 3), euros(perEntrenar.sumOf { it.kwhAny } / 12 * preu)))
                perEntrenar.forEach { a ->
                    val mesurat = mes != null && vm.config.teLinia && a.nom.equals(vm.config.nomLinia, ignoreCase = true)
                    if (mesurat) {
                        FilaConsum(
                            icona(a.categoria), LiniaFluix, Linia, a.nom,
                            stringResource(R.string.aparells_mesurat_pinca2, num(a.kwhAny)), euros(mes!!.euros.linia), 3,
                            modifier = Modifier.clickable { obert = a },
                        )
                    } else {
                        FilaConsum(
                            icona(a.categoria), PrimariFluix, Primari, a.nom,
                            stringResource(R.string.aparells_kwh_any_font, num(a.kwhAny), stringResource(a.font.nomId)),
                            stringResource(R.string.aparells_euros_mes, euros(a.kwhAny / 12 * preu)), a.font.punts,
                            subtitolColor = if (a.perCompletar) Desconegut else Suau,
                            modifier = Modifier.clickable { obert = a },
                        )
                    }
                }
            }
            TextButton(onClick = { vm.pantalla = Pantalla.QUESTIONARI }) { Text(stringResource(R.string.aparells_torna_questionari)) }
        }
        Spacer(Modifier.height(80.dp))
    }

    obert?.let { a ->
        AlertDialog(
            onDismissRequest = { obert = null },
            title = { Text(a.nom) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${stringResource(a.categoria.nomId)} · ${stringResource(a.font.nomId)}")
                    if (a.marca.isNotBlank()) Text("${a.marca} ${a.model}")
                    if (a.classe.isNotBlank()) Text(stringResource(R.string.aparells_classe, a.classe))
                    Text(stringResource(R.string.aparells_kwh_euros_any, num(a.kwhAny), num(a.kwhAny * preu)))
                    a.esperaW?.let { Text(stringResource(R.string.aparells_en_espera, num(it, 1))) }
                    vm.referencies[a.id]?.let { r ->
                        Text(
                            r.duradaMin?.let { pluralStringResource(R.plurals.aparells_referencia_durada, r.mostres, num(r.w), r.mostres, num(it)) }
                                ?: pluralStringResource(R.plurals.aparells_referencia, r.mostres, num(r.w), r.mostres),
                            color = Primari, fontSize = 13.sp,
                        )
                        TextButton(onClick = { vm.tornaAEntrenar(a.id); obert = null }) { Text(stringResource(R.string.aparells_torna_entrenar)) }
                    }
                    if (a.eprel.isNotBlank()) Text("EPREL ${a.eprel}", color = Suau, fontSize = 12.sp)
                    if (a.font == Font.TIPIC) Text(stringResource(R.string.aparells_es_tipic), color = Desconegut, fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { obert = null }) { Text(stringResource(R.string.aparells_dacord)) } },
            dismissButton = {
                TextButton(onClick = { vm.esborraAparell(a.id); obert = null }) { Text(stringResource(R.string.aparells_esborra), color = Desconegut) }
            },
        )
    }
}
