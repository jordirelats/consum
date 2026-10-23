package cat.jordi.consum.ui

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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.consum.Actualitzacio
import cat.jordi.consum.shelly.InfoShelly
import cat.jordi.consum.shelly.ShellyLocal
import cat.jordi.consum.shelly.ShellyNuvol
import cat.jordi.consum.shelly.Trobat
import cat.jordi.consum.shelly.descobreix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** El primer cop, i després com a configuració: on és el Shelly, les pinces, el núvol i el preu. */
@Composable
fun PantallaConnecta(vm: ConsumViewModel) {
    val config = vm.config
    val context = LocalContext.current
    val abast = rememberCoroutineScope()
    var host by remember { mutableStateOf(config.host) }
    var info by remember { mutableStateOf<InfoShelly?>(null) }
    var provant by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var nomLinia by remember { mutableStateOf(config.nomLinia) }
    var servidor by remember { mutableStateOf(config.servidorNuvol) }
    var clau by remember { mutableStateOf(config.clauNuvol) }
    var idNuvol by remember { mutableStateOf(config.idDispositiu) }
    var provantNuvol by remember { mutableStateOf(false) }
    var nuvol by remember { mutableStateOf<String?>(null) }
    var nuvolOk by remember { mutableStateOf(false) }
    val trobats = remember { mutableStateListOf<Trobat>() }

    DisposableEffect(Unit) {
        val atura = descobreix(context) { t -> if (trobats.none { it.host == t.host }) trobats += t }
        onDispose { atura() }
    }

    val teclat = LocalSoftwareKeyboardController.current

    fun prova() {
        teclat?.hide()
        provant = true
        error = null
        info = null
        abast.launch {
            val r = withContext(Dispatchers.IO) { runCatching { ShellyLocal(host.trim()).info() } }
            provant = false
            r.onSuccess { info = it; idNuvol = it.idDispositiu }
                .onFailure { error = "No hi arribo. Comprova l'adreça i que el mòbil és a la wifi de casa." }
        }
    }

    // Per configurar-la des de fora de casa: n'hi ha prou que el núvol respongui.
    fun provaNuvol() {
        teclat?.hide()
        provantNuvol = true
        nuvol = null
        nuvolOk = false
        abast.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { ShellyNuvol(netejaServidor(servidor), clau.trim(), netejaId(idNuvol)).lectura() }
            }
            provantNuvol = false
            r.onSuccess { l ->
                nuvolOk = l != null
                nuvol = if (l != null) "El núvol respon: ara ${num(l.total.w)} W" else "El núvol no coneix aquest Shelly. Mira l'ID."
            }.onFailure {
                nuvol = if (it.message?.contains("401") == true) "La clau no és bona." else "No arribo al núvol: ${it.message}"
            }
        }
    }

    val potDesar = info != null || nuvolOk || (host.trim() == config.host && config.configurat)

    // Els camps es desplacen; el botó de desar queda fix a baix.
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Targeta {
                TitolTargeta("1. El Shelly a la wifi de casa")
                if (trobats.isEmpty()) Nota("Buscant Shelly a la wifi… Si no surt, escriu-ne l'adreça (és a l'app de Shelly).")
                trobats.forEach { t ->
                    TextButton(onClick = { host = t.host; info = null }) {
                        Icon(Icons.Outlined.Wifi, null)
                        Spacer(Modifier.width(8.dp))
                        Text("${t.nom} · ${t.host}")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        host, { host = it; info = null }, Modifier.weight(1f), label = { Text("Adreça del Shelly") },
                        placeholder = { Text("192.168.1.40") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = ::prova, enabled = host.isNotBlank() && !provant) { Text("Prova") }
                }
                if (provant) CircularProgressIndicator(Modifier.padding(top = 8.dp))
                info?.let {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Check, null, tint = Ok)
                        Text("  ${it.nom} · ${it.model} · versió ${it.versio}", fontSize = 13.sp, color = Ok)
                    }
                }
                error?.let { Text(it, Modifier.padding(top = 8.dp), color = Desconegut, fontSize = 13.sp) }
            }

            Targeta {
                TitolTargeta("2. Les pinces")
                Text("Pinça 1: tota la casa", fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    nomLinia, { nomLinia = it }, Modifier.fillMaxWidth(), label = { Text("Pinça 2: què mesura?") },
                    placeholder = { Text("Per exemple: Cuina, Rentadora, Termo") }, singleLine = true,
                    supportingText = { Text("Deixa-ho buit si només hi ha una pinça.") },
                )
            }

            Targeta {
                TitolTargeta("3. Per veure-ho des de fora de casa")
                Nota("És opcional. A l'app de Shelly: Configuració → Llar → Accés i permisos → Cloud Key. Hi ha la clau i el servidor. La clau es queda només en aquest mòbil.")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    servidor, { servidor = it }, Modifier.fillMaxWidth(), label = { Text("Servidor") },
                    placeholder = { Text("shelly-00-eu.shelly.cloud") }, singleLine = true,
                )
                OutlinedTextField(
                    clau, { clau = it; nuvolOk = false }, Modifier.fillMaxWidth(), label = { Text("Clau del núvol") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    idNuvol, { idNuvol = it; nuvolOk = false }, Modifier.fillMaxWidth(), label = { Text("ID del Shelly") },
                    placeholder = { Text("a1b2c3d4e5f6") }, singleLine = true,
                    supportingText = { Text("Es posa sol si l'has provat a casa. Si no, és a la informació del dispositiu de l'app de Shelly.") },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = ::provaNuvol,
                        enabled = servidor.isNotBlank() && clau.isNotBlank() && idNuvol.isNotBlank() && !provantNuvol,
                    ) { Text("Prova el núvol") }
                    if (provantNuvol) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(24.dp), strokeWidth = 2.dp)
                }
                nuvol?.let { Text(it, Modifier.padding(top = 6.dp), color = if (nuvolOk) Ok else Desconegut, fontSize = 13.sp) }
            }

            Targeta {
                TitolTargeta("4. El preu de la llum")
                Text(resumTarifa(config.tarifa), fontSize = 14.sp)
                if (config.configurat) {
                    TextButton(onClick = { vm.pantalla = Pantalla.PREU }) { Text("Canvia el preu") }
                } else {
                    Nota("El posaràs després de connectar el Shelly, des d'aquí mateix o tocant el preu a la pantalla Ara.")
                }
            }
            Targeta {
                TitolTargeta("Actualitzacions")
                val versio = remember { Actualitzacio.versioActual(context) }
                Text("Tens la versió $versio.", fontSize = 14.sp)
                val nova = vm.versioNova
                val progres = vm.progresActualitzacio
                when {
                    progres != null -> {
                        Nota("Baixant la versió ${nova?.versio}…", Modifier.padding(top = 6.dp))
                        LinearProgressIndicator(progress = { progres }, Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    nova != null -> {
                        Text("Hi ha la versió ${nova.versio}.", Modifier.padding(top = 4.dp), fontSize = 14.sp, color = Primari)
                        Button(onClick = vm::actualitza, Modifier.padding(top = 6.dp)) { Text("Actualitza") }
                    }
                    else -> TextButton(onClick = { vm.comprovaVersio(forcat = true) }) { Text("Mira si n'hi ha de noves") }
                }
                vm.missatgeActualitzacio?.let { Nota(it, Modifier.padding(top = 4.dp)) }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.padding(12.dp)) {
            Button(
                onClick = {
                    config.host = host.trim()
                    info?.let { config.model = it.model }
                    config.idDispositiu = netejaId(idNuvol)
                    config.nomLinia = nomLinia
                    config.servidorNuvol = servidor
                    config.clauNuvol = clau
                    vm.configuracioDesada()
                    vm.sincronitza()
                },
                Modifier.fillMaxWidth(), enabled = potDesar,
            ) { Text(if (config.configurat) "Desa" else "Comença") }
            if (!potDesar) {
                Nota("Prova primer el Shelly a casa, o el núvol.", Modifier.align(Alignment.CenterHorizontally))
            }

        }
    }
}

private fun netejaServidor(s: String) = s.trim().removePrefix("https://").removeSuffix("/")

/** L'ID que fa servir el núvol és la MAC en minúscules: accepta "shellyemg3-dcb4…" o "DC:B4:…". */
private fun netejaId(s: String) = s.trim().lowercase().substringAfterLast('-').replace(":", "")
