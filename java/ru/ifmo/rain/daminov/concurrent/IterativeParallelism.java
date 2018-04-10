package ru.ifmo.rain.daminov.concurrent;

import info.kgeorgiy.java.advanced.concurrent.*;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IterativeParallelism implements ScalarIP, ListIP {

    private final ParallelMapper myMapper;
    public IterativeParallelism(ParallelMapper mapper) {
        this.myMapper = mapper;
    }
    public IterativeParallelism() {
        myMapper = null;
    }
    private <T, R> R baseTask(int threadNumber, final List<? extends T> list,
                              final Function<? super Stream<? extends T>, ? extends R> mapper,
                              final Function<? super Stream<? extends R>, ? extends R> resultCollector) throws InterruptedException {
        if (threadNumber <= 0) {
            throw new IllegalArgumentException("Number of threads should be positive");
        }
        threadNumber = Math.max(1, Math.min(threadNumber, list.size()));
        final List<Stream<? extends  T>> subTasks = new ArrayList<>();
        final int blockSize = list.size() / threadNumber;
        int carry = list.size() % threadNumber;
        int leftBound = 0;
        for (int i = 0; i < threadNumber; i++) {
            final int l = leftBound;
            final int r = Math.min(list.size(), l + blockSize + (carry-- > 0 ? 1 : 0));
            subTasks.add(list.subList(l, r).stream());
            leftBound = r;
        }
        final List<R> res;
        if (myMapper != null) {
            res = myMapper.map(mapper, subTasks);
        } else {
            final List<Thread> threads = new ArrayList<>();
            res = new ArrayList<>(Collections.nCopies(subTasks.size(), null));

            for (int i = 0; i < threadNumber; ++i) {
                final int index = i;
                Thread thread = new Thread(() -> res.set(index, mapper.apply(subTasks.get(index))));
                threads.add(thread);
                thread.start();
            }
            boolean failedToJoin = false;
            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    failedToJoin = true;
                    break;
                }
            }
            if (failedToJoin) {
                throw new InterruptedException("At least 1 thread failed to join");
            }
        }
        return resultCollector.apply(res.stream());
    }

    @Override
    public <T> T maximum(int threadNumber, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        return minimum(threadNumber, list, comparator.reversed());
    }

    @Override
    public <T> T minimum(int threadNumber, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        if (list.size() == 0) {
            throw new IllegalArgumentException("Non zero list size expected");
        }
        Function<Stream<? extends  T>, ? extends T> getMin = stream -> stream.min(comparator).get();
        return baseTask(threadNumber, list, getMin, getMin);
    }

    @Override
    public <T> boolean all(int threadNumber, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return baseTask(threadNumber, list,
                stream -> stream.allMatch(predicate),
                stream -> stream.allMatch(Boolean::booleanValue));
    }

    @Override
    public <T> boolean any(int threadNumber, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return baseTask(threadNumber, list,
                stream -> stream.anyMatch(predicate),
                stream -> stream.anyMatch(Boolean::booleanValue));
    }

    @Override
    public String join(int threadNumber, List<?> list) throws InterruptedException {
        return baseTask(threadNumber, list,
                stream -> stream.map(Object::toString).collect(Collectors.joining()),
                stream -> stream.collect(Collectors.joining()));
    }

    @Override
    public <T> List<T> filter(int threadNumber, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return baseTask(threadNumber, list,
                stream -> stream.filter(predicate).collect(Collectors.toList()),
                stream -> stream.flatMap(Collection::stream).collect(Collectors.toList()));
    }

    @Override
    public <T, U> List<U> map(int threadNumber, List<? extends T> list, Function<? super T, ? extends U> function) throws InterruptedException {
        return baseTask(threadNumber, list,
                stream -> stream.map(function).collect(Collectors.toList()),
                stream -> stream.flatMap(Collection::stream).collect(Collectors.toList()));
    }
}
//java -cp ./../../../artifacts/ParallelMapperTest.jar:./../../../artifacts/IterativeParallelismTest.jar:./../../../lib/hamcrest-core-1.3.jar:./../../../lib/junit-4.11.jar:./../../../lib/jsoup-1.8.1.jar:./../../../lib/quickcheck-0.6.jar: info.kgeorgiy.java.advanced.concurrent.Tester list ru.ifmo.rain.daminov.concurrent.IterativeParallelism
//java -cp ./../../../artifacts/ParallelMapperTest.jar:./../../../lib/hamcrest-core-1.3.jar:./../../../lib/junit-4.11.jar:./../../../lib/jsoup-1.8.1.jar:./../../../lib/quickcheck-0.6.jar: info.kgeorgiy.java.advanced.mapper.Tester list ru.ifmo.rain.daminov.concurrent.ParallelMapperImpl,ru.ifmo.rain.daminov.concurrent.IterativeParallelism

