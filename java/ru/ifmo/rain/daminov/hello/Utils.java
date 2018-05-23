package ru.ifmo.rain.daminov.hello;

import java.net.DatagramPacket;
import java.net.SocketAddress;

import static java.lang.System.exit;

public class Utils {
    public Utils() {

    }
    public static int[] checkArguments(int[] indexes, String[] args, int expectedLength) {
        if (args.length < expectedLength) {
            System.err.println("Not enough arguments for running, expected 5 arguments");
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

    public static DatagramPacket createToSend(final SocketAddress adr, final int sz) {
        final byte[] buf = new byte[sz];
        return new DatagramPacket(buf, buf.length, adr);
    }
    public static DatagramPacket createToReceive(final int sz) {
        final byte[] buf = new byte[sz];
        return new DatagramPacket(buf, buf.length);
    }
}
