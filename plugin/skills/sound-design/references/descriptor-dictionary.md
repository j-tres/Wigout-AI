> Provenance: Original distillation (Wigout Studio) — sound-design descriptor translations (sonic adjective → parameter-move intents).

# Descriptor Dictionary — sonic adjective → parameter moves

This is the translation layer of the sound-design role. A request arrives as
a *word* ("make it warmer", "too harsh", "wider") and has to leave as a small
list of *parameter moves*. Each entry below turns one adjective into an
ordered set of moves stated in **parameter families**, not device-specific
knobs — cutoff, resonance, amp/filter envelope stages, unison/detune,
saturation/drive, EQ shelves and bands, reverb/chorus. Why a move works is in
`synthesis-fundamentals.md`; which knob a family maps to on a given device is
in `references/devices/`. This file sits deliberately in the middle.

**How to use an entry.** Read *Perceived as* to confirm you and the user mean
the same thing. Apply *Move* top-down and stop as soon as it is enough — the
first move is usually the biggest lever. Respect *Avoid*: every quality has an
over-correction that lands you in a different (usually named) descriptor, so
each entry cross-links the trap. Finally, *Verify (analysis)* names the
`sound_analysis.py` features that should shift if the move worked — bounce a
short note and confirm the numbers moved the way the ear expects. The analysis
is a coarse mono proxy (centroid_hz, rolloff_hz, flatness, rms, crest, and the
low/mid/high band split), advisory only — trust ears first, use it to catch
moves that went the wrong way.

---

## warm
**Perceived as:** rounded, full low-mids, gentle highs; analog-leaning.
**Move (in priority order):**
1. Lower filter cutoff slightly (roll off brittle highs) — Low-pass down ~10-20%.
2. Add gentle saturation/drive for even harmonics (low drive amount).
3. Small high-shelf cut above ~6 kHz if still fizzy.
4. Slightly longer amp attack softens the transient edge.
**Avoid:** over-lowpassing into "muddy" (see muddy) or killing presence.
**Verify (analysis):** centroid_hz drops; low+mid band share rises (warm bucket climbs).

## bright
**Perceived as:** present, open, plenty of upper harmonics; forward top end.
**Move (in priority order):**
1. Raise filter cutoff — open the low-pass so highs come through.
2. High-shelf boost above ~4-8 kHz for air and presence.
3. Add upper harmonics at the source (brighter waveform / saturation) so there is content to lift.
4. High-pass out low mud that masks the highs; shorten amp attack to expose the transient.
**Avoid:** over-brightening into "harsh" (see harsh) or losing body into "thin" (see thin).
**Verify (analysis):** centroid_hz and rolloff_hz rise; high band share increases (bright bucket climbs).

## dark
**Perceived as:** subdued highs, moody, rounded, distant top; the inverse of bright.
**Move (in priority order):**
1. Lower filter cutoff (low-pass down) — the primary brightness control.
2. High-shelf cut above ~4 kHz.
3. Choose a mellower source waveform (saw → triangle/sine) so there are fewer highs to begin with.
4. Reduce resonance and any exciter/high harmonics; low-pass reverb returns.
**Avoid:** over-darkening into "muddy" (see muddy) or dull and lifeless.
**Verify (analysis):** centroid_hz and rolloff_hz drop; high band share falls; warm bucket rises.

## punchy
**Perceived as:** strong transient hit, tight, impactful, dynamic; the attack lands.
**Move (in priority order):**
1. Shorten amp attack to near-zero for a sharp transient.
2. Shorten amp decay and lower sustain so the body gets out of the transient's way.
3. Add a fast pitch/filter-envelope blip on the attack for extra snap.
4. Tighten release and ensure the sub isn't smearing the hit; transient-shaper attack up.
**Avoid:** over-tightening into "thin" (see thin) — all click, no body — or a clicky artifact.
**Verify (analysis):** crest rises (peak-to-rms up); sustained rms lower relative to the peak.

## wide
**Perceived as:** broad stereo image, spacious, enveloping; fills past the speakers.
**Move (in priority order):**
1. Add unison voices with detune spread — beating reads as width.
2. Chorus/ensemble for stereo thickening.
3. Stereo doubling: slight L/R detune or a short Haas delay; stereo reverb/delay.
4. Pan-spread layered oscillators or octave doublings.
**Avoid:** phasey mono-collapse into "hollow" (see hollow) — a wide sound that vanishes in mono.
**Verify (analysis):** width is stereo — the mono analysis can't score it; instead confirm mono-compatibility: mid band and rms hold and flatness doesn't spike (no comb-filter cancellation).

## thin
**Perceived as:** light, delicate, lacking low-end weight; sits high and out of the way.
**Move (in priority order):**
1. High-pass up to remove sub and low body — the fastest thinner.
2. Reduce or disable the sub oscillator / low octave.
3. Low-shelf cut and narrower unison so less energy piles up low.
4. Lower sustain / shorten body if it still feels heavy.
**Avoid:** over-thinning into "hollow" (see hollow) — no body left — or weak and inaudible in the mix.
**Verify (analysis):** low band share falls; centroid_hz and rolloff_hz rise as energy shifts up; warm/full buckets drop.

## muddy
**Perceived as:** usually a fault — congested, boxy low-mids (~200-500 Hz), unclear, boomy-but-blurred.
**Move (to clear it, in priority order):**
1. High-pass up ~80-120 Hz to remove sub rumble that isn't pitch.
2. Cut low-mids ~200-400 Hz with a moderate EQ band.
3. Reduce resonance/drive and unison that thicken the low-mid pileup.
4. Shorten release and reverb tail that smear notes together.
**Avoid:** over-clearing into "thin" (see thin) or scooping so hard it turns "harsh" (see harsh).
**Verify (analysis):** low+low-mid band share drops; centroid_hz rises slightly; warm bucket eases down from "high".

## aggressive
**Perceived as:** forward, biting, distorted, in-your-face; dense harmonics that push.
**Move (in priority order):**
1. Add drive/distortion for harmonic density and edge.
2. Raise cutoff and resonance for a present, cutting peak.
3. Oscillator hard-sync or FM feedback for a torn, formant-rich source.
4. Faster/harder attack; boost the presence band ~2-5 kHz; steeper filter slope.
**Avoid:** over-driving into "harsh" (see harsh) — painful and uncontrolled rather than powerful.
**Verify (analysis):** centroid_hz and flatness rise; presence/high band share up; crest may drop as saturation compresses transients.

## lush
**Perceived as:** rich, wide, dense, evolving, ensemble-like and reverberant; blooms over time.
**Move (in priority order):**
1. Unison/detune up for an ensemble of slightly-out-of-tune voices.
2. Chorus/ensemble for stereo depth.
3. Slow LFO on pitch / pulse-width / wavetable position for gentle movement.
4. Longer release into reverb for bloom; layer octaves for weight.
**Avoid:** piling on reverb + detune until it washes into "muddy" (see muddy) or loses definition.
**Verify (analysis):** partly time/stereo qualities the mono snapshot only samples — confirm mid band is retained, flatness doesn't spike, and the sustained tail keeps rms up longer.

## hollow
**Perceived as:** usually a fault — scooped mids, missing fundamental, phasey or boxy-empty; full at the edges but empty in the middle.
**Move (to fill it in, in priority order):**
1. Widen pulse width toward 50% (or pick a fuller waveform) so the fundamental returns.
2. Restore the mid-band with a gentle bell boost ~300 Hz-1 kHz.
3. Reduce excessive detune/phase that comb-filters and cancels the center.
4. Add a fundamental-strong oscillator or sub layer for body.
**Avoid:** over-filling into "muddy" (see muddy) or a boxy low-mid buildup.
**Verify (analysis):** mid band share rises; flatness recovers toward baseline as comb notches close; full bucket climbs.

## glassy
**Perceived as:** bright, brittle, bell-like, high shimmer; a slightly inharmonic, digital sheen.
**Move (in priority order):**
1. Use FM/phase-mod with a non-integer ratio for inharmonic upper partials a filter can't create (see synthesis-fundamentals, FM family).
2. High-shelf boost for top-end sheen.
3. Add high-frequency sparkle (ring-mod / bright FM operator).
4. Trim low-mid warmth so the highs dominate; bright/shimmer reverb on top.
**Avoid:** pushing the top until it turns "harsh" (see harsh) — brittle and piercing.
**Verify (analysis):** centroid_hz and rolloff_hz rise; high band share up; flatness ticks up from inharmonic content.

## gritty
**Perceived as:** rough, saturated, edgy, harmonically dense; lo-fi bite and dirt.
**Move (in priority order):**
1. Add distortion/drive (clip or wavefold) for dense added harmonics.
2. Bitcrush / downsample for digital grime.
3. Drive the filter and add resonance for a resonant, dirty edge.
4. A touch of noise or ring-mod for texture.
**Avoid:** over-distorting into "harsh" (see harsh) — fizzy and fatiguing instead of characterful.
**Verify (analysis):** flatness rises (denser, broadband harmonics); centroid_hz rises; crest drops as saturation flattens transients.

## airy
**Perceived as:** open, breathy, spacious; gentle high-frequency "air" above ~10 kHz.
**Move (in priority order):**
1. High-shelf boost ~10-16 kHz for top-end air.
2. Add a filtered noise layer in the attack/sustain for breath (see synthesis-fundamentals, noise).
3. Light, bright reverb for space around the sound.
4. Slight high-pass to lift it clear of low mud; a gentle exciter on the very top.
**Avoid:** over-lifting the top into "harsh" (see harsh) — hissy and sibilant rather than airy.
**Verify (analysis):** high band share and rolloff_hz rise; centroid_hz moves up modestly; flatness ticks up from the added noise.

## boomy
**Perceived as:** big, sub-heavy low end with a resonant low bump ~60-120 Hz; can be a target (808s) or a fault (one-note boom).
**Move (in priority order):**
1. Boost the sub oscillator / low-shelf for low-end weight.
2. Lower cutoff so the energy concentrates down low.
3. Add a low-frequency resonant emphasis ~80-120 Hz for the "boom".
4. Lengthen the low-end decay so it blooms and sustains.
**Avoid:** letting the low-mids pile up into "muddy" (see muddy), or a resonant one-note boom that ignores pitch.
**Verify (analysis):** low band share rises strongly; centroid_hz drops; full bucket climbs.

## harsh
**Perceived as:** usually a fault — piercing, fatiguing, spiky upper-mids/highs ~2-6 kHz; fizzy and edgy.
**Move (to tame it, in priority order):**
1. Cut the offending band ~2-6 kHz with a narrow EQ dip — find it by sweeping a boost first.
2. Lower filter cutoff or add a gentle high-shelf cut.
3. Reduce drive/distortion amount at the source of the fizz.
4. Reduce resonance sitting near the peak; dynamic EQ / de-ess the loudest peaks.
**Avoid:** over-taming into "dark" (see dark) — dull and lifeless — or scooping into "muddy" (see muddy).
**Verify (analysis):** centroid_hz and high band share drop; rolloff_hz lowers; bright bucket eases down from "high".
