package cat.jordi.consum.dades

import org.junit.Assert.assertEquals
import org.junit.Test

class VegadesTest {
    private val t0 = 1_792_800_000L

    private fun aparell(id: Long, c: Categoria) = Aparell(id, c.name, c, Font.TIPIC, c.kwhAnyTipic)
    private val rentaplats = aparell(1, Categoria.RENTAPLATS)
    private val bullidor = aparell(2, Categoria.BULLIDOR)
    private val estufa = aparell(3, Categoria.ESTUFA)
    private val placa = aparell(4, Categoria.PLACA)

    /** Una vegada de [w] W i [minuts] minuts; [variacio] 0,9 polsa, com la placa. */
    private fun vegada(w: Double, minuts: Int, variacio: Double = 0.0, canal: Int = 0) =
        Vegada(0, canal, t0, t0 + (minuts - 1) * 60L, minuts, w, w * minuts / 60_000, variacio, 1.0)

    private val refs = mapOf(
        1L to Referencia(1, 2175.0, 4, 10.0, variacio = 0.0, arrencada = 1.0),
        2L to Referencia(2, 2100.0, 3, 3.0, variacio = 0.0, arrencada = 1.0),
        3L to Referencia(3, 964.0, 12, 3.0, 771.0, 1211.0, variacio = 0.0, arrencada = 1.0),
        4L to Referencia(4, 1500.0, 3, null, variacio = 0.9, arrencada = 0.6),
    )
    private val tots = listOf(rentaplats, bullidor, estufa, placa)

    @Test
    fun elQueSEnsenyaEsSeu() {
        // El rentaplats de diumenge, ensenyat amb una estona passada: és seu encara que s'assembli al bullidor
        val e = listOf(Etiqueta(0, t0 - 60, t0 + 9 * 60, 1))
        assertEquals(1L to Com.ENSENYAT, assigna(vegada(2100.0, 3), tots, refs, e))
        // A la línia (un altre canal), aquesta etiqueta no hi compta
        assertEquals(null to null, assigna(vegada(2100.0, 3, canal = 1), emptyList(), emptyMap(), e))
        // Un instant (una pregunta respost) dins de la vegada també
        assertEquals(3L to Com.ENSENYAT, assigna(vegada(500.0, 4), tots, refs, listOf(Etiqueta(0, t0 + 120, t0 + 120, 3))))
    }

    @Test
    fun unaEtiquetaNoSEmportaElQueNoEsSeu() {
        // El cotxe de 19:54 i la placa que s'hi encén a sobre a les 20:31 són una sola vegada: la resposta «Placa» (un instant
        // lluny de l'inici) no se l'emporta; la del cotxe, sí
        val llarga = Vegada(0, 0, t0, t0 + 300 * 60L, 301, 2800.0, 14.0, 0.0, 1.0)
        assertEquals(null to null, assigna(llarga, tots, refs, listOf(Etiqueta(0, t0 + 37 * 60L, t0 + 37 * 60L, 4))))
        assertEquals(9L to Com.ENSENYAT, assigna(llarga, tots, refs, listOf(Etiqueta(0, t0, t0, 9))))
        // Una estona que només en cobreix un trosset (una rentadora ensenyada 20 minuts dins d'una vegada de 5 hores), tampoc
        assertEquals(null to null, assigna(llarga, tots, refs, listOf(Etiqueta(0, t0 + 3600, t0 + 3600 + 20 * 60L, 1))))
        // El que ara està sempre encès no es queda res, encara que se n'hagués dit
        assertEquals(null to null, assigna(vegada(2100.0, 3), emptyList(), refs, listOf(Etiqueta(0, t0, t0, 2)), exclosos = setOf(2L)))
    }

    @Test
    fun lIndexTrobaLesEtiquetesDUnaVegada() {
        val e = listOf(
            Etiqueta(0, t0 - 4 * 3600, t0 - 3 * 3600, 1), Etiqueta(0, t0 - 60, t0 - 60, 2),
            Etiqueta(0, t0 + 2 * 60, t0 + 2 * 60, 3), Etiqueta(1, t0, t0, 4), Etiqueta(0, t0 + 3600, t0 + 3600, 5),
        )
        val trobades = IndexEtiquetes(e).de(vegada(2000.0, 10)).map { it.aparellId }.toSet()
        // La de fa 3 hores, la de la línia i la d'una hora després, no; les dues d'a prop, sí
        assertEquals(setOf(2L, 3L), trobades)
    }

    @Test
    fun laMateixaPotenciaLaDecideixLaDurada() {
        assertEquals(1L to Com.RECONEGUT, assigna(vegada(2150.0, 11), tots, refs, emptyList()))
        assertEquals(2L to Com.RECONEGUT, assigna(vegada(2150.0, 3), tots, refs, emptyList()))
    }

    @Test
    fun unIntervalIUnaFormaQueNoQuadra() {
        // L'estufa, dins del seu interval (amb el 15 % de marge)
        assertEquals(3L to Com.RECONEGUT, assigna(vegada(1300.0, 2), tots, refs, emptyList()))
        // 1,5 kW que polsen: la placa, no l'estufa ni el bullidor
        assertEquals(4L to Com.RECONEGUT, assigna(vegada(1500.0, 20, variacio = 0.9), tots, refs, emptyList()))
        // 1,5 kW constants durant 20 minuts: ningú
        assertEquals(null to null, assigna(vegada(1500.0, 20), tots, refs, emptyList()))
    }

    @Test
    fun sNoEraEllNoTornaASerSeu() {
        val no = listOf(Etiqueta(0, t0, t0 + 10 * 60, 1, no = true))
        assertEquals(null to null, assigna(vegada(2150.0, 11), tots, refs, no))
    }

    @Test
    fun senseReferenciaNoEsReconeix() {
        // Un aparell amb només la potència típica (sense mostres) no es queda res
        assertEquals(null to null, assigna(vegada(2000.0, 10), listOf(aparell(9, Categoria.RENTADORA)), emptyMap(), emptyList()))
    }
}
