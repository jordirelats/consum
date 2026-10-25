package cat.jordi.consum

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cat.jordi.consum.dades.Aparell
import cat.jordi.consum.dades.Idioma
import cat.jordi.consum.dades.Pregunta
import cat.jordi.consum.dades.Referencia
import cat.jordi.consum.dades.endevina
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Els avisos de consum inesperat: una notificació que pregunta què és, amb dos aparells proposats
 * (els que s'hi assemblen més) i «Un altre…», que obre l'app a Entrena.
 */
object Avisos {
    private const val CANAL = "avisos"
    private const val CANAL_POTENCIA = "potencia"
    const val CANAL_VIGILANCIA = "vigilancia"
    private const val ID_POTENCIA = 900_001
    const val OBRE = "obre"
    const val ENTRENA = "entrena"

    fun creaCanal(context: Context) {
        val canal = NotificationChannel(CANAL, context.getString(R.string.avis_canal), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.avis_canal_descripcio)
        }
        val potencia = NotificationChannel(CANAL_POTENCIA, context.getString(R.string.avis_canal_potencia), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.avis_canal_potencia_descripcio)
        }
        val vigilancia = NotificationChannel(CANAL_VIGILANCIA, context.getString(R.string.avis_canal_vigilancia), NotificationManager.IMPORTANCE_MIN).apply {
            description = context.getString(R.string.avis_canal_vigilancia_descripcio)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(canal, potencia, vigilancia))
    }

    fun obreApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** La potència ha passat del 90 % de la contractada ([limitKw]). [ences]: els aparells que l'expliquen, si se'n sap cap. */
    fun avisaPotencia(context: Context, w: Double, limitKw: Double, ences: List<Pair<String, Double>> = emptyList()) {
        val gestor = NotificationManagerCompat.from(context)
        if (!gestor.areNotificationsEnabled()) return
        val watts = NumberFormat.getIntegerInstance(Idioma.locale).format(w)
        val percent = NumberFormat.getIntegerInstance(Idioma.locale).format(w / (limitKw * 10))
        val kw = NumberFormat.getNumberInstance(Idioma.locale).apply { maximumFractionDigits = 2 }.format(limitKw)
        val titol = if (w >= limitKw * 1000) context.getString(R.string.avis_potencia_passada, watts, kw)
        else context.getString(R.string.avis_potencia_titol, watts, percent)
        val quilos = NumberFormat.getNumberInstance(Idioma.locale).apply { maximumFractionDigits = 1; minimumFractionDigits = 1 }
        val text = if (ences.isEmpty()) context.getString(R.string.avis_potencia_text)
        else context.getString(R.string.avis_potencia_ences, ences.joinToString(", ") { (nom, w) -> "$nom ${quilos.format(w / 1000)} kW" })
        val n = NotificationCompat.Builder(context, CANAL_POTENCIA)
            .setSmallIcon(R.drawable.ic_llamp)
            .setContentTitle(titol)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(obreApp(context))
            .setAutoCancel(true)
            .setTimeoutAfter(15 * 60_000L)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        try {
            gestor.notify(ID_POTENCIA, n)
        } catch (_: SecurityException) {
        }
    }

    private fun obreEntrena(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).putExtra(OBRE, ENTRENA).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun resposta(context: Context, pregunta: Long, aparell: Long): PendingIntent = PendingIntent.getBroadcast(
        context, (pregunta * 1000 + aparell).toInt(),
        Intent(context, RespostaReceiver::class.java).putExtra("pregunta", pregunta).putExtra("aparell", aparell),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun avisa(context: Context, p: Pregunta, aparells: List<Aparell>, referencies: Map<Long, Referencia>) {
        val gestor = NotificationManagerCompat.from(context)
        if (!gestor.areNotificationsEnabled()) return
        val w = NumberFormat.getIntegerInstance(Idioma.locale).format(p.dp)
        val hora = Instant.ofEpochSecond(p.ts).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        val n = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_llamp)
            .setContentTitle(context.getString(R.string.avis_titol, w))
            .setContentText(context.getString(R.string.avis_text, hora))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.avis_text, hora)))
            .setContentIntent(obreEntrena(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        endevina(p.dp, aparells, referencies).forEach { a -> n.addAction(0, a.nom, resposta(context, p.id, a.id)) }
        n.addAction(0, context.getString(R.string.avis_un_altre), obreEntrena(context))
        try {
            gestor.notify(p.id.toInt(), n.build())
        } catch (_: SecurityException) {
            // Sense permís de notificacions: la pregunta queda igualment a Entrena
        }
    }

    fun confirma(context: Context, preguntaId: Long, text: String) {
        val n = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_llamp)
            .setContentTitle(text)
            .setContentIntent(obreEntrena(context))
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(preguntaId.toInt(), n)
        } catch (_: SecurityException) {
        }
    }
}

/** La resposta a una pregunta des de la mateixa notificació. */
class RespostaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pregunta = intent.getLongExtra("pregunta", 0)
        val aparell = intent.getLongExtra("aparell", 0)
        val repo = (context.applicationContext as ConsumApp).repo
        val pendent = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repo.respon(pregunta, aparell)
                val nom = repo.aparells().firstOrNull { it.id == aparell }?.nom
                Avisos.confirma(
                    context, pregunta,
                    if (nom != null) context.getString(R.string.avis_respost, nom) else context.getString(R.string.avis_respost_sense_nom),
                )
            } finally {
                pendent.finish()
            }
        }
    }
}
