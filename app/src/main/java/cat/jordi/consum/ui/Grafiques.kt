package cat.jordi.consum.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.dades.Barra
import cat.jordi.consum.dades.Punt
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil

/** Un sostre rodó per a l'eix: 200, 400, 600, 800, 1000, 1500, 2000, 3000... */
private fun sostre(maxim: Double): Double {
    val passos = listOf(200.0, 400.0, 600.0, 800.0, 1000.0, 1500.0, 2000.0, 3000.0, 4000.0, 5000.0, 7500.0, 10000.0)
    return passos.firstOrNull { it >= maxim } ?: (ceil(maxim / 5000) * 5000)
}

/** La potència de les últimes 24 hores: el total en àrea i la línia de la segona pinça en blau. */
@Composable
fun GraficaPotencia(serie: List<Punt>, ambLinia: Boolean) {
    val mesura = rememberTextMeasurer()
    val estil = TextStyle(fontSize = 10.sp, color = Suau)
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val peu = 16.dp.toPx()
        val alt = size.height - peu
        if (serie.size < 2) {
            drawText(mesura, "Encara no hi ha dades", Offset(0f, alt / 2), estil)
            return@Canvas
        }
        val t0 = serie.first().ts.toFloat()
        val t1 = serie.last().ts.toFloat().coerceAtLeast(t0 + 1)
        val ymax = sostre(serie.maxOf { it.wTotal }).toFloat()
        fun x(ts: Long) = (ts - t0) / (t1 - t0) * size.width
        fun y(w: Double) = alt - (w.toFloat() / ymax).coerceIn(0f, 1f) * alt

        for (f in listOf(0.25f, 0.5f, 0.75f, 1f)) {
            val yy = alt - f * alt
            drawLine(Separador, Offset(0f, yy), Offset(size.width, yy), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            val etiqueta = "${num((ymax * f).toDouble())} W"
            val mida = mesura.measure(etiqueta, estil).size
            drawText(mesura, etiqueta, Offset(size.width - mida.width, yy + 2), estil)
        }
        val area = Path().apply {
            moveTo(x(serie.first().ts), alt)
            serie.forEach { lineTo(x(it.ts), y(it.wTotal)) }
            lineTo(x(serie.last().ts), alt)
            close()
        }
        drawPath(area, Primari.copy(alpha = 0.14f))
        val linia = Path().apply {
            serie.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.ts), y(p.wTotal)) else lineTo(x(p.ts), y(p.wTotal)) }
        }
        drawPath(linia, Primari, style = Stroke(1.5.dp.toPx()))
        if (ambLinia) {
            val l2 = Path().apply {
                serie.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.ts), y(p.wLinia)) else lineTo(x(p.ts), y(p.wLinia)) }
            }
            drawPath(l2, Linia, style = Stroke(1.5.dp.toPx()))
        }
        // Les hores en punt múltiples de 6 a sota
        val zona = ZoneId.systemDefault()
        var ts = (serie.first().ts / 3600 + 1) * 3600
        while (ts <= serie.last().ts) {
            val h = Instant.ofEpochSecond(ts).atZone(zona).hour
            if (h % 6 == 0) {
                val etiqueta = "$h h"
                val mida = mesura.measure(etiqueta, estil).size
                drawText(mesura, etiqueta, Offset(x(ts) - mida.width / 2, alt + 3), estil)
            }
            ts += 3600
        }
    }
}

/** Les barres de l'historial: sempre encès (gris), la línia (blau) i sense identificar (taronja). */
@Composable
fun GraficaBarres(barres: List<Barra>) {
    val mesura = rememberTextMeasurer()
    val estil = TextStyle(fontSize = 10.sp, color = Suau)
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val peu = 16.dp.toPx()
        val alt = size.height - peu
        if (barres.isEmpty()) return@Canvas
        val ymax = (barres.maxOf { it.hora.total } * 1.1).coerceAtLeast(0.001)
        val pas = size.width / barres.size
        val ample = (pas * 0.72f).coerceAtMost(28.dp.toPx())
        val cadaQuants = when {
            barres.size > 20 -> 6
            barres.size > 10 -> 5
            else -> 1
        }
        barres.forEachIndexed { i, b ->
            val x = i * pas + (pas - ample) / 2
            var base = alt
            for ((kwh, color) in listOf(b.hora.sempre to Sempre, b.hora.linia to Linia, b.hora.desconegut to Desconegut)) {
                val h = (kwh / ymax * alt).toFloat()
                if (h > 0.5f) {
                    drawRoundRect(color, Offset(x, base - h), Size(ample, h), CornerRadius(2.dp.toPx()))
                    base -= h
                }
            }
            if (i % cadaQuants == 0 || barres.size <= 10) {
                val mida = mesura.measure(b.etiqueta, estil).size
                drawText(mesura, b.etiqueta, Offset(x + ample / 2 - mida.width / 2, alt + 3), estil)
            }
        }
    }
}
