package org.wigout.mcp.mcp.bridge;

import com.bitwig.extension.controller.api.NoteStep;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sparse cache of the launcher cursor clip's note steps, fed exclusively by
 * a NoteStepChangedCallback registered AT INIT (init-only rule — the
 * observer cannot be added later). Callbacks arrive on Bitwig's controller
 * thread; reads come from Jetty request threads — hence ConcurrentHashMap
 * and atomic per-key updates. Only State.Empty means "no note": NoteOn and
 * NoteSustain cells are both cached (sustain cells are the read-back of a
 * long note's tail).
 */
public final class NoteStepCache {

    record Key(int channel, int x, int y) {}

    private static final Comparator<NoteStep> ORDER =
        Comparator.comparingInt(NoteStep::channel)
            .thenComparingInt(NoteStep::x)
            .thenComparingInt(NoteStep::y);

    private final ConcurrentHashMap<Key, NoteStep> steps = new ConcurrentHashMap<>();

    /** Observer entry point — wired as cursorClip.addNoteStepObserver(cache::onNoteStep) at init. */
    public void onNoteStep(NoteStep step) {
        Key key = new Key(step.channel(), step.x(), step.y());
        if (step.state() == NoteStep.State.Empty) {
            steps.remove(key);
        } else {
            steps.put(key, step);
        }
    }

    /** All cached steps, sorted (channel, x, y) for deterministic dumps. */
    public List<NoteStep> all() {
        return steps.values().stream().sorted(ORDER).toList();
    }

    public Optional<NoteStep> find(int channel, int x, int y) {
        return Optional.ofNullable(steps.get(new Key(channel, x, y)));
    }

    public int size() {
        return steps.size();
    }
}
