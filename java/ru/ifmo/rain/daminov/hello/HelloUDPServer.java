package ru.ifmo.rain.daminov.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;
import info.kgeorgiy.java.advanced.hello.Util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static java.lang.System.exit;

public class HelloUDPServer implements HelloServer, AutoCloseable {
    private HelloServer server = null;
    private final static int MAX_SIZE = 10000;
    public HelloUDPServer() {
    }

    public static void main(String[] args) {
        int data[] = Utils.checkArguments(new int[]{0, 1}, args, 2);
        (new HelloUDPServer()).start(data[0], data[1]);
    }
    @Override
    public void start(int port, int threads) {
        try {
            server = new HelloServer(port, threads);
        } catch (SocketException e) {
            System.err.println("Unable to create socket binded to port : " + port);
        }
    }

    @Override
    public void close() {
        server.close();
    }

    private class HelloServer implements AutoCloseable {
        private final DatagramSocket socket;
        private boolean closed = false;
        private ExecutorService serverThreads;
        private ExecutorService listener;
//        private final Thread[] serverThreads;
        private HelloServer(int port, int threads) throws SocketException {
            socket = new DatagramSocket(port);
            serverThreads = new ThreadPoolExecutor(threads, threads, 1, TimeUnit.MINUTES
                                    , new ArrayBlockingQueue<>(MAX_SIZE), new ThreadPoolExecutor.DiscardPolicy());
            listener = Executors.newSingleThreadExecutor();
            listener.submit(this::listenAndSubmit);
        }

        private void listenAndSubmit() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final DatagramPacket p = Utils.createToReceive(socket.getReceiveBufferSize());
                    socket.receive(p);
                    serverThreads.submit(() -> respond(p));
                } catch (IOException e) {
                    if (!closed) {
                        System.err.println("An I/O error occured during proccesing datagram: " + e.getMessage());
                    }
                }
            }
        }

        private void respond(final DatagramPacket p) {
            final String reply = "Hello, " + new String(p.getData(), p.getOffset(), p.getLength(), Util.CHARSET);
            try {
                final DatagramPacket respond =  new DatagramPacket(reply.getBytes(Util.CHARSET), reply.getBytes().length, p.getSocketAddress());
                socket.send(respond);
            } catch (PortUnreachableException e) {
                System.err.println("Socket is connected to unreachable destination: " + e.getMessage());
            } catch (IOException e) {
                if (!closed) {
                    System.err.println("An I/O error occured during proccesing datagram: " + e.getMessage());
                }
            }
        }

        @Override
        public void close() {
            socket.close();
            closed = true;
            listener.shutdownNow();
            serverThreads.shutdownNow();
            try {
                serverThreads.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }
    }
}
