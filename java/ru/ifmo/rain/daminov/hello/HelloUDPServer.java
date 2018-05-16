package ru.ifmo.rain.daminov.hello;

import info.kgeorgiy.java.advanced.hello.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.util.Arrays;
import java.util.stream.Stream;

import static java.lang.System.exit;

public class HelloUDPServer implements HelloServer, AutoCloseable {
    private HelloServer server = null;
    HelloUDPServer() {
    }

    private static int[] checkArguments(int[] indexes, String[] args, int expectedLength) {
        if (args.length < expectedLength) {
            System.err.println("Not enough arguments for running");
            exit(1);
        }
        int res[] = new int[indexes.length];
        for (int i = 0; i < indexes.length; i++) {
            int index = indexes[i];
            try {
                res[i] = Integer.parseInt(args[index]);
            } catch (NumberFormatException e) {
                System.err.println("Argument #" + index + ": " + args[index] + " expected to be Integer");
                exit(1);
            }
        }
        return res;
    }
    public static void main(String[] args) {
        int data[] = checkArguments(new int[]{0, 1}, args, 2);
        (new HelloUDPServer()).start(data[0], data[1]);
    }
    @Override
    public void start(int port, int threads) {
        try {
            server = new HelloServer(port, threads);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        server.close();
    }

    private class HelloServer implements AutoCloseable {
        private final DatagramSocket socket;
        private final Thread[] serverThreads;
        private HelloServer(int port, int threads) throws SocketException {
            socket = new DatagramSocket(port);
            serverThreads = Stream.generate(() -> new Thread(() -> {
                try {
                    while (!Thread.interrupted()) {
                        DatagramPacket p = new DatagramPacket(new byte[socket.getReceiveBufferSize()],
                                socket.getReceiveBufferSize());
                        socket.receive(p);
                        String reply = "Hello, " + new String(p.getData(), p.getOffset(), p.getLength(), Util.CHARSET);
                        p.setData(reply.getBytes(Util.CHARSET), 0, reply.getBytes().length);
                        socket.send(p);
                    }
                } catch (PortUnreachableException e) {
                    System.err.println("Socket is connected to unreachable destination: " + e.getMessage());
                } catch (IOException e) {
                    System.err.println("An I/O error occured: " + e.getMessage());
                }
            })).limit(threads).toArray(Thread[]::new);
            Arrays.stream(serverThreads).forEach(Thread::start);
        }

        @Override
        public void close() {
            for (Thread serverThread : serverThreads) {
                serverThread.interrupt();
            }
            for (Thread serverThread : serverThreads) {
                while (!serverThread.isInterrupted()) {
                    try {
                        serverThread.join();
                    } catch (InterruptedException e) {
                        System.err.println("Failed to join at least one server thread: " + e.getMessage());
                    }
                }
            }
            socket.close();
        }
    }
}
