package dev.jarekprzygodzki.future_poller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Represents task of polling a Future
 */
public class Poll<T> {
    private final Future<T> source;
    private final CompletableFuture<T> target;

    Poll(Future<T> source, CompletableFuture<T> target) {
        this.source = source;
        this.target = target;
    }

    public boolean isReady() {
        return source.isDone();
    }

    public void completeSync() {
        try {
            /*
                Threads from our private executor are never interrupted.
                Threads from a user-supplied executor might be, but... what can we actually do other that preserve interrupt status ?
             */
            target.complete(getUninterruptibly(source));
        } catch (ExecutionException e) {
            target.completeExceptionally(e.getCause());
        }
    }

    private static <V> V getUninterruptibly(Future<V> future) throws ExecutionException {
        boolean interrupted = false;
        try {
            for (; ; ) {
                try {
                    return future.get();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public Future<T> getSource() {
        return source;
    }

    public CompletableFuture<T> getTarget() {
        return target;
    }
}
