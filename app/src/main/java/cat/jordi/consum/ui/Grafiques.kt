package cat.jordi.consum.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.R
import cat.jordi.consum.dades.Barra
import cat.jordi.consum.dades.Punt
import cat.jordi.consum.dades.nivellPotencia
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil

/** Un pas rodó per a l'eix (en les unitats que sigui), perquè hi hagi entre 3 i 5 ratlles. */
private fun pas(maxim: Double): Double {
    val passos = listOf(0.1, 0.2, 0.25, 0.5, 1.0, 2.0, 2.5, 5.0, 10.0, 20.0, 25.0, 50.0, 100.0)
    return passos.firstOrNull { maxim / it <= 4 } ?: (ceil(maxim / 4 / 100) * 100)
}

private val EstilEix = TextStyle(fontSize = 11.sp, color = Eix, fontWeight = FontWeight.Medium)

/**
 * La potència de les últimes 24 hores, amb l'eix en kW a l'esquerra i les hores a sota.
 * - El total, en àrea i línia: contínua on les dades són exactes, i lila i de punts on són aproximades (el núvol).
 * - Amb la potència contractada ([limitKw]): les ratlles del 80 %, el 90 % i el 100 %, i els trams que passen
 *   del 80 % en taronja i del 90 % en vermell.
 * - La línia de la segona pinça, en blau.
 */
@Composable
fun GraficaPotencia(serie: List<Punt>, ambLinia: Boolean, limitKw: Double?) {
    val mesura = rememberTextMeasurer()
    val context = LocalContext.current
    val senseDades = stringResource(R.string.grafiques_sense_dades)
    val contractats = limitKw?.let { stringResource(R.string.grafiques_contractats, num(it, 2)) }
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val peu = 20.dp.toPx()
        val alt = size.height - peu
        if (serie.size < 2) {
            drawText(mesura, senseDades, Offset(0f, alt / 2), EstilEix)
            return@Canvas
        }
        val limitW = limitKw?.times(1000)
        val maxim = maxOf(serie.maxOf { it.wTotal }, (limitW ?: 0.0) * 1.08, 200.0)
        val pasKw = pas(maxim / 1000)
        val ymax = ceil(maxim / 1000 / pasKw) * pasKw * 1000
        val etiquetes = generateSequence(0.0) { it + pasKw }.takeWhile { it <= ymax / 1000 + 1e-9 }
            .map { kw -> kw to context.getString(R.string.grafiques_kw, num(kw, if (pasKw < 1) 1 else 0)) }.toList()
        val marge = etiquetes.maxOf { mesura.measure(it.second, EstilEix).size.width } + 6.dp.toPx()
        val ample = size.width - marge
        val t0 = serie.first().ts.toFloat()
        val t1 = serie.last().ts.toFloat().coerceAtLeast(t0 + 1)
        fun x(ts: Long) = marge + (ts - t0) / (t1 - t0) * ample
        fun y(w: Double) = alt - (w / ymax).toFloat().coerceIn(0f, 1f) * alt

        // L'eix en kW
        for ((kw, text) in etiquetes) {
            val yy = y(kw * 1000)
            if (kw > 0) drawLine(Separador, Offset(marge, yy), Offset(size.width, yy))
            val mida = mesura.measure(text, EstilEix).size
            drawText(mesura, text, Offset(marge - 4.dp.toPx() - mida.width, yy - mida.height / 2f), EstilEix)
        }
        drawLine(Eix.copy(alpha = 0.5f), Offset(marge, 0f), Offset(marge, alt))
        drawLine(Eix.copy(alpha = 0.5f), Offset(marge, alt), Offset(size.width, alt))

        // L'àrea, per trams exactes i aproximats
        var i = 0
        while (i < serie.size - 1) {
            val aprox = serie[i + 1].aprox
            var j = i + 1
            while (j < serie.size - 1 && serie[j + 1].aprox == aprox) j++
            val area = Path().apply {
                moveTo(x(serie[i].ts), alt)
                for (k in i..j) lineTo(x(serie[k].ts), y(serie[k].wTotal))
                lineTo(x(serie[j].ts), alt)
                close()
            }
            drawPath(area, (if (aprox) Nuvol else Primari).copy(alpha = 0.12f))
            i = j
        }

        // Les ratlles de la potència contractada
        if (limitW != null && contractats != null) {
            for ((part, color) in listOf(0.8 to PotenciaAlta, 0.9 to PotenciaLimit)) {
                val yy = y(limitW * part)
                drawLine(color, Offset(marge, yy), Offset(size.width, yy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)))
                val text = "${num(part * 100)} %"
                val mida = mesura.measure(text, EstilEix.copy(color = color)).size
                drawText(mesura, text, Offset(size.width - mida.width, yy - mida.height - 1.dp.toPx()), EstilEix.copy(color = color))
            }
            val yy = y(limitW)
            drawLine(Eix, Offset(marge, yy), Offset(size.width, yy), 1.dp.toPx())
            val mida = mesura.measure(contractats, EstilEix).size
            drawText(mesura, contractats, Offset(size.width - mida.width, yy - mida.height - 1.dp.toPx()), EstilEix)
        }

        liniaNivells(serie, limitKw, ::x) { y(it.wTotal) }
        if (ambLinia) liniaPunts(serie, Linia, ::x) { y(it.wLinia) }

        // Les hores en punt múltiples de 6 a sota, sense sortir dels marges
        val zona = ZoneId.systemDefault()
        var ts = (serie.first().ts / 3600 + 1) * 3600
        while (ts <= serie.last().ts) {
            val h = Instant.ofEpochSecond(ts).atZone(zona).hour
            if (h % 6 == 0) {
                val etiqueta = context.getString(R.string.grafiques_hora, h)
                val mida = mesura.measure(etiqueta, EstilEix).size
                val xx = (x(ts) - mida.width / 2).coerceIn(marge, size.width - mida.width)
                drawText(mesura, etiqueta, Offset(xx, alt + 4.dp.toPx()), EstilEix)
            }
            ts += 3600
        }
    }
    Llegenda(serie.any { it.aprox }, limitKw != null)
}

/** El total, en trams del mateix aspecte: el color segons la potència i si és aproximat (lila i de punts). */
private fun DrawScope.liniaNivells(serie: List<Punt>, limitKw: Double?, x: (Long) -> Float, y: (Punt) -> Float) {
    fun aspecte(a: Punt, b: Punt): Pair<Color, Boolean> {
        val n = maxOf(nivellPotencia(a.wTotal, limitKw), nivellPotencia(b.wTotal, limitKw))
        return colorNivell(n, if (b.aprox) Nuvol else Primari) to b.aprox
    }
    var i = 1
    while (i < serie.size) {
        val (color, aprox) = aspecte(serie[i - 1], serie[i])
        val cami = Path().apply { moveTo(x(serie[i - 1].ts), y(serie[i - 1])) }
        while (i < serie.size && aspecte(serie[i - 1], serie[i]) == color to aprox) {
            cami.lineTo(x(serie[i].ts), y(serie[i]))
            i++
        }
        drawPath(
            cami, color,
            style = Stroke(1.5.dp.toPx(), pathEffect = if (aprox) PathEffect.dashPathEffect(floatArrayOf(5f, 6f)) else null),
        )
    }
}

/**
 * Una línia que passa per tots els punts: contínua on les dades són exactes i de punts on són aproximades
 * (un tram és aproximat si el punt on acaba ho és).
 */
private fun DrawScope.liniaPunts(serie: List<Punt>, color: Color, x: (Long) -> Float, y: (Punt) -> Float) {
    val exacta = Path()
    val aproximada = Path()
    for (i in 1 until serie.size) {
        val (a, b) = serie[i - 1] to serie[i]
        val cami = if (b.aprox) aproximada else exacta
        if (i == 1 || serie[i - 1].aprox != b.aprox) cami.moveTo(x(a.ts), y(a))
        cami.lineTo(x(b.ts), y(b))
    }
    drawPath(exacta, color, style = Stroke(1.5.dp.toPx()))
    drawPath(aproximada, color, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 6f))))
}

/** La llegenda de sota la gràfica: només el que hi surt. */
@Composable
private fun Llegenda(ambAproximat: Boolean, ambLimit: Boolean) {
    if (!ambAproximat && !ambLimit) return
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        @Composable
        fun Element(color: Color, text: String, ratllat: Boolean = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(14.dp).height(4.dp)) {
                    drawLine(
                        color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.5.dp.toPx(),
                        pathEffect = if (ratllat) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null,
                    )
                }
                Text(text, Modifier.padding(start = 4.dp), fontSize = 11.sp, color = Eix)
            }
        }
        Element(Primari, stringResource(R.string.grafiques_exacte))
        if (ambAproximat) Element(Nuvol, stringResource(R.string.grafiques_aproximat), ratllat = true)
        if (ambLimit) {
            Element(PotenciaAlta, "80 %")
            Element(PotenciaLimit, "90 %")
        }
    }
}

/** Les barres de l'historial: sempre encès (gris), la línia (blau) i sense identificar (taronja). */
@Composable
fun GraficaBarres(barres: List<Barra>) {
    val mesura = rememberTextMeasurer()
    val context = LocalContext.current
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val peu = 18.dp.toPx()
        val alt = size.height - peu
        if (barres.isEmpty()) return@Canvas
        val maxim = (barres.maxOf { it.hora.total } * 1.05).coerceAtLeast(0.1)
        val pasKwh = pas(maxim)
        val ymax = ceil(maxim / pasKwh) * pasKwh
        val etiquetes = generateSequence(pasKwh) { it + pasKwh }.takeWhile { it <= ymax + 1e-9 }
            .map { kwh -> kwh to context.getString(R.string.grafiques_kwh, num(kwh, if (pasKwh < 1) 1 else 0)) }.toList()
        val marge = etiquetes.maxOf { mesura.measure(it.second, EstilEix).size.width } + 6.dp.toPx()
        for ((kwh, text) in etiquetes) {
            val yy = alt - (kwh / ymax * alt).toFloat()
            drawLine(Separador, Offset(marge, yy), Offset(size.width, yy))
            val mida = mesura.measure(text, EstilEix).size
            drawText(mesura, text, Offset(marge - 4.dp.toPx() - mida.width, yy - mida.height / 2f), EstilEix)
        }
        drawLine(Eix.copy(alpha = 0.5f), Offset(marge, alt), Offset(size.width, alt))
        val pas = (size.width - marge) / barres.size
        val ample = (pas * 0.72f).coerceAtMost(28.dp.toPx())
        val cadaQuants = when {
            barres.size > 20 -> 6
            barres.size > 10 -> 5
            else -> 1
        }
        barres.forEachIndexed { i, b ->
            val x = marge + i * pas + (pas - ample) / 2
            var base = alt
            for ((kwh, color) in listOf(b.hora.sempre to Sempre, b.hora.linia to Linia, b.hora.desconegut to Desconegut)) {
                val h = (kwh / ymax * alt).toFloat()
                if (h > 0.5f) {
                    drawRoundRect(color, Offset(x, base - h), Size(ample, h), CornerRadius(2.dp.toPx()))
                    base -= h
                }
            }
            if (i % cadaQuants == 0 || barres.size <= 10) {
                val mida = mesura.measure(b.etiqueta, EstilEix).size
                drawText(mesura, b.etiqueta, Offset(x + ample / 2 - mida.width / 2, alt + 3), EstilEix)
            }
        }
    }
}
