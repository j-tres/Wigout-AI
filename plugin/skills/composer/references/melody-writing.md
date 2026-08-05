<!-- provenance: original distillation by Wigout Studio authors, 2026-07. -->
# Melody Writing

A melody is a shape you can remember. Give it a clear contour, a motif that
recurs, and note choices that land where the harmony wants them.

## Contour — the line's silhouette
Sketch the up/down shape before the pitches. Four archetypes:
- **Arch**: rise to a peak, fall back. The default singable phrase.
- **Ascending**: builds tension — good into a chorus or drop.
- **Descending**: releases — good for resolutions and outros.
- **Wave**: gentle up-down oscillation — good for verses that shouldn't
  steal the chorus's thunder.

One phrase gets **one climax** — the highest note, hit once. If every bar
peaks, nothing peaks. Save the highest note of the whole song for the final
chorus.

## Motif and variation
Write a short **motif** — 2 to 5 notes, one or two bars — memorable on its
own. Then generate the rest of the melody by transforming it. This is why
strong melodies feel inevitable: the ear keeps recognizing the seed.
- **Repeat**: state it again, unchanged. Repetition is how a hook sticks.
- **Sequence / transpose**: same shape, shifted up or down a step or a
  third (add that interval to every pitch).
- **Invert**: flip the direction — where it rose a third, fall a third.
- **Augment / diminish**: double or halve every note's duration.
- **Fragment**: take just the tail of the motif and develop that.

Rule of thumb: **repeat, repeat, then vary.** Two statements then a twist
keeps a listener oriented but not bored.

## Question and answer (antecedent–consequent)
Phrase melodies in pairs. The first phrase (the "question") ends
unresolved — hanging on a non-tonic scale degree (2, 5, or 7). The second
(the "answer") echoes its rhythm but resolves down to the tonic. Four-bar
question + four-bar answer is the pop default. The answer should reuse the
question's rhythm so they clearly belong together.

## Note targeting — scale-tone vs chord-tone
Every note is either a **chord tone** (root, 3rd, 5th, 7th of the current
chord) or a **non-chord tone** (a passing/neighbor note from the scale).
- Put **chord tones on strong beats** (steps 1, 5, 9, 13) and on long
  notes. This is what makes a melody sound "in the pocket" with the chords.
- Use **non-chord tones on weak beats and passing motion** — they create
  motion and tension, then resolve by step to a chord tone.
- The **3rd and 7th** of a chord carry its color; land on them to sound
  intentional. The root is safe but plain; the 9th and 6th sound modern.
- When the chord changes, move the melody to a tone of the *new* chord.

Compute chord tones with the `music-theory` skill (`chord` / `progression`)
rather than recalling them — then target those pitches.

## Register and range
Keep a phrase within about an octave — singable, and it reads as one idea.
Use **register as arrangement**: verse melody low and conversational,
chorus melody up a fourth or an octave for lift. A leap of a 6th or octave
is an event — spend them sparingly, on the payoff note. Fill leaps back in
by step (leap up, walk down).

## Humanization — concrete note editing
Quantized-perfect melodies sound robotic. When writing via `cursorClip`
step calls (`step(channel, x, y)` sets the note at grid position x, pitch
y):
- **Velocity**: vary 70-110 instead of a flat 100. Accent the downbeat and
  the phrase peak (~110); soften pickup and passing notes (~70-80). A gentle
  arch of velocity across a phrase reads as breath.
- **Timing**: nudge expressive notes a few ticks early or late. Melody
  slightly ahead of the beat feels urgent; behind feels relaxed (lo-fi).
  Keep the downbeat honest so the groove holds.
- **Length**: overlap for legato (let one note run into the next), or clip
  short for staccato phrases. Consistent note lengths within a phrase sound
  deliberate; random lengths sound sloppy.
- **Leave space**: rests are melody too. A phrase that breathes beats one
  that never stops. Don't fill every step.

## Workflow
1. Set the contour and write a 1–2 bar motif on strong-beat chord tones.
2. Build the phrase by transformation (sequence, invert, fragment).
3. Cast it as question/answer over the progression.
4. Check every strong-beat note is a chord tone of that beat's chord.
5. Humanize velocity and timing; verify by reading the clip back.
