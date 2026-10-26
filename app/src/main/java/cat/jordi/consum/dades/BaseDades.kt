package cat.jordi.consum.dades

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Un minut del Shelly: l'energia del minut (Wh) i les potències màxima i mínima (W). */
data class Minut(val ts: Long, val wh: Double, val pmax: Double, val pmin: Double)

/** Un punt de la gràfica: la potència mitjana del minut (W) del total i de la línia. [aprox]: calculat des de fora. */
data class Punt(val ts: Long, val wTotal: Double, val wLinia: Double, val aprox: Boolean = false)

/** L'energia d'una hora (kWh) repartida: sempre encès, la línia de la segona pinça i la resta sense identificar. */
data class Hora(val ts: Long, val sempre: Double, val linia: Double, val desconegut: Double) {
    val total get() = sempre + linia + desconegut
}

/**
 * Les dades queden al mòbil. Canal 0 = pinça 1 (tota la casa), canal 1 = pinça 2 (una línia).
 * La "resta" és el total menys la línia; d'aquí surten el "sempre encès" i el "sense identificar".
 * Els minuts aproximats (aprox = 1) es fan des de fora amb el comptador del núvol; els exactes, que es baixen
 * del Shelly a casa, els substitueixen.
 */
class BaseDades(context: Context) : SQLiteOpenHelper(context, "consum.db", null, 6) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE minuts (ts INTEGER NOT NULL, canal INTEGER NOT NULL, wh REAL NOT NULL,
               pmax REAL, pmin REAL, aprox INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (ts, canal)) WITHOUT ROWID"""
        )
        db.execSQL(
            """CREATE TABLE aparells (id INTEGER PRIMARY KEY AUTOINCREMENT, nom TEXT NOT NULL,
               categoria TEXT NOT NULL, font TEXT NOT NULL, kwh_any REAL NOT NULL, espera_w REAL,
               marca TEXT, model TEXT, classe TEXT, eprel TEXT, creat INTEGER NOT NULL, potencia_w REAL, funciona TEXT)"""
        )
        creaPreus(db)
        creaEntrenament(db)
    }

    /** L'entrenament (versió 3): les mostres de cada aparell i les preguntes sobre consums desconeguts. */
    private fun creaEntrenament(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE mostres (id INTEGER PRIMARY KEY AUTOINCREMENT, aparell INTEGER NOT NULL, ts INTEGER NOT NULL,
               dp REAL NOT NULL, durada INTEGER, font TEXT NOT NULL, wmin REAL, wmax REAL)"""
        )
        db.execSQL(
            """CREATE TABLE preguntes (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, dp REAL NOT NULL,
               estat TEXT NOT NULL, aparell INTEGER)"""
        )
    }

    /** Els preus de mercat per hores (versió 2): EnergyZero als Països Baixos, el PVPC a Espanya. */
    private fun creaPreus(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE preus (ts INTEGER NOT NULL, pais TEXT NOT NULL, valor REAL NOT NULL, PRIMARY KEY (ts, pais)) WITHOUT ROWID")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) creaPreus(db)
        if (oldVersion < 3) creaEntrenament(db)
        // Versió 4: la potència de la placa de característiques
        if (oldVersion < 4) db.execSQL("ALTER TABLE aparells ADD COLUMN potencia_w REAL")
        // Versió 5: els minuts aproximats, calculats des de fora
        if (oldVersion < 5) db.execSQL("ALTER TABLE minuts ADD COLUMN aprox INTEGER NOT NULL DEFAULT 0")
        // Versió 6: com funciona cada aparell (buit: el del seu tipus) i l'interval de potència de les mostres
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE aparells ADD COLUMN funciona TEXT")
            // Les taules de la versió 3 ja tenen les columnes noves si s'acaben de crear en aquesta actualització
            if (oldVersion >= 3) {
                db.execSQL("ALTER TABLE mostres ADD COLUMN wmin REAL")
                db.execSQL("ALTER TABLE mostres ADD COLUMN wmax REAL")
            }
        }
    }

    /** Els minuts exactes, baixats del Shelly: substitueixen els aproximats que hi hagués. */
    fun insereixMinuts(canal: Int, files: List<Minut>) = insereix(canal, files, aprox = false)

    /** Els minuts aproximats: no toquen els exactes que ja hi són. */
    fun insereixAproximats(canal: Int, files: List<Minut>) = insereix(canal, files, aprox = true)

    private fun insereix(canal: Int, files: List<Minut>, aprox: Boolean) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val sql = db.compileStatement(
                "INSERT OR ${if (aprox) "IGNORE" else "REPLACE"} INTO minuts (ts, canal, wh, pmax, pmin, aprox) VALUES (?, ?, ?, ?, ?, ${if (aprox) 1 else 0})"
            )
            for (m in files) {
                sql.bindLong(1, m.ts)
                sql.bindLong(2, canal.toLong())
                sql.bindDouble(3, m.wh)
                sql.bindDouble(4, m.pmax)
                sql.bindDouble(5, m.pmin)
                sql.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** L'últim minut exacte: d'aquí en endavant es baixa del Shelly (també on ara hi ha minuts aproximats). */
    fun darrerTs(canal: Int): Long? =
        readableDatabase.rawQuery("SELECT MAX(ts) FROM minuts WHERE canal = ? AND aprox = 0", arrayOf(canal.toString())).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }

    private val restaSql = """FROM minuts m0 LEFT JOIN minuts m1 ON m1.ts = m0.ts AND m1.canal = 1
        WHERE m0.canal = 0 AND m0.ts >= ? AND m0.ts < ?"""

    fun serie(desDe: Long, finsA: Long): List<Punt> =
        readableDatabase.rawQuery(
            "SELECT m0.ts, m0.wh * 60, IFNULL(m1.wh, 0) * 60, m0.aprox $restaSql ORDER BY m0.ts",
            arrayOf(desDe.toString(), finsA.toString())
        ).use { c ->
            buildList { while (c.moveToNext()) add(Punt(c.getLong(0), c.getDouble(1), c.getDouble(2), c.getInt(3) == 1)) }
        }

    /** El pic instantani (W) d'un minut exacte del total, o null si no se sap. */
    fun pmax(ts: Long): Double? =
        readableDatabase.rawQuery("SELECT pmax FROM minuts WHERE canal = 0 AND ts = ? AND aprox = 0", arrayOf(ts.toString())).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getDouble(0).takeIf { w -> w > 0 } else null
        }

    /** Si entre aquestes dues hores hi ha algun minut aproximat. */
    fun teAproximats(desDe: Long, finsA: Long): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM minuts WHERE aprox = 1 AND ts >= ? AND ts < ? LIMIT 1", arrayOf(desDe.toString(), finsA.toString())
        ).use { it.moveToFirst() }

    /**
     * El consum de fons (Wh per minut): el percentil 5 de la resta. Null si encara no hi ha prou dades.
     * Amb [nomesExactes], sense els minuts aproximats, que són mitjanes i el farien més alt.
     */
    fun fons(desDe: Long, finsA: Long, nomesExactes: Boolean): Double? {
        val args = arrayOf(desDe.toString(), finsA.toString())
        val filtre = if (nomesExactes) " AND m0.aprox = 0" else ""
        val n = readableDatabase.rawQuery("SELECT COUNT(*) $restaSql$filtre", args).use { if (it.moveToFirst()) it.getLong(0) else 0 }
        if (n < 60) return null
        return readableDatabase.rawQuery(
            "SELECT MAX(m0.wh - IFNULL(m1.wh, 0), 0) AS resta $restaSql$filtre ORDER BY resta LIMIT 1 OFFSET ${n / 20}", args
        ).use { if (it.moveToFirst()) it.getDouble(0) else null }
    }

    /**
     * L'energia per hores locals (kWh), amb el fons donat en Wh per minut. [desfas]: quants segons passa l'hora
     * local de l'hora en punt UTC ([desfasHora]). El fons es converteix a número a la consulta: rawQuery passa els
     * arguments com a text, i MIN(número, text) de SQLite torna sempre el número.
     */
    fun perHores(desDe: Long, finsA: Long, fonsWh: Double, desfas: Long): List<Hora> {
        val f = fonsWh.toString()
        val d = desfas.toString()
        return readableDatabase.rawQuery(
            """SELECT ((m0.ts + CAST(? AS INTEGER)) / 3600) * 3600 - CAST(? AS INTEGER) AS h,
                 SUM(MIN(MAX(m0.wh - IFNULL(m1.wh, 0), 0), CAST(? AS REAL))),
                 SUM(IFNULL(m1.wh, 0)),
                 SUM(MAX(m0.wh - IFNULL(m1.wh, 0) - CAST(? AS REAL), 0))
               $restaSql GROUP BY h ORDER BY h""",
            arrayOf(d, d, f, f, desDe.toString(), finsA.toString())
        ).use { c ->
            buildList { while (c.moveToNext()) add(Hora(c.getLong(0), c.getDouble(1) / 1000, c.getDouble(2) / 1000, c.getDouble(3) / 1000)) }
        }
    }

    fun desaPreus(pais: Pais, preus: List<Pair<Long, Double>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val sql = db.compileStatement("INSERT OR REPLACE INTO preus (ts, pais, valor) VALUES (?, ?, ?)")
            for ((ts, valor) in preus) {
                sql.bindLong(1, ts)
                sql.bindString(2, pais.name)
                sql.bindDouble(3, valor)
                sql.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun preus(pais: Pais, desDe: Long, finsA: Long): Map<Long, Double> =
        readableDatabase.rawQuery(
            "SELECT ts, valor FROM preus WHERE pais = ? AND ts >= ? AND ts < ?", arrayOf(pais.name, desDe.toString(), finsA.toString())
        ).use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getDouble(1)) } }

    fun primerTs(): Long? =
        readableDatabase.rawQuery("SELECT MIN(ts) FROM minuts", null).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }

    fun aparells(): List<Aparell> =
        readableDatabase.rawQuery(
            "SELECT id, nom, categoria, font, kwh_any, espera_w, marca, model, classe, eprel, potencia_w, funciona FROM aparells ORDER BY kwh_any DESC", null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val categoria = Categoria.de(c.getString(2))
                    add(
                        Aparell(
                            id = c.getLong(0), nom = c.getString(1), categoria = categoria,
                            font = Font.de(c.getString(3)), kwhAny = c.getDouble(4),
                            esperaW = if (c.isNull(5)) null else c.getDouble(5),
                            marca = c.getString(6) ?: "", model = c.getString(7) ?: "",
                            classe = c.getString(8) ?: "", eprel = c.getString(9) ?: "",
                            potenciaW = if (c.isNull(10)) null else c.getDouble(10),
                            funciona = Funcionament.de(c.getString(11), categoria),
                        )
                    )
                }
            }
        }

    fun desaAparell(a: Aparell) {
        val v = ContentValues().apply {
            put("nom", a.nom); put("categoria", a.categoria.name); put("font", a.font.name)
            put("kwh_any", a.kwhAny); put("espera_w", a.esperaW); put("marca", a.marca)
            put("model", a.model); put("classe", a.classe); put("eprel", a.eprel); put("potencia_w", a.potenciaW)
            put("funciona", a.funciona.name)
        }
        if (a.id == 0L) {
            v.put("creat", System.currentTimeMillis() / 1000)
            writableDatabase.insert("aparells", null, v)
        } else {
            writableDatabase.update("aparells", v, "id = ?", arrayOf(a.id.toString()))
        }
    }

    fun esborraAparell(id: Long) {
        writableDatabase.delete("aparells", "id = ?", arrayOf(id.toString()))
        esborraMostres(id)
    }

    fun desaMostra(aparellId: Long, ts: Long, dp: Double, durada: Long?, font: String) {
        writableDatabase.insert("mostres", null, ContentValues().apply {
            put("aparell", aparellId); put("ts", ts); put("dp", dp); put("durada", durada); put("font", font)
        })
    }

    fun teMostra(aparellId: Long, ts: Long, font: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM mostres WHERE aparell = ? AND ts = ? AND font = ? LIMIT 1", arrayOf(aparellId.toString(), ts.toString(), font),
        ).use { it.moveToFirst() }

    fun mostres(): List<Mostra> =
        readableDatabase.rawQuery("SELECT id, aparell, ts, dp, durada, wmin, wmax, font FROM mostres ORDER BY ts", null).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Mostra(
                        c.getLong(0), c.getLong(1), c.getLong(2), c.getDouble(3), if (c.isNull(4)) null else c.getLong(4),
                        if (c.isNull(5)) null else c.getDouble(5), if (c.isNull(6)) null else c.getDouble(6), c.getString(7) ?: "",
                    )
                )
            }
        }

    /** L'interval de potència d'una mostra, quan ja hi ha les dades de l'episodi. */
    fun desaInterval(mostraId: Long, wMin: Double, wMax: Double) {
        writableDatabase.update("mostres", ContentValues().apply { put("wmin", wMin); put("wmax", wMax) }, "id = ?", arrayOf(mostraId.toString()))
    }

    fun esborraMostres(aparellId: Long) {
        writableDatabase.delete("mostres", "aparell = ?", arrayOf(aparellId.toString()))
    }

    fun creaPregunta(ts: Long, dp: Double): Long =
        writableDatabase.insert("preguntes", null, ContentValues().apply {
            put("ts", ts); put("dp", dp); put("estat", EstatPregunta.PENDENT.name)
        })

    fun preguntes(estat: EstatPregunta): List<Pregunta> =
        readableDatabase.rawQuery("SELECT id, ts, dp, estat, aparell FROM preguntes WHERE estat = ? ORDER BY ts DESC", arrayOf(estat.name)).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Pregunta(c.getLong(0), c.getLong(1), c.getDouble(2), EstatPregunta.valueOf(c.getString(3)), if (c.isNull(4)) null else c.getLong(4))
                )
            }
        }

    fun pregunta(id: Long): Pregunta? =
        readableDatabase.rawQuery("SELECT id, ts, dp, estat, aparell FROM preguntes WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) Pregunta(c.getLong(0), c.getLong(1), c.getDouble(2), EstatPregunta.valueOf(c.getString(3)), if (c.isNull(4)) null else c.getLong(4)) else null
        }

    fun respon(id: Long, estat: EstatPregunta, aparellId: Long?) {
        writableDatabase.update("preguntes", ContentValues().apply { put("estat", estat.name); put("aparell", aparellId) }, "id = ?", arrayOf(id.toString()))
    }
}
