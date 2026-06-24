# MORPHE / Boost / Imgur – kilde, regelsett og hurtigoppslag

**Formål:** Dette dokumentet er laget som et kompakt kunnskapsgrunnlag for fremtidige Morphe-, Boost- og Imgur-økter. Bruk det som “Kilde”/knowledge-fil for raske oppslag, release-gates, testpolicy og arbeidsregler.

**Sist oppdatert:** 2026-06-24

---

## 1. Grunnprinsipp

Dette prosjektet skal behandles som et stabiliserings- og releasekritisk Android patch-prosjekt, ikke som tilfeldig feilsøking. Standard arbeidsmåte er:

1. Klassifiser feilen før ny patching.
2. Verifiser tool-syntaks og repo-fakta før lange kommandoer.
3. Gjør én minimal, begrunnet endring om gangen.
4. Bygg kandidat fra riktig kildeartefakt.
5. Test statisk og runtime med logcat/systemtilstand.
6. Ikke release direkte etter teknisk build/release-gate; funksjonell test må først passere.
7. Etter push/release: kontroller remote, tag, asset, metadata, cache og runtime-status separat.

---

## 2. Faste prosjektfakta

| Tema | Verdi / regel |
|---|---|
| Hovedrepo | `~/dev/breal-morphe-patches` |
| Morphe CLI | `/home/b-real/.local/share/morphe/tools/morphe-cli-1.10.0-dev.1-all.jar` |
| Boost base APK | Original APKMirror Boost 1.12.12 APK. Ikke bygg videre fra allerede patchet APK eller installert `base.apk`. |
| Reddit secrets | Ligger lokalt i `~/.config/morphe/reddit.env`, sourced fra `~/.zshrc`. Eksporterer `REDDIT_CLIENT_ID` og `REDDIT_REDIRECT_URI`. Aldri gjenta faktisk client-id i dokumentasjon eller chat. |
| Releasable MPP | Må bygges med `./gradlew :patches:buildAndroid`. `:patches:assemble` alene er ikke nok. |
| MPP-krav | Må inneholde `classes.dex` og `extensions/boostforreddit.mpe`. Morphe Manager trenger `classes.dex`. |
| Foretrukket testbane | Dev-clone/dev package når mulig for å beskytte normal app/data. Normal-package/Manager-signed update brukes når dev-login eller signing gjør dev-clone utilstrekkelig. |
| Brukerpreferanse | Gi copy-pastebare terminalkommandoer som printer faktisk relevant output. Ikke be brukeren selv vurdere diff/status. Bruk alltid `git --no-pager`. |

---

## 3. Terminal- og kommando-policy

### 3.1 Ikke “poison” brukerens interaktive terminal

Unngå dette direkte i brukerens shell:

- `set -euo pipefail`
- rå `exit 1`
- lange kommandoer som kan avslutte terminalsesjonen
- kommandoer som åpner pager og skjuler output

Bruk heller guarded child-script:

```bash
cat > /tmp/morphe-task.sh <<'BASH'
#!/usr/bin/env bash
FAIL=0
mark_fail() { echo "FAIL: $*"; FAIL=1; }

# kommandoer her, med eksplisitt logging og grep-tester

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=PASS"
else
  echo "RESULT=FAIL"
fi
exit "$FAIL"
BASH

LOG="/tmp/morphe-task.$(date +%Y%m%d-%H%M%S).log"
bash -c 'bash /tmp/morphe-task.sh 2>&1 | tee "$0"; RC=${PIPESTATUS[0]}; echo "SCRIPT EXIT CODE: $RC"; echo "LOG: $0"; echo "Terminal still alive."; exit $RC' "$LOG"
```

### 3.2 Git-output skal være synlig

Bruk:

```bash
git --no-pager status -sb
git --no-pager diff
git --no-pager show --stat --oneline --decorate HEAD
git --no-pager log --oneline --decorate -8
```

Ikke bruk kommandoer som lar `less`/pager overta terminalen.

---

## 4. Patch- og build-policy

### 4.1 Før patching

- Finn først riktig fil, klasse, fingerprint eller smali-/Java-kandidat.
- Ikke anta Morphe CLI-syntaks fra hukommelse. Sjekk `--help`, repo-kilde eller eksisterende scripts.
- Ikke skriv store scripts før feilen er klassifisert.
- Én minimal endring per runde. Tooling-/release-script-endringer skal behandles som produksjonskode.

### 4.2 Boost APK-kandidat

- Bygg fra original APKMirror Boost 1.12.12 APK.
- Ikke bruk installert `base.apk` som base for ny patchkandidat.
- Ikke bygg fra allerede patchet APK.
- Morphe CLI patch-options må bindes til patch enable-blokken: `-O=client-id=...`, `-O=redirect-uri=...`, og `-O=user-agent=...` må ligge rett før `-e "Spoof client"`.
- `Fix Boost target SDK 35 compatibility` må eksplisitt aktiveres med `-e`, fordi MPP-default kan være false.

### 4.3 Functional-test baseline

For Boost funksjonstesting skal APK-kandidat minimum ha:

- `Spoof client`
- `Modify login WebView`

Valider dette fra Morphe CLI apply-log/options, ikke bare fra MPP-innhold eller APK-string markers. En MPP kan inneholde patch-navn selv om APK ble bygget med patchen deaktivert/skippet.

---

## 5. Dev-clone-policy

Dev-clone foretrekkes for å beskytte normal installasjon, men har kjente begrensninger.

### 5.1 Før overskriving av dev package

Ta read-only checkpoint:

- package name
- launcher label, for eksempel `Boost Dev`
- signer SHA
- installed `base.apk` SHA
- targetSdk
- first/last install time
- om mulig: trygg observasjon av appdata/auth-state
- trekk kopi av installert APK før eksperimentering

### 5.2 Dev support patch set

Minstekrav for dev-clone-støtte:

- provider/authority rewrite
- self-intent/package rewrite fra `com.rubenmayayo.reddit` til dev package
- dev login/auth-håndtering
- eventuelt Play/Billing/Integrity-neutralisering
- notification permission helper ved target SDK 33+

### 5.3 Kjente dev-clone-blokkere

- `INSTALL_FAILED_CONFLICTING_PROVIDER` betyr at provider authority fortsatt peker på normal package.
- `ActivityNotFoundException` med `{com.rubenmayayo.reddit/...}` i dev-clone betyr at intern self-routing fortsatt lekker normal package.
- Dev-clone-login har tidligere gitt `{}` selv med `Spoof client` og `Modify login WebView`. `Modify login WebView` alene er ikke rotårsaken.

---

## 6. Runtime-testpolicy

### 6.1 Ingen release uten runtime

En teknisk gyldig release-gate er ikke nok. Før release skal relevant runtime-path testes med logcat/systemtilstand.

Minimum:

- app starter og holder seg i forventet activity
- ingen `FATAL EXCEPTION`
- ingen relevant `AndroidRuntime`-crash
- ingen regresjon i login der login er relevant
- ingen gamle blocker-logglinjer for den feilen som ble fikset
- faktisk brukerflyt fungerer, ikke bare at APK installerer

### 6.2 Target SDK 35 / notifications

Ved target SDK-endringer skal build-success aldri tolkes som runtime-validering. Sjekk:

- manifest permissions, spesielt `POST_NOTIFICATIONS`
- installed requested permissions
- appops
- notification channels
- faktisk `NotificationRecord`
- receiver registration flags
- foreground service constraints
- storage/media-permissions der relevant
- pending-intent mutability der relevant
- exported component rules der relevant

Completed download notification-pass krever at Boost-notifikasjonen faktisk observeres, for eksempel på `81_downloads_completed_channel`.

### 6.3 Crashlytics/noise

Den grønne Boost Crashlytics-løsningen var:

- NOP/fjern `MyApplication.onCreate()` sin `FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(...)`-call.
- Fjern kun `com.google.firebase.components:com.google.firebase.crashlytics.CrashlyticsRegistrar` fra Firebase `ComponentDiscoveryService`.
- Behold `FirebaseInitProvider`, Firebase Analytics (`FA`), Installations, Remote Config og ABT.

Acceptance:

- ingen `FirebaseCrashlytics`
- ingen `firebase-settings`
- ingen `Settings request failed`
- app forblir i forventet activity
- `FirebaseApp`, `FirebaseInitProvider` og `FA` kan fortsatt forekomme og er ikke i seg selv feil

### 6.4 Boost media-routing

For Boost inline/static media:

- statiske preview-bilder og direkte `i.redd.it/*.gif` skal rutes til Boost image viewer / `MediaImageActivity`
- animerte Giphy/video-paths kan bruke `MediaVideoActivity` der det er korrekt
- direkte `i.redd.it/*.gif` skal ikke tvinges til avledet `.mp4`-URL
- ingen `GifIOException`
- ingen ExoPlayer 404 for direkte GIF som skyldes `.gif -> .mp4`-tvang
- generiske Glide 404-linjer er ikke automatisk blocker uten URL-kontekst

---

## 7. Release-gate

### 7.1 Metadata og versjon

Før release:

- `gradle.properties` har riktig versjon
- release tag stemmer, f.eks. `morphe-patches-27`
- MPP-navn stemmer, f.eks. `patches-1.4.27.mpp`
- `patches-bundle.json` peker til riktig tag/asset/version
- README viser riktig versjon, tag, asset og SHA256
- gamle releaseverdier er borte
- Manager-facing `description` er kort og gjelder kun aktuell release

### 7.2 Manager-facing description

`patches-bundle.json.description` skal være kort. Ikke bruk den som full historikk.

Unngå/forby slike fraser:

- `Morphe patch bundle`
- `Latest in`
- `Also includes`
- `previous fixes`
- `Clean APKs`
- lange “wall of text”-beskrivelser

Bruk heller én kategori og én eller få bullets, for eksempel `Bug Fixes` med nåværende endringer.

### 7.3 Artefakt-gate

Releasable MPP må være bygget med:

```bash
./gradlew :patches:buildAndroid
```

Kontroller:

```bash
unzip -l patches/build/libs/patches-<VERSION>.mpp | grep -E 'classes.dex|extensions/boostforreddit.mpe'
sha256sum patches/build/libs/patches-<VERSION>.mpp
```

Fail release hvis:

- `classes.dex` mangler
- `extensions/boostforreddit.mpe` mangler
- README SHA ikke matcher MPP
- build artifacts er staged
- gamle tagger/versjoner fortsatt ligger i metadata
- required marker strings mangler

### 7.4 Marker strings

Release-gate skal sjekke patch-spesifikke marker strings i releasable artefakt/source, for eksempel:

- `openStaticImageViaBoost`
- `MediaImageActivity`
- `com.rubenmayayo.reddit.ui.activities.i`
- `open direct i.redd.it gif via Boost image viewer`
- `InlineGiphy`
- relevante Crashlytics-/notification-markører ved slike endringer

---

## 8. Post-push og remote verification

Etter hver meningsfull push:

1. Sjekk lokal status.
2. Sjekk `main == origin/main`.
3. Hvis release: sjekk tag peker på riktig commit.
4. Sjekk GitHub release eksisterer.
5. Sjekk remote asset SHA matcher lokal/README.
6. Last ned remote MPP og kontroller `classes.dex` og `extensions/boostforreddit.mpe`.
7. Sjekk `patches-bundle.json` via lokal fil, `origin/main` git object og GitHub API.
8. Hvis raw.githubusercontent.com er stale, behandle det som CDN/Fastly-cache bare hvis API/git object er korrekt.
9. Kjør `make verify-remote` / `verify-remote-release.sh` der tilgjengelig.

Etter metadata-only commits:

- retag release tag til final metadata commit
- ingen ny MPP-upload er nødvendig hvis asset er uendret
- remote verification må likevel kjøres

---

## 9. Kjente grønne stoppunkter

### 9.1 Morphe patch bundle 1.4.26

Publisert og remote-verifisert 2026-06-22.

- Tag: `morphe-patches-26`
- Commit: `e9dba33`
- Asset: `patches-1.4.26.mpp`
- SHA256: `9161f21927522ccfcabeaebf2644d51ba1db6e94d42de0f383570131c9466550`
- Innhold: `classes.dex` og `extensions/boostforreddit.mpe`
- `verify-remote`: `REMOTE RELEASE OK`
- Hovedendring: Boost target SDK 35 compatibility med `POST_NOTIFICATIONS` for synlige completed download notifications på Android 13+.

### 9.2 Morphe patch bundle 1.4.27

Publisert og remote-verifisert 2026-06-23.

- Tag: `morphe-patches-27`
- Commit: `93b11d083f0e4b93555400aa956579fd1a3e0645`
- Asset: `patches-1.4.27.mpp`
- SHA256: `2d0a79bc6190ce924a0c2121cd9eecb423f541c9c4168e126a7ed9b1508d519a`
- Innhold: `classes.dex` og `extensions/boostforreddit.mpe`
- `verify-remote-release.sh`: `REMOTE RELEASE OK`
- Hovedendringer: direkte `i.redd.it` GIF-lenker rutes via Boost image viewer i stedet for video/.mp4-path; safe URL-handling ble gjenopprettet for å unngå login callback `{}`-regresjon.
- Post-release Manager runtime: `POST_RELEASE_MANAGER_RUNTIME=PASS`. Første Reddit-loginforsøk ga serverfeil, andre forsøk fungerte. Dette ble klassifisert som transient, ikke kodefeil.

---

## 10. Imgur patch-regler

For Imgur 7.33.0.0 / media share:

- “Share selected media” skal dele valgt media-item, ikke parent gallery.
- Link-sharing og file-sharing skal holdes separert.
- File-sharing skal bruke privat cache + Android sharesheet, ikke permanent lagre i `/sdcard/Download/Imgur`.
- Bruk `ClipData` og `FLAG_GRANT_READ_URI_PERMISSION` for mottakerapper/preview.
- Ønsket UX-retning: rename “Imgur Download” til “Imgur – Share” og etter hvert autokjøre sharesheet uten ekstra notifikasjonstrykk, hvis teknisk forsvarlig.

---

## 11. Feilklassifisering før neste handling

Før ny kommando eller patch skal feilen plasseres i én av disse kategoriene:

| Kategori | Eksempel | Neste riktige handling |
|---|---|---|
| Build/tooling | Gradle, D8, MPP mangler dex | Les buildlogg, minimal build-script fix, rerun gate |
| Metadata/release | Feil tag, SHA, raw cache, description | Patch metadata/scripts, verify API/git/raw separat |
| Static APK | Manglende permission, marker, class, manifest | Inspect APK/MPP før install |
| Install/signing | signer mismatch, provider conflict | Klassifiser som installbane, ikke kodefeil før bevist |
| Runtime crash | `FATAL EXCEPTION`, `AndroidRuntime` med app context | Logcat narrow filter + root cause før ny patch |
| Runtime behavior | feil activity/routing/login/resultat | Reproduser minimal path og sammenlign expected vs actual |
| Noise/non-blocker | generiske Glide 404, unrelated system warnings | Ikke patch uten URL-/context-bevis |

---

## 12. Standard “next response” fra assistenten i dette prosjektet

Når brukeren ber om neste steg i Morphe/Boost/Imgur:

1. Start med kort klassifisering av nåværende status.
2. Gi én konkret kommando eller ett guarded child-script.
3. Kommandoen skal printe output assistenten trenger for å vurdere neste steg.
4. Ikke be brukeren “se om det ser riktig ut”.
5. Ikke gi mange alternative scripts samtidig.
6. Ikke fortsett til release uten eksplisitt runtime-validering.
7. Når noe er grønt, skriv tydelig hva som er validert og hva som fortsatt ikke er validert.

---

## 13. Copy-paste hurtigkommandoer

### 13.1 Repo status

```bash
cd ~/dev/breal-morphe-patches

echo "===== repo status ====="
git --no-pager status -sb

echo
 echo "===== recent commits ====="
git --no-pager log --oneline --decorate -8

echo
 echo "===== local/main alignment ====="
echo "HEAD:        $(git rev-parse HEAD)"
echo "origin/main: $(git rev-parse origin/main)"
```

### 13.2 Sjekk MPP-innhold

```bash
cd ~/dev/breal-morphe-patches
MPP="patches/build/libs/patches-<VERSION>.mpp"

echo "===== MPP SHA ====="
sha256sum "$MPP"

echo
 echo "===== required MPP entries ====="
unzip -l "$MPP" | grep -E 'classes.dex|extensions/boostforreddit.mpe' || true
```

### 13.3 Logcat filter for Boost media/Crashlytics

```bash
adb logcat -c
adb logcat \
  | grep -Ei 'InlineGiphy|open direct i\.redd\.it gif|MediaImageActivity|MediaVideoActivity|\.mp4|404|ExoPlayer|GifIOException|FirebaseCrashlytics|firebase-settings|Settings request failed|AndroidRuntime|FATAL EXCEPTION'
```

### 13.4 Remote release sanity

```bash
cd ~/dev/breal-morphe-patches

echo "===== local status ====="
git --no-pager status -sb

echo
 echo "===== heads ====="
echo "HEAD:        $(git rev-parse HEAD)"
echo "origin/main: $(git rev-parse origin/main)"

echo
 echo "===== latest tags ====="
git --no-pager tag --sort=-creatordate | head -10
```

---

## 14. Ikke gjør dette

- Ikke release fordi builden passerte.
- Ikke stol på MPP-innhold alene for å si at APK-kandidat har patch aktivert.
- Ikke bygg testkandidat fra allerede patchet APK.
- Ikke skriv eller gjenta hemmelige client IDs.
- Ikke la raw.githubusercontent.com alene avgjøre sannhet rett etter push; sammenlign API/git object.
- Ikke behandle dev-clone smoke som full validering hvis login/auth ikke fungerer.
- Ikke bruk lange Manager descriptions eller “also includes previous fixes”.
- Ikke overskriv kjent fungerende dev installasjon uten checkpoint.
- Ikke gi terminalkommandoer som kan lukke brukerens shell.

---

## 15. Praktisk definisjon av “grønt”

En endring er bare grønn når alle relevante lag er passert:

1. Source/diff er forstått.
2. Build er riktig type build.
3. Artefakt inneholder nødvendige filer.
4. Static APK/MPP checks passer.
5. Kandidaten er bygget med forventede patch-options.
6. Runtime path er testet med faktisk brukerflyt.
7. Logcat/systemtilstand viser ingen relevante blokkere.
8. Repo er rent eller endringer er bevisst staged/committed.
9. Remote release/metadata er verifisert hvis release er gjort.
10. Utestede områder er eksplisitt merket som utestet, ikke implicit godkjent.
