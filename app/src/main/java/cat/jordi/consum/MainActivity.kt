package cat.jordi.consum

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import cat.jordi.consum.dades.Eprel
import cat.jordi.consum.dades.Idioma
import cat.jordi.consum.ui.Compartit
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
import cat.jordi.consum.ui.Rebut
import cat.jordi.consum.ui.TemaConsum

class MainActivity : ComponentActivity() {
    private val vm: ConsumViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Idioma.actualitza(this)
        // L'app sempre és clara: les icones de les barres d'Android, fosques, també amb el mòbil en mode fosc
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        // En girar la pantalla torna a arribar el mateix intent: l'avís i el que s'havia compartit ja s'han recollit
        if (savedInstanceState == null) {
            obre(intent)
            rep(intent)
        }
        setContent { TemaConsum { Arrel(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        obre(intent)
        rep(intent)
    }

    /** Des d'un avís, l'app s'obre a Entrena, on hi ha la pregunta. */
    private fun obre(intent: Intent?) {
        if (intent?.getStringExtra(Avisos.OBRE) == Avisos.ENTRENA && vm.config.configurat) {
            vm.pantalla = Pantalla.PRINCIPAL
            vm.pestanya = Pestanya.ENTRENA
            vm.refresca()
        }
    }

    /**
     * El que s'ha compartit amb Consum des d'una altra app: un enllaç de l'EPREL (se'n busca la fitxa), o una foto
     * o un PDF (es llegeix com la placa). Una fitxa o una etiqueta baixades de l'EPREL es reconeixen pel nom.
     */
    private fun rep(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_SEND || !vm.config.configurat) return
        val rebut: Rebut = if (intent.type?.startsWith("text/") == true) {
            val numero = Eprel.enllac(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty())
            if (numero == null) {
                Toast.makeText(this, R.string.marca_compartit_no_eprel, Toast.LENGTH_LONG).show()
                return
            }
            Rebut.Registre(numero)
        } else {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.takeIf { it.scheme == "content" } ?: return
            nomFitxer(uri)?.let(Eprel::deFitxer)?.let { Rebut.Registre(it) } ?: Rebut.Fitxer(uri)
        }
        Compartit.rebut = rebut
        vm.pantalla = Pantalla.AFEGIR
    }

    private fun nomFitxer(uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment

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
        Pantalla.CONNECTA -> stringResource(if (vm.config.configurat) R.string.principal_configuracio else R.string.principal_connecta)
        Pantalla.AFEGIR -> stringResource(R.string.principal_afegeix_aparell)
        Pantalla.QUESTIONARI -> stringResource(R.string.principal_revisem_casa)
        Pantalla.PREU -> stringResource(R.string.principal_preu_llum)
        Pantalla.PRINCIPAL -> stringResource(if (vm.pestanya == Pestanya.ARA) R.string.principal_casa else vm.pestanya.nomId)
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
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.principal_enrere))
                        }
                    }
                },
                actions = {
                    if (principal) {
                        if (vm.sincronitzant) {
                            Box(Modifier.padding(12.dp)) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
                        } else {
                            IconButton(onClick = vm::sincronitza) { Icon(Icons.Outlined.Sync, stringResource(R.string.principal_recull_dades)) }
                        }
                        IconButton(onClick = { vm.pantalla = Pantalla.CONNECTA }) { Icon(Icons.Outlined.Settings, stringResource(R.string.principal_configuracio)) }
                    }
                },
            )
        },
        floatingActionButton = {
            if (principal && vm.pestanya == Pestanya.APARELLS) {
                // Sense la descripció, l'accessibilitat (TalkBack) el llegeix buit.
                val afegeix = stringResource(R.string.principal_afegeix_aparell)
                ExtendedFloatingActionButton(
                    onClick = { vm.pantalla = Pantalla.AFEGIR },
                    modifier = Modifier.semantics { contentDescription = afegeix },
                    containerColor = Primari, contentColor = Color.White,
                    icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(afegeix) },
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
                            // Una sola línia: amb la lletra gran, «Geschiedenis» es partia en dues
                            label = { Text(stringResource(p.nomId), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Primari, indicatorColor = PrimariFluix),
                        )
                    }
                }
            }
        },
    ) { marge ->
        // Amb Android 15 l'app ocupa tota la pantalla i el sistema ja no l'escurça quan surt el teclat: ho fa ella, i
        // així el camp on s'escriu queda per sobre del teclat a totes les pantalles
        Box(Modifier.padding(marge).consumeWindowInsets(marge).imePadding()) {
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
