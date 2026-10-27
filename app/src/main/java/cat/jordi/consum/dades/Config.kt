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
        get() = synchronized(this) {
            // La pantalla la llegeix a cada recomposició: només es torna a llegir el JSON quan ha canviat
            val text = p.getString("tarifa", null)
            tarifaLlegida?.takeIf { text == tarifaText }
                ?: (Tarifa.de(text) ?: Tarifa(preuFix = p.getFloat("preu", 0.25f).toDouble())).also { tarifaText = text; tarifaLlegida = it }
        }
        set(v) = p.edit().putString("tarifa", v.json()).apply()
    private var tarifaText: String? = null
    private var tarifaLlegida: Tarifa? = null

    /**
     * Els avisos de consum inesperat: si n'hi ha, a partir de quants W sense explicar i durant quants minuts. Fins a la
     * 0.19 eren 300 W durant 10 minuts, fixos, i saltaven massa (24 vegades en una setmana a Guíxols).
     */
    var avisInesperat: Boolean
        get() = p.getBoolean("avis_inesperat", true)
        set(v) = p.edit().putBoolean("avis_inesperat", v).apply()
    var llindarInesperat: Double
        get() = p.getFloat("llindar_inesperat", 2000f).toDouble()
        set(v) = p.edit().putFloat("llindar_inesperat", v.toFloat()).apply()
    var minutsInesperat: Int
        get() = p.getInt("minuts_inesperat", 30)
        set(v) = p.edit().putInt("minuts_inesperat", v).apply()

    // L'última vegada que el consum inesperat passava del llindar, i l'últim avís (s)
    var inesperatAlt: Long
        get() = p.getLong("inesperat_alt", 0)
        set(v) = p.edit().putLong("inesperat_alt", v).apply()
    var darrerAvisInesperat: Long
        get() = p.getLong("darrer_avis_inesperat", 0)
        set(v) = p.edit().putLong("darrer_avis_inesperat", v).apply()

    /**
     * Omplir el que falta amb el consum per hores del núvol (maqueta 11): les absències de més de 7 dies i el d'abans
     * d'instal·lar l'app. Els dies ja mirats (epoch day, separats per comes), perquè no es tornin a demanar, i el primer
     * dia que el núvol ja no té (no cal mirar més enrere).
     */
    var nuvolHores: Boolean
        get() = p.getBoolean("nuvol_hores", true)
        set(v) = p.edit().putBoolean("nuvol_hores", v).apply()
    var nuvolMirats: Set<Long>
        get() = text("nuvol_mirats").split(",").mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = desa("nuvol_mirats", v.sorted().joinToString(","))
    var nuvolSenseAbans: Long
        get() = p.getLong("nuvol_sense_abans", Long.MIN_VALUE)
        set(v) = p.edit().putLong("nuvol_sense_abans", v).apply()
    var nuvolDarrer: Long
        get() = p.getLong("nuvol_darrer", 0)
        set(v) = p.edit().putLong("nuvol_darrer", v).apply()

    /** El compressor de la nevera trobat a les dades (W; 0 si no s'ha trobat) i quan s'ha mirat (s). */
    var neveraW: Double
        get() = p.getFloat("nevera_w", 0f).toDouble()
        set(v) = p.edit().putFloat("nevera_w", v.toFloat()).apply()
    var neveraMirada: Long
        get() = p.getLong("nevera_mirada", 0)
        set(v) = p.edit().putLong("nevera_mirada", v).apply()

    /** Quina part del temps funciona el compressor de la nevera (de 0 a 1), mirat a les nits (maqueta 13). */
    var neveraCicle: Double
        get() = p.getFloat("nevera_cicle", 0f).toDouble()
        set(v) = p.edit().putFloat("nevera_cicle", v.toFloat()).apply()

    /** Fins a quin minut s'han buscat les vegades (maqueta 13, s; 0: encara cap). */
    var vegadesFins: Long
        get() = p.getLong("vegades_fins", 0)
        set(v) = p.edit().putLong("vegades_fins", v).apply()

    /** Quan s'han buscat per últim cop els episodis de la línia i el que es repeteix (maqueta 12, s). */
    var liniaMirada: Long
        get() = p.getLong("linia_mirada", 0)
        set(v) = p.edit().putLong("linia_mirada", v).apply()
    var repetitsMirats: Long
        get() = p.getLong("repetits_mirats", 0)
        set(v) = p.edit().putLong("repetits_mirats", v).apply()

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

    /**
     * L'última lectura dels comptadors d'energia: el minut i els Wh de cada pinça. És el punt de partida del
     * consum aproximat de fora de casa. Es desa com a text perquè un Float perdria els decimals dels Wh.
     */
    var comptador: Comptador?
        get() = text("comptador").split(";").takeIf { it.size == 3 }?.let { (ts, wh, linia) ->
            Comptador(ts.toLongOrNull() ?: return null, wh.toDoubleOrNull() ?: return null, linia.toDoubleOrNull())
        }
        set(v) = desa("comptador", v?.let { "${it.ts};${it.wh};${it.whLinia ?: ""}" } ?: "")

    /** L'avís quan la potència passa del 90 % de la contractada. */
    var avisPotencia: Boolean
        get() = p.getBoolean("avis_potencia", true)
        set(v) = p.edit().putBoolean("avis_potencia", v).apply()

    /** Si l'usuari ha dit «Ara no» a la pregunta de la potència contractada, a la pantalla Ara. */
    var potenciaNoPreguntis: Boolean
        get() = p.getBoolean("potencia_no_preguntis", false)
        set(v) = p.edit().putBoolean("potencia_no_preguntis", v).apply()

    /** Amb l'app tancada: al moment (el servei que vigila cada 10 s) o cada 15 minuts (el treball de fons). */
    var avisAlMoment: Boolean
        get() = p.getBoolean("avis_al_moment", true)
        set(v) = p.edit().putBoolean("avis_al_moment", v).apply()

    /** L'últim avís de potència (s), perquè no se n'enviïn seguits. */
    var darrerAvisPotencia: Long
        get() = p.getLong("darrer_avis_potencia", 0)
        set(v) = p.edit().putLong("darrer_avis_potencia", v).apply()

    /** L'última vegada que s'ha mirat si hi ha una versió nova (ms). */
    var darreraComprovacio: Long
        get() = p.getLong("darrera_comprovacio", 0)
        set(v) = p.edit().putLong("darrera_comprovacio", v).apply()

    /** Configurada vol dir que sap on llegir: el Shelly de casa, el núvol o tots dos. */
    val configurat get() = host.isNotBlank() || teNuvol
    val teLinia get() = nomLinia.isNotBlank()
    val teNuvol get() = servidorNuvol.isNotBlank() && clauNuvol.isNotBlank() && idDispositiu.isNotBlank()
}

/** Els comptadors d'energia del Shelly (Wh acumulats) en un minut. */
data class Comptador(val ts: Long, val wh: Double, val whLinia: Double?)
