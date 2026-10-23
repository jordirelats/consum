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
import java.util.Locale

/**
 * Els avisos de consum inesperat: una notificació que pregunta què és, amb dos aparells proposats
 * (els que s'hi assemblen més) i «Un altre…», que obre l'app a Entrena.
 */
object Avisos {
    private const val CANAL = "avisos"
    const val OBRE = "obre"
    const val ENTRENA = "entrena"

    fun creaCanal(context: Context) {
        val canal = NotificationChannel(CANAL, "Consum inesperat", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Quan s'encén una cosa que l'app no reconeix"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
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
        val w = NumberFormat.getIntegerInstance(Locale.forLanguageTag("ca-ES")).format(p.dp)
        val hora = Instant.ofEpochSecond(p.ts).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        val n = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_llamp)
            .setContentTitle("Hi ha +$w W que no reconec")
            .setContentText("Des de les $hora. No s'assembla a cap aparell que m'hagis ensenyat. Què és?")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Des de les $hora. No s'assembla a cap aparell que m'hagis ensenyat. Què és?"))
            .setContentIntent(obreEntrena(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        endevina(p.dp, aparells, referencies).forEach { a -> n.addAction(0, a.nom, resposta(context, p.id, a.id)) }
        n.addAction(0, "Un altre…", obreEntrena(context))
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
                val nom = repo.aparells().firstOrNull { it.id == aparell }?.nom ?: "l'aparell"
                Avisos.confirma(context, pregunta, "Has respost «$nom». L'he après.")
            } finally {
                pendent.finish()
            }
        }
    }
}
