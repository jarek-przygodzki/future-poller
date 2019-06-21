package dev.jarekprzygodzki.future_poller;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;


class PollerTest {

    @Test
    public void completableFuturesAreCompletedWhenSourceFutureDone() throws Exception {
        int nthreads = 10;
        ExecutorService executor = Executors.newCachedThreadPool();
        Poller poller = new Poller();
        List<CompletableFuture<Integer>> cfs = new ArrayList<>();
        for (int i = 0; i < nthreads; ++i) {
            Random rand = new Random();
            int result = i;
            CompletableFuture<Integer> cf = poller.register(executor.submit(() -> {
                Thread.sleep(rand.nextInt(1000) + 100);
                return result;
            }));
            cfs.add(cf);
        }

        CompletableFuture.allOf(cfs.toArray(new CompletableFuture[cfs.size()])).get();
        assertThat(cfs).extracting(CompletableFuture::get).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    @Test
    public void returnsUncompletedFuturesWhenStopped() throws Exception {
        Poller poller = new Poller();
        ExecutorService executor = Executors.newCachedThreadPool();

        CountDownLatch l1 = new CountDownLatch(1);
        CountDownLatch l2 = new CountDownLatch(1);

        CompletableFuture<Integer> cf1 = poller.register(executor.submit(() -> {
            l1.await();
            return 1;
        }));

        CompletableFuture<Integer> cf2 = poller.register(executor.submit(() -> {
            l2.await();
            return 2;
        }));

        l1.countDown();
        assertThat(cf1.get()).isEqualTo(1);
        List<Poll> pendingPolls = poller.stop();
        assertThat(pendingPolls).hasSize(1);
        Poll poll = pendingPolls.get(0);
        assertThat(poll.getSource().isDone()).isFalse();
        l2.countDown();
        poll.completeSync();
        assertThat(cf2.isDone()).isTrue();
        assertThat(cf2).isSameAs(poll.getTarget());
        assertThat(cf2.get()).isEqualTo(2);
    }


    @Test
    void sourceFutureExceptionIsUnwrapped() throws Exception {
        Poller poller = new Poller();
        ExecutorService executor = Executors.newCachedThreadPool();
        CompletableFuture<Integer> cf = poller.register(executor.submit(() -> {
            throw new RuntimeException("Test exception message");
        }));
        try {
            cf.get();
        } catch (ExecutionException e) {
            assertThat(cf).isCompletedExceptionally();
            assertThat(e.getCause()).isInstanceOf(RuntimeException.class);
            assertThat(e.getCause().getMessage()).isEqualTo("Test exception message");
        }

    }

}