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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            Text("Aquest mes, fins ara", fontSize = 14.sp, color = Suau)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(euros(mes.euros.total), fontSize = 40.sp, fontWeight = FontWeight.Light)
                Text("  ${num(mes.total)} kWh", Modifier.padding(bottom = 7.dp), fontSize = 14.sp, color = Suau)
            }
            Text("${euros(mes.euros.consum)} de consum + ${euros(mes.euros.fixos)} de fixos", fontSize = 13.sp, color = Suau)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Punts(3); Text("mesurat o après", fontSize = 11.sp, color = Suau)
            Punts(2); Text("etiqueta", fontSize = 11.sp, color = Suau)
            Punts(1); Text("típic o sense identificar", fontSize = 11.sp, color = Suau)
        }

        val consells = buildList {
            vm.fonsW?.takeIf { it >= 1 }?.let { w ->
                add("Sempre encès: ${num(w)} W dia i nit són ${num(w * 24 * 365 / 1000 * preu)} € l'any. Mira quins aparells es queden en espera.")
            }
            val perCompletar = vm.aparells.count { it.perCompletar }
            vm.consellLinia?.let { c ->
                add("${vm.config.nomLinia}: el ${c.percentCar} % del consum és en hores cares. A les hores barates, aquest mes hauria costat uns ${euros(c.estalviMes)} menys.")
            }
            if (perCompletar > 0) add("$perCompletar aparells tenen un valor típic. Escaneja'n l'etiqueta perquè el número sigui més segur.")
        }
        if (consells.isNotEmpty()) {
            Targeta(color = DesconegutFluix) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = Desconegut, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    TitolTargeta("Què pots fer", Modifier.padding(top = 6.dp))
                }
                consells.forEach { Text(it, Modifier.padding(top = 2.dp), fontSize = 13.sp, lineHeight = 18.sp) }
            }
        }

        if (mes != null) {
            Targeta {
                TitolTargeta("Mesurat aquest mes")
                if (vm.config.teLinia) {
                    FilaConsum(Icons.Outlined.Bolt, LiniaFluix, Linia, vm.config.nomLinia, "${num(mes.linia)} kWh · segona pinça", euros(mes.euros.linia), 3)
                }
                FilaConsum(Icons.Outlined.Power, SempreFluix, Sempre, "Sempre encès", "${num(mes.sempre)} kWh", euros(mes.euros.sempre), 3)
                // Els aparells après es queden la seva part del que no és fons; la resta, sense identificar
                val rep = vm.repartiment
                vm.aparells.filter { (rep?.kwh?.get(it.id) ?: 0.0) > 0.0 }.sortedByDescending { rep!!.euros[it.id] }.forEach { a ->
                    val apresa = vm.referencies[a.id]?.apresa == true
                    FilaConsum(
                        icona(a.categoria), PrimariFluix, Primari, a.nom,
                        "${num(rep!!.kwh.getValue(a.id), 1)} kWh · ${if (apresa) "après" else "aprenent"} · l'etiqueta diu ${num(a.kwhAny / 12)} kWh/mes",
                        euros(rep.euros.getValue(a.id)), if (apresa) 3 else 2, modifier = Modifier.clickable { obert = a },
                    )
                }
                FilaConsum(
                    Icons.Outlined.QuestionMark, DesconegutFluix, Desconegut, "Sense identificar",
                    "${num(rep?.residuKwh ?: mes.desconegut)} kWh · entrena per separar-ho", euros(rep?.residuEuros ?: mes.euros.desconegut), 1,
                )
                FilaConsum(
                    Icons.Outlined.Euro, SempreFluix, Sempre, "Costos fixos",
                    "${quinsFixos(vm.config.tarifa)} · no depenen de l'ús", euros(mes.euros.fixos),
                )
            }
        }

        if (vm.aparells.isEmpty()) {
            Targeta {
                TitolTargeta("Encara no hi ha cap aparell")
                Nota("Comença pel qüestionari: anirem estança per estança perquè no te'n deixis cap.")
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { vm.pantalla = Pantalla.QUESTIONARI }) {
                    Icon(Icons.Outlined.Checklist, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Revisem la casa")
                }
            }
        } else {
            // Els que ja tenen referència surten amb el que gasten de debò, a «Mesurat aquest mes»
            val perEntrenar = vm.aparells.filter { (vm.repartiment?.kwh?.get(it.id) ?: 0.0) == 0.0 }
            if (perEntrenar.isNotEmpty()) Targeta {
                TitolTargeta("Els teus aparells, segons l'etiqueta")
                Nota(
                    "Encara no entrenats: és el que haurien de gastar, no el que gasten, a ${num(preu, 3)} €/kWh de mitjana. " +
                        "Sumen ${euros(perEntrenar.sumOf { it.kwhAny } / 12 * preu)} al mes."
                )
                perEntrenar.forEach { a ->
                    val mesurat = mes != null && vm.config.teLinia && a.nom.equals(vm.config.nomLinia, ignoreCase = true)
                    if (mesurat) {
                        FilaConsum(
                            icona(a.categoria), LiniaFluix, Linia, a.nom,
                            "mesurat a la pinça 2 · l'etiqueta diu ${num(a.kwhAny)} kWh/any", euros(mes!!.euros.linia), 3,
                            modifier = Modifier.clickable { obert = a },
                        )
                    } else {
                        FilaConsum(
                            icona(a.categoria), PrimariFluix, Primari, a.nom,
                            "${num(a.kwhAny)} kWh/any · ${a.font.nom}", "${euros(a.kwhAny / 12 * preu)}/mes", a.font.punts,
                            subtitolColor = if (a.perCompletar) Desconegut else Suau,
                            modifier = Modifier.clickable { obert = a },
                        )
                    }
                }
            }
            TextButton(onClick = { vm.pantalla = Pantalla.QUESTIONARI }) { Text("Torna a fer el qüestionari") }
        }
        Spacer(Modifier.height(80.dp))
    }

    obert?.let { a ->
        AlertDialog(
            onDismissRequest = { obert = null },
            title = { Text(a.nom) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${a.categoria.nom} · ${a.font.nom}")
                    if (a.marca.isNotBlank()) Text("${a.marca} ${a.model}")
                    if (a.classe.isNotBlank()) Text("Classe energètica ${a.classe}")
                    Text("${num(a.kwhAny)} kWh l'any ≈ ${num(a.kwhAny * preu)} € l'any")
                    a.esperaW?.let { Text("En espera: ${num(it, 1)} W") }
                    vm.referencies[a.id]?.let { r ->
                        Text(
                            "Referència apresa: ${num(r.w)} W, de ${r.mostres} mostres" + (r.duradaMin?.let { " · sol estar encès ${num(it)} min" } ?: ""),
                            color = Primari, fontSize = 13.sp,
                        )
                        TextButton(onClick = { vm.tornaAEntrenar(a.id); obert = null }) { Text("Torna a entrenar-lo") }
                    }
                    if (a.eprel.isNotBlank()) Text("EPREL ${a.eprel}", color = Suau, fontSize = 12.sp)
                    if (a.font == Font.TIPIC) Text("És un valor típic: escaneja l'etiqueta per afinar-lo.", color = Desconegut, fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { obert = null }) { Text("D'acord") } },
            dismissButton = {
                TextButton(onClick = { vm.esborraAparell(a.id); obert = null }) { Text("Esborra'l", color = Desconegut) }
            },
        )
    }
}
