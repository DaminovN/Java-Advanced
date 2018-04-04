package ru.ifmo.rain.daminov.concurrent;

import info.kgeorgiy.java.advanced.mapper.*;

import java.util.*;
import java.util.function.Function;

public class ParallelMapperImpl implements  ParallelMapper {
    private final Queue<Runnable> tasks;
    private final List<Thread> threads;
    private final static int MAX = 100000;
    public ParallelMapperImpl(final int threadNumb) {
        threads = new ArrayList<>();
        tasks = new ArrayDeque<>();
        for (int i = 0; i < threadNumb; i++) {
            Thread thread = new Thread(() -> {
                try {
                    while (!Thread.interrupted()) {
                        Runnable task;
                        synchronized (tasks) {
                            while (tasks.isEmpty()) {
                                tasks.wait();
                            }
                            task = tasks.poll();
                            tasks.notifyAll();
                        }
                        task.run();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(thread);
            thread.start();
        }

    }
    private class ResultCollector<R> {
        private final List<R> res;
        private int cnt;

        ResultCollector(final int size) {
            res = new ArrayList<>(Collections.nCopies(size, null));
            cnt = 0;
        }

        void setData(final int pos, R data) {
            res.set(pos, data);
            synchronized (this) {
                if (++cnt == res.size()) {
                    notify();
                }
            }
        }

        synchronized List<R> getRes() throws InterruptedException {
            while (cnt < res.size()) {
                wait();
            }
            return res;
        }
    }
    private void add(final Runnable task) throws InterruptedException {
        synchronized (tasks) {
            while (tasks.size() == MAX) {
                tasks.wait();
            }
            tasks.add(task);
            tasks.notifyAll();
        }
    }
    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> function, List<? extends T> list) throws InterruptedException {
        ResultCollector<R> resultCollector = new ResultCollector<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            add(() -> resultCollector.setData(index, function.apply(list.get(index))));
        }
        return resultCollector.getRes();
    }

    @Override
    public void close() {
        threads.forEach(Thread::interrupt);
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException ignored) {
            }
        }
    }
}
