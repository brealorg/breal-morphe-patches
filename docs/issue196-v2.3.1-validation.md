# #196 – manuelt gjennomgått runtime-resultat for v2.3.1

## Vurdering

**PASS_FOR_TESTED_RECOVERY_FLOW** for innlegget `b1ew30` og kontrollbildet `n4ss3gg7aam21.jpg`.

Dette er en manuell vurdering av brukerens innlimte `summary.json`-utskrift og `relevant.txt` fra det samme forsøket. Den erstatter ikke råopptaket, endrer ikke den opprinnelige summary-filen og er ikke en ny runtime-kjøring.

## Kandidat og evidens

- Kandidat: `v2.3.1`
- Pakke: `com.rubenmayayo.reddit.dev`
- APK SHA-256, bekreftet av den opprinnelige proben: `c64e185a770e9b97d1a216cf8a4736fb47346549dc981f982f6718057cb45d1d`
- Opptaksmappe på brukerens PC: `$HOME/Downloads/morphe-issue196-known-media-runtime-v1.1-20260915-230212-ee8704`
- Innlegg: `https://www.reddit.com/r/gaming/comments/b1ew30/`
- Kontrollbilde: `https://i.redd.it/n4ss3gg7aam21.jpg`
- Arkivopptak valgt av appen: `https://web.archive.org/web/20190315163551if_/https://i.redd.it/n4ss3gg7aam21.jpg`
- Lokal referanse: opptaket med samme timestamp, hentet med `id_`; visuelt godkjent av brukeren.
- Referansens SHA-256: `21fb92ee33ad906dc8566ed40e763eaf818f69248c9e07096bf92e52f0fe5dc0`

## Observerte hendelser

Alle nedenstående recovery-linjer er fra app-PID `11255` i det innlimte utdraget.

1. `Arctic Shift: .../api/posts/ids?ids=b1ew30` etterfølges av én `morphe_boost_undelete_issue196_post_restored`.
2. Ti kommentaroppslag vises. Ni etterfølges av `morphe_boost_undelete_issue196_comment_restored`. Utdraget gir ikke et suksessresultat for oppslaget på `eimw8mw`; dette vurderes ikke som automatisk feil eller suksess.
3. Ved timestamp `1789506166.449113` logges originaladresse → arkivadresse for kontrollbildet.
4. Ved timestamp `1789506185.595235` starter Android `MediaImageActivity` i Boost DEV.
5. Ved timestamp `1789506185.636320` logges et positivt cachetreff for den samme arkivadressen.
6. Brukeren bekrefter at Boosts egen bildeviser viser det samme faktiske bildet som den lokale referansen, og at det ikke oppstod krasj eller uventet retur til Home.

Den tidligere innlimte summaryen oppgir verifisert opptaksvindu, samme start-/slutt-PID og null app-fatal-, app-crash-, ABI-, native-fatal-, death- og ANR-tellere. Det fulle private råopptaket er ikke gjennomgått på nytt som del av denne manuelle vurderingen.

## Hva Wayback-linjene betyr

I den undersøkte `WaybackMachine.java` fra commit `e3f67666d00fa0060958d487792008eacfbc69cd` logges `URL <original> -> <arkivadresse>` etter at timemap-resultatet er lest og et timestamp er valgt i banen som ikke returnerer fra et tidligere cachetreff. Arkivadressen lagres deretter, og koden kaller `HttpUtils.get`.

Den senere `URL <arkivadresse> cached`-linjen tilhører en positiv URL-cache. Denne cachen inneholder arkivadressen; koden kaller også her `HttpUtils.get(cachedUrl)`. Linjen betyr derfor ikke i seg selv at bildeviseren bare leste en gammel bitmap.

Kombinasjonen av de målspesifikke Wayback-linjene, intern bildeviser og brukerens visuelle bekreftelse er tilstrekkelig for et funksjonelt PASS for denne flyten.

## Hvorfor automatisk resultat beholdes separat

Den leverte `known-media-runtime-v1.1`-proben krever `glide_control_restored > 0` for samlet PASS. Den registrerer Wayback-ruting og positiv URL-cache, men har ingen positiv recovery-klassifisering for denne kombinasjonen.

Automatisk resultat beholdes som historikk:

`MORPHE_ISSUE196_KNOWN_MEDIA_RUNTIME_NEEDS_REVIEW`

Manuelt gjennomgått funksjonelt resultat:

`PASS_FOR_TESTED_RECOVERY_FLOW`

Dette er ikke en endring fra «Glide-hook bevist» til PASS: den spesifikke Glide-hooken er fortsatt **ikke demonstrert**. Det bredere funksjonelle resultatet er dokumentert separat.

## Begrensninger

- Logglinjene alene identifiserer ikke sikkert hvilken direkte caller som brukte den delte Wayback-hjelperen. Glide-hook og OkHttp-interceptor skal ikke likestilles uten caller-evidens.
- Loggen viser ikke HTTP-status, overførte byte eller transportcache for hvert appkall. Ny overføring av bildebyte over nett er ikke en separat måling i dette forsøket.
- Ni kommentar-recovery-markører er kodeevidens, ikke en visuell kontroll av alle ni kommentarene.
- Resultatet gjelder ett kontrollbilde og det tilhørende innlegget. Det er ikke et PASS for de to bildene fra `1vdrb92`, alle gallerier eller enhver recovery-kilde.
- En vellykket ON-kjøring erstatter ikke samlet dokumentasjon av OFF, ugyldige data, kilde-/testendringer og øvrige akseptansekriterier i #196.
- Ingen GitHub-endring, commit, merge eller publisering utføres eller bekreftes av denne vurderingen.

## Videre behandling

Behold v2.3.1 og dette forsøket som kvalifikasjonsevidens. Det er ikke nødvendig å gjenta kontrollbildet bare for å frembringe Glide-markøren. Neste arbeidssteg er samlet evidens-/scopegjennomgang og checkpoint av kandidaten, ikke en ny spekulativ bildefiks.

## Kildegrunnlag for kodetolkningen

- `https://github.com/brealorg/breal-morphe-patches/blob/e3f67666d00fa0060958d487792008eacfbc69cd/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/http/wayback/WaybackMachine.java`
- Lokal kopi av levert probe: `morphe-issue196-known-media-runtime-v1.1.sh`, særlig klassifiseringslogikken.
- Akseptansekriterier: `https://github.com/brealorg/breal-morphe-patches/issues/196`


## Samlet lokal checkpoint-evidens

Kilde-/testfilene er bundet til eksisterende bygg gjennom build-runnerens opprinnelige fingerprint og filenes SHA-256. Ingen tester, APK-bygg eller runtime-kjøringer gjentas. Den ferdige committen bevarer disse byteverdiene; ny dokumentasjon er ikke ny appkode.

- 22 eksisterende regresjonsresultater gjenbrukes, kontrollert mot de 22 testnavnene.
- Signert DEV-APK: 242 Jackson-referanser, 0 ABI-funn i det opprinnelige rapporterte scope.
- To opprinnelige NPE-fixtures: TWO_EXPECTED_ORIGINAL_CODE_NPE_PROOFS_REUSED.
- OFF-test i JVM-harness er bestått. Historisk OFF-runtime fra v2.2 er ikke gjort om til en OFF-kjøring på v2.3.1.
- Runtime for 1vdrb92: ingen krasj i cache-/markørbanen, men originalbildene ble ikke vist.
- #168 og de historiske kandidat-ABI-feilene er holdt atskilt fra nåværende primary-resultat.

**Release er ikke godkjent av dette checkpointet.** OFF på endelig kandidat og samlet akseptansevurdering står fortsatt separat. Ingen eksakt identitet med ekstern rapportørs krasj hevdes. Ingen push, merge, tag, release eller issue-endring inngår.

Maskinlesbar evidens og kildehashene ligger i `issue196-v2.3.1-validation.json`. Rå systemlogger og cache-/bildedata holdes utenfor Git. Opprinnelige summary-filer er uendret.

## Final v2.3.1 ON/OFF acceptance addendum

Source checkpoint: `0ba093eb126bef452dc9acbc2f954f0c303291a9`. DEV APK: `c64e185a770e9b97d1a216cf8a4736fb47346549dc981f982f6718057cb45d1d`.
This addendum supersedes the *pending final-candidate OFF* disposition above, not the historical evidence itself.
The original automatic ON `NEEDS_REVIEW` and the separate reviewed functional PASS are retained.

| Evidence | Accepted scope |
|---|---|
| Focused regression | 22 existing PASS results, reused without rerun |
| Signed-APK Jackson ABI | 242 references / 0 findings in the previously checked scope |
| Original-source NPE fixtures | `TWO_EXPECTED_ORIGINAL_CODE_NPE_PROOFS_REUSED` |
| ON, b1ew30 | Post/comment recovery, selected Wayback URL, native viewer, visually matching control image; reviewed PASS |
| ON, 1vdrb92 | Stable cached recovery and deletion-marker normalization; original gallery images NOT visible |
| OFF, b1ew30 and 1vdrb92 | Same final APK; refresh, comments and navigation confirmed without observed UI/loading failure |
| OFF, normal feed | Feed refresh, ordinary post, comments and Back confirmed |
| OFF saved flag | Verified at all five recorded checkpoints |
| OFF app fatal/crash/ABI/native death/ANR counters | All zero |
| OFF recovery markers / Arctic Shift / Reddit Wayback | All zero recorded lines |
| OFF malformed-URL warning lines | 6; retained as warnings, not counted as crashes |

### Maintainer disposition

**Ready for PR review of the tested candidate.** No blocking failure was identified within the validated
#196 scope. This is not a complete CI/release gate and does not authorize issue closure or publication.
The original reporter's exact fatal stack was not obtained; deterministic original-source NPE fixture
failures and historical candidate-introduced Jackson regressions must not be represented as that stack.

The saved OFF value plus zero recovery markers supports disabled behavior in these flows, not a packet trace.
Warnings do not negate the observed stable run, but issue168's full regression contract was not rerun.
Archive availability, the specific Glide hook and missing original gallery pixels remain separately qualified.

Only this evidence addendum is new. Production code, test code, APKs, original summaries and raw logs are unchanged.
The structured addendum and local input hashes are in `issue196-v2.3.1-validation.json` under `final_acceptance`.
