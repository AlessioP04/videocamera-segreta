# Videocamera segreta

App Android che fa una cosa sola: **registra video anche a schermo spento**.

- **Apri l'app** → parte la registrazione (fotocamera posteriore, con audio) e l'app si chiude subito.
  Ora puoi spegnere lo schermo: la registrazione continua.
- **Riapri l'app** (oppure tocca la notifica, o il pulsante *Ferma e salva*) → la registrazione si ferma
  e il video viene salvato in **galleria**, album `Movies/Videocamera segreta` (`VID_aaaammgg_hhmmss.mp4`).

Un breve messaggio conferma l'avvio ("Registrazione avviata") e il salvataggio ("Video salvato in galleria").

## Installazione e aggiornamenti

Scarica l'APK dall'ultima [Release](https://github.com/AlessioP04/videocamera-segreta/releases/latest)
e installalo (serve consentire l'installazione da origini sconosciute). Al primo avvio l'app chiede i permessi:

| Permesso | Serve per | Se lo neghi |
|---|---|---|
| Fotocamera | registrare | l'app non funziona (apre le impostazioni per concederlo) |
| Microfono | l'audio del video | il video è muto |
| Notifiche | vedere la notifica di registrazione e gli avvisi di aggiornamento | la registrazione funziona lo stesso |

Gli aggiornamenti si installano sopra la versione esistente. Al massimo una volta al giorno, quando parte una
registrazione, l'app controlla su GitHub se c'è una versione nuova e in quel caso mostra una notifica che porta
alla pagina di download (è l'unico uso di internet).

### Cose da sapere

- **Notifica e indicatore verde**: Android obbliga un'app che usa la fotocamera in background a mostrare una
  notifica fissa, e da Android 12 il sistema mostra il pallino verde della fotocamera. Non si possono nascondere.
  La notifica è comunque silenziosa e a bassa priorità.
- **Risparmio energetico**: su alcuni telefoni (Xiaomi, Huawei, Samsung con ottimizzazioni aggressive) conviene
  impostare la batteria dell'app su *Senza restrizioni*, altrimenti il sistema potrebbe interrompere registrazioni lunghe.
- Se un'altra app prende la fotocamera (es. apri la Fotocamera), la registrazione si ferma e il video fin lì viene salvato.
  Lo stesso succede se lo spazio finisce.
- Registrare persone a loro insaputa può essere illegale: usala nel rispetto della privacy altrui.

## Sviluppo

Android nativo in Kotlin, senza interfaccia grafica (≈1,6 MB). Richiede Android 10+.

| File | Ruolo |
|---|---|
| `MainActivity.kt` | activity invisibile: chiede i permessi, avvia o ferma il servizio e si chiude |
| `RecorderService.kt` | servizio in primo piano (tipo `camera\|microphone`) con CameraX `VideoCapture`; scrive in MediaStore |
| `Rotation.kt` | orientamento del video dal sensore (dritto anche con la rotazione automatica spenta), nome del file |
| `Updates.kt` | controllo giornaliero dell'ultima release su GitHub |

```
cd android
./gradlew testDebugUnitTest assembleDebug     # test + APK di debug
```

## Automazione (GitHub Actions)

- `android.yml`: test, lint e APK di debug (come artifact) a ogni modifica di `android/`.
- `release.yml`: a ogni tag `vX.Y.Z` compila l'APK firmato e lo pubblica nelle
  [Releases](https://github.com/AlessioP04/videocamera-segreta/releases/latest):
  ```
  git tag v0.2.0 && git push origin v0.2.0
  ```
  La versione dell'app viene dal tag (versionCode = X·10000 + Y·100 + Z).

La chiave di firma è nei secret del repo (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`); la copia di sicurezza è fuori dal repo, in `../videocamera-segreta-firma/`
(vedi `LEGGIMI.txt` lì dentro). **Non va mai rigenerata**: con una chiave diversa gli aggiornamenti non si installano.
