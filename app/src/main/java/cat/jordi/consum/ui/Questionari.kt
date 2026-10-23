@file:OptIn(ExperimentalLayoutApi::class)

package cat.jordi.consum.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.dades.Aparell
import cat.jordi.consum.dades.Categoria
import cat.jordi.consum.dades.Font

/** Una pregunta del qüestionari: si [unica], només s'hi pot triar una resposta. Null vol dir que no gasta llum. */
private class Pregunta(val text: String, val opcions: List<Pair<String, Categoria?>>, val unica: Boolean = false)

private class Estanca(val nom: String, val preguntes: List<Pregunta>)

private val ESTANCES = listOf(
    Estanca(
        "Calefacció i aigua calenta", listOf(
            Pregunta(
                "Com s'escalfa la casa?", listOf(
                    "Caldera de gas" to Categoria.CALDERA, "Bomba de calor" to Categoria.BOMBA_CALOR,
                    "Estufes o radiadors elèctrics" to Categoria.ESTUFA, "Calefacció de barri" to null, "No n'hi ha" to null,
                ), unica = true,
            ),
            Pregunta(
                "I l'aigua calenta?", listOf(
                    "La mateixa caldera o bomba" to null, "Termo elèctric" to Categoria.TERMO,
                    "Calefacció de barri" to null, "Escalfador de gas" to null,
                ), unica = true,
            ),
            Pregunta("Hi ha aire condicionat?", listOf("Aire condicionat" to Categoria.AIRE)),
        )
    ),
    Estanca(
        "Cuina", listOf(
            Pregunta(
                "Què tens a la cuina?", listOf(
                    "Nevera" to Categoria.NEVERA, "Congelador" to Categoria.CONGELADOR, "Forn" to Categoria.FORN,
                    "Microones" to Categoria.MICROONES, "Rentaplats" to Categoria.RENTAPLATS, "Campana" to Categoria.CAMPANA,
                    "Cafetera" to Categoria.CAFETERA, "Bullidor" to Categoria.BULLIDOR, "Torradora" to Categoria.TORRADORA,
                    "Robot de cuina" to Categoria.ROBOT,
                ),
            ),
            Pregunta(
                "La placa és…", listOf(
                    "Inducció" to Categoria.PLACA, "Vitroceràmica" to Categoria.PLACA, "Gas" to null,
                ), unica = true,
            ),
        )
    ),
    Estanca(
        "Rentador", listOf(
            Pregunta(
                "Què hi ha al rentador?", listOf(
                    "Rentadora" to Categoria.RENTADORA, "Assecadora" to Categoria.ASSECADORA,
                    "Planxa" to Categoria.PLANXA, "Un altre congelador" to Categoria.CONGELADOR,
                ),
            ),
        )
    ),
    Estanca(
        "Sala", listOf(
            Pregunta(
                "Què hi ha a la sala?", listOf(
                    "Televisor" to Categoria.TELEVISOR, "Consola" to Categoria.CONSOLA, "Router" to Categoria.ROUTER,
                    "Llums" to Categoria.LLUMS, "Altaveus" to Categoria.ALTRES,
                ),
            ),
        )
    ),
    Estanca(
        "Habitacions i despatx", listOf(
            Pregunta(
                "Què hi ha a les habitacions i al despatx?", listOf(
                    "Ordinador" to Categoria.ORDINADOR, "Un altre televisor" to Categoria.TELEVISOR,
                    "Estufa elèctrica" to Categoria.ESTUFA, "Impressora" to Categoria.ALTRES,
                ),
            ),
        )
    ),
    Estanca(
        "Bany", listOf(
            Pregunta(
                "Què hi ha al bany?", listOf(
                    "Assecador de cabells" to Categoria.ASSECADOR_CABELLS, "Tovalloler elèctric" to Categoria.TOVALLOLER,
                    "Estufa de bany" to Categoria.ESTUFA,
                ),
            ),
        )
    ),
    Estanca(
        "Exterior, garatge i cotxe", listOf(
            Pregunta(
                "I a fora?", listOf(
                    "Cotxe elèctric" to Categoria.COTXE, "Bomba d'aigua" to Categoria.BOMBA_AIGUA,
                    "Depuradora de piscina" to Categoria.DEPURADORA, "Eines" to Categoria.ALTRES,
                ),
            ),
        )
    ),
)

@Composable
fun PantallaQuestionari(vm: ConsumViewModel) {
    var pas by remember { mutableIntStateOf(0) }
    // La clau és "estança|pregunta|opció"
    val triat = remember { mutableStateMapOf<String, Boolean>() }
    val estanca = ESTANCES[pas]

    fun clau(p: Pregunta, opcio: String) = "${estanca.nom}|${p.text}|$opcio"

    fun acaba() {
        val existents = vm.aparells.map { it.nom.lowercase() }.toMutableSet()
        val nous = mutableListOf<Aparell>()
        val bombaJa = mutableSetOf<Categoria>()
        for (e in ESTANCES) for (p in e.preguntes) for ((opcio, cat) in p.opcions) {
            if (cat == null || triat["${e.nom}|${p.text}|$opcio"] != true) continue
            // La placa: el nom és el tipus; la bomba de calor no es compta dues vegades
            val nom = if (cat == Categoria.PLACA) "Placa ($opcio)".replace("(I", "(i").replace("(V", "(v") else opcio
            if (cat == Categoria.BOMBA_CALOR && !bombaJa.add(cat)) continue
            if (nom.lowercase() in existents) continue
            existents += nom.lowercase()
            nous += Aparell(nom = nom, categoria = cat, font = Font.TIPIC, kwhAny = cat.kwhAnyTipic)
        }
        vm.desaAparells(nous)
        vm.pantalla = Pantalla.PRINCIPAL
        vm.pestanya = Pestanya.APARELLS
    }

    val desplaçament = rememberScrollState()
    LaunchedEffect(pas) { desplaçament.scrollTo(0) }

    // El contingut es desplaça; els botons queden fixos a baix.
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(desplaçament).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LinearProgressIndicator(progress = { (pas + 0.5f) / ESTANCES.size }, Modifier.fillMaxWidth())
            Text("Estança ${pas + 1} de ${ESTANCES.size} · ${estanca.nom}", fontSize = 14.sp, color = Suau)
            for (p in estanca.preguntes) {
                Targeta {
                    TitolTargeta(p.text)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((opcio, _) in p.opcions) {
                            val k = clau(p, opcio)
                            val si = triat[k] == true
                            FilterChip(
                                selected = si,
                                onClick = {
                                    if (p.unica) p.opcions.forEach { (o, _) -> triat[clau(p, o)] = false }
                                    triat[k] = !si
                                },
                                label = { Text(opcio) },
                                leadingIcon = if (si) ({ Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) }) else null,
                            )
                        }
                    }
                }
            }
            Targeta {
                ESTANCES.forEachIndexed { i, e ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (i < pas) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                            tint = if (i < pas) Ok else if (i == pas) Primari else Suau, modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "  ${e.nom}", fontSize = 13.sp, color = if (i == pas) Primari else Suau,
                            fontWeight = if (i == pas) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            Nota("Cada aparell que marquis queda a la llista amb un valor típic, i en pots escanejar l'etiqueta quan vulguis.")
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pas > 0) OutlinedButton(onClick = { pas-- }) { Text("Enrere") }
            Button(
                onClick = { if (pas < ESTANCES.lastIndex) pas++ else acaba() },
                Modifier.weight(1f),
            ) { Text(if (pas < ESTANCES.lastIndex) "Següent: ${ESTANCES[pas + 1].nom.lowercase()}" else "Fet") }
        }
    }
}
