package cat.jordi.consum.shelly

import cat.jordi.consum.dades.Minut
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** El que diu una pinça en un instant. */
data class Canal(val w: Double, val va: Double, val pf: Double, val v: Double)

enum class Origen { LOCAL, NUVOL }

/**
 * Una lectura en directe: la pinça 1 (total) i la pinça 2 (línia), si n'hi ha, i els seus comptadors d'energia
 * (Wh acumulats; el núvol els actualitza cada minut).
 */
data class Lectura(
    val total: Canal, val linia: Canal?, val origen: Origen, val ts: Long,
    val whTotal: Double? = null, val whLinia: Double? = null,
) {
    companion object {
        /** Llegeix l'estat de Shelly.GetStatus, que és igual en local i al núvol. */
        fun de(estat: JSONObject, origen: Origen): Lectura? {
            val total = canal(estat.optJSONObject("em1:0")) ?: return null
            return Lectura(
                total, canal(estat.optJSONObject("em1:1")), origen, System.currentTimeMillis() / 1000,
                comptador(estat.optJSONObject("em1data:0")), comptador(estat.optJSONObject("em1data:1")),
            )
        }

        private fun comptador(o: JSONObject?) = o?.optDouble("total_act_energy")?.takeIf { !it.isNaN() }

        private fun canal(o: JSONObject?) = o?.let {
            Canal(it.optDouble("act_power", 0.0), it.optDouble("aprt_power", 0.0), it.optDouble("pf", 0.0), it.optDouble("voltage", 0.0))
        }
    }
}

data class InfoShelly(val model: String, val idDispositiu: String, val nom: String, val versio: String)

private fun llegeix(c: HttpURLConnection): String {
    try {
        if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().readText()
    } finally {
        c.disconnect()
    }
}

/** El Shelly a la xarxa de casa (API RPC de Gen2/Gen3, per HTTP). */
class ShellyLocal(private val host: String) {

    private fun rpc(metode: String, tempsMs: Int = 3000): JSONObject {
        val c = URL("http://$host/rpc/$metode").openConnection() as HttpURLConnection
        c.connectTimeout = tempsMs
        c.readTimeout = tempsMs
        return JSONObject(llegeix(c))
    }

    fun info(): InfoShelly {
        val o = rpc("Shelly.GetDeviceInfo")
        return InfoShelly(
            model = o.optString("model"),
            idDispositiu = o.optString("mac").lowercase(),
            nom = o.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: o.optString("id"),
            versio = o.optString("ver"),
        )
    }

    fun lectura(): Lectura? = Lectura.de(rpc("Shelly.GetStatus", 1500), Origen.LOCAL)

    /**
     * Les dades per minut d'un canal a partir de [desDe], totes les pàgines.
     * El Shelly en guarda una setmana o més; el que ja s'ha esborrat, no torna.
     */
    fun minuts(canal: Int, desDe: Long, enCadaPagina: (List<Minut>) -> Unit) {
        var ts: Long? = desDe
        while (ts != null) {
            val o = rpc("EM1Data.GetData?id=$canal&ts=$ts", 10_000)
            val claus = o.getJSONArray("keys").let { k -> List(k.length()) { k.getString(it) } }
            val iEnergia = claus.indexOf("total_act_energy")
            if (iEnergia < 0) throw IOException("EM1Data sense total_act_energy")
            val iMax = claus.indexOf("max_act_power")
            val iMin = claus.indexOf("min_act_power")
            val files = mutableListOf<Minut>()
            val blocs = o.optJSONArray("data") ?: JSONArray()
            for (b in 0 until blocs.length()) {
                val bloc = blocs.getJSONObject(b)
                val inici = bloc.getLong("ts")
                val periode = bloc.optLong("period", 60)
                val valors = bloc.getJSONArray("values")
                for (i in 0 until valors.length()) {
                    val v = valors.getJSONArray(i)
                    files += Minut(inici + i * periode, v.getDouble(iEnergia), v.optDouble(iMax, 0.0), v.optDouble(iMin, 0.0))
                }
            }
            enCadaPagina(files)
            // Si el Shelly torna el mateix punt de partida, s'acaba: si no, no se'n sortiria mai
            ts = if (o.has("next_record_ts") && !o.isNull("next_record_ts")) o.getLong("next_record_ts").takeIf { it > ts!! } else null
        }
    }
}

/**
 * El núvol de Shelly (Cloud Control API v2), per veure el consum des de fora de casa.
 * Només dona l'estat del moment, amb el comptador d'energia; l'historial per minuts es recull a casa.
 * Màxim una petició per segon. El servidor va per HTTPS: la clau va a l'adreça. Només una adreça de la xarxa local
 * (el núvol fals de les proves, a l'emulador 10.0.2.2) pot anar per HTTP.
 */
class ShellyNuvol(private val servidor: String, private val clau: String, private val idDispositiu: String) {

    private fun local(host: String) =
        host == "localhost" || Regex("""(10|127)\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(1[6-9]|2\d|3[01])\.\d+\.\d+""").matches(host)

    fun lectura(): Lectura? {
        val sense = servidor.substringAfter("://")
        val base = if (servidor.startsWith("http://") && local(sense.substringBefore('/').substringBefore(':'))) servidor else "https://$sense"
        val c = URL("$base/v2/devices/api/get?auth_key=${URLEncoder.encode(clau, "UTF-8")}")
            .openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 5000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write("""{"ids":["$idDispositiu"],"select":["status"]}""".toByteArray()) }
        val llista = JSONArray(llegeix(c))
        if (llista.length() == 0) return null
        val estat = llista.getJSONObject(0).optJSONObject("status") ?: return null
        return Lectura.de(estat, Origen.NUVOL)
    }
}
