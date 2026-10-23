package cat.jordi.consum.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.dades.Contracte
import cat.jordi.consum.dades.Franja
import cat.jordi.consum.dades.InfoPreu
import cat.jordi.consum.shelly.Origen

@Composable
fun PantallaAra(vm: ConsumViewModel) {
    val l = vm.lectura
    val teLinia = vm.config.teLinia
    val info = vm.infoPreu
    val preu = info?.ara?.preu
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            l?.origen == Origen.LOCAL -> Pastilla("A casa · directe, cada segon", Primari, PrimariFluix, punt = Ok)
            l?.origen == Origen.NUVOL -> Pastilla("Des de fora · núvol de Shelly", Primari, PrimariFluix, punt = Ok)
            !vm.haLlegit -> Pastilla("Connectant…", Suau, SempreFluix)
            vm.config.teNuvol -> Pastilla("No arribo al Shelly ni al núvol", Desconegut, DesconegutFluix, punt = Desconegut)
            else -> Pastilla("Fora de casa: posa la clau del núvol per veure-ho", Desconegut, DesconegutFluix, punt = Desconegut)
        }
        vm.versioNova?.let { v ->
            Targeta(color = PrimariFluix) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hi ha una versió nova de l'app (${v.versio}).", Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = { vm.pantalla = Pantalla.CONNECTA }) { Text("Actualitza") }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(l?.let { num(it.total.w) } ?: "—", fontSize = 56.sp, fontWeight = FontWeight.Light)
            Text(" W", Modifier.padding(bottom = 10.dp), fontSize = 24.sp, color = Suau)
        }
        val araEuros = if (l != null && preu != null) "Ara gastes ${euros(l.total.w / 1000 * preu)} l'hora · " else ""
        Text("${araEuros}avui ${num(vm.avuiKwh, 2)} kWh, ${euros(vm.avuiEuros)}", fontSize = 14.sp, color = Suau)

        if (info != null) PastillaPreu(info) { vm.pantalla = Pantalla.PREU }

        Targeta {
            TitolTargeta("Últimes 24 hores")
            GraficaPotencia(vm.serie, teLinia)
            if (vm.serie.isEmpty()) Nota("Les dades es recullen del Shelly quan el mòbil és a la wifi de casa.")
        }

        if (info != null && info.tarifa.contracte != Contracte.FIX) {
            Targeta {
                TitolTargeta("Quan és més barat avui")
                TiraPreus(info.avui, info.ara.ts)
                mesBarat(info)?.let { Nota(it, Modifier.padding(top = 4.dp)) }
            }
        }

        if (l != null) {
            Targeta {
                TitolTargeta("Què hi ha encès ara")
                val linia = if (teLinia) l.linia?.w ?: 0.0 else 0.0
                val resta = (l.total.w - linia).coerceAtLeast(0.0)
                val fons = vm.fonsW
                val sempre = if (fons != null) minOf(resta, fons) else 0.0
                fun perHora(w: Double) = if (preu != null) "${euros(w / 1000 * preu)}/h" else "${num(w)} W"
                if (teLinia) {
                    FilaConsum(Icons.Outlined.Bolt, LiniaFluix, Linia, vm.config.nomLinia, "${num(linia)} W · segona pinça, mesurat", perHora(linia), 3)
                }
                if (fons != null) {
                    FilaConsum(Icons.Outlined.Power, SempreFluix, Sempre, "Sempre encès", "${num(sempre)} W · nevera, router, espera…", perHora(sempre), 3)
                }
                // El que passa del fons, explicat amb els aparells après; el que queda, sense identificar
                val e = vm.explicador.explica(resta - sempre)
                e.aparells.forEach { (id, w) ->
                    val a = vm.aparells.firstOrNull { it.id == id } ?: return@forEach
                    val apresa = vm.referencies[id]?.apresa == true
                    FilaConsum(icona(a.categoria), PrimariFluix, Primari, a.nom, "${num(w)} W · " + if (apresa) "après" else "aprenent", perHora(w), if (apresa) 3 else 2)
                }
                FilaConsum(
                    Icons.Outlined.QuestionMark, DesconegutFluix, Desconegut, "Sense identificar",
                    "${num(e.residu)} W · " + if (fons == null) "cal un dia de dades per saber el fons" else "entrena l'app",
                    perHora(e.residu), 1,
                )
                Nota("Ara: ${num(l.total.va)} VA · factor de potència ${num(l.total.pf, 2)} · ${num(l.total.v, 1)} V")
            }
            AvisCar(l.total.w, info)
        }
        if (vm.errorSincronitzacio != null && vm.haLlegit && l == null) {
            Nota("Comprova que el mòbil és a la wifi de casa i que l'adreça del Shelly és bona (Configuració).")
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
    val nom = when (h.franja) {
        Franja.UNICA -> "Preu únic"
        Franja.MERCAT -> "Preu de mercat"
        else -> h.franja.nom + (info.canviaA?.let { " fins a les $it h" } ?: "")
    }
    val preu = h.preu?.let { " · el kWh a ${num(it, 3)} €" } ?: " · encara sense preu"
    Row(Modifier.clickable(onClick = obre)) { Pastilla(nom + preu, color, fons, punt = color) }
}

/** L'hora més barata de les properes 24, si val la pena esperar-la. */
private fun mesBarat(info: InfoPreu): String? {
    val ara = info.ara.preu ?: return null
    val barata = info.properes.filter { it.preu != null }.minByOrNull { it.preu!! } ?: return null
    if (barata.preu!! > ara * 0.9) return "Ara ja és de les hores barates."
    return "La més barata de les properes 24 h, a les ${barata.hora} h: ${num(barata.preu, 3)} €/kWh (${num((1 - barata.preu / ara) * 100)} % menys)."
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
            Text("Molt consum en una hora cara", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Ara gastes ${num(w)} W a ${num(ara, 3)} €/kWh. Si ho poguessis fer a les ${barata.hora} h, " +
                "cada hora així costaria ${euros(w / 1000 * barata.preu)} en lloc de ${euros(w / 1000 * ara)}.",
            Modifier.padding(top = 4.dp), fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF1C2421),
        )
    }
}
