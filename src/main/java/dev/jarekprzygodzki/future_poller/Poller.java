package dev.jarekprzygodzki.future_poller;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;

public class Poller {

    private final ArrayList<Poll> polls = new ArrayList<>();

    private final Object lock = new Object();
    private final long pollPeriod;
    private final TimeUnit pollUnit;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollSchedule;

    /**
     * Poll registered futures completion status at fixed interval and complete delegates when source futures are ready
     *
     * @param pollPeriod how often to poll pending futures
     * @param pollUnit
     * @param scheduler  scheduler to run polling task
     */
    public Poller(long pollPeriod, TimeUnit pollUnit, ScheduledExecutorService scheduler) {
        this.pollPeriod = pollPeriod;
        this.pollUnit = pollUnit;
        this.scheduler = scheduler;
    }

    public Poller(long period, TimeUnit unit) {
        this(period, unit, Executors.newScheduledThreadPool(1));
    }

    public Poller() {
        this(100, TimeUnit.MILLISECONDS);
    }

    /*
     * Adapt a {@code source} to a {@link CompletableFuture}
     */
    public <T> CompletableFuture<T> register(Future<T> source) {
        CompletableFuture<T> target = new CompletableFuture<>();
        synchronized (lock) {
            polls.add(new Poll(source, target));
            if (pollSchedule == null) {
                pollSchedule = scheduler.scheduleAtFixedRate(() -> poll(), pollPeriod, pollPeriod, pollUnit);
            }
        }
        return target;
    }

    // Check all registered futures for readiness
    private void poll() {
        synchronized (lock) {
            try {
                Iterator<Poll> pendingPolls = this.polls.iterator();
                while (pendingPolls.hasNext()) {
                    Poll poll = pendingPolls.next();
                    if (poll.isReady()) {
                        poll.completeSync();
                        pendingPolls.remove();
                    }
                }
            } finally {
                if (this.polls.isEmpty()) {
                    stopPolling();
                }
            }
        }
    }

    /**
     * Stop polling and return list of incomplete futures. It's now caller's responsibility to complete them.
     */
    public List<Poll> stop() {
        ArrayList<Poll> pendingPolls = new ArrayList<>();
        synchronized (lock) {
            stopPolling();
            pendingPolls.addAll(polls);
            polls.clear();
        }
        return pendingPolls;
    }

    private void stopPolling() {
        pollSchedule.cancel(false);
        pollSchedule = null;
    }

}
