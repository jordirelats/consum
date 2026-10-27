package cat.jordi.consum

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import cat.jordi.consum.dades.EstatGiny
import cat.jordi.consum.dades.NivellPotencia
import cat.jordi.consum.dades.Repositori
import cat.jordi.consum.dades.nivellPotencia
import cat.jordi.consum.shelly.Lectura
import cat.jordi.consum.ui.euros
import cat.jordi.consum.ui.num
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** El giny gran de la pantalla d'inici (4 × 2, maqueta 16): els watts d'ara, el consum d'avui i el que més gasta. */
class GinyGran : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Ginys.pinta(context)
}

/** El giny petit (2 × 1): els watts d'ara i els kWh d'avui. */
class GinyPetit : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Ginys.pinta(context)
}

/**
 * Els ginys es pinten amb l'última lectura desada ([EstatGiny]): cada minut amb l'app oberta o mentre la vigilància vigila la
 * potència, i si no, cada 15 minuts amb el treball de fons. Tocant-los s'obre l'app.
 */
object Ginys {
    @Volatile
    private var darrer = 0L

    private fun ids(context: Context, classe: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, classe))

    /** Si n'hi ha cap a la pantalla d'inici: si no, no cal calcular res. */
    private fun hiHa(context: Context) = ids(context, GinyGran::class.java).isNotEmpty() || ids(context, GinyPetit::class.java).isNotEmpty()

    /**
     * Amb una lectura nova, desa què cal ensenyar (els watts, el consum d'avui i el que més gasta) i ho pinta. Com a molt un
     * cop per minut: la vigilància llegeix cada 10 segons.
     */
    fun actualitza(context: Context, repo: Repositori, l: Lectura?) {
        if (l == null || !hiHa(context)) return
        val ara = System.currentTimeMillis()
        if (ara - darrer < 55_000) return
        darrer = ara
        val avui = runCatching { repo.avui() }.getOrNull()
        val mes = runCatching { repo.encesAra(l).firstOrNull() }.getOrNull()
        repo.config.giny = EstatGiny(l.ts, l.total.w, avui?.kwh, avui?.euros, mes?.first, mes?.second)
        pinta(context)
    }

    fun pinta(context: Context) {
        val config = (context.applicationContext as ConsumApp).repo.config
        val e = config.giny
        val limit = config.tarifa.limitKw
        val gestor = AppWidgetManager.getInstance(context)
        for (id in ids(context, GinyGran::class.java)) gestor.updateAppWidget(id, vistes(context, R.layout.giny_gran, e, limit))
        for (id in ids(context, GinyPetit::class.java)) gestor.updateAppWidget(id, vistes(context, R.layout.giny_petit, e, limit))
    }

    private fun vistes(context: Context, layout: Int, e: EstatGiny?, limitKw: Double?): RemoteViews {
        val v = RemoteViews(context.packageName, layout)
        v.setOnClickPendingIntent(R.id.giny, Avisos.obreApp(context))
        if (e == null) return v
        val gran = layout == R.layout.giny_gran
        // Una lectura de fa més de 20 minuts ja no és «ara»
        val vella = System.currentTimeMillis() / 1000 - e.ts > 20 * 60
        v.setTextViewText(R.id.watts, if (vella) "—" else num(e.w))
        // El launcher reaprofita la vista: el color i els textos es posen sempre, també quan tornen a ser els normals
        v.setTextColor(
            R.id.watts,
            when (nivellPotencia(e.w, limitKw).takeIf { !vella }) {
                NivellPotencia.ALT -> 0xFFD97706.toInt()
                NivellPotencia.LIMIT -> 0xFFDC2626.toInt()
                else -> ContextCompat.getColor(context, R.color.giny_text)
            },
        )
        val kwh = e.kwh
        v.setTextViewText(
            R.id.avui,
            when {
                kwh == null -> ""
                gran -> context.getString(R.string.giny_avui, num(kwh, 1), euros(e.euros ?: 0.0))
                else -> context.getString(R.string.giny_avui_curt, num(kwh, 1))
            },
        )
        if (gran) {
            v.setTextViewText(R.id.hora, Instant.ofEpochSecond(e.ts).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")))
            if (limitKw != null && !vella) {
                v.setViewVisibility(R.id.barra, View.VISIBLE)
                v.setProgressBar(R.id.barra, 100, (e.w / (limitKw * 10)).toInt().coerceIn(0, 100), false)
            } else {
                v.setViewVisibility(R.id.barra, View.GONE)
            }
            val nom = e.mesNom
            val w = e.mesW
            if (nom != null && w != null && !vella) {
                v.setViewVisibility(R.id.mes, View.VISIBLE)
                v.setTextViewText(R.id.mes, context.getString(R.string.giny_mes, nom, if (w >= 1000) "${num(w / 1000, 1)} kW" else "${num(w)} W"))
            } else {
                v.setViewVisibility(R.id.mes, View.GONE)
            }
        }
        return v
    }
}
