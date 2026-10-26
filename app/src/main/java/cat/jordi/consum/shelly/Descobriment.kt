package cat.jordi.consum.shelly

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.util.ArrayDeque

/** Un Shelly trobat a la wifi. */
data class Trobat(val nom: String, val host: String)

/**
 * Busca Shelly a la xarxa local de dues maneres alhora:
 * - per mDNS (els Gen2 i Gen3 s'anuncien com a _shelly._tcp), que molts routers no deixen passar
 *   (sobretot entre la banda de 2,4 GHz i la de 5, o amb repetidors);
 * - preguntant a cada adreça de la wifi si és un Shelly amb mesura d'energia ([escaneja]).
 * Crida [enTrobar] al fil principal per cada un que troba. Retorna una funció per aturar la cerca.
 */
fun descobreix(context: Context, enTrobar: (Trobat) -> Unit): () -> Unit {
    val principal = Handler(Looper.getMainLooper())
    val troba: (Trobat) -> Unit = { t -> principal.post { enTrobar(t) } }
    val atura = mdns(context, troba)
    val feina = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    feina.launch { escaneja(context, troba) }
    return {
        atura()
        feina.cancel()
    }
}

private fun mdns(context: Context, enTrobar: (Trobat) -> Unit): () -> Unit {
    val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    val cua = ArrayDeque<NsdServiceInfo>()
    var resolent = false

    // L'NsdManager només resol un servei alhora: es resolen d'un en un, en cua.
    fun seguent(): Unit = synchronized(cua) {
        if (resolent) return
        val servei = cua.pollFirst() ?: return
        resolent = true
        @Suppress("DEPRECATION")
        try { nsd.resolveService(servei, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, codi: Int) {
                resolent = false
                seguent()
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                val ip = (info.host as? Inet4Address)?.hostAddress
                if (ip != null) enTrobar(Trobat(info.serviceName, if (info.port == 80) ip else "$ip:${info.port}"))
                resolent = false
                seguent()
            }
        }) } catch (_: IllegalArgumentException) {
            // Un oient que encara es fa servir (alguns Android antics): se salta aquest servei
            resolent = false
        }
    }

    val oient = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(tipus: String) {}
        override fun onDiscoveryStopped(tipus: String) {}
        override fun onStartDiscoveryFailed(tipus: String, codi: Int) {}
        override fun onStopDiscoveryFailed(tipus: String, codi: Int) {}
        override fun onServiceLost(info: NsdServiceInfo) {}
        override fun onServiceFound(info: NsdServiceInfo) {
            synchronized(cua) { cua.addLast(info) }
            seguent()
        }
    }
    nsd.discoverServices("_shelly._tcp", NsdManager.PROTOCOL_DNS_SD, oient)
    return { runCatching { nsd.stopServiceDiscovery(oient) } }
}

/**
 * Pregunta a les 254 adreces de la wifi del mòbil (la seva /24) si són un Shelly: /shelly, i després, si té
 * mesura d'energia (em1:0 a Shelly.GetStatus). En pocs segons, amb 48 preguntes alhora.
 */
private suspend fun escaneja(context: Context, enTrobar: (Trobat) -> Unit) {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val xarxa = cm.activeNetwork ?: return
    if (cm.getNetworkCapabilities(xarxa)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return
    val propia = cm.getLinkProperties(xarxa)?.linkAddresses?.map { it.address }?.filterIsInstance<Inet4Address>()?.firstOrNull() ?: return
    val b = propia.address.map { it.toInt() and 0xff }
    val alhora = Semaphore(48)
    coroutineScope {
        for (n in 1..254) {
            if (n == b[3]) continue
            val ip = "${b[0]}.${b[1]}.${b[2]}.$n"
            launch { alhora.withPermit { shellyA(ip)?.let(enTrobar) } }
        }
    }
}

/** Si a [ip] hi ha un Shelly Gen2 o Gen3 amb mesura d'energia, el seu nom; si no, null. */
private fun shellyA(ip: String): Trobat? {
    val info = runCatching { JSONObject(llegeixUrl("http://$ip/shelly", 600)) }.getOrNull() ?: return null
    if (info.optInt("gen") < 2) return null
    val estat = runCatching { JSONObject(llegeixUrl("http://$ip/rpc/Shelly.GetStatus", 2000)) }.getOrNull() ?: return null
    if (!estat.has("em1:0")) return null
    val nom = info.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: info.optString("id")
    return Trobat(nom, ip)
}

private fun llegeixUrl(url: String, tempsMs: Int): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = tempsMs
    c.readTimeout = tempsMs
    try {
        if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().readText()
    } finally {
        c.disconnect()
    }
}
