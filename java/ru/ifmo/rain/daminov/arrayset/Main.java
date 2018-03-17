package ru.ifmo.rain.daminov.arrayset;

import java.util.*;

import static java.util.Collections.binarySearch;

public class Main {
    public static void main(String[] args) {
        List<Integer> list = new ArrayList<>();
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);
        list.add(6);
        NavigableSet<Integer> set = new ArraySet<>(list);
        System.out.println(set.first());
    }
}
