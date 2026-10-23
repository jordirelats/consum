package cat.jordi.consum.dades

import android.content.Context

/** La configuració de l'usuari: on és el Shelly, què mesura la segona pinça, el núvol i el preu. */
class Config(context: Context) {
    private val p = context.getSharedPreferences("config", Context.MODE_PRIVATE)

    private fun text(clau: String) = p.getString(clau, "") ?: ""
    private fun desa(clau: String, valor: String) = p.edit().putString(clau, valor.trim()).apply()

    /** L'adreça del Shelly a la xarxa de casa, amb el port si no és el 80 (per exemple 192.168.1.40). */
    var host: String
        get() = text("host")
        set(v) = desa("host", v)

    /** L'identificador del Shelly al núvol: la MAC en minúscules. */
    var idDispositiu: String
        get() = text("id")
        set(v) = desa("id", v)

    var model: String
        get() = text("model")
        set(v) = desa("model", v)

    /** El nom de la línia que mesura la segona pinça. Buit vol dir que no n'hi ha. */
    var nomLinia: String
        get() = text("nom_linia")
        set(v) = desa("nom_linia", v)

    var servidorNuvol: String
        get() = text("servidor_nuvol")
        set(v) = desa("servidor_nuvol", v.removePrefix("https://").removeSuffix("/"))

    var clauNuvol: String
        get() = text("clau_nuvol")
        set(v) = desa("clau_nuvol", v)

    /** Com es paga la llum. Les versions 0.1 i 0.2 només desaven un preu: es converteix en una tarifa de preu únic. */
    var tarifa: Tarifa
        get() = Tarifa.de(p.getString("tarifa", null)) ?: Tarifa(preuFix = p.getFloat("preu", 0.25f).toDouble())
        set(v) = p.edit().putString("tarifa", v.json()).apply()

    /** A partir de quants W sense explicar l'app pregunta què és. */
    var llindarAvis: Double
        get() = p.getFloat("llindar_avis", 300f).toDouble()
        set(v) = p.edit().putFloat("llindar_avis", v.toFloat()).apply()

    // El consum inesperat que s'està seguint: des de quan, de quants W, i la pregunta que ja se n'ha fet (0 si cap)
    var inesperatDes: Long
        get() = p.getLong("inesperat_des", 0)
        set(v) = p.edit().putLong("inesperat_des", v).apply()
    var inesperatW: Double
        get() = p.getFloat("inesperat_w", 0f).toDouble()
        set(v) = p.edit().putFloat("inesperat_w", v.toFloat()).apply()
    var inesperatPregunta: Long
        get() = p.getLong("inesperat_pregunta", 0)
        set(v) = p.edit().putLong("inesperat_pregunta", v).apply()

    /** L'última vegada que s'ha mirat si hi ha una versió nova (ms). */
    var darreraComprovacio: Long
        get() = p.getLong("darrera_comprovacio", 0)
        set(v) = p.edit().putLong("darrera_comprovacio", v).apply()

    /** Configurada vol dir que sap on llegir: el Shelly de casa, el núvol o tots dos. */
    val configurat get() = host.isNotBlank() || teNuvol
    val teLinia get() = nomLinia.isNotBlank()
    val teNuvol get() = servidorNuvol.isNotBlank() && clauNuvol.isNotBlank() && idDispositiu.isNotBlank()
}
