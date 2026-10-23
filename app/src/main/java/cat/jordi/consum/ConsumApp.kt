package cat.jordi.consum

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cat.jordi.consum.dades.BaseDades
import cat.jordi.consum.dades.Config
import cat.jordi.consum.dades.Repositori
import java.util.concurrent.TimeUnit

class ConsumApp : Application() {
    lateinit var repo: Repositori
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repositori(Config(this), BaseDades(this))
        Avisos.creaCanal(this)
        programaSincronitzacio(this)
    }
}

/**
 * Cada 15 minuts (el mínim d'Android per a una feina en segon pla):
 * - si el mòbil és a la wifi de casa, baixa del Shelly els minuts que falten (fora de casa no hi arriba i no
 *   passa res: el Shelly els guarda fins que torna);
 * - amb un contracte dinàmic, baixa els preus de mercat;
 * - mira si hi ha un consum que l'app no sap explicar i, si fa estona que dura, ho pregunta amb una notificació.
 */
class Sincronitza(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as ConsumApp).repo
        if (repo.config.configurat) {
            runCatching { repo.sincronitza() }
            runCatching {
                repo.lectura()?.let { l ->
                    val resta = l.total.w - (if (repo.config.teLinia) l.linia?.w ?: 0.0 else 0.0)
                    repo.comprovaInesperat(resta)?.let { p ->
                        Avisos.avisa(applicationContext, p, repo.aparellsAEntrenar(), repo.referencies())
                    }
                }
            }
        }
        runCatching { repo.actualitzaPreus() }
        return Result.success()
    }
}

fun programaSincronitzacio(context: Context) {
    val feina = PeriodicWorkRequestBuilder<Sincronitza>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("sincronitza", ExistingPeriodicWorkPolicy.UPDATE, feina)
}
