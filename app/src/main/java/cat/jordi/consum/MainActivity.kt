package cat.jordi.consum

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cat.jordi.consum.ui.ConsumViewModel
import cat.jordi.consum.ui.Fons
import cat.jordi.consum.ui.Pantalla
import cat.jordi.consum.ui.PantallaAfegir
import cat.jordi.consum.ui.PantallaAparells
import cat.jordi.consum.ui.PantallaAra
import cat.jordi.consum.ui.PantallaConnecta
import cat.jordi.consum.ui.PantallaEntrena
import cat.jordi.consum.ui.PantallaHistorial
import cat.jordi.consum.ui.PantallaPreu
import cat.jordi.consum.ui.PantallaQuestionari
import cat.jordi.consum.ui.Pestanya
import cat.jordi.consum.ui.Primari
import cat.jordi.consum.ui.PrimariFluix
import cat.jordi.consum.ui.TemaConsum

class MainActivity : ComponentActivity() {
    private val vm: ConsumViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        obre(intent)
        setContent { TemaConsum { Arrel(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        obre(intent)
    }

    /** Des d'un avís, l'app s'obre a Entrena, on hi ha la pregunta. */
    private fun obre(intent: Intent?) {
        if (intent?.getStringExtra(Avisos.OBRE) == Avisos.ENTRENA && vm.config.configurat) {
            vm.pantalla = Pantalla.PRINCIPAL
            vm.pestanya = Pestanya.ENTRENA
            vm.refresca()
        }
    }

    override fun onResume() {
        super.onResume()
        vm.enPrimerPla(true)
    }

    override fun onPause() {
        vm.enPrimerPla(false)
        super.onPause()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Arrel(vm: ConsumViewModel) {
    val principal = vm.pantalla == Pantalla.PRINCIPAL
    BackHandler(enabled = !principal && vm.config.configurat) { vm.pantalla = Pantalla.PRINCIPAL }

    val titol = when (vm.pantalla) {
        Pantalla.CONNECTA -> if (vm.config.configurat) "Configuració" else "Connecta el Shelly"
        Pantalla.AFEGIR -> "Afegeix un aparell"
        Pantalla.QUESTIONARI -> "Revisem la casa"
        Pantalla.PREU -> "Preu de la llum"
        Pantalla.PRINCIPAL -> if (vm.pestanya == Pestanya.ARA) "Casa" else vm.pestanya.nom
    }
    Scaffold(
        containerColor = Fons,
        topBar = {
            TopAppBar(
                title = { Text(titol) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Fons),
                navigationIcon = {
                    if (!principal && vm.config.configurat) {
                        IconButton(onClick = { vm.pantalla = Pantalla.PRINCIPAL }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Enrere")
                        }
                    }
                },
                actions = {
                    if (principal) {
                        if (vm.sincronitzant) {
                            Box(Modifier.padding(12.dp)) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
                        } else {
                            IconButton(onClick = vm::sincronitza) { Icon(Icons.Outlined.Sync, "Recull les dades del Shelly") }
                        }
                        IconButton(onClick = { vm.pantalla = Pantalla.CONNECTA }) { Icon(Icons.Outlined.Settings, "Configuració") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (principal && vm.pestanya == Pestanya.APARELLS) {
                // Sense la descripció, l'accessibilitat (TalkBack) el llegeix buit.
                ExtendedFloatingActionButton(
                    onClick = { vm.pantalla = Pantalla.AFEGIR },
                    modifier = Modifier.semantics { contentDescription = "Afegeix un aparell" },
                    containerColor = Primari, contentColor = Color.White,
                    icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Afegeix un aparell") },
                )
            }
        },
        bottomBar = {
            if (principal) {
                NavigationBar(containerColor = Color.White) {
                    Pestanya.entries.forEach { p ->
                        NavigationBarItem(
                            selected = vm.pestanya == p,
                            onClick = { vm.pestanya = p },
                            icon = {
                                Icon(
                                    when (p) {
                                        Pestanya.ARA -> Icons.Outlined.Home
                                        Pestanya.APARELLS -> Icons.Outlined.Devices
                                        Pestanya.ENTRENA -> Icons.Outlined.School
                                        Pestanya.HISTORIAL -> Icons.Outlined.BarChart
                                    },
                                    null,
                                )
                            },
                            label = { Text(p.nom) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Primari, indicatorColor = PrimariFluix),
                        )
                    }
                }
            }
        },
    ) { marge ->
        Box(Modifier.padding(marge)) {
            when (vm.pantalla) {
                Pantalla.CONNECTA -> PantallaConnecta(vm)
                Pantalla.AFEGIR -> PantallaAfegir(vm)
                Pantalla.QUESTIONARI -> PantallaQuestionari(vm)
                Pantalla.PREU -> PantallaPreu(vm)
                Pantalla.PRINCIPAL -> when (vm.pestanya) {
                    Pestanya.ARA -> PantallaAra(vm)
                    Pestanya.APARELLS -> PantallaAparells(vm)
                    Pestanya.ENTRENA -> PantallaEntrena(vm)
                    Pestanya.HISTORIAL -> PantallaHistorial(vm)
                }
            }
        }
    }
}
