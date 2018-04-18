package ru.ifmo.rain.daminov.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class WebCrawler implements Crawler {
    private final Downloader downloader;
    private final ExecutorService extractorPool;
    private final ExecutorService downloaderPool;
    private final LinkHandler handler;
    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        downloaderPool = Executors.newFixedThreadPool(downloaders);
        extractorPool = Executors.newFixedThreadPool(extractors);
        handler = new LinkHandler(perHost);
        this.downloader = downloader;
    }
    @Override
    public Result download(String url, int depth) {
        Phaser waiter = new Phaser(1);
        Set<String> downloaded = ConcurrentHashMap.newKeySet();
        Map<String, IOException> errors = new ConcurrentHashMap<>();
        myDownload(waiter, downloaded, errors, url, depth);
        waiter.arriveAndAwaitAdvance();
        downloaded.removeAll(errors.keySet());
        return new Result(new ArrayList<>(downloaded), errors);
    }

    private void errorCatchWrapper(Phaser waiter, Map<String, IOException> errors,
                                   String url, Callable<?> callable) {
        try {
            callable.call();
        } catch (IOException e) {
            errors.put(url, e);
        } catch (Exception e) {
            System.out.println(e.getMessage() + " " + e.getCause());
            e.printStackTrace();
        } finally {
            waiter.arrive();
        }
    }

    private void myDownload(Phaser waiter, Set<String> downloaded, Map<String, IOException> errors,
                            String url, int depth) {
        if (depth >= 1 && downloaded.add(url)) {
            final String host;
            try {
                host = URLUtils.getHost(url);
            } catch (MalformedURLException e) {
                errors.put(url, e);
                return;
            }
            waiter.register();
            handler.add(host, () -> errorCatchWrapper(waiter, errors, url,
                    () -> {
                        try {
                            final Document document = downloader.download(url);
                            if (depth != 1) {
                                waiter.register();
                                extractorPool.submit(() -> errorCatchWrapper(waiter, errors, url,
                                        () -> {
                                            document.extractLinks().forEach(s -> myDownload(waiter, downloaded, errors,
                                                    s, depth - 1));
                                            return null;
                                        }));
                            }
                        } finally {
                            handler.finish(host);
                        }
                        return null;
                    }));
        }
    }

    @Override
    public void close() {
        downloaderPool.shutdown();
        extractorPool.shutdown();
    }
    private class LinkHandler {
        private final int maxPerHost;
        private final Map<String, HostData> countersAndQueue = new ConcurrentHashMap<>();
        LinkHandler(int maxPerHost) {
            this.maxPerHost = maxPerHost;
        }

        void add(String host, Runnable url) {
            HostData hostData = countersAndQueue.putIfAbsent(host, new HostData());
            if (hostData == null) {
                hostData = countersAndQueue.get(host);
            }
            hostData.locker.lock();
            if (hostData.connections == maxPerHost) {
                hostData.q.add(url);
            } else {
                downloaderPool.submit(url);
                hostData.connections++;
            }
            hostData.locker.unlock();
        }

        void finish(String host) {
            HostData hostData = countersAndQueue.get(host);
            hostData.locker.lock();
            if (!hostData.q.isEmpty()) {
                downloaderPool.submit(hostData.q.poll());
            } else {
                hostData.connections--;
            }
            hostData.locker.unlock();
        }
    }
    private class HostData {
        final Queue<Runnable> q;
        final ReentrantLock locker;
        int connections;
        HostData() {
            q = new LinkedBlockingQueue<>();
            locker = new ReentrantLock();
            connections = 0;
        }
    }

    public static void main(String[] args) {
        try (Crawler crawler = new WebCrawler(new CachingDownloader(),
                Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]))) {
            crawler.download(args[0], Integer.parseInt(args[1]));
        } catch (IOException e) {
            System.out.println("Cannot create instance of FilterCachingDownloader: " + e);
        } catch (NumberFormatException e) {
            System.out.println("The integer number expected in the argument: " + e);
        }
    }
}
