package cat.jordi.consum.dades

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** El que diu l'etiqueta energètica d'un aparell, tret de l'EPREL (la base de dades europea). */
data class FitxaEprel(
    val numero: String,
    val grup: String,
    val categoria: Categoria,
    val marca: String,
    val model: String,
    val classe: String,
    /** El consum anual que se'n dedueix (kWh/any), o null si l'etiqueta no el permet calcular. */
    val kwhAny: Double?,
    val esperaW: Double?,
    /** Com s'ha calculat el consum anual, per ensenyar-ho. */
    val explicacio: String,
) {
    fun comAparell(nom: String) = Aparell(
        nom = nom, categoria = categoria, font = Font.ETIQUETA, kwhAny = kwhAny ?: categoria.kwhAnyTipic,
        esperaW = esperaW, marca = marca, model = model, classe = classe, eprel = numero,
    )
}

/**
 * L'EPREL: el QR de l'etiqueta porta a eprel.ec.europa.eu/qr/<número>, i /api/product/<número>
 * dona la fitxa sense cap clau. (Cercar per marca i model sí que en demana, per això no es fa.)
 */
object Eprel {
    private val reQr = Regex("""eprel\.ec\.europa\.eu/(?:qr|screen/product/[^/]+)/(\d+)""")

    /** El número de registre d'un QR escanejat, o d'un número escrit a mà. */
    fun numero(text: String): String? {
        reQr.find(text)?.let { return it.groupValues[1] }
        val net = text.trim()
        return net.takeIf { it.length in 4..10 && it.all(Char::isDigit) }
    }

    fun fitxa(numero: String): FitxaEprel {
        val c = URL("https://eprel.ec.europa.eu/api/product/$numero").openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/json")
        val text = try {
            if (c.responseCode == 404) throw IOException("No hi ha cap aparell amb el número $numero")
            if (c.responseCode !in 200..299) throw IOException("L'EPREL ha respost ${c.responseCode}")
            c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
        return llegeix(numero, JSONObject(text))
    }

    private fun JSONObject.num(vararg claus: String): Double? =
        claus.firstNotNullOfOrNull { k -> if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null }

    private fun categoria(grup: String) = when {
        grup.startsWith("refrigeratingappliances") -> Categoria.NEVERA
        grup.startsWith("washerdriers") -> Categoria.RENTADORA
        grup.startsWith("washingmachines") -> Categoria.RENTADORA
        grup.startsWith("dishwashers") -> Categoria.RENTAPLATS
        grup.startsWith("tumbledr") -> Categoria.ASSECADORA
        grup.startsWith("electronicdisplays") || grup.startsWith("televisions") -> Categoria.TELEVISOR
        grup.startsWith("ovens") -> Categoria.FORN
        grup.startsWith("rangehoods") -> Categoria.CAMPANA
        grup.startsWith("airconditioners") -> Categoria.AIRE
        grup.startsWith("waterheaters") -> Categoria.TERMO
        grup.startsWith("lightsources") -> Categoria.LLUMS
        else -> Categoria.ALTRES
    }

    fun llegeix(numero: String, o: JSONObject): FitxaEprel {
        val grup = o.optString("productGroup")
        val cat = categoria(grup)
        // Els usos anuals són els que fa servir l'etiqueta europea per a cada tipus d'aparell.
        val (kwh, explicacio) = when {
            // Les neveres noves el porten a energyConsAnnualV2 (energyConsAnnual hi és, però buit)
            o.num("energyConsAnnual", "energyConsAnnualV2", "consolidatedEnergyConsAnnual") != null ->
                o.num("energyConsAnnual", "energyConsAnnualV2", "consolidatedEnergyConsAnnual") to "consum anual de l'etiqueta"
            cat == Categoria.RENTADORA && o.num("energyConsPer100Cycle") != null ->
                o.num("energyConsPer100Cycle")!! * 2.2 to "${fmt(o.num("energyConsPer100Cycle")!!)} kWh cada 100 rentades, a 220 rentades l'any"
            cat == Categoria.RENTAPLATS && o.num("energyCons100") != null ->
                o.num("energyCons100")!! * 2.8 to "${fmt(o.num("energyCons100")!!)} kWh cada 100 rentades, a 280 rentades l'any"
            o.num("powerOnModeSDR", "powerOnMode") != null -> {
                val w = o.num("powerOnModeSDR", "powerOnMode")!!
                val hores = if (cat == Categoria.LLUMS) 3 else 4
                w * hores * 365 / 1000 to "${fmt(w)} W encès, $hores hores al dia"
            }
            else -> null to "l'etiqueta no dona un consum anual: s'ha posat un valor típic"
        }
        return FitxaEprel(
            numero = numero, grup = grup, categoria = cat,
            marca = o.optString("supplierOrTrademark"), model = o.optString("modelIdentifier"),
            classe = o.optString("energyClass"), kwhAny = kwh,
            esperaW = o.num("powerStandbyMode", "powerStandby", "powerNetworkStandby", "powerOffMode"),
            explicacio = explicacio,
        )
    }

    private fun fmt(x: Double) = if (x % 1.0 == 0.0) x.toInt().toString() else x.toString().replace('.', ',')
}
