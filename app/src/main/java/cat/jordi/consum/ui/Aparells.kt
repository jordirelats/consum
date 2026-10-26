package cat.jordi.consum.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.FilterChip
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
import cat.jordi.consum.dades.Funcionament

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaAparells(vm: ConsumViewModel) {
    val mes = vm.resumMes
    // El preu mitjà que ha pagat la casa aquest mes; si encara no hi ha dades, el d'ara
    val preu = mes?.preuMitja ?: vm.infoPreu?.ara?.preu ?: vm.config.tarifa.preuFix
    var obert by remember { mutableStateOf<Aparell?>(null) }
    // L'aparell que es vol esborrar (true) o tornar a entrenar (false), pendent de confirmar
    var perConfirmar by remember { mutableStateOf<Pair<Aparell, Boolean>?>(null) }

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

        // Si no hi cap en una línia (la lletra gran, el neerlandès), cada element salta sencer a la següent
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for ((punts, text) in listOf(3 to R.string.aparells_llegenda_mesurat, 2 to R.string.aparells_llegenda_etiqueta, 1 to R.string.aparells_llegenda_tipic)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Punts(punts)
                    Text(stringResource(text), fontSize = 11.sp, color = Suau)
                }
            }
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
                // Els aparells de la línia surten a la targeta de la línia
                vm.aparells.filter { !aLaLinia(vm, it) && (rep?.kwh?.get(it.id) ?: 0.0) > 0.0 }.sortedByDescending { rep!!.euros[it.id] }.forEach { a ->
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
            TargetaLinia(vm) { obert = it }
            TargetaSempreEnces(vm, preu) { obert = it }
            TargetaSols(vm) { obert = it }
            // Els que ja tenen referència surten amb el que gasten de debò, a «Mesurat aquest mes»; els que estan
            // sempre encesos, s'engeguen sols o són a la línia, a les seves targetes
            val perEntrenar = vm.aparells.filter { !aLaLinia(vm, it) && (vm.repartiment?.kwh?.get(it.id) ?: 0.0) == 0.0 && it.funciona == Funcionament.JO }
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
                    (vm.referencies[a.id] ?: vm.referenciesLinia[a.id])?.let { r ->
                        Text(
                            r.duradaMin?.let { pluralStringResource(R.plurals.aparells_referencia_durada, r.mostres, num(r.w), r.mostres, num(it)) }
                                ?: pluralStringResource(R.plurals.aparells_referencia, r.mostres, num(r.w), r.mostres),
                            color = Primari, fontSize = 13.sp,
                        )
                        // Com és (maqueta 12): constant, polsa, arrencada suau o amb un pic
                        r.forma?.let { f ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DibuixForma(f, Primari)
                                Text("  " + stringResource(f.textId), color = Primari, fontSize = 13.sp)
                            }
                        }
                        TextButton(onClick = { perConfirmar = a to false; obert = null }) { Text(stringResource(R.string.aparells_torna_entrenar)) }
                    }
                    if (a.eprel.isNotBlank()) Text("EPREL ${a.eprel}", color = Suau, fontSize = 12.sp)
                    // Com funciona: d'això depèn si cal entrenar-lo
                    Text(stringResource(R.string.aparells_com_funciona), Modifier.padding(top = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Funcionament.entries.forEach { f ->
                            FilterChip(a.funciona == f, {
                                // Un aparell sempre encès va dins del fons de tota la casa: no pot ser a la línia
                                val nou = a.copy(funciona = f, linia = a.linia && f != Funcionament.SEMPRE)
                                vm.desaAparells(listOf(nou))
                                obert = nou
                            }, label = { Text(stringResource(f.nomId)) })
                        }
                    }
                    // A la línia de la pinça 2 (maqueta 12): s'aprèn d'allà
                    if (vm.config.teLinia && a.funciona != Funcionament.SEMPRE && !a.nom.equals(vm.config.nomLinia, ignoreCase = true)) {
                        FilterChip(a.linia, {
                            vm.posaALinia(a, !a.linia)
                            obert = a.copy(linia = !a.linia)
                        }, label = { Text(stringResource(R.string.aparells_a_la_linia, vm.config.nomLinia)) })
                    }
                    if (a.font == Font.TIPIC) Text(stringResource(R.string.aparells_es_tipic), color = DesconegutText, fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { obert = null }) { Text(stringResource(R.string.aparells_dacord)) } },
            dismissButton = {
                TextButton(onClick = { perConfirmar = a to true; obert = null }) { Text(stringResource(R.string.aparells_esborra), color = PuntaText) }
            },
        )
    }

    perConfirmar?.let { (a, esborra) ->
        AlertDialog(
            onDismissRequest = { perConfirmar = null },
            text = { Text(stringResource(if (esborra) R.string.aparells_confirma_esborra else R.string.aparells_confirma_entrena, a.nom)) },
            confirmButton = {
                TextButton(onClick = {
                    if (esborra) vm.esborraAparell(a.id) else vm.tornaAEntrenar(a.id)
                    perConfirmar = null
                }) {
                    Text(stringResource(if (esborra) R.string.aparells_esborra else R.string.aparells_torna_entrenar), color = PuntaText)
                }
            },
            dismissButton = { TextButton(onClick = { perConfirmar = null }) { Text(stringResource(R.string.escaneig_cancella)) } },
        )
    }
}

/**
 * El que la casa gasta sempre (el fons): els aparells que hi són sempre encesos, amb la seva potència, i la resta
 * («altres en espera»). Només si se'n sap el fons i hi ha algun aparell sempre encès.
 */
@Composable
private fun TargetaSempreEnces(vm: ConsumViewModel, preu: Double, obre: (Aparell) -> Unit) {
    val fons = vm.fonsW ?: return
    val sempre = vm.aparells.filter { it.funciona == Funcionament.SEMPRE }
    if (sempre.isEmpty()) return
    Targeta {
        FilaConsum(
            Icons.Outlined.Power, SempreFluix, Sempre, stringResource(R.string.aparells_titol_sempre, num(fons)),
            stringResource(R.string.aparells_sempre_subtitol, euros(fons * 8760 / 1000 / 12 * preu)), "",
        )
        sempre.forEach { a ->
            FilaConsum(
                icona(a.categoria), SempreFluix, Sempre, a.nom, stringResource(a.font.nomId), "${num(a.wTipic)} W",
                modifier = Modifier.clickable { obre(a) },
            )
        }
        val resta = fons - sempre.sumOf { it.wTipic }
        if (resta >= 1) {
            FilaConsum(
                Icons.Outlined.Power, SempreFluix, Sempre, stringResource(R.string.aparells_altres_espera),
                stringResource(R.string.aparells_altres_espera_text), "${num(resta)} W",
            )
        }
    }
}

/**
 * Els aparells que s'engeguen sols: com els reconeix l'app (el compressor trobat a les dades, l'interval après d'una
 * resposta) o que encara no els ha reconegut. No cal entrenar-los i no se n'avisa.
 */
@Composable
private fun TargetaSols(vm: ConsumViewModel, obre: (Aparell) -> Unit) {
    val sols = vm.aparells.filter { it.funciona == Funcionament.SOL && !aLaLinia(vm, it) }
    if (sols.isEmpty()) return
    Targeta {
        TitolTargeta(stringResource(R.string.aparells_titol_sols))
        Nota(stringResource(R.string.aparells_sols_subtitol))
        sols.forEach { a ->
            val r = vm.referencies[a.id]
            val (com, valor) = when {
                r == null -> stringResource(R.string.aparells_sol_no_encara) to "—"
                r.automatica -> stringResource(R.string.aparells_sol_trobat) to "${num(r.w)} W"
                r.wMin != null -> stringResource(R.string.aparells_sol_interval) to "${num(r.min)}–${num(r.max)} W"
                else -> stringResource(R.string.aparells_sol_apres) to "${num(r.w)} W"
            }
            FilaConsum(
                icona(a.categoria), PrimariFluix, Primari, a.nom, listOfNotNull(com, textFirma(r)).joinToString(" · "), valor, if (r?.apresa == true) 3 else null,
                subtitolColor = if (r == null) Desconegut else Suau, modifier = Modifier.clickable { obre(a) }, liniesSubtitol = 2,
            )
        }
    }
}

/** Si l'aparell és a la línia de la pinça 2 (i no és la línia mateixa). */
private fun aLaLinia(vm: ConsumViewModel, a: Aparell) =
    vm.config.teLinia && a.linia && !a.nom.equals(vm.config.nomLinia, ignoreCase = true)

/**
 * La línia de la pinça 2 (maqueta 12): quins aparells hi ha connectats i què n'ha après l'app. Els aparells de la línia es
 * mesuren aquí: l'app n'aprèn la firma sola i ja no els busca a la resta de la casa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetaLinia(vm: ConsumViewModel, obre: (Aparell) -> Unit) {
    if (!vm.config.teLinia) return
    val nom = vm.config.nomLinia
    val candidats = vm.aparells.filter { it.funciona != Funcionament.SEMPRE && !it.nom.equals(nom, ignoreCase = true) }
    if (candidats.isEmpty()) return
    val dins = candidats.filter { it.linia }
    var tots by remember { mutableStateOf(false) }
    val rep = vm.repartiment
    Targeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Bolt, null, tint = Linia, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            TitolTargeta(stringResource(R.string.linia_titol, nom), Modifier.padding(top = 6.dp))
        }
        Text(stringResource(R.string.linia_que_hi_ha), Modifier.padding(top = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        // Primer els que ja hi són; la resta, plegats si són molts
        val ordenats = dins + candidats.filterNot { it.linia }
        val visibles = if (tots || ordenats.size <= 6) ordenats else ordenats.take(maxOf(6, dins.size))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            visibles.forEach { a ->
                FilterChip(a.linia, { vm.posaALinia(a, !a.linia) }, label = { Text(a.nom) })
            }
            if (visibles.size < ordenats.size) {
                FilterChip(false, { tots = true }, label = { Text(stringResource(R.string.linia_mes)) })
            }
        }
        Nota(stringResource(R.string.linia_nota))
        dins.forEach { a ->
            val r = vm.referenciesLinia[a.id]
            val kwh = rep?.kwh?.get(a.id) ?: 0.0
            val sub = when {
                r == null -> stringResource(R.string.linia_encara_no)
                else -> listOfNotNull(
                    pluralStringResource(R.plurals.linia_apres, r.mostres, r.mostres, if (r.wMin != null) "${num(r.min)}–${num(r.max)}" else num(r.w)),
                    textFirma(r),
                ).joinToString(" · ")
            }
            FilaConsum(
                icona(a.categoria), LiniaFluix, Linia, a.nom, sub, "${num(kwh, 1)} kWh", if (r?.apresa == true) 3 else null,
                subtitolColor = if (r == null) Desconegut else Suau, modifier = Modifier.clickable { obre(a) }, liniesSubtitol = 3,
            )
        }
        if (dins.isNotEmpty() && (rep?.liniaAltresKwh ?: 0.0) >= 0.05) {
            FilaConsum(
                Icons.Outlined.QuestionMark, LiniaFluix, Linia, stringResource(R.string.linia_altres),
                stringResource(R.string.linia_altres_text), "${num(rep!!.liniaAltresKwh, 1)} kWh",
            )
        }
    }
}
