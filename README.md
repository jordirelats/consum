# Consum

App d'Android per saber què gasta cada aparell de casa i quant costa, amb un mesurador d'energia Shelly i sense cap servidor.

*English below.*

## Què fa

- **Ara:** el consum en directe, el preu del kWh d'aquesta hora i què hi ha encès.
  - Amb la potència contractada, quina part se'n fa servir. A la gràfica, en taronja des del 80 % i en vermell des del 90 %.
  - Un avís quan la casa passa del 90 %. Amb l'app tancada, al moment (amb una notificació fixa) o cada 15 minuts.
- **Historial:** el consum per dies, setmanes i mesos, en kWh i en euros.
  - Els moments punta: el minut de més consum i els 5 que gastaven més, amb des de quan eren encesos. Si un salt no s'assembla a cap aparell conegut, l'app pregunta què era i ho aprèn.
- **Aparells:** el consum de cada aparell, comparat amb el que diu la seva etiqueta energètica.
  - Els aparells s'afegeixen amb un qüestionari estança per estança, escanejant el QR de l'etiqueta (la base de dades europea EPREL), buscant-ne la marca i el model a l'EPREL, fent una foto de la placa de característiques o a mà.
  - Una ajuda diu on és la placa amb el model a cada tipus d'aparell. Una fitxa de l'EPREL, una foto o un PDF es poden compartir amb l'app des d'una altra.
  - L'app llegeix la potència de la foto de la placa amb Tesseract, dins del mòbil.
- **Entrena:** li ensenyes quan encens o apagues un aparell, i l'app n'aprèn la potència.
  - Si apareix un consum que no reconeix, t'ho pregunta amb una notificació.
- **El preu de la llum:**
  - Espanya: 2.0TD de 3 franges o PVPC.
  - Països Baixos: fix, normaal/dal o dinàmic.
  - Un altre país: un preu únic.
  - Cada kWh es paga al preu de la seva hora. Els costos fixos van a part.
  - Els preus es poden omplir fent una foto o triant el PDF del contracte o de la factura. L'app els hi busca i els ensenya abans de posar-los. Provat amb un contracte holandès i amb factures d'Endesa i d'Estabanell.
- **Dues pinces:** la primera, la de tota la casa; la segona, una línia concreta, que es mesura per separat.

## Què cal

- **Un Shelly amb mesura d'energia de la família Gen2 o Gen3 que tingui els components EM1 i EM1Data**, com el Shelly EM Gen3. Els trifàsics (Pro 3EM) encara no hi són.
- **Android 8 o més nou.**
- **Per veure-ho des de fora de casa, la clau del núvol de Shelly.** És a l'app de Shelly: Configuració → Llar → Accés i permisos → Cloud Key.

## Com s'instal·la

1. Baixa l'APK de l'última versió a [Releases](../../releases).
2. Obre'l des de «Fitxers» i permet instal·lar apps d'aquesta font.
   - En un Samsung, si surt «S'ha bloquejat una aplicació desconeguda», cal desactivar Ajustaments → Seguretat i privadesa → Bloquejador automàtic.
3. A partir d'aquí, l'app avisa quan hi ha una versió nova i s'actualitza des de la Configuració.

## Les dades

- **No hi ha cap servidor.** Les dades del Shelly es guarden només al mòbil.
- **Les fotos de la placa i els contractes o factures es llegeixen dins del mòbil** i no s'envien enlloc.
- **A casa,** l'app llegeix el Shelly directament per la wifi.
- **Des de fora,** fa servir el núvol de Shelly amb la teva clau, que no surt del mòbil.
  - El núvol només dona el comptador d'energia, i per això el consum de fora és aproximat. Quan el mòbil torna a casa, l'app el substitueix per les dades de cada minut del Shelly, que en guarda una setmana.
- **Les úniques connexions a internet són aquestes:**
  - els preus de mercat: EnergyZero als Països Baixos i Red Eléctrica a Espanya;
  - les fitxes de les etiquetes energètiques i la cerca per model (la web de l'EPREL);
  - GitHub, per mirar si hi ha una versió nova.

## Limitacions

- **El repartiment per aparells és aproximat.** Amb una sola pinça per a tota la casa, els aparells de potència semblant es confonen, i els de menys de 30–50 W no es distingeixen bé.
- **Els avisos poden trigar entre 15 i 30 minuts quan l'app és tancada.** És el mínim que permet Android a les feines en segon pla.
- **Aquesta app no té res a veure amb Shelly (Allterco Robotics).**

## Com es compila

Cal el JDK 17 i l'SDK d'Android 35:

```bash
./gradlew assembleRelease
```

## Llengües

L'app és en català i en neerlandès. Si el mòbil és en neerlandès, surt en neerlandès, i si no, en català. A Android 13 o més nou, la llengua també es pot triar a la Configuració de l'app.

**Per traduir-la a una altra llengua:**
1. Copia [`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml) a `app/src/main/res/values-<codi>/strings.xml`, per exemple `values-es` o `values-en`.
2. Tradueix cada text. No canviïs els `name` ni els `%1$s`, `%2$d`… del text.
3. A `codi_idioma`, posa-hi el codi de la teva llengua (decideix com s'escriuen els números).
4. Obre una *pull request*. La llengua nova surt sola a l'app; no cal tocar cap més fitxer.

Si deixes textos sense traduir, l'app els ensenya en català.

## Llicència

[GPL-3.0](LICENSE).

Fa servir aquestes llibreries i dades, totes amb llicència Apache-2.0:
- [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded), per llegir els QR;
- [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android), per llegir les plaques;
- el model anglès de [tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast).

---

## English

**Consum** is an Android app that shows what each household appliance uses and what it costs, using a Shelly energy meter (Gen2/Gen3 with EM1/EM1Data, e.g. Shelly EM Gen3) and no server of its own.

- **Live view and history**, with hour-by-hour prices: Spain 2.0TD/PVPC, Netherlands fixed, normaal/dal or dynamic (EnergyZero), or a single price. Fixed costs are shown separately.
- **Appliance inventory**: a room-by-room questionnaire, the EU energy label QR (EPREL), a brand and model search on the EPREL website, a photo of the rating plate (read on the device with Tesseract), or manual entry.
- **Contracted power**: how close the house is to it, with an alert above 90 % (immediately, with a persistent notification, or every 15 minutes).
- **Training**: tell the app when you switch an appliance on or off and it learns its power.
- **Peak moments**: the busiest minute of each day and the 5 loads behind it, with when each switched on; unknown steps can be labelled to teach the app.
- **Unexpected-consumption alerts**: the app asks what an unknown load is through a notification.
- **Fill in prices from a contract or bill** (photo or PDF), read on the device.
- **Privacy**: data stays on the phone. Remote access uses your own Shelly cloud key, which never leaves the device.
- **Away from home**, consumption is estimated from the cloud energy counter and replaced with the Shelly's per-minute data once the phone is back on the home Wi-Fi.

Install the APK from [Releases](../../releases). Licence: GPL-3.0. Not affiliated with Shelly (Allterco Robotics).

**Languages:** Catalan and Dutch. To add one, copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<code>/strings.xml`, translate the texts (keep the names and the `%1$s` placeholders), set `codi_idioma` to your language code and open a pull request. Nothing else needs changing; untranslated texts fall back to Catalan.
