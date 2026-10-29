# PLAN — Finnish voice navigation for Android (working name: **FinNav**)

> Revision 2 (2026-10-29). Changes from revision 1 follow the plan review: the voice
> assistant becomes the primary trigger, the Bluetooth button becomes a fallback, the
> geocoder leaves the MVP, the latency budget and accuracy gate are made explicit,
> and the code is split so pure logic builds without the Android SDK. See §13.

## 1. Goal

Drive with both hands on the wheel, say one English trigger phrase (*"Hey Google, open FinNav"*), say a Finnish destination — *"Hämeenkatu 10, Tampere"*, *"Särkänniemi"*, *"mökki"* — and have turn-by-turn navigation start in Google Maps (and therefore on the Android Auto screen) **without touching the phone or the car screen**.

### Success criteria (MVP)
- Destination utterance → route running in ≤ 8 s for a clear utterance (budget in §4.1).
- Recognition accuracy: **≥ 45 correct of ≥ 50 trials** parked, ≥ 40 of 50 driving at road noise. (Ten trials cannot demonstrate 90 %: 9/10 has a 95 % Wilson interval of 60–98 %, and a 70 %-accurate system scores ≥ 9/10 about 15 % of the time. 45/50 gives 79–96 %.)
- Zero screen touches in the happy path.
- The recognised text is spoken back in Finnish before or as the route starts, with a way to cancel.

### Non-goals (for now)
- General voice assistant, seat heating, music, messaging.
- An Android Auto–native (Car App Library) UI.
- iOS.
- A backend server. Everything runs on the phone.

---

## 2. Why this has to be built (context)

| Fact | Status | Source |
|---|---|---|
| Gemini supports Finnish but marks it as *not* supporting "Hey Google" voice commands and some Gemini action features — so "navigate to …" in Finnish fails | Sourced | android.com/auto (language list, Finnish*) |
| Waze voice commands are English-only in seven countries; elsewhere the mic is voice *search* only and stops at a result list | Sourced | support.google.com/waze/answer/6268717 |
| In English, Finnish place names are mangled by speech-to-text ("Tampere" → "Tampa") | User observation | — |
| Google's speech recogniser handles Finnish well when the language is set to Finnish | User observation + inferred | — |
| `google.navigation:q=<address or lat,lng>` launches Google Maps **turn-by-turn navigation** from the current location; if a text query has several results, the first is chosen | Sourced | developer.android.com/guide/components/google-maps-intents |
| Waze `https://waze.com/ul?q=…&ll=…&navigate=yes` starts navigation; documented examples pair `q` with `ll` | Sourced | developers.google.com/waze/api |

**The whole problem is the speech-to-text language.** If we choose the recogniser language ourselves (fi-FI) and pass the result straight to the navigation intent, the problem goes away. The English assistant is used only for the trigger phrase, which contains no Finnish.

---

## 3. Platform constraints that shape the design (Android 14+, minSdk 33)

1. **Background activity starts are blocked** unless an exemption applies (developer.android.com/guide/components/activities/background-starts). When a voice assistant or the system launches our activity, the launcher is exempt, not us — so the voice trigger needs no special permission (inferred; Phase 0 verifies). `SYSTEM_ALERT_WINDOW` remains the fallback exemption.
2. **Microphone from the background is blocked** for foreground services started in the background (developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
3. **Core design rule:** every trigger **opens a visible Activity first**. While it is visible the app is in the foreground, so the mic and the Maps launch are both allowed. No background mic access in the MVP.
4. The phone is usually **locked, screen off**. The capture Activity uses `showWhenLocked` + `turnScreenOn`. Whether the assistant will open a third-party app on a locked phone, and whether the Maps intent then starts navigation, are **unknown → spikes S4 and S1**. Gemini asks to unlock for "personal content" requests (support.google.com/gemini/answer/14576209); whether "open an app" counts is untested.
5. **Hardware keys do not reach apps while the display is off.** `PhoneWindowManager.interceptKeyBeforeQueueing` drops non-injected keys when the device is non-interactive (it may wake the device, and the waking press is swallowed), and `AccessibilityInputFilter.onInputEvent` skips any event without `FLAG_PASS_TO_USER`. Sourced from AOSP `main`. This is why the Bluetooth-button trigger is no longer primary (§7, Phase 3).
6. **Long-press of headset-hook / play-pause** on a locked or screen-off phone makes `MediaSessionService.startVoiceInput` start `RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE` (AOSP `main`). An app that handles that intent and is chosen as default can be launched from a pocket by a cheap media remote. OEM overrides unknown → spike S3b.
7. Samsung phones may default to **Samsung's speech recognition service**. We enumerate `RecognitionService`s and pin Google's explicitly (spike S2).
8. **Sideloading.** An app not installed by a store cannot enable an accessibility service until the user chooses *App info → ⋮ → Allow restricted settings* (Android 13+, extended in Android 15). Google's developer-verification requirement for sideloaded apps took effect on 2026-09-30 in Brazil, Indonesia, Singapore and Thailand and is planned globally for 2027; ADB installs are reported to remain possible. The MVP avoids accessibility services entirely; distribution is a debug build over ADB / Android Studio.

---

## 4. Architecture

```
[Trigger: "Hey Google / Hi Bixby, open FinNav"  |  hands-free voice-search intent]
        │
        ▼
[CaptureActivity (visible, over lock screen)]
   ├─ AudioFocus (transient)
   ├─ Recogniser (fi-FI, pinned service, optional biasing strings) ─► n-best + confidence
   ├─ Normaliser  ─► clean destination string
   ├─ AliasStore  ─► alias hit → stored address/coords; otherwise the text itself
   ├─ Confirmer (TTS fi-FI, nav-guidance audio usage) ─┐  run concurrently
   └─ NavLauncher ─► google.navigation:q=…  ───────────┘
   finish()
```

The MVP passes **recognised text** to Maps and lets Maps' own search resolve it (location-biased, very likely better than the platform `Geocoder`). The confirmation reads back the recognised text — which is what catches recognition errors. The geocoder returns in Phase 2, where Waze and disambiguation need coordinates.

### 4.1 Latency budget (estimates, to be replaced by S1/S2 measurements)

| Stage | Estimate |
|---|---|
| Activity + recogniser warm-up | 0.8 s |
| User speaks | 2.0 s |
| End-of-speech silence | 1.0 s |
| Final result | 0.5 s |
| TTS confirmation | 2.5 s |
| Cancel window | 2.0 s |
| Maps launch + route | 2.0 s |
| **Sequential total** | **10.8 s** |

Sequential execution misses the 8 s target. Design response: start Maps **while** the confirmation is spoken, and make "cancel" mean *stop navigation* rather than a pre-launch window. That removes the 2.0 s window and overlaps TTS with the Maps launch. Phase 0 measures both orderings.

### Components (Kotlin)

| Component | Module | Responsibility |
|---|---|---|
| `CaptureActivity` | app | Minimal full-screen UI ("Kuuntelen…"), shows over the lock screen, runs the pipeline, then finishes. Launcher entry point (so "open FinNav" works) and handler of `ACTION_VOICE_SEARCH_HANDS_FREE`. |
| `Recogniser` / `AndroidSpeechRecogniser` | app | `SpeechRecognizer` with `EXTRA_LANGUAGE="fi-FI"`, `EXTRA_MAX_RESULTS=5`, explicit `ComponentName` chosen in S2, `EXTRA_BIASING_STRINGS` (API 33) fed with aliases and frequent destinations if S2 shows it helps. `checkRecognitionSupport()` verifies fi-FI. |
| `CloudRecogniser` (Phase 2, optional) | app | Fallback with fi-FI and phrase hints. API key in `local.properties`, never committed. |
| `Normaliser` | core | Pure Kotlin. Strips Finnish command words (`navigoi`, `aja`, `vie minut`, `mennään`, `kohteeseen`, `osoitteeseen`), trims filler, normalises whitespace. Phase 2: case endings (§6). |
| `AliasStore` | core (+ app persistence) | Spoken aliases → address/coords (`mökki`, `koti`, `työ`, `sali`). Fuzzy match, ä/ö-aware. |
| `Confirmer` | app | `TextToSpeech`, `Locale fi-FI`, `AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` so Android Auto treats it like navigation prompts. *"Navigoidaan: Hämeenkatu 10, Tampere."* On failure: *"En kuullut. Yritä uudelleen."* → one retry, then give up. |
| `NavUri` | core | Builds `google.navigation:` URIs (UTF-8 percent-encoding, locale-independent coordinates). Waze URL in Phase 2. |
| `NavLauncher` | app | Fires the intent with `setPackage("com.google.android.apps.maps")`. |
| `SettingsActivity` | app | Phase 1: permission onboarding, recogniser picker, alias editor, confirmation on/off, test button, run-log viewer. |

### Modules
- **`core`** — plain Kotlin/JVM library, no Android dependencies, JVM unit tests. Builds anywhere with a JDK (including CI and cloud sessions without the Android SDK).
- **`app`** — the Android application; depends on `core`. Builds in Android Studio. `settings.gradle.kts` includes it only when an Android SDK is configured.

### Tech stack
- Kotlin, platform APIs only. Phase 0 uses no AndroidX at all; Phase 1 may add Compose for settings, justified in the PR.
- `minSdk 33` (`checkRecognitionSupport`, `EXTRA_BIASING_STRINGS`, async `Geocoder`).

### Permissions (MVP)
`RECORD_AUDIO`. That is all. No accessibility service, no `SYSTEM_ALERT_WINDOW`, unless Phase 0 shows the voice-launched activity cannot start Maps without it. Phase 2 adds `ACCESS_COARSE_LOCATION` if the geocoder returns.

---

## 5. Phase 0 — Feasibility spikes (do these FIRST)

A throwaway debug app (`app/`, package `io.github.vcorr.finnav`) with test buttons and a run log. **Stop after Phase 0 and fill in `docs/SPIKES.md`.** Test protocol is in that file.

Ordered by importance:

| # | Question | How to test | Pass condition |
|---|---|---|---|
| **S4** | Does **"Hey Google, open FinNav"** (and **"Hi Bixby, open FinNav"**, and a Bixby Quick Command) open our Activity and start listening — phone **locked, screen off**, Android Auto connected? Which app name does the assistant hear best? | Two launcher names ship in the spike (*FinNav*, *Finnish Navigator*); the log records which alias, referrer and keyguard state each launch had. | Activity opens and listening starts with no touch, locked, in at least one assistant. |
| **S1** | Does `google.navigation:` started from our Activity begin navigation on **Android Auto**, with the phone locked? | Variants: (a) unlocked; (b) locked, from the voice-launched activity; (c) with `requestDismissKeyguard` first; (d) delayed 10 s with the screen off (expect blocked — confirms §3.1). | Route on the car screen with no touch in (b) or (c). |
| **S2** | Which `RecognitionService` on the S25 gives the best Finnish, and does biasing help? | List services; run the §9 corpus (≥ 50 trials) per service, biasing off then on; also the on-device recogniser. Log n-best, confidence, per-stage latency. | ≥ 45/50 parked for one service. Pin it. |
| **S5** | Audio routing with Android Auto: which mic, and does TTS (nav-guidance usage) play through the car speakers? | Log input/output devices and the active recording device; speak from the driver's seat with the phone in its holder. | Acceptable recognition from the driving position; TTS audible. |
| **S3b** | Does long-press of a media remote's play/pause (or headset hook) on a locked phone launch our `ACTION_VOICE_SEARCH_HANDS_FREE` handler? | Pair a BT media remote, long-press with the screen off; pick FinNav as default if a chooser appears. | Activity opens and listens. |
| **S6** | `Geocoder` quality for Finnish places (Phase 2 input only). | "Geokoodaa" button with the Finland bounding box. | Correct top result ≥ 9/10. Informational. |
| ~~S3~~ | BT key via AccessibilityService | **Deferred to Phase 3.** §3.5 shows keys do not reach the service with the screen off; only worth testing if S4 and S3b both fail. | — |

**Decision gates:**
- S4 fails for every assistant while locked → S3b becomes primary; if that fails too, the fallback is "press twice" via AccessibilityService (Phase 3), accepting restricted-settings onboarding.
- S1 fails in all variants → investigate the Android Auto side (Car App Library POI app) before going further.
- S2 fails → `CloudRecogniser` moves into the MVP.
- Measured latency > 8 s with Maps launched during TTS → shorten the confirmation phrase or make it optional.

---

## 6. Finnish-specific handling

- **Command words:** *"aja Tampereelle"*, *"navigoi Hämeenkatu kymmenen"*. The Normaliser strips leading command words.
- **Case endings (Phase 2):** *Tampereelle, Hervantaan, Pirkkalaan, Ideaparkkiin*. First check whether Maps' own search already resolves inflected forms (S2 logs reveal what the recogniser outputs; S1 can try them). Only if not: heuristic suffix stripping (`-lle`, `-lla`, `-sta`, `-ssa`, `-an/-än`, `-een`, `-iin`, common consonant-gradation reversals) in one table-driven, unit-tested function. No full morphological analyser.
- **Numbers:** expect digits from the recogniser; if words come through (*"kymmenen"*), convert 1–200.
- **Aliases first:** frequent places never depend on search.
- Keep ä/ö/å intact end-to-end; percent-encoding is unit-tested.

---

## 7. Phases

### Phase 0 — Spikes (§5)
Deliverable: the spike app, and `docs/SPIKES.md` filled in with pass/fail per spike, chosen recogniser, chosen trigger, logs.

### Phase 1 — MVP
- Pipeline: voice trigger → listen (fi-FI, pinned service) → normalise → alias → speak recognised text while launching `google.navigation:` → finish.
- Cancel: *"Hey Google, stop navigation"* (English, handled by the assistant) — verify in Phase 1; fallback: an on-screen cancel during confirmation.
- Aliases (add/edit/delete; "add current location as alias").
- Onboarding: microphone permission; how to set up the assistant phrase / Bixby Quick Command; Samsung "never sleeping apps".
- Run log (last 50 runs: raw text, n-best, result, timings), viewable in-app, never uploaded.
- Unit tests (core): Normaliser, alias matching, URI building.

### Phase 2 — Robustness
- Case-ending fallback (§6), if needed.
- n-best disambiguation: *"Tarkoititko A vai B?"* → "eka"/"toka" (needs the geocoder).
- Geocoder (async API) and Waze as an alternative target (needs coordinates).
- Optional `CloudRecogniser`.
- Offline behaviour: detect no connectivity and say so (*"Ei verkkoyhteyttä"*); on-device Finnish is doubtful.
- "Add stop" vs "new destination" — out of scope unless Maps supports it via intent.

### Phase 3 — Optional
- Bluetooth steering-wheel button via AccessibilityService ("press twice": first press wakes, second triggers), only if the voice trigger proved unreliable.
- App-specific wake word (on-device engine; check licence and pricing at the time). The mic foreground service must start from a visible activity or a notification tap (§3.2).
- `VoiceInteractionService` (default assistant role) — replaces Gemini as the phone's assistant; Android Auto's own voice button will likely still go to Gemini (inferred).

---

## 8. Project layout

```
settings.gradle.kts          includes :core always, :app when an Android SDK is configured
core/                        pure Kotlin/JVM, no Android
  src/main/kotlin/io/github/vcorr/finnav/core/
    NavUri.kt  TranscriptMatch.kt  Corpus.kt  Stats.kt  StageTimer.kt  RunLog.kt  SpeechErrors.kt  Finland.kt
    (Phase 1+) Normaliser.kt  AliasStore.kt  CaseEndings.kt  NumberWords.kt
  src/test/kotlin/...
app/                         Android application
  src/main/java/io/github/vcorr/finnav/
    spike/ (Phase 0, deleted after)  capture/  speech/  confirm/  nav/  settings/
docs/SPIKES.md
PLAN.md
```

---

## 9. Test corpus (start with these; extend with real destinations)

Bare places: Tampere, Pirkkala, Nokia (the town — a good trap), Ylöjärvi, Lempäälä, Kangasala, Orivesi, Hervanta, Särkänniemi, Ideapark.
Streets: Hämeenkatu 10, Tampere; Hervannan valtaväylä; Pirkkalan lentoasema; Sammonkatu; Itsenäisyydenkatu.
Inflected: "aja Tampereelle", "navigoi Hervantaan", "vie minut Ideaparkkiin".
Aliases: koti, mökki, työ, sali.

22 entries; one S2 run is ≥ 50 trials (the corpus cycled, ~2.3 passes). Score a trial correct when the recogniser's top result equals the utterance after case, punctuation and whitespace normalisation; also record whether any n-best entry matched.

For each run, log: utterance (ground truth), recogniser output + n-best + confidence, launch context, latency per stage, match y/n.

---

## 10. Privacy & safety
- No audio is stored. Recognised text and the run log stay on the device; the log can be cleared and is shared only when the user taps *Jaa loki*.
- No analytics, no backend.
- API keys (Phase 2+) in `local.properties` / build config, never in git.
- The happy path never requires looking at the phone; any failure is spoken.

---

## 11. Open questions (resolve during Phase 0)
1. Does any assistant open FinNav on a **locked** phone with Android Auto active? (S4 — the figure the whole design rests on.)
2. Which S1 variant starts the route on the car screen from a locked phone?
3. Which recognition service on the S25 is best for Finnish, is it online-only, and does `EXTRA_BIASING_STRINGS` change anything?
4. App name: which one does the assistant recognise reliably *in English*?
5. Does *"Hey Google, stop navigation"* work as the cancel?
6. What happens with no mobile signal?

---

## 12. Instructions for Claude Code
- Work **phase by phase**. After Phase 0, stop and present `docs/SPIKES.md`. Do not start Phase 1 until the gates in §5 are resolved.
- Keep pure logic in `core`, free of Android dependencies and covered by JVM unit tests.
- Prefer platform APIs over libraries. Justify any dependency in the PR description.
- Every user-facing string in Finnish; code and comments in English.
- Small, reviewable commits per component.

---

## 13. Changes from revision 1

| Change | Reason |
|---|---|
| Voice assistant is the primary trigger; BT button moved to Phase 3 | Keys do not reach apps with the screen off (§3.5); the assistant hotword works screen-off and needs no accessibility service |
| Added S3b (hands-free voice-search intent) | Long-press of a media key launches it on a locked phone (§3.6) |
| Geocoder removed from the MVP | Maps resolves text itself; confirmation should echo recognised text |
| Latency budget and concurrent TTS + Maps launch | Sequential estimate 10.8 s > 8 s target |
| Accuracy gate 45/50 instead of 9/10 | 9/10 has a 60–98 % confidence interval |
| `EXTRA_BIASING_STRINGS`, nav-guidance audio usage | Cheap accuracy and routing improvements to test |
| `core` / `app` split | Pure logic builds and tests without the Android SDK |
| Sideloading notes | Restricted settings; developer verification from 2027 |
