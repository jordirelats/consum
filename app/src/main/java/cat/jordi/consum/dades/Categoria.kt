package cat.jordi.consum.dades

/**
 * Els tipus d'aparell, amb un consum anual típic (kWh/any) per quan encara no se'n sap l'etiqueta, i la potència
 * típica quan funciona (W), per endevinar què és un consum que l'app no coneix.
 * Són valors orientatius d'una llar europea; l'etiqueta o l'entrenament els substitueixen.
 */
enum class Categoria(val nom: String, val kwhAnyTipic: Double, val wTipic: Double) {
    NEVERA("Nevera", 250.0, 100.0),
    CONGELADOR("Congelador", 220.0, 100.0),
    FORN("Forn", 100.0, 2200.0),
    PLACA("Placa", 250.0, 1800.0),
    MICROONES("Microones", 40.0, 1000.0),
    RENTAPLATS("Rentaplats", 230.0, 1900.0),
    CAMPANA("Campana", 30.0, 150.0),
    CAFETERA("Cafetera", 60.0, 1200.0),
    BULLIDOR("Bullidor", 90.0, 2000.0),
    TORRADORA("Torradora", 20.0, 900.0),
    ROBOT("Robot de cuina", 20.0, 800.0),
    RENTADORA("Rentadora", 130.0, 2000.0),
    ASSECADORA("Assecadora", 250.0, 2200.0),
    PLANXA("Planxa", 50.0, 2000.0),
    TELEVISOR("Televisor", 100.0, 100.0),
    CONSOLA("Consola", 60.0, 150.0),
    ROUTER("Router", 90.0, 10.0),
    LLUMS("Llums", 150.0, 60.0),
    ORDINADOR("Ordinador", 120.0, 120.0),
    ASSECADOR_CABELLS("Assecador de cabells", 30.0, 1800.0),
    TOVALLOLER("Tovalloler elèctric", 150.0, 300.0),
    ESTUFA("Estufa elèctrica", 400.0, 1500.0),
    AIRE("Aire condicionat", 300.0, 900.0),
    BOMBA_CALOR("Bomba de calor", 2500.0, 1500.0),
    TERMO("Termo elèctric", 1500.0, 1500.0),
    CALDERA("Caldera de gas (la part elèctrica)", 80.0, 80.0),
    COTXE("Cotxe elèctric", 2000.0, 3500.0),
    BOMBA_AIGUA("Bomba d'aigua", 100.0, 800.0),
    DEPURADORA("Depuradora", 800.0, 700.0),
    ALTRES("Un altre", 50.0, 200.0);

    companion object {
        fun de(clau: String?) = entries.firstOrNull { it.name == clau } ?: ALTRES
    }
}

/** D'on surt el consum teòric d'un aparell. */
enum class Font(val nom: String, val punts: Int) {
    ETIQUETA("Etiqueta (QR)", 2),
    PLACA("Placa", 2),
    MANUAL("Escrit a mà", 2),
    TIPIC("Valor típic", 1);

    companion object {
        fun de(clau: String?) = entries.firstOrNull { it.name == clau } ?: TIPIC
    }
}

data class Aparell(
    val id: Long = 0,
    val nom: String,
    val categoria: Categoria,
    val font: Font,
    val kwhAny: Double,
    val esperaW: Double? = null,
    val marca: String = "",
    val model: String = "",
    val classe: String = "",
    val eprel: String = "",
    /** La potència de la placa de característiques (W), si se n'ha fet la foto. */
    val potenciaW: Double? = null,
) {
    /** Encara ve del qüestionari o d'un valor típic: falta escanejar-ne l'etiqueta. */
    val perCompletar get() = font == Font.TIPIC
}
