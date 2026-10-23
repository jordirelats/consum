package cat.jordi.consum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.dades.Contracte
import cat.jordi.consum.dades.Factura
import cat.jordi.consum.dades.LecturaFactura
import cat.jordi.consum.dades.HoraPreu
import cat.jordi.consum.dades.Pais
import cat.jordi.consum.dades.Tarifa
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** Una línia que resumeix la tarifa, per a la configuració. */
fun resumTarifa(t: Tarifa): String = when (t.pais) {
    Pais.ES -> "Espanya · " + when (t.contracte) {
        Contracte.FIX -> "preu fix"
        Contracte.FRANGES -> "3 franges"
        Contracte.DINAMIC -> "PVPC"
    } + " · ${num(t.potenciaKw, 1)} kW"
    Pais.NL -> "Països Baixos · " + when (t.contracte) {
        Contracte.FIX -> "preu fix"
        Contracte.FRANGES -> "normaal i dal"
        Contracte.DINAMIC -> "dinàmic"
    }
    Pais.ALTRE -> "Preu únic · ${num(t.preuFix, 3)} €/kWh"
}

/** Què hi ha dins dels costos fixos, segons el país. */
fun quinsFixos(t: Tarifa) = when (t.pais) {
    Pais.ES -> "potència, comptador i bo social"
    Pais.NL -> "quotes i descompte d'impostos"
    Pais.ALTRE -> "quotes fixes"
}

private fun noms(pais: Pais) = when (pais) {
    Pais.ES -> listOf("Fix", "3 franges", "PVPC")
    Pais.NL -> listOf("Fix", "Normaal i dal", "Dinàmic")
    Pais.ALTRE -> listOf("Fix")
}

/** Un número per editar: coma decimal, sense separador de milers ni zeros de més. */
private fun textNum(x: Double, decimals: Int = 6): String =
    BigDecimal(x).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

/** Un camp numèric. Només avisa quan el text és un número vàlid. */
@Composable
private fun CampNum(etiqueta: String, valor: Double, unitat: String, ajuda: String? = null, enCanviar: (Double) -> Unit) {
    var text by remember { mutableStateOf(textNum(valor)) }
    val bo = text.replace(',', '.').toDoubleOrNull() != null
    OutlinedTextField(
        text, { nou -> text = nou; nou.replace(',', '.').toDoubleOrNull()?.let(enCanviar) },
        Modifier.fillMaxWidth(), label = { Text(etiqueta) }, singleLine = true, isError = !bo,
        suffix = { Text(unitat) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = ajuda?.let { { Text(it) } },
    )
}

/** Les 24 hores d'avui amb les franges d'una tarifa que encara no s'ha desat. */
private fun franges(t: Tarifa): List<HoraPreu> {
    val zona = ZoneId.systemDefault()
    val inici = LocalDate.now(zona).atStartOfDay(zona).toEpochSecond()
    return (0 until 24).map { h -> (inici + h * 3600L).let { HoraPreu(it, h, t.preuKwh(it, zona) { null }, t.franja(it, zona)) } }
}

@Composable
fun PantallaPreu(vm: ConsumViewModel) {
    var t by remember { mutableStateOf(vm.config.tarifa) }
    val desada = vm.infoPreu
    // El que s'ha llegit d'un contracte o factura i s'està revisant; i quantes vegades se n'han posat preus,
    // perquè els camps es tornin a crear amb els valors nous
    var lectura by remember { mutableStateOf<LecturaFactura?>(null) }
    var posats by remember { mutableIntStateOf(0) }

    lectura?.let { l ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
            RevisioDocument(l, enCancellar = { lectura = null }) { triats ->
                t = Factura.aplica(t, l, triats)
                posats++
                lectura = null
            }
            Spacer(Modifier.height(16.dp))
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TargetaEscaneig { lectura = it }
            // En canviar de país es comença amb els valors per defecte d'aquell país: el dinàmic funciona sense escriure res
            Pestanyes(Pais.entries, t.pais, { it.nom }) { p ->
                if (p != t.pais) t = Tarifa(pais = p, contracte = if (p == Pais.ALTRE) Contracte.FIX else Contracte.DINAMIC, potenciaKw = t.potenciaKw)
            }
            if (t.pais != Pais.ALTRE) {
                Targeta {
                    TitolTargeta("Quin contracte tens?")
                    Pestanyes(Contracte.entries, t.contracte, { noms(t.pais)[it.ordinal] }) { t = t.copy(contracte = it) }
                }
            }
            // Els camps es tornen a crear quan canvia el país o el contracte, o quan s'hi posen preus d'un document
            key(t.pais, t.contracte, posats) {
                when (t.pais) {
                    Pais.ES -> Espanya(t, desada?.takeIf { it.tarifa.pais == Pais.ES && it.tarifa.dinamica }?.avui) { t = it }
                    Pais.NL -> PaisosBaixos(t, desada?.takeIf { it.tarifa.pais == Pais.NL && it.tarifa.dinamica }?.avui) { t = it }
                    Pais.ALTRE -> Targeta {
                        CampNum("Preu del kWh, tot inclòs", t.preuFix, "€") { t = t.copy(preuFix = it) }
                        CampNum("Costos fixos", t.fixosMes, "€/mes", "Quotes que pagues igualment, gastis o no.") { t = t.copy(fixosMes = it) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = { vm.desaTarifa(t) }, Modifier.fillMaxWidth().padding(12.dp)) { Text("Desa") }
    }
}

@Composable
private fun Espanya(t: Tarifa, mercat: List<HoraPreu>?, canvia: (Tarifa) -> Unit) {
    fun ambImpostos(x: Double) = x * (1 + t.iee) * (1 + t.iva)
    Targeta {
        when (t.contracte) {
            Contracte.FIX -> {
                TitolTargeta("Energia")
                CampNum("Preu del kWh, sense impostos", t.preuFix, "€", "Com a la factura. S'hi sumen l'impost elèctric i l'IVA.") { canvia(t.copy(preuFix = it)) }
            }
            Contracte.FRANGES -> {
                TitolTargeta("Energia, per franges")
                TiraPreus(franges(t), null, 14.dp)
                Nota("Punta 10–14 i 18–22 h · pla 8–10, 14–18 i 22–24 h · vall 0–8 h. Els caps de setmana i festius, tot vall.", Modifier.padding(vertical = 4.dp))
                CampNum("Punta", t.punta, "€/kWh") { canvia(t.copy(punta = it)) }
                CampNum("Pla", t.pla, "€/kWh") { canvia(t.copy(pla = it)) }
                CampNum("Vall", t.vall, "€/kWh") { canvia(t.copy(vall = it)) }
                Nota("Sense impostos, com a la factura. Amb impostos: ${num(ambImpostos(t.punta), 3)}, ${num(ambImpostos(t.pla), 3)} i ${num(ambImpostos(t.vall), 3)} €.")
            }
            Contracte.DINAMIC -> {
                TitolTargeta("Energia, al preu de cada hora (PVPC)")
                Nota("L'app baixa sola cada dia el PVPC de Red Eléctrica. Hi suma l'impost elèctric i l'IVA.", Modifier.padding(bottom = 6.dp))
                if (mercat != null) BarresPreu(mercat, null) else Nota("Desa-ho i els preus es baixaran sols.")
            }
        }
    }
    Targeta {
        TitolTargeta("Potència contractada (no va als aparells)")
        CampNum("Potència", t.potenciaKw, "kW") { canvia(t.copy(potenciaKw = it)) }
        CampNum("P1", t.potP1, "€/kW i dia") { canvia(t.copy(potP1 = it)) }
        CampNum("P2", t.potP2, "€/kW i dia", "Sense impostos, com a la factura.") { canvia(t.copy(potP2 = it)) }
        Text("${euros(ambImpostos(t.potenciaKw * (t.potP1 + t.potP2)) * 365 / 12)} al mes, amb impostos", fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
    Targeta {
        TitolTargeta("Impostos i altres")
        CampNum("Impost elèctric", t.iee * 100, "%") { canvia(t.copy(iee = it / 100)) }
        CampNum("IVA", t.iva * 100, "%") { canvia(t.copy(iva = it / 100)) }
        CampNum("Lloguer del comptador", t.comptadorMes, "€/mes", "Sense IVA. No va als aparells.") { canvia(t.copy(comptadorMes = it)) }
        CampNum("Finançament del bo social", t.boSocialDia, "€/dia", "Sense impostos. No va als aparells.") { canvia(t.copy(boSocialDia = it)) }
    }
}

@Composable
private fun PaisosBaixos(t: Tarifa, mercat: List<HoraPreu>?, canvia: (Tarifa) -> Unit) {
    Nota(
        "Els noms en neerlandès són els del contracte: copia-hi els números tal com hi surten. Els fixos, per dia.",
        Modifier.padding(horizontal = 4.dp),
    )
    Targeta {
        when (t.contracte) {
            Contracte.FIX -> {
                TitolTargeta("Leveringstarief")
                CampNum("Leveringstarief", t.preuFix, "€/kWh", "Amb IVA i sense l'energiebelasting, que va a sota.") { canvia(t.copy(preuFix = it)) }
            }
            Contracte.FRANGES -> {
                TitolTargeta("Leveringstarief, normaal i dal")
                TiraPreus(franges(t), null, 14.dp)
                Nota("Dal de 23 a 7 h els dies feiners, i tot el dia els caps de setmana i festius.", Modifier.padding(vertical = 4.dp))
                CampNum("Leveringstarief · Normaal", t.normaal, "€/kWh") { canvia(t.copy(normaal = it)) }
                CampNum("Leveringstarief · Dal", t.dal, "€/kWh", "Amb IVA i sense l'energiebelasting, que va a sota.") { canvia(t.copy(dal = it)) }
            }
            Contracte.DINAMIC -> {
                TitolTargeta("Energia, al preu de cada hora")
                Nota(
                    "L'app baixa sola cada dia el preu de mercat (EnergyZero), que ja porta l'energiebelasting i l'IVA. Hi suma el recàrrec del proveïdor.",
                    Modifier.padding(bottom = 6.dp),
                )
                if (mercat != null) BarresPreu(mercat, null) else Nota("Desa-ho i els preus es baixaran sols.")
                Spacer(Modifier.height(6.dp))
                CampNum("Inkoopvergoeding", t.recarrec, "€/kWh", "El recàrrec del proveïdor, amb IVA.") { canvia(t.copy(recarrec = it)) }
            }
        }
        if (t.contracte != Contracte.DINAMIC) {
            CampNum("Energiebelasting (EB)", t.ebKwh, "€/kWh", "L'impost de l'energia, amb IVA. El 2026, 0,11085.") { canvia(t.copy(ebKwh = it)) }
            val total = if (t.contracte == Contracte.FIX) num(t.preuFix + t.ebKwh, 3) else "${num(t.normaal + t.ebKwh, 3)} i ${num(t.dal + t.ebKwh, 3)}"
            Text("Cada kWh: $total €, tot inclòs", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
    Targeta {
        TitolTargeta("Costos fixos (no van als aparells)")
        CampNum("Vaste leveringskosten", t.proveidorDia, "€/dag") { canvia(t.copy(proveidorDia = it)) }
        CampNum("Netbeheerkosten", t.xarxaDia, "€/dag") { canvia(t.copy(xarxaDia = it)) }
        // Al contracte surt amb un menys davant («€- 1,72317»): es guarda sempre en positiu i es resta
        CampNum("Vermindering energiebelasting", t.descompteDia, "€/dag", "Es resta dels altres dos.") { canvia(t.copy(descompteDia = abs(it))) }
        Text("Total fixos: ${euros(t.fixosDia() * 365 / 12)} al mes", fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
