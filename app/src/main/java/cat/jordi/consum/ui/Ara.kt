package cat.jordi.consum.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.R
import cat.jordi.consum.dades.Contracte
import cat.jordi.consum.dades.Franja
import cat.jordi.consum.dades.InfoPreu
import cat.jordi.consum.dades.NivellPotencia
import cat.jordi.consum.dades.nivellPotencia
import cat.jordi.consum.shelly.Origen

@Composable
fun PantallaAra(vm: ConsumViewModel) {
    val l = vm.lectura
    val teLinia = vm.config.teLinia
    val info = vm.infoPreu
    val preu = info?.ara?.preu
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            l?.origen == Origen.LOCAL -> Pastilla(stringResource(R.string.ara_origen_local), Primari, PrimariFluix, punt = Ok)
            l?.origen == Origen.NUVOL -> Pastilla(stringResource(R.string.ara_origen_nuvol), Nuvol, NuvolFluix, punt = Nuvol)
            !vm.haLlegit -> Pastilla(stringResource(R.string.ara_connectant), Suau, SempreFluix)
            vm.config.teNuvol -> Pastilla(stringResource(R.string.ara_sense_connexio), Desconegut, DesconegutFluix, punt = Desconegut)
            else -> Pastilla(stringResource(R.string.ara_falta_clau), Desconegut, DesconegutFluix, punt = Desconegut)
        }
        vm.versioNova?.let { v ->
            Targeta(color = PrimariFluix) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ara_versio_nova, v.versio), Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = { vm.pantalla = Pantalla.CONNECTA }) { Text(stringResource(R.string.ara_actualitza)) }
                }
            }
        }
        val limit = vm.config.tarifa.limitKw
        val nivell = nivellPotencia(l?.total?.w ?: 0.0, limit)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(l?.let { num(it.total.w) } ?: "—", fontSize = 56.sp, fontWeight = FontWeight.Light, color = colorNivell(nivell, Color.Unspecified))
            Text(" W", Modifier.padding(bottom = 10.dp), fontSize = 24.sp, color = Suau)
        }
        Text(
            if (l != null && preu != null) {
                stringResource(
                    if (vm.avuiAproximat) R.string.ara_resum_amb_preu_aprox else R.string.ara_resum_amb_preu,
                    euros(l.total.w / 1000 * preu), num(vm.avuiKwh, 2), euros(vm.avuiEuros),
                )
            } else {
                stringResource(if (vm.avuiAproximat) R.string.ara_resum_aprox else R.string.ara_resum, num(vm.avuiKwh, 2), euros(vm.avuiEuros))
            },
            fontSize = 14.sp, color = Suau,
        )

        if (l != null && limit != null) BarraPotencia(l.total.w, limit, nivell)
        if (l != null && limit != null && nivell == NivellPotencia.LIMIT) {
            Targeta(color = Color(0xFFFEF2F2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = PotenciaLimit, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (l.total.w >= limit * 1000) R.string.ara_limit_passat else R.string.ara_limit_a_prop),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PotenciaLimit,
                    )
                }
                Nota(stringResource(R.string.ara_limit_text), Modifier.padding(top = 4.dp))
            }
        }

        if (info != null) PastillaPreu(info) { vm.pantalla = Pantalla.PREU }

        Targeta {
            TitolTargeta(stringResource(R.string.ara_ultimes_24h))
            GraficaPotencia(vm.serie, teLinia, limit)
            when {
                vm.serie.isEmpty() ->
                    Nota(stringResource(if (l?.origen == Origen.NUVOL) R.string.ara_dades_nuvol else R.string.ara_dades_wifi))
                vm.serie.any { it.aprox } -> NotaAproximat(stringResource(R.string.ara_aproximat), vm.config.host.isNotBlank())
            }
        }

        if (info != null && info.tarifa.contracte != Contracte.FIX) {
            Targeta {
                TitolTargeta(stringResource(R.string.ara_mes_barat_avui))
                TiraPreus(info.avui, info.ara.ts)
                mesBarat(info)?.let { Nota(it, Modifier.padding(top = 4.dp)) }
            }
        }

        if (l != null) {
            Targeta {
                TitolTargeta(stringResource(R.string.ara_que_hi_ha_ences))
                val linia = if (teLinia) l.linia?.w ?: 0.0 else 0.0
                val resta = (l.total.w - linia).coerceAtLeast(0.0)
                val fons = vm.fonsW
                val sempre = if (fons != null) minOf(resta, fons) else 0.0
                fun perHora(w: Double) = if (preu != null) context.getString(R.string.ara_euros_hora, euros(w / 1000 * preu)) else "${num(w)} W"
                if (teLinia) {
                    FilaConsum(Icons.Outlined.Bolt, LiniaFluix, Linia, vm.config.nomLinia, stringResource(R.string.ara_linia_subtitol, num(linia)), perHora(linia), 3)
                }
                if (fons != null) {
                    FilaConsum(
                        Icons.Outlined.Power, SempreFluix, Sempre, stringResource(R.string.ara_sempre_ences),
                        stringResource(R.string.ara_sempre_subtitol, num(sempre)), perHora(sempre), 3,
                    )
                }
                // El que passa del fons, explicat amb els aparells après; el que queda, sense identificar
                val e = vm.explicador.explica(resta - sempre)
                e.aparells.forEach { (id, w) ->
                    val a = vm.aparells.firstOrNull { it.id == id } ?: return@forEach
                    val apresa = vm.referencies[id]?.apresa == true
                    FilaConsum(
                        icona(a.categoria), PrimariFluix, Primari, a.nom,
                        stringResource(if (apresa) R.string.ara_aparell_apres else R.string.ara_aparell_aprenent, num(w)), perHora(w), if (apresa) 3 else 2,
                    )
                }
                FilaConsum(
                    Icons.Outlined.QuestionMark, DesconegutFluix, Desconegut, stringResource(R.string.ara_sense_identificar),
                    stringResource(if (fons == null) R.string.ara_residu_sense_fons else R.string.ara_residu_entrena, num(e.residu)),
                    perHora(e.residu), 1,
                )
                Nota(stringResource(R.string.ara_detall, num(l.total.va), num(l.total.pf, 2), num(l.total.v, 1)))
            }
            AvisCar(l.total.w, info)
        }
        if (vm.errorSincronitzacio != null && vm.haLlegit && l == null) {
            Nota(stringResource(R.string.ara_comprova_wifi))
        }
    }
}

/** La part de la potència contractada que es fa servir ara, amb les marques del 80 i del 90 %. */
@Composable
private fun BarraPotencia(w: Double, limitKw: Double, nivell: NivellPotencia) {
    val part = (w / (limitKw * 1000)).toFloat()
    Column {
        Text(
            stringResource(R.string.ara_part_contractada, num(part * 100.0), num(limitKw, 2)),
            fontSize = 14.sp, color = if (nivell == NivellPotencia.NORMAL) Suau else colorNivell(nivell, Suau),
        )
        Canvas(Modifier.fillMaxWidth().height(14.dp).padding(top = 6.dp)) {
            val alt = size.height
            drawRoundRect(Separador, Offset(0f, 0f), Size(size.width, alt), CornerRadius(alt / 2))
            drawRoundRect(colorNivell(nivell, Primari), Offset(0f, 0f), Size(size.width * part.coerceIn(0f, 1f), alt), CornerRadius(alt / 2))
            for ((marca, color) in listOf(0.8f to PotenciaAlta, 0.9f to PotenciaLimit)) {
                drawLine(color, Offset(size.width * marca, -3.dp.toPx()), Offset(size.width * marca, alt + 3.dp.toPx()), 2.dp.toPx())
            }
        }
    }
}

/** La franja d'ara, el preu del kWh i fins quan dura. Tocant-la s'obre el preu de la llum. */
@Composable
private fun PastillaPreu(info: InfoPreu, obre: () -> Unit) {
    val h = info.ara
    val (color, fons) = when (h.franja) {
        Franja.PUNTA -> PuntaColor to PuntaFluix
        Franja.VALL, Franja.DAL -> VallColor to VallFluix
        Franja.PLA, Franja.NORMAAL -> Desconegut to DesconegutFluix
        Franja.MERCAT -> colorPreu(h, info.avui).let { it to it.copy(alpha = 0.14f) }
        Franja.UNICA -> Primari to PrimariFluix
    }
    val franja = stringResource(h.franja.nomId)
    val nom = when (h.franja) {
        Franja.UNICA, Franja.MERCAT -> franja
        else -> info.canviaA?.let { stringResource(R.string.ara_franja_fins_a, franja, it) } ?: franja
    }
    val text = h.preu?.let { stringResource(R.string.ara_pastilla_preu, nom, num(it, 3)) } ?: stringResource(R.string.ara_pastilla_sense_preu, nom)
    Row(Modifier.clickable(onClick = obre)) { Pastilla(text, color, fons, punt = color) }
}

/** L'hora més barata de les properes 24, si val la pena esperar-la. */
@Composable
private fun mesBarat(info: InfoPreu): String? {
    val ara = info.ara.preu ?: return null
    val barata = info.properes.filter { it.preu != null }.minByOrNull { it.preu!! } ?: return null
    if (barata.preu!! > ara * 0.9) return stringResource(R.string.ara_ja_barat)
    return stringResource(R.string.ara_mes_barata, barata.hora, num(barata.preu, 3), num((1 - barata.preu / ara) * 100))
}

/** Si la casa gasta molt en una hora cara, ho diu i proposa quan seria més barat. */
@Composable
private fun AvisCar(w: Double, info: InfoPreu?) {
    val ara = info?.ara?.preu ?: return
    val barata = info.properes.filter { it.preu != null }.minByOrNull { it.preu!! } ?: return
    if (w < 1500 || barata.preu!! > ara * 0.85) return
    Targeta(color = DesconegutFluix) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, null, tint = Desconegut, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ara_avis_car_titol), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            stringResource(R.string.ara_avis_car_text, num(w), num(ara, 3), barata.hora, euros(w / 1000 * barata.preu), euros(w / 1000 * ara)),
            Modifier.padding(top = 4.dp), fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF1C2421),
        )
    }
}
