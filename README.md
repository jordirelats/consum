# Consum

App d'Android per saber què gasta cada aparell de casa i quant costa, amb un mesurador d'energia Shelly i sense cap servidor.

*English below.*

## Què fa

- **Ara:** el consum en directe, el preu del kWh d'aquesta hora i què hi ha encès.
- **Historial:** el consum per dies, setmanes i mesos, en kWh i en euros.
- **Aparells:** el consum de cada aparell, comparat amb el que diu la seva etiqueta energètica.
  - Els aparells s'afegeixen amb un qüestionari estança per estança, escanejant el QR de l'etiqueta (la base de dades europea EPREL) o a mà.
- **Entrena:** li ensenyes quan encens o apagues un aparell, i l'app n'aprèn la potència.
  - Si apareix un consum que no reconeix, t'ho pregunta amb una notificació.
- **El preu de la llum:**
  - Espanya: 2.0TD de 3 franges o PVPC.
  - Països Baixos: fix, normaal/dal o dinàmic.
  - Un altre país: un preu únic.
  - Cada kWh es paga al preu de la seva hora. Els costos fixos van a part.
- **Dues pinces:** la primera, la de tota la casa; la segona, una línia concreta, que es mesura per separat.

## Què cal

- **Un Shelly amb mesura d'energia de la família Gen2 o Gen3 que tingui els components EM1 i EM1Data**, com el Shelly EM Gen3. Els trifàsics (Pro 3EM) encara no hi són.
- **Android 8 o més nou.**
- **Per veure-ho des de fora de casa, la clau del núvol de Shelly.** És a l'app de Shelly: Configuració → Llar → Accés i permisos → Cloud Key.

## Com s'instal·la

1. Baixa l'APK de l'última versió a [Releases](../../releases).
2. Obre'l des de «Fitxers» i permet instal·lar apps d'aquesta font.
3. A partir d'aquí, l'app avisa quan hi ha una versió nova i s'actualitza des de la Configuració.

## Les dades

- **No hi ha cap servidor.** Les dades del Shelly es guarden només al mòbil.
- **A casa,** l'app llegeix el Shelly directament per la wifi.
- **Des de fora,** fa servir el núvol de Shelly amb la teva clau, que no surt del mòbil.
- **Les úniques connexions a internet són aquestes:**
  - els preus de mercat: EnergyZero als Països Baixos i Red Eléctrica a Espanya;
  - les fitxes de les etiquetes energètiques (EPREL);
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

## Llicència

[GPL-3.0](LICENSE)

---

## English

**Consum** is an Android app that shows what each household appliance uses and what it costs, using a Shelly energy meter (Gen2/Gen3 with EM1/EM1Data, e.g. Shelly EM Gen3) and no server of its own.

- **Live view and history**, with hour-by-hour prices: Spain 2.0TD/PVPC, Netherlands fixed, normaal/dal or dynamic (EnergyZero), or a single price. Fixed costs are shown separately.
- **Appliance inventory**: a room-by-room questionnaire, the EU energy label QR (EPREL), or manual entry.
- **Training**: tell the app when you switch an appliance on or off and it learns its power.
- **Unexpected-consumption alerts**: the app asks what an unknown load is through a notification.
- **Privacy**: data stays on the phone. Remote access uses your own Shelly cloud key, which never leaves the device.

Install the APK from [Releases](../../releases). Licence: GPL-3.0. Not affiliated with Shelly (Allterco Robotics).
