package cat.jordi.consum.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.R
import cat.jordi.consum.dades.Categoria
import cat.jordi.consum.dades.Franja
import cat.jordi.consum.dades.HoraPreu
import cat.jordi.consum.dades.Idioma
import cat.jordi.consum.dades.NivellPotencia
import cat.jordi.consum.dades.TextRecurs
import java.text.NumberFormat

/**
 * Clar o fosc (maqueta 16). Els colors de l'app es llegeixen d'aquí cada cop que es fan servir, també dins de les gràfiques:
 * en canviar-ho, tota la pantalla es torna a pintar. Ho decideix [MainActivity] amb la Configuració i el mòbil.
 */
object Aspecte {
    var fosc by mutableStateOf(false)
}

private fun tria(clar: Long, fosc: Long) = Color(if (Aspecte.fosc) fosc else clar)

val Primari get() = tria(0xFF0F766E, 0xFF2DD4BF)
val PrimariFluix get() = tria(0xFFCCFBF1, 0xFF133B37)
val Fons get() = tria(0xFFF6F7F6, 0xFF111614)

// L'Historial d'un període anterior (maqueta 10): la pàgina color sèpia, com una foto antiga
val Sepia get() = tria(0xFFF3ECE0, 0xFF1E1A14)
val SepiaFort get() = tria(0xFFE9D9BD, 0xFF3A2F1E)
val SepiaTargeta get() = tria(0xFFFFFAF2, 0xFF27221B)
val SepiaVora get() = tria(0xFFE6D3B3, 0xFF4A3C26)
val SepiaText get() = tria(0xFF5C4316, 0xFFE9D2A6)
val Suau get() = tria(0xFF5F6B66, 0xFF9AA7A2)
val Separador get() = tria(0xFFE3E7E5, 0xFF2C3532)
val Sempre get() = tria(0xFF94A3B8, 0xFF94A3B8)
val SempreFluix get() = tria(0xFFEEF2F6, 0xFF232B33)
val Desconegut get() = tria(0xFFD97706, 0xFFF59E0B)
val DesconegutFluix get() = tria(0xFFFEF3C7, 0xFF3A2C0E)
/** El taronja dels textos: el [Desconegut] no arriba al contrast que cal per llegir-lo. */
val DesconegutText get() = tria(0xFFB45309, 0xFFFBBF24)
val Linia get() = tria(0xFF2563EB, 0xFF60A5FA)
val LiniaFluix get() = tria(0xFFDBEAFE, 0xFF172A45)
val Ok get() = tria(0xFF15803D, 0xFF4ADE80)
val OkFluix get() = tria(0xFFDCFCE7, 0xFF12321F)
/** El que ve del núvol de Shelly i encara és aproximat. */
val Nuvol get() = tria(0xFF7C3AED, 0xFFA78BFA)
val NuvolFluix get() = tria(0xFFEDE9FE, 0xFF2A2145)
/** Des del 80 % de la potència contractada, i des del 90 %. */
val PotenciaAlta get() = tria(0xFFD97706, 0xFFF59E0B)
val PotenciaLimit get() = tria(0xFFDC2626, 0xFFF87171)
/** El fons de les targetes, el text principal, el text sobre el [Primari], i el fons de l'avís del límit. */
val Superficie get() = tria(0xFFFFFFFF, 0xFF1B2220)
val TextPrincipal get() = tria(0xFF1C2421, 0xFFE6ECEA)
val SobrePrimari get() = tria(0xFFFFFFFF, 0xFF06201D)
val LimitFluix get() = tria(0xFFFEF2F2, 0xFF3B1A1A)
private val PuntApagat get() = tria(0xFFD4D9D7, 0xFF3A4541)

/** Les etiquetes dels eixos de les gràfiques: prou grans i fosques per llegir-les. */
val Eix get() = tria(0xFF3F4A45, 0xFFB8C4BF)

fun colorNivell(n: NivellPotencia, normal: Color) = when (n) {
    NivellPotencia.NORMAL -> normal
    NivellPotencia.ALT -> PotenciaAlta
    NivellPotencia.LIMIT -> PotenciaLimit
}

@Composable
fun TemaConsum(contingut: @Composable () -> Unit) {
    val colors = if (Aspecte.fosc) darkColorScheme(
        primary = Primari, onPrimary = SobrePrimari, primaryContainer = PrimariFluix, onPrimaryContainer = Primari,
        secondaryContainer = PrimariFluix, onSecondaryContainer = Primari,
        background = Fons, onBackground = TextPrincipal, surface = Fons, onSurface = TextPrincipal,
        surfaceContainer = Superficie, surfaceContainerHigh = Superficie, surfaceContainerHighest = Superficie,
        onSurfaceVariant = Suau, outlineVariant = Separador,
    ) else lightColorScheme(
        primary = Primari, onPrimary = SobrePrimari, primaryContainer = PrimariFluix, onPrimaryContainer = Primari,
        secondaryContainer = PrimariFluix, onSecondaryContainer = Primari,
        background = Fons, surface = Fons, surfaceContainer = Superficie, onSurfaceVariant = Suau,
        outlineVariant = Separador,
    )
    MaterialTheme(colorScheme = colors, content = contingut)
}

/** Un número com s'escriu en la llengua de l'app (en català, punt de milers i coma decimal). */
fun num(x: Double, decimals: Int = 0): String =
    NumberFormat.getNumberInstance(Idioma.locale).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }.format(x)

fun euros(x: Double) = Idioma.euros(num(x, 2))

/**
 * Un número escrit per l'usuari, amb coma o punt decimal: «358.5» i «1,5» són decimals. Si hi ha punt i coma, el
 * darrer és el decimal. Amb [milers], «1.150» (com el que posa [num]) són 1150; als preus, un punt sempre és decimal.
 */
fun llegeixNum(text: String, milers: Boolean = true): Double? {
    val t = text.trim().replace(" ", "").replace("\u00A0", "")
    val decimal = maxOf(t.lastIndexOf(','), t.lastIndexOf('.'))
    val net = when {
        ',' in t && '.' in t -> t.substring(0, decimal).replace(".", "").replace(",", "") + "." + t.substring(decimal + 1)
        ',' in t -> t.replace(',', '.')
        milers && Regex("""[1-9]\d{0,2}(\.\d{3})+""").matches(t) -> t.replace(".", "")
        else -> t
    }
    return net.toDoubleOrNull()
}

/** Un text fet fora de la interfície (al ViewModel o a les dades), en la llengua d'ara. */
@Composable
fun TextRecurs.text(): String = text(LocalContext.current)

/** «X € de consum + Y € de fixos»; als Països Baixos els fixos poden ser negatius, i llavors es resten. */
@Composable
fun consumIFixos(consum: Double, fixos: Double) =
    if (fixos >= 0) stringResource(R.string.comu_consum_mes_fixos, euros(consum), euros(fixos))
    else stringResource(R.string.comu_consum_menys_fixos, euros(consum), euros(-fixos))

fun icona(c: Categoria): ImageVector = when (c) {
    Categoria.NEVERA -> Icons.Outlined.Kitchen
    Categoria.CONGELADOR, Categoria.AIRE -> Icons.Outlined.AcUnit
    Categoria.FORN -> Icons.Outlined.OutdoorGrill
    Categoria.PLACA -> Icons.Outlined.Whatshot
    Categoria.MICROONES -> Icons.Outlined.Microwave
    Categoria.RENTAPLATS -> Icons.Outlined.LocalDining
    Categoria.CAMPANA, Categoria.ASSECADOR_CABELLS -> Icons.Outlined.Air
    Categoria.CAFETERA -> Icons.Outlined.Coffee
    Categoria.BULLIDOR -> Icons.Outlined.EmojiFoodBeverage
    Categoria.TORRADORA -> Icons.Outlined.BreakfastDining
    Categoria.ROBOT -> Icons.Outlined.Blender
    Categoria.RENTADORA -> Icons.Outlined.LocalLaundryService
    Categoria.ASSECADORA -> Icons.Outlined.Dry
    Categoria.PLANXA -> Icons.Outlined.Iron
    Categoria.TELEVISOR -> Icons.Outlined.Tv
    Categoria.CONSOLA -> Icons.Outlined.SportsEsports
    Categoria.ROUTER -> Icons.Outlined.Router
    Categoria.LLUMS -> Icons.Outlined.Lightbulb
    Categoria.ORDINADOR -> Icons.Outlined.Computer
    Categoria.TOVALLOLER -> Icons.Outlined.Bathtub
    Categoria.ESTUFA, Categoria.CALDERA -> Icons.Outlined.LocalFireDepartment
    Categoria.BOMBA_CALOR -> Icons.Outlined.Thermostat
    Categoria.TERMO, Categoria.BOMBA_AIGUA -> Icons.Outlined.WaterDrop
    Categoria.COTXE -> Icons.Outlined.ElectricCar
    Categoria.DEPURADORA -> Icons.Outlined.Pool
    Categoria.ALTRES -> Icons.Outlined.DevicesOther
}

@Composable
fun Targeta(modifier: Modifier = Modifier, color: Color = Superficie, contingut: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = color) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), content = contingut)
    }
}

@Composable
fun TitolTargeta(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(bottom = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun Bombolla(icona: ImageVector, fons: Color, color: Color) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(fons), contentAlignment = Alignment.Center) {
        Icon(icona, null, tint = color, modifier = Modifier.size(21.dp))
    }
}

/** Com de segur és un número: 3 punts, après o mesurat; 2, estimat amb l'etiqueta; 1, valor típic o sense identificar. */
@Composable
fun Punts(n: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { i ->
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (i < n) Primari else PuntApagat))
        }
    }
}

@Composable
fun FilaConsum(
    icona: ImageVector, fons: Color, color: Color, titol: String, subtitol: String, valor: String,
    punts: Int? = null, subtitolColor: Color = Suau, modifier: Modifier = Modifier, liniesSubtitol: Int = 1,
    subValor: String? = null,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Bombolla(icona, fons, color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titol, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitol, fontSize = 12.sp, lineHeight = 16.sp, color = textFort(subtitolColor), maxLines = liniesSubtitol, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(valor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subValor != null) Text(subValor, fontSize = 12.sp, color = Suau)
            if (punts != null) {
                Spacer(Modifier.size(4.dp))
                Punts(punts)
            }
        }
    }
}

@Composable
fun Pastilla(text: String, color: Color, fons: Color, punt: Color? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(fons).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (punt != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(punt))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 13.sp, color = textFort(color))
    }
}

/** Unes pestanyes petites com les de la maqueta (Dia, Setmana, Mes...). */
@Composable
fun <T> Pestanyes(opcions: List<T>, triada: T, nom: @Composable (T) -> String, enTriar: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (o in opcions) {
            val actiu = o == triada
            // Amb selected, el lector de pantalla diu quina pestanya està triada
            Surface(
                selected = actiu, onClick = { enTriar(o) }, modifier = Modifier.weight(1f).semantics { role = Role.Tab }, shape = RoundedCornerShape(10.dp),
                color = if (actiu) Primari else Superficie,
                // Amb vora, les que no estan triades també semblen botons dins d'una targeta blanca
                border = if (actiu) null else BorderStroke(1.dp, Separador),
            ) {
                Text(
                    nom(o), Modifier.padding(vertical = 8.dp), fontSize = 13.sp,
                    color = if (actiu) SobrePrimari else Suau, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun Nota(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 12.5.sp, color = Suau, lineHeight = 17.sp)
}

/** Que una part del consum és aproximada; si hi ha l'adreça del Shelly de casa, que s'hi corregirà. */
@Composable
fun NotaAproximat(text: String, esCorregira: Boolean) {
    Nota(if (esCorregira) "$text ${stringResource(R.string.comu_es_corregira)}" else text, Modifier.padding(top = 4.dp))
}

val PuntaColor get() = tria(0xFFDC2626, 0xFFF87171)
val PuntaText get() = tria(0xFFB91C1C, 0xFFFCA5A5)
val PuntaFluix get() = tria(0xFFFEE2E2, 0xFF3B1A1A)
val VallColor get() = tria(0xFF16A34A, 0xFF4ADE80)
val VallText get() = tria(0xFF15803D, 0xFF86EFAC)
val VallFluix get() = tria(0xFFDCFCE7, 0xFF12321F)

/** Per escriure amb el to d'un color de les gràfiques: el mateix to, prou fosc per llegir-lo. */
fun textFort(c: Color): Color = when (c) {
    Desconegut -> DesconegutText
    PuntaColor -> PuntaText
    VallColor -> VallText
    Separador, Sempre -> Suau
    else -> c
}

/** El color d'una hora: el de la seva franja, o, si el preu és de mercat, segons si és dels més barats o dels més cars del dia. */
fun colorPreu(h: HoraPreu, dia: List<HoraPreu>): Color = when (h.franja) {
    Franja.PUNTA -> PuntaColor
    Franja.PLA, Franja.NORMAAL -> Desconegut
    Franja.VALL, Franja.DAL -> VallColor
    Franja.UNICA -> Primari
    Franja.MERCAT -> {
        val preus = dia.mapNotNull { it.preu }.sorted()
        val p = h.preu
        when {
            p == null || preus.size < 3 -> Separador
            p <= preus[preus.size / 3] -> VallColor
            p >= preus[preus.size * 2 / 3] -> PuntaColor
            else -> Desconegut
        }
    }
}

/** Les 24 hores d'un dia en una tira de colors, amb l'hora d'ara marcada. */
@Composable
fun TiraPreus(hores: List<HoraPreu>, ara: Long?, alcada: androidx.compose.ui.unit.Dp = 20.dp) {
    Column {
        Row(Modifier.fillMaxWidth().height(alcada), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            hores.forEach { h ->
                val actual = ara != null && h.ts == ara
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(colorPreu(h, hores))
                        .then(if (actual) Modifier.border(2.dp, TextPrincipal, RoundedCornerShape(3.dp)) else Modifier)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(stringResource(R.string.comu_hora_zero), "6", "12", "18", "24").forEach { Text(it, fontSize = 10.sp, color = Suau) }
        }
    }
}

/** El preu de cada hora en barres (per als contractes dinàmics). */
@Composable
fun BarresPreu(hores: List<HoraPreu>, ara: Long?) {
    val preus = hores.mapNotNull { it.preu }
    if (preus.isEmpty()) {
        Nota(stringResource(R.string.comu_sense_preus_avui))
        return
    }
    val maxim = preus.max() * 1.08
    Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        hores.forEach { h ->
            val actual = ara != null && h.ts == ara
            Box(
                Modifier.weight(1f).fillMaxHeight(((h.preu ?: 0.0) / maxim).toFloat().coerceIn(0.02f, 1f))
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(colorPreu(h, hores).copy(alpha = if (actual) 1f else 0.6f))
                    .then(if (actual) Modifier.border(1.5.dp, TextPrincipal, RoundedCornerShape(2.dp)) else Modifier)
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(stringResource(R.string.comu_hora_zero), "6", "12", "18", "24").forEach { Text(it, fontSize = 10.sp, color = Suau) }
    }
}
