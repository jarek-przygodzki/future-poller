# future-poller
Convert Future to CompletableFuture by polling

## Why

The usual approach to converting Future to CompletableFuture is to wait for a result in a dedicated thread.

```
   <T> CompletableFuture<T> blockInNewThread(Future<T> source) {
        CompletableFuture<T> target = new CompletableFuture<>();
        // or `ThreadFactory.newThread` or `Executor.execute`
        new Thread(() -> {
            try {
                // Thread is never interrupted.
                target.complete(getUninterruptibly(source));
            } catch (ExecutionException e) {
                target.completeExceptionally(e.getCause());
            }
        }).start();
        return target;
    }

    <V> V getUninterruptibly(Future<V> future) throws ExecutionException {
        for (; ; ) {
            try {
                return future.get();
            } catch (InterruptedException ignore) {
            }
        }
    }

```

The obvious problem with this approach is, that for each Future, a thread will be blocked to wait for the result. It works well for a few futures, but what if we have thousands of them?

It is possible to do better than blocking thread for each future if we are willing to add some latency. For many use cases, like background tasks, it's an acceptable trade-off. 

The  idea is to maintain collections of futures and periodically check whenever any of the them is completed.


## How to use?

```
Future oldFuture = ...;
// poll every 100 ms - default
Poller poller = new Poller(100, TimeUnit.MILLISECONDS);

CompletableFuture cf = poller.register(oldFutue);
```