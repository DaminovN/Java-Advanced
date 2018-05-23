package ru.ifmo.rain.daminov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HelloUDPClient implements HelloClient {

    public static void main(String[] args) {
        int data[] = Utils.checkArguments(new int[]{1, 3, 4}, args, 5);
        new HelloUDPClient().run(args[0], data[0], args[2], data[1], data[2]);
    }

    private static String getRequestForm(final String prefix, final int threadNumb, final int requestNumb) {
        return prefix + threadNumb + "_" + requestNumb;
    }
    @Override
    public void run(String host, int port, String prefix, int threadNumb, int requests) {
        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            System.err.println("Unable to find host :" + host + " ;" + e.getMessage());
            return;
        }
        final SocketAddress socketAdr = new InetSocketAddress(address, port);
        final ExecutorService threads = Executors.newFixedThreadPool(threadNumb);
        for (int i = 0; i < threadNumb; i++) {
            final int id = i;
            threads.submit(() -> completeRequests(socketAdr, prefix, requests, id));
        }
        threads.shutdown();
        try {
            threads.awaitTermination(threadNumb * requests * 4, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
        }
    }



    private static void completeRequests(final SocketAddress address, final String prefix, final int requests, final int threadID) {
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(400);
            final DatagramPacket request = Utils.createToSend(address, 0);
            final DatagramPacket response = Utils.createToReceive(socket.getReceiveBufferSize());

            for (int requestNumb = 0; requestNumb < requests; requestNumb++) {
                boolean gotResponse = false;
                while (!gotResponse) {
                    try {
                        final String requestStr = getRequestForm(prefix, threadID, requestNumb);
                        request.setData(requestStr.getBytes(StandardCharsets.UTF_8));
                        socket.send(request);
                        System.out.println("\nRequest sent:\n" + requestStr);
                        socket.receive(response);
                        final String responseStr = new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8);
                        if ((responseStr.length() > requestStr.length()) && responseStr.contains(requestStr)) {
                            System.out.println("\nRequest " + requestStr + " received: " + responseStr + "\n");
                            gotResponse = true;
                        }
                    } catch (PortUnreachableException e) {
                        System.err.println("Socket is connected to a currently unreachable destination: " + e.getMessage());
                    } catch (IOException e) {
                        System.err.println("An I/O error occured: " + e.getMessage());
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("Unable to create socket to server: " + address.toString());
        }
    }
}

//java -cp ./../../../artifacts/HelloUDPTest.jar:./../../../lib/hamcrest-core-1.3.jar:./../../../lib/junit-4.11.jar:./../../../lib/jsoup-1.8.1.jar:./../../../lib/quickcheck-0.6.jar: info.kgeorgiy.java.advanced.hello.Tester client-i18n ru.ifmo.rain.daminov.hello.HelloUDPClient