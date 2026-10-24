package cat.jordi.consum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.R
import cat.jordi.consum.dades.Periode

@Composable
fun PantallaHistorial(vm: ConsumViewModel) {
    val r = vm.resum
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Pestanyes(Periode.entries, vm.periode, { stringResource(it.nomId) }, vm::triaPeriode)
        Text(
            stringResource(
                when (vm.periode) {
                    Periode.DIA -> R.string.historial_ultimes_24h
                    Periode.SETMANA -> R.string.historial_ultims_7_dies
                    Periode.MES -> R.string.historial_ultims_30_dies
                }
            ),
            fontSize = 14.sp, color = Suau,
        )
        if (r == null) return@Column
        Row(verticalAlignment = Alignment.Bottom) {
            Text(num(r.total, 2), fontSize = 40.sp, fontWeight = FontWeight.Light)
            Text(" kWh   ≈ ${euros(r.euros.total)}", Modifier.padding(bottom = 7.dp), fontSize = 17.sp, color = Suau)
        }
        Text(consumIFixos(r.euros.consum, r.euros.fixos), fontSize = 13.sp, color = Suau)
        Targeta {
            GraficaBarres(r.barres)
            if (r.aproximat) NotaAproximat(stringResource(R.string.historial_aproximat), vm.config.host.isNotBlank())
        }
        Targeta {
            fun pct(x: Double) = if (r.total > 0) "${num(x / r.total * 100)} %" else "—"
            if (vm.config.teLinia) {
                FilaConsum(
                    Icons.Outlined.Bolt, LiniaFluix, Linia, vm.config.nomLinia, "${pct(r.linia)} · ${euros(r.euros.linia)}",
                    "${num(r.linia, 2)} kWh", 3,
                )
            }
            FilaConsum(
                Icons.Outlined.Power, SempreFluix, Sempre, stringResource(R.string.historial_sempre_ences),
                r.fonsW?.let { stringResource(R.string.historial_fons, num(it), pct(r.sempre), euros(r.euros.sempre)) }
                    ?: stringResource(R.string.historial_cal_un_dia),
                "${num(r.sempre, 2)} kWh", 3,
            )
            FilaConsum(
                Icons.Outlined.QuestionMark, DesconegutFluix, Desconegut, stringResource(R.string.historial_sense_identificar),
                "${pct(r.desconegut)} · ${euros(r.euros.desconegut)}", "${num(r.desconegut, 2)} kWh", 1,
            )
            FilaConsum(
                Icons.Outlined.Euro, SempreFluix, Sempre, stringResource(R.string.historial_costos_fixos),
                stringResource(R.string.historial_no_depenen_us), euros(r.euros.fixos),
            )
        }
        Nota(
            stringResource(R.string.historial_nota_aparells),
            Modifier.padding(horizontal = 4.dp),
        )
    }
}
