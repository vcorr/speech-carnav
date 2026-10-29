# Phase 0 — Spike results

Status: **spike app built, car tests not yet run.** Fill in the tables below from the in-app log
(*Jaa loki* shares `runs.tsv` and `notes.txt`; they are also in
`Android/data/io.github.vcorr.finnav/files/` on the phone).

Phone: Samsung Galaxy S25, Android ___, One UI ___ · Car: Hyundai Ioniq 5, Android Auto wired / wireless · Date: ___

---

## Before you start (5 minutes, parked, phone unlocked)

1. Install the debug build from Android Studio (see README).
2. Open **FinNav** once from the app drawer and grant the microphone permission.
   (The permission dialog may not appear over the lock screen, so do this unlocked.)
3. Tap **Diagnostiikka**. Note the recognition services, the default recogniser and Google Maps version below.
4. For each recogniser (**Tunnistin:** button cycles through them), tap **Tarkista suomen tuki**.
5. Tap **Testaa puhe**: you should hear *"Navigoidaan: Hämeenkatu 10, Tampere."*
6. For the S4 Bixby test, create a Bixby Quick Command (e.g. *"Finnish navigation"* → Open app → FinNav).

| Item | Value |
|---|---|
| Recognition services found | |
| Default recogniser | |
| fi-FI support per service (online / on-device) | |
| TTS engine and fi-FI status | |
| Google Maps version | |

---

## S4 — Voice trigger (the gate everything rests on)

Android Auto connected. Phone **locked, screen off**, in its holder. *Kuuntele heti avattaessa* on.
For each row, say the phrase, then say "Tampere" when you hear the recogniser start.
The app logs `trigger`, `referrer` and `locked` for every launch.

| # | Phrase | Phone state | Opened? | Listening started? | Route on car screen? | since-launch (ms) | Notes |
|---|---|---|---|---|---|---|---|
| 1 | "Hey Google, open FinNav" | unlocked | | | | | |
| 2 | "Hey Google, open FinNav" | locked, screen off | | | | | |
| 3 | "Hey Google, open Finnish Navigator" | locked, screen off | | | | | |
| 4 | "Hi Bixby, open FinNav" | locked, screen off | | | | | |
| 5 | Bixby Quick Command | locked, screen off | | | | | |
| 6 | Steering-wheel voice button → "open FinNav" | locked | | | | | |

**Verdict S4:** ___ (pass = row 2, 3, 4 or 5 opens and listens with no touch)

---

## S1 — Maps intent → Android Auto

*Käynnistä Google Maps* on. Use the typed destination field (default *Hämeenkatu 10, Tampere*) or the voice flow.

| Variant | How | Route on car screen? | Touch needed? | Notes |
|---|---|---|---|---|
| a | Unlocked, **Navigoi nyt** | | | |
| b | Locked, voice flow from S4 | | | |
| c | Locked, voice flow, *Avaa lukitus ennen Mapsia* on | | | |
| d | **Navigoi 10 s päästä**, then lock the phone (expected: blocked) | | | |

Also try one inflected destination (*"aja Tampereelle"*) to see whether Maps resolves it unaided: ___

**Verdict S1:** ___

---

## S2 — Finnish recognition accuracy

*Testisarja* on, *Käynnistä Google Maps* off. The screen shows *Sano (n/22): …*; the app speaks
*Oikein / Väärin* and the next phrase. A passenger presses **Kuuntele** while driving.
Run **≥ 50 trials** (the corpus cycles) per row. The score line shows k/n, the 95 % interval and the gate verdict.
Biased runs are tallied separately (condition `…+bias`).

| Recogniser | Condition | Top-1 correct | Any of n-best | 95 % interval | Gate | Median result latency (speech-end → results) |
|---|---|---|---|---|---|---|
| | parked | / | / | | | |
| | parked+bias | / | / | | | |
| | driving | / | / | | | |
| on-device | parked | / | / | | | |

Recurring errors (what the recogniser heard instead): ___

**Verdict S2:** ___ (pass = ≥ 45/50 parked for one recogniser) · **Pinned recogniser:** ___

---

## S5 — Audio routing with Android Auto

| Question | Observation |
|---|---|
| Recording device during recognition (`mic=` in notes) | |
| Recognition from driver's seat, phone in holder — acceptable? | |
| TTS confirmation audible through car speakers? | |
| Music paused / ducked while listening? Resumed after? | |
| Maps' own "starting route" prompt clashes with our confirmation? | |

**Verdict S5:** ___

---

## Latency (from S1/S4 runs, `timings` column)

| Stage | Measured | Budget (PLAN §4.1) |
|---|---|---|
| since-launch (activity opened → listening requested) | | 0.8 s incl. warm-up |
| created → ready | | |
| speech-end → results | | 1.5 s |
| results → maps-intent | | |
| results → tts-done | | 2.5 s |
| Trigger phrase end → route visible (stopwatch) | | ≤ 8 s |

Compare *Käynnistä Maps vahvistuksen aikana* on vs off: ___

---

## S3b — Hands-free voice-search intent (optional, needs a BT media remote)

| Test | Result |
|---|---|
| Long-press play/pause, screen off, locked: does FinNav (or a chooser) open? | |
| After choosing FinNav as default: opens and listens? | |
| Car steering-wheel play/pause long-press: anything? | |

---

## S6 — Geocoder (informational)

Type each place, tap **Geokoodaa**.

| Query | Top result | Correct? |
|---|---|---|
| Tampere | | |
| Nokia | | |
| Särkänniemi | | |
| Ideapark | | |
| Hämeenkatu 10, Tampere | | |

---

## Decisions

| Gate (PLAN §5) | Outcome | Decision |
|---|---|---|
| S4 voice trigger works locked | | |
| S1 route starts on car screen | | |
| S2 ≥ 45/50 | | |
| Latency ≤ 8 s | | |

Chosen trigger: ___ · Chosen recogniser: ___ · App name: ___
