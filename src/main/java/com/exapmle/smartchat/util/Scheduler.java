package com.example.smartchat.util;

import com.example.smartchat.SmartChatSpawnerMod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;

/** Tiny tick-based scheduler used for animations, countdowns and delayed effects. */
public final class Scheduler {

    public static final class Handle {
        private boolean cancelled;

        public void cancel() {
            cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static final class Task {
        final long runAt;
        final Runnable action;
        final Handle handle;

        Task(long runAt, Runnable action, Handle handle) {
            this.runAt = runAt;
            this.action = action;
            this.handle = handle;
        }
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static long now = 0;

    private Scheduler() {}

    /** Runs the action once after the given number of ticks (20 ticks = 1 second). */
    public static Handle later(int ticks, Runnable action) {
        Handle handle = new Handle();
        TASKS.add(new Task(now + Math.max(0, ticks), action, handle));
        return handle;
    }

    /** Runs step(0), step(1) ... step(count-1), starting after delay and then every period ticks. */
    public static Handle repeat(int delay, int period, int count, IntConsumer step) {
        Handle handle = new Handle();
        scheduleStep(handle, delay, Math.max(1, period), count, 0, step);
        return handle;
    }

    private static void scheduleStep(Handle handle, int delay, int period, int count, int index, IntConsumer step) {
        TASKS.add(new Task(now + Math.max(0, delay), () -> {
            step.accept(index);
            if (index + 1 < count) scheduleStep(handle, period, period, count, index + 1, step);
        }, handle));
    }

    public static void tick() {
        now++;
        if (TASKS.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task task = it.next();
            if (task.handle.cancelled) {
                it.remove();
            } else if (task.runAt <= now) {
                due.add(task);
                it.remove();
            }
        }
        for (Task task : due) {
            if (task.handle.cancelled) continue;
            try {
                task.action.run();
            } catch (Throwable t) {
                SmartChatSpawnerMod.LOGGER.error("Scheduled task failed", t);
            }
        }
    }
}
