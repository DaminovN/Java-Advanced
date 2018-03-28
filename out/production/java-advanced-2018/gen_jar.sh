#!/bin/bash
javac -cp ./../artifacts/JarImplementorTest.jar:./../lib/hamcrest-core-1.3.jar:./../lib/junit-4.11.jar:./../lib/jsoup-1.8.1.jar:./../lib/quickcheck-0.6.jar: -Xlint ru/ifmo/rain/daminov/implementor/Implementor.java

jar cfm Implementor.jar Manifest.txt ru/ifmo/rain/daminov/implementor/*.class info/kgeorgiy/java/advanced/implementor/*.class

