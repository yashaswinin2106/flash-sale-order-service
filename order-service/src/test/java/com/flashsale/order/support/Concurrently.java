package com.flashsale.order.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

/** Runs tasks on separate threads, released at the same moment by a shared latch. */
public final class Concurrently {

    private Concurrently() {
    }

    /** Each result is either the task's return value or the exception it threw. */
    public static <T> List<Object> run(int threads, IntFunction<Callable<T>> taskForIndex) throws Exception {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                Callable<T> task = taskForIndex.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            ready.await();
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> f : futures) {
                results.add(f.get());
            }
            return results;
        }
    }
}
