package cat.jordi.consum.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cat.jordi.consum.dades.Idioma
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.R
import cat.jordi.consum.dades.Categoria
import cat.jordi.consum.dades.Com
import cat.jordi.consum.dades.Contracte
import cat.jordi.consum.dades.EncesAra
import cat.jordi.consum.dades.TipusEnces
import cat.jordi.consum.dades.encesDelCanal
import cat.jordi.consum.dades.Franja
import cat.jordi.consum.dades.Funcionament
import cat.jordi.consum.dades.InfoPreu
import cat.jordi.consum.dades.NivellPotencia
import cat.jordi.consum.dades.Pais
import cat.jordi.consum.dades.ResumSetmana
import cat.jordi.consum.dades.nivellPotencia
import cat.jordi.consum.shelly.Origen
import java.time.format.DateTimeFormatter

@Composable
fun PantallaAra(vm: ConsumViewModel) {
    val l = vm.lectura
    val teLinia = vm.config.teLinia
    val info = vm.infoPreu
    val preu = info?.ara?.preu
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            l?.origen == Origen.LOCAL -> Pastilla(stringResource(R.string.ara_origen_local), Primari, PrimariFluix, punt = Ok)
            l?.origen == Origen.NUVOL -> Pastilla(stringResource(R.string.ara_origen_nuvol), Nuvol, NuvolFluix, punt = Nuvol)
            !vm.haLlegit -> Pastilla(stringResource(R.string.ara_connectant), Suau, SempreFluix)
            vm.config.teNuvol -> Pastilla(stringResource(R.string.ara_sense_connexio), Desconegut, DesconegutFluix, punt = Desconegut)
            else -> Pastilla(stringResource(R.string.ara_falta_clau), Desconegut, DesconegutFluix, punt = Desconegut)
        }
        vm.versioNova?.let { v ->
            Targeta(color = PrimariFluix) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ara_versio_nova, v.versio), Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = { vm.pantalla = Pantalla.CONNECTA }) { Text(stringResource(R.string.ara_actualitza)) }
                }
            }
        }
        // El dilluns (i fins que l'ha vist), el resum de la setmana passada (maqueta 15)
        vm.resumSetmana?.let { TargetaResumSetmana(vm, it) }
        val limit = vm.config.tarifa.limitKw
        val nivell = nivellPotencia(l?.total?.w ?: 0.0, limit)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(l?.let { num(it.total.w) } ?: "—", fontSize = 56.sp, fontWeight = FontWeight.Light, color = colorNivell(nivell, Color.Unspecified))
            Text(" W", Modifier.padding(bottom = 10.dp), fontSize = 24.sp, color = Suau)
        }
        Text(
            if (l != null && preu != null) {
                stringResource(
                    if (vm.avuiAproximat) R.string.ara_resum_amb_preu_aprox else R.string.ara_resum_amb_preu,
                    euros(l.total.w / 1000 * preu), num(vm.avuiKwh, 2), euros(vm.avuiEuros),
                )
            } else {
                stringResource(if (vm.avuiAproximat) R.string.ara_resum_aprox else R.string.ara_resum, num(vm.avuiKwh, 2), euros(vm.avuiEuros))
            },
            fontSize = 14.sp, color = Suau,
        )

        if (l != null && limit != null) BarraPotencia(l.total.w, limit, nivell)
        if (l != null && limit != null && nivell == NivellPotencia.LIMIT) {
            Targeta(color = LimitFluix) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = PotenciaLimit, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (l.total.w >= limit * 1000) R.string.ara_limit_passat else R.string.ara_limit_a_prop),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PotenciaLimit,
                    )
                }
                Nota(stringResource(R.string.ara_limit_text), Modifier.padding(top = 4.dp))
            }
        }

        // A Espanya, mentre no s'ha posat la potència contractada no s'avisa: es pregunta aquí
        var potenciaResposta by remember { mutableStateOf(false) }
        val tarifa = vm.config.tarifa
        if (!potenciaResposta && tarifa.pais == Pais.ES && limit == null && !vm.config.potenciaNoPreguntis) {
            PreguntaPotencia(
                desa = { vm.desaPotencia(it); potenciaResposta = true },
                // A la pantalla del preu, la foto o el PDF de la factura també en treuen la potència contractada
                factura = { vm.pantalla = Pantalla.PREU },
                araNo = { vm.config.potenciaNoPreguntis = true; potenciaResposta = true },
            )
        }

        if (info != null) PastillaPreu(info) { vm.pantalla = Pantalla.PREU }

        Targeta {
            TitolTargeta(stringResource(R.string.ara_ultimes_24h))
            GraficaPotencia(vm.serie, teLinia, limit)
            when {
                vm.serie.isEmpty() ->
                    Nota(stringResource(if (l?.origen == Origen.NUVOL) R.string.ara_dades_nuvol else R.string.ara_dades_wifi))
                vm.serie.any { it.aprox } -> NotaAproximat(stringResource(R.string.ara_aproximat), vm.config.host.isNotBlank())
            }
        }

        if (info != null && info.tarifa.contracte != Contracte.FIX) {
            Targeta {
                TitolTargeta(stringResource(R.string.ara_mes_barat_avui))
                TiraPreus(info.avui, info.ara.ts)
                mesBarat(info)?.let { Nota(it, Modifier.padding(top = 4.dp)) }
            }
        }

        if (l != null) {
            Targeta {
                TitolTargeta(stringResource(R.string.ara_que_hi_ha_ences))
                val linia = if (teLinia) (l.linia?.w ?: 0.0).coerceAtLeast(0.0) else 0.0
                val resta = (l.total.w - linia).coerceAtLeast(0.0)
                val fons = vm.fonsW
                val sempre = if (fons != null) minOf(resta, fons) else 0.0
                fun perHora(w: Double) = if (preu != null) context.getString(R.string.ara_euros_hora, euros(w / 1000 * preu)) else "${num(w)} W"
                // Amb el mateix criteri que el rànquing (maqueta 15): les vegades que encara duren, amb el seu aparell; des de
                // fora, o sense les dades al dia, només la potència
                val o = vm.obertes
                val vegades = if (o?.fresques == true) o.vegades else emptyList()
                val ara = System.currentTimeMillis() / 1000
                val noms = vm.aparells.associateBy { it.id }
                // Cada cosa, una fila, de més a menys consum (maqueta 9): el que és a 0 W, al final i més apagat
                val files = mutableListOf<FilaAra>()
                @Composable
                fun fila(ences: EncesAra, alaLinia: Boolean) {
                    // Si l'aparell s'acaba d'esborrar, fins al proper refresc és una cosa sense nom
                    val a = ences.aparellId?.let(noms::get)
                    val e = if (ences.tipus == TipusEnces.APARELL && a == null) ences.copy(tipus = TipusEnces.SENSE_IDENTIFICAR, com = null) else ences
                    val (fonsF, color) = if (alaLinia) LiniaFluix to Linia else PrimariFluix to Primari
                    val desDe = e.desDe?.let { horaMinut(it) }
                    val fins = e.kwh?.takeIf { it >= 0.05 }?.let { num(it, 1) }
                    val quan = when {
                        desDe != null && fins != null -> stringResource(R.string.ara_des_de_kwh, desDe, fins)
                        desDe != null -> stringResource(R.string.ara_des_de, desDe)
                        else -> stringResource(R.string.ara_acaba_encendre)
                    }
                    files += when (e.tipus) {
                        TipusEnces.APARELL -> FilaAra(
                            icona(a?.categoria ?: Categoria.ALTRES), fonsF, color, a?.nom.orEmpty(),
                            when {
                                e.com == null && desDe != null -> stringResource(R.string.ara_sembla_ell_des_de, quan)
                                e.com == null -> stringResource(R.string.ara_sembla_ell)
                                alaLinia -> stringResource(R.string.ara_a_la_pinca, quan)
                                else -> quan
                            },
                            e.w, when (e.com) { Com.ENSENYAT -> 3; Com.RECONEGUT -> 2; null -> 1 },
                        )
                        TipusEnces.SENSE_IDENTIFICAR, TipusEnces.NOU -> FilaAra(
                            Icons.Outlined.QuestionMark, DesconegutFluix, Desconegut,
                            if (alaLinia) stringResource(R.string.ranquing_linia_altres, vm.config.nomLinia) else stringResource(R.string.ara_sense_identificar),
                            if (e.tipus == TipusEnces.NOU) stringResource(R.string.ara_acaba_encendre_no_se) else stringResource(R.string.ara_no_se_que_es, quan),
                            e.w, 1, obre = { vm.pestanya = Pestanya.ENTRENA },
                        )
                        TipusEnces.NEVERA -> FilaAra(
                            icona(o?.nevera?.categoria ?: Categoria.NEVERA), PrimariFluix, Primari, o?.nevera?.nom.orEmpty(),
                            stringResource(R.string.ara_compressor, num(e.w)), e.w, 3,
                        )
                        TipusEnces.PETITS -> if (alaLinia) FilaAra(
                            Icons.Outlined.Bolt, LiniaFluix, Linia, stringResource(R.string.ranquing_linia_altres, vm.config.nomLinia),
                            stringResource(R.string.ranquing_linia_altres_text), e.w, null,
                        ) else FilaAra(
                            Icons.Outlined.LightMode, SempreFluix, Sempre, stringResource(R.string.ranquing_petits),
                            stringResource(R.string.ranquing_petits_text), e.w, null,
                        )
                    }
                }
                if (teLinia) {
                    if (vm.aparellsLinia.isEmpty()) {
                        // Sense aparells a la línia, la línia sencera, com al rànquing
                        files += FilaAra(
                            Icons.Outlined.Bolt, LiniaFluix, Linia, vm.config.nomLinia,
                            stringResource(if (linia < 10) R.string.ara_linia_apagat else R.string.ara_linia_subtitol, num(linia)), linia, 3,
                        )
                    } else {
                        encesDelCanal(ara, 1, linia, vegades, vm.aparellsLinia, vm.referenciesLinia).forEach { fila(it, alaLinia = true) }
                    }
                }
                if (fons != null) {
                    // Els aparells sempre encesos (el router) diuen que funcionen, amb la seva potència; la resta del fons és «altres en espera».
                    // Tocant-los, la caça del sempre encès
                    val sempreEncesos = vm.aparells.filter { it.funciona == Funcionament.SEMPRE }
                    val suma = sempreEncesos.sumOf { it.wTipic }
                    val escala = if (suma > sempre && suma > 0) sempre / suma else 1.0
                    sempreEncesos.forEach { a ->
                        val w = a.wTipic * escala
                        files += FilaAra(icona(a.categoria), SempreFluix, Sempre, a.nom, stringResource(R.string.ara_sempre_aparell, num(w)), w, a.font.punts, obre = vm::obreEspera)
                    }
                    val altres = sempre - suma * escala
                    if (sempreEncesos.isEmpty()) {
                        files += FilaAra(Icons.Outlined.Power, SempreFluix, Sempre, stringResource(R.string.ara_sempre_ences), stringResource(R.string.ara_sempre_subtitol, num(sempre)), sempre, 3, obre = vm::obreEspera)
                    } else if (altres >= 1) {
                        files += FilaAra(Icons.Outlined.Power, SempreFluix, Sempre, stringResource(R.string.aparells_altres_espera), stringResource(R.string.ara_altres_espera, num(altres)), altres, 3, obre = vm::obreEspera)
                    }
                    encesDelCanal(ara, 0, resta - sempre, vegades, vm.aparellsAEntrenar, vm.referencies, o?.neveraW).forEach { fila(it, alaLinia = false) }
                } else {
                    files += FilaAra(
                        Icons.Outlined.QuestionMark, DesconegutFluix, Desconegut, stringResource(R.string.ara_sense_identificar),
                        stringResource(R.string.ara_residu_sense_fons, num(resta)), resta, 1,
                    )
                }
                files.sortedByDescending { it.w }.forEach { f ->
                    FilaConsum(
                        f.icona, f.fons, f.color, f.titol, f.subtitol, perHora(f.w), f.punts,
                        modifier = (if (f.obre != null) Modifier.clickable(onClick = f.obre) else Modifier).then(if (f.w < 10) Modifier.alpha(0.55f) else Modifier),
                    )
                }
                // Des de fora no hi ha les vegades: el que diu és només per la potència
                if (o?.fresques != true && fons != null) Nota(stringResource(R.string.ara_nomes_potencia), Modifier.padding(bottom = 4.dp))
                Nota(stringResource(R.string.ara_detall, num(l.total.va), num(l.total.pf, 2), num(l.total.v, 1)))
            }
            AvisCar(l.total.w, info)
        }
        if (vm.errorSincronitzacio != null && vm.haLlegit && l == null) {
            Nota(stringResource(R.string.ara_comprova_wifi))
        }
    }
}

/** Una fila de «Què hi ha encès ara», abans d'ordenar-les pel que gasten. [obre]: què passa en tocar-la. */
private class FilaAra(
    val icona: ImageVector, val fons: Color, val color: Color, val titol: String, val subtitol: String, val w: Double, val punts: Int?,
    val obre: (() -> Unit)? = null,
)


/** La part de la potència contractada que es fa servir ara, amb les marques del 80 i del 90 %. */
@Composable
private fun BarraPotencia(w: Double, limitKw: Double, nivell: NivellPotencia) {
    val part = (w / (limitKw * 1000)).toFloat()
    Column {
        Text(
            stringResource(R.string.ara_part_contractada, num(part * 100.0), num(limitKw, 2)),
            fontSize = 14.sp, color = if (nivell == NivellPotencia.NORMAL) Suau else colorNivell(nivell, Suau),
        )
        Canvas(Modifier.fillMaxWidth().height(14.dp).padding(top = 6.dp)) {
            val alt = size.height
            drawRoundRect(Separador, Offset(0f, 0f), Size(size.width, alt), CornerRadius(alt / 2))
            drawRoundRect(colorNivell(nivell, Primari), Offset(0f, 0f), Size(size.width * part.coerceIn(0f, 1f), alt), CornerRadius(alt / 2))
            for ((marca, color) in listOf(0.8f to PotenciaAlta, 0.9f to PotenciaLimit)) {
                drawLine(color, Offset(size.width * marca, -3.dp.toPx()), Offset(size.width * marca, alt + 3.dp.toPx()), 2.dp.toPx())
            }
        }
    }
}

/** Quina potència hi ha contractada, perquè l'app sàpiga quan avisar. */
@Composable
private fun PreguntaPotencia(desa: (Double) -> Unit, factura: () -> Unit, araNo: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val kw = llegeixNum(text, milers = false)?.takeIf { it in 1.0..100.0 }
    Targeta(color = PrimariFluix) {
        TitolTargeta(stringResource(R.string.ara_potencia_titol))
        Nota(stringResource(R.string.ara_potencia_text))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it }, Modifier.weight(1f), singleLine = true,
                suffix = { Text("kW") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { kw?.let(desa) }, enabled = kw != null) { Text(stringResource(R.string.connecta_desa)) }
        }
        Row {
            TextButton(onClick = factura) { Text(stringResource(R.string.ara_potencia_factura)) }
            TextButton(onClick = araNo) { Text(stringResource(R.string.ara_potencia_ara_no)) }
        }
    }
}

/** La franja d'ara, el preu del kWh i fins quan dura. Tocant-la s'obre el preu de la llum. */
@Composable
private fun PastillaPreu(info: InfoPreu, obre: () -> Unit) {
    val h = info.ara
    val (color, fons) = when (h.franja) {
        Franja.PUNTA -> PuntaColor to PuntaFluix
        Franja.VALL, Franja.DAL -> VallColor to VallFluix
        Franja.PLA, Franja.NORMAAL -> Desconegut to DesconegutFluix
        Franja.MERCAT -> colorPreu(h, info.avui).let { it to it.copy(alpha = 0.14f) }
        Franja.UNICA -> Primari to PrimariFluix
    }
    val franja = stringResource(h.franja.nomId)
    val nom = when (h.franja) {
        Franja.UNICA, Franja.MERCAT -> franja
        else -> info.canviaA?.let { stringResource(R.string.ara_franja_fins_a, franja, it) } ?: franja
    }
    val text = h.preu?.let { stringResource(R.string.ara_pastilla_preu, nom, num(it, 3)) } ?: stringResource(R.string.ara_pastilla_sense_preu, nom)
    Row(Modifier.clickable(onClick = obre)) { Pastilla(text, color, fons, punt = color) }
}

/** L'hora més barata de les properes 24, si val la pena esperar-la. */
@Composable
private fun mesBarat(info: InfoPreu): String? {
    val ara = info.ara.preu ?: return null
    val barata = info.properes.filter { it.preu != null }.minByOrNull { it.preu!! } ?: return null
    if (barata.preu!! > ara * 0.9) return stringResource(R.string.ara_ja_barat)
    return stringResource(R.string.ara_mes_barata, barata.hora, num(barata.preu, 3), num((1 - barata.preu / ara) * 100))
}

/** Si la casa gasta molt en una hora cara, ho diu i proposa quan seria més barat. */
@Composable
private fun AvisCar(w: Double, info: InfoPreu?) {
    val ara = info?.ara?.preu ?: return
    val barata = info.properes.filter { it.preu != null }.minByOrNull { it.preu!! } ?: return
    if (w < 1500 || barata.preu!! > ara * 0.85) return
    Targeta(color = DesconegutFluix) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, null, tint = Desconegut, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ara_avis_car_titol), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            stringResource(R.string.ara_avis_car_text, num(w), num(ara, 3), barata.hora, euros(w / 1000 * barata.preu), euros(w / 1000 * ara)),
            Modifier.padding(top = 4.dp), fontSize = 13.sp, lineHeight = 18.sp, color = TextPrincipal,
        )
    }
}

/**
 * El resum de la setmana passada (maqueta 15): el total, els tres aparells que han gastat més, el sempre encès, i el que queda
 * per fer. Surt el dilluns fins que l'usuari l'ha vist.
 */
@Composable
private fun TargetaResumSetmana(vm: ConsumViewModel, s: ResumSetmana) {
    val rq = s.resum.ranquing ?: return
    val fi = s.dilluns.plusDays(6)
    val dataFi = fi.format(DateTimeFormatter.ofPattern(stringResource(R.string.format_dia_mes), Idioma.locale))
    val dataInici = if (fi.month == s.dilluns.month) "${s.dilluns.dayOfMonth}"
    else s.dilluns.format(DateTimeFormatter.ofPattern(stringResource(R.string.format_dia_mes), Idioma.locale))
    Targeta {
        TitolTargeta(stringResource(R.string.resum_setmana_titol, dataInici, dataFi))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(num(rq.total, 1), fontSize = 32.sp, fontWeight = FontWeight.Light)
            Text(" kWh   ≈ ${euros(s.resum.euros.total)}", Modifier.padding(bottom = 5.dp), fontSize = 15.sp, color = Suau)
        }
        rq.aparells.take(3).forEachIndexed { i, f ->
            FilaResum("${i + 1}. ${f.nom.ifBlank { stringResource(R.string.pinca2_xip) }}", "${num(f.kwh, 1)} kWh · ${euros(f.euros)}")
        }
        FilaResum(stringResource(R.string.ranquing_sempre), "${num(rq.sempre, 1)} kWh · ${euros(rq.sempreEuros)}")
        // El que queda per fer: el que es repeteix sense nom, el que no en té, el sempre encès
        val perFer = buildList {
            if (s.repetits > 0) add(Triple(Icons.Outlined.QuestionMark, pluralStringResource(R.plurals.resum_repetits, s.repetits, s.repetits)) { vm.pestanya = Pestanya.ENTRENA })
            val senseNom = rq.senseIdentificar / rq.total.coerceAtLeast(0.001)
            if (senseNom >= 0.2) add(Triple(Icons.Outlined.QuestionMark, stringResource(R.string.resum_sense_nom, num(senseNom * 100))) { vm.pestanya = Pestanya.ENTRENA })
            val sempre = rq.sempre / rq.total.coerceAtLeast(0.001)
            if (sempre >= 0.15) add(Triple(Icons.Outlined.Power, stringResource(R.string.resum_sempre, num(sempre * 100)), vm::obreEspera))
        }
        if (perFer.isNotEmpty()) {
            Text(stringResource(R.string.resum_per_fer), Modifier.padding(top = 10.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Suau)
            perFer.forEach { (ic, text, obre) ->
                Row(Modifier.fillMaxWidth().clickable(onClick = obre).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ic, null, tint = if (ic == Icons.Outlined.Power) Suau else Desconegut, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(text, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 18.sp)
                    Icon(Icons.Outlined.ChevronRight, null, tint = Suau, modifier = Modifier.size(18.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { vm.tancaResum(); vm.miraSetmana(s.dilluns) }) { Text(stringResource(R.string.resum_mira)) }
            TextButton(onClick = vm::tancaResum) { Text(stringResource(R.string.resum_d_acord)) }
        }
    }
}

@Composable
private fun FilaResum(nom: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(nom, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(valor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
