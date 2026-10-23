package cat.jordi.consum.shelly

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.ArrayDeque

/** Un Shelly trobat a la wifi. */
data class Trobat(val nom: String, val host: String)

/**
 * Busca Shelly a la xarxa local per mDNS (els Gen2 i Gen3 s'anuncien com a _shelly._tcp).
 * Crida [enTrobar] per cada un que resol. Retorna una funció per aturar la cerca.
 */
fun descobreix(context: Context, enTrobar: (Trobat) -> Unit): () -> Unit {
    val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    val cua = ArrayDeque<NsdServiceInfo>()
    var resolent = false

    // L'NsdManager només resol un servei alhora: es resolen d'un en un, en cua.
    fun seguent(): Unit = synchronized(cua) {
        if (resolent) return
        val servei = cua.pollFirst() ?: return
        resolent = true
        @Suppress("DEPRECATION")
        nsd.resolveService(servei, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, codi: Int) {
                resolent = false
                seguent()
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                val ip = info.host?.hostAddress
                if (ip != null) enTrobar(Trobat(info.serviceName, if (info.port == 80) ip else "$ip:${info.port}"))
                resolent = false
                seguent()
            }
        })
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
