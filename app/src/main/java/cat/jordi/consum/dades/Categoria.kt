package cat.jordi.consum.dades

import androidx.annotation.StringRes
import cat.jordi.consum.R

/**
 * Els tipus d'aparell, amb un consum anual típic (kWh/any) per quan encara no se'n sap l'etiqueta, i la potència
 * típica quan funciona (W), per endevinar què és un consum que l'app no coneix.
 * Són valors orientatius d'una llar europea; l'etiqueta o l'entrenament els substitueixen.
 */
enum class Categoria(@StringRes val nomId: Int, val kwhAnyTipic: Double, val wTipic: Double) {
    NEVERA(R.string.categoria_nevera, 250.0, 100.0),
    CONGELADOR(R.string.categoria_congelador, 220.0, 100.0),
    FORN(R.string.categoria_forn, 100.0, 2200.0),
    PLACA(R.string.categoria_placa, 250.0, 1800.0),
    MICROONES(R.string.categoria_microones, 40.0, 1000.0),
    RENTAPLATS(R.string.categoria_rentaplats, 230.0, 1900.0),
    CAMPANA(R.string.categoria_campana, 30.0, 150.0),
    CAFETERA(R.string.categoria_cafetera, 60.0, 1200.0),
    BULLIDOR(R.string.categoria_bullidor, 90.0, 2000.0),
    TORRADORA(R.string.categoria_torradora, 20.0, 900.0),
    ROBOT(R.string.categoria_robot, 20.0, 800.0),
    RENTADORA(R.string.categoria_rentadora, 130.0, 2000.0),
    ASSECADORA(R.string.categoria_assecadora, 250.0, 2200.0),
    PLANXA(R.string.categoria_planxa, 50.0, 2000.0),
    TELEVISOR(R.string.categoria_televisor, 100.0, 100.0),
    CONSOLA(R.string.categoria_consola, 60.0, 150.0),
    ROUTER(R.string.categoria_router, 90.0, 10.0),
    LLUMS(R.string.categoria_llums, 150.0, 60.0),
    ORDINADOR(R.string.categoria_ordinador, 120.0, 120.0),
    ASSECADOR_CABELLS(R.string.categoria_assecador_cabells, 30.0, 1800.0),
    TOVALLOLER(R.string.categoria_tovalloler, 150.0, 300.0),
    ESTUFA(R.string.categoria_estufa, 400.0, 1500.0),
    AIRE(R.string.categoria_aire, 300.0, 900.0),
    BOMBA_CALOR(R.string.categoria_bomba_calor, 2500.0, 1500.0),
    TERMO(R.string.categoria_termo, 1500.0, 1500.0),
    CALDERA(R.string.categoria_caldera, 80.0, 80.0),
    COTXE(R.string.categoria_cotxe, 2000.0, 3500.0),
    BOMBA_AIGUA(R.string.categoria_bomba_aigua, 100.0, 800.0),
    DEPURADORA(R.string.categoria_depuradora, 800.0, 700.0),
    ALTRES(R.string.categoria_altres, 50.0, 200.0);

    companion object {
        fun de(clau: String?) = entries.firstOrNull { it.name == clau } ?: ALTRES
    }
}

/** D'on surt el consum teòric d'un aparell. */
enum class Font(@StringRes val nomId: Int, val punts: Int) {
    ETIQUETA(R.string.font_etiqueta, 2),
    PLACA(R.string.font_placa, 2),
    MANUAL(R.string.font_manual, 2),
    TIPIC(R.string.font_tipic, 1);

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
