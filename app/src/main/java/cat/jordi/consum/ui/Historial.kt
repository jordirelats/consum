package cat.jordi.consum.ui

import android.app.DatePickerDialog
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.R
import cat.jordi.consum.dades.Idioma
import cat.jordi.consum.dades.Periode
import cat.jordi.consum.dades.Resum
import cat.jordi.consum.dades.fiPeriode
import cat.jordi.consum.dades.iniciPeriode
import cat.jordi.consum.dades.periodesEnrere
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
fun PantallaHistorial(vm: ConsumViewModel) {
    val r = vm.resum
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Un període anterior: la pàgina és color sèpia (ho fa MainActivity) i, a dalt, què s'està mirant
        if (r?.actual == false) FranjaPassat(vm)
        Pestanyes(Periode.entries, vm.periode, { stringResource(it.nomId) }, vm::triaPeriode)
        NavegaPeriode(vm, r)
        if (r == null) return@Column
        if (!r.ambDades) {
            Text(
                r.primerDiaDades?.let { stringResource(R.string.historial_sense_dades, format(it, R.string.format_data_llarga)) }
                    ?: stringResource(R.string.historial_sense_dades_cap),
                Modifier.fillMaxWidth().background(SepiaTargeta, RoundedCornerShape(14.dp)).padding(12.dp),
                fontSize = 14.sp, lineHeight = 20.sp, color = SepiaText,
            )
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(num(r.total, 2), fontSize = 40.sp, fontWeight = FontWeight.Light)
            Text(" kWh   ≈ ${euros(r.euros.total)}", Modifier.padding(bottom = 7.dp), fontSize = 17.sp, color = Suau)
        }
        Text(consumIFixos(r.euros.consum, r.euros.fixos), fontSize = 13.sp, color = Suau)
        Text(comparacio(vm.periode, r), fontSize = 13.sp, color = Suau)
        Targeta {
            GraficaBarres(r.barres)
            if (r.aproximat) NotaAproximat(stringResource(R.string.historial_aproximat), vm.config.host.isNotBlank())
            // Els dies que venen del núvol per hores (maqueta 11)
            if (r.barres.any { it.hora.nuvol > 0 }) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Nuvol, RoundedCornerShape(2.dp)))
                    Text("  " + stringResource(R.string.historial_del_nuvol), fontSize = 12.sp, color = Suau)
                }
                Nota(stringResource(R.string.historial_nuvol_nota), Modifier.padding(top = 2.dp))
            }
        }
        // De l'any no se'n busquen: caldria llegir-ne tots els minuts
        if (vm.periode != Periode.ANY) MomentsPunta(vm)
        // Qui ha gastat més, cada aparell un cop, i la resta a part (maqueta 13)
        r.ranquing?.let { rq ->
            BarraConsum(rq)
            Targeta {
                TitolTargeta(stringResource(R.string.ranquing_titol))
                LlistaRanquing(rq) { vm.obreAparell(it) }
            }
            Targeta {
                TitolTargeta(stringResource(R.string.ranquing_resta_titol))
                RestaConsum(rq, r.fonsW, vm.config.nomLinia, r.euros.fixos)
            }
        }
    }
}

/** Una data amb un dels formats de la traducció (format_dia_llarg, format_data_llarga…), en la llengua de l'app. */
@Composable
private fun format(dia: LocalDate, @StringRes patro: Int): String =
    dia.format(DateTimeFormatter.ofPattern(stringResource(patro), Idioma.locale))

/** El període que es mira: «dimecres 21 d'octubre», «19–25 d'octubre», «octubre del 2026», «2026». */
@Composable
private fun etiquetaPeriode(p: Periode, dia: LocalDate): String = when (p) {
    Periode.DIA -> format(dia, R.string.format_dia_llarg)
    Periode.SETMANA -> {
        val inici = iniciPeriode(p, dia)
        val fi = fiPeriode(p, dia).minusDays(1)
        if (inici.month == fi.month) "${inici.dayOfMonth}–${format(fi, R.string.format_dia_mes)}"
        else "${format(inici, R.string.format_dia_mes)} – ${format(fi, R.string.format_dia_mes)}"
    }
    Periode.MES -> stringResource(R.string.historial_mes, dia.month.getDisplayName(TextStyle.FULL_STANDALONE, Idioma.locale), dia.year)
    Periode.ANY -> "${dia.year}"
}

/** Quant fa: «avui», «ahir», «fa 5 dies», «la setmana passada», «fa 2 anys»… */
@Composable
private fun relatiu(p: Periode, enrere: Long): String {
    val n = enrere.toInt()
    val (ara, passat, plural) = when (p) {
        Periode.DIA -> Triple(R.string.historial_rel_dia_0, R.string.historial_rel_dia_1, R.plurals.historial_rel_dia_n)
        Periode.SETMANA -> Triple(R.string.historial_rel_setmana_0, R.string.historial_rel_setmana_1, R.plurals.historial_rel_setmana_n)
        Periode.MES -> Triple(R.string.historial_rel_mes_0, R.string.historial_rel_mes_1, R.plurals.historial_rel_mes_n)
        Periode.ANY -> Triple(R.string.historial_rel_any_0, R.string.historial_rel_any_1, R.plurals.historial_rel_any_n)
    }
    return when (n) {
        0 -> stringResource(ara)
        1 -> stringResource(passat)
        else -> pluralStringResource(plural, n, n)
    }
}

/** Les fletxes per anar al període d'abans o al de després; tocant la data, el calendari. */
@Composable
private fun NavegaPeriode(vm: ConsumViewModel, r: Resum?) {
    val context = LocalContext.current
    val p = vm.periode
    val dia = vm.diaHistorial
    val enrere = periodesEnrere(p, dia, LocalDate.now())
    val passat = enrere > 0
    Surface(
        shape = RoundedCornerShape(14.dp), color = if (passat) SepiaTargeta else Color.White,
        border = if (passat) BorderStroke(1.5.dp, SepiaVora) else null,
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            // Enrere fins al primer període sense dades, i prou
            FilledTonalIconButton({ vm.mouHistorial(-1) }, enabled = r?.ambDades != false) {
                Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.historial_anterior))
            }
            Column(
                Modifier.weight(1f).clickable {
                    DatePickerDialog(context, { _, a, m, d -> vm.veesA(LocalDate.of(a, m + 1, d)) }, dia.year, dia.monthValue - 1, dia.dayOfMonth).apply {
                        datePicker.maxDate = System.currentTimeMillis()
                        r?.primerDiaDades?.let { datePicker.minDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                    }.show()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(etiquetaPeriode(p, dia), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(relatiu(p, enrere), fontSize = 12.sp, color = Suau)
            }
            FilledTonalIconButton({ vm.mouHistorial(1) }, enabled = passat) {
                Icon(Icons.Outlined.ChevronRight, stringResource(R.string.historial_seguent))
            }
        }
    }
}

/** A dalt d'un període anterior: què s'està mirant, i «Torna a avui». */
@Composable
private fun FranjaPassat(vm: ConsumViewModel) {
    val p = vm.periode
    val dia = vm.diaHistorial
    val que = when (p) {
        Periode.DIA -> format(dia, R.string.format_dia_curt)
        Periode.ANY -> stringResource(R.string.historial_mirant_any, dia.year)
        else -> relatiu(p, periodesEnrere(p, dia, LocalDate.now()))
    }
    Row(
        Modifier.fillMaxWidth().background(SepiaFort, RoundedCornerShape(10.dp)).padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.History, null, Modifier.size(16.dp), tint = SepiaText)
        Text(stringResource(R.string.historial_mirant, que), Modifier.weight(1f).padding(start = 6.dp), fontSize = 13.sp, color = SepiaText)
        TextButton(onClick = vm::tornaAvui) { Text(stringResource(R.string.historial_torna_avui), fontWeight = FontWeight.SemiBold) }
    }
}

/** «Ahir, 3,48 kWh.» o «La setmana abans, l'app encara no en tenia totes les dades.» */
@Composable
private fun comparacio(p: Periode, r: Resum): String {
    val quin = stringResource(
        when (p) {
            Periode.DIA -> if (r.actual) R.string.historial_comp_dia_ara else R.string.historial_comp_dia_abans
            Periode.SETMANA -> if (r.actual) R.string.historial_comp_setmana_ara else R.string.historial_comp_setmana_abans
            Periode.MES -> if (r.actual) R.string.historial_comp_mes_ara else R.string.historial_comp_mes_abans
            Periode.ANY -> if (r.actual) R.string.historial_comp_any_ara else R.string.historial_comp_any_abans
        }
    )
    return r.anteriorKwh?.let { stringResource(R.string.historial_comp, quin, num(it, 2)) } ?: stringResource(R.string.historial_comp_sense, quin)
}
