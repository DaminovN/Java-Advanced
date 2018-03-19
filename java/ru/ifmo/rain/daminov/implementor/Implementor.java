package ru.ifmo.rain.daminov.implementor;

import info.kgeorgiy.java.advanced.implementor.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class Implementor implements Impler, JarImpler {
    private Class<?> mainClass;
    private String outputClassName;
    private BufferedWriter writer;
    private final static String EOLN = System.lineSeparator();
    private final static String varNames = "var";
    private final static String TAB = "    ";
    public Implementor() {
    }

    private void nullCheck(Object... args) throws ImplerException {
        for (Object val : args) {
            if (val == null) {
                throw new ImplerException("Non null arguments expected");
            }
        }
    }

    @Override
    public void implement(Class<?> aClass, Path path) throws ImplerException {
        nullCheck(aClass, path);
        if (aClass.isArray() || aClass == Enum.class || aClass.isPrimitive()) {
            throw new ImplerException("The given Class<> should be a class or an interface");
        }
        if (Modifier.isFinal(aClass.getModifiers())) {
            throw new ImplerException("Cannot implement final class");
        }
        mainClass = aClass;
        outputClassName = mainClass.getSimpleName() + "Impl";
        try(BufferedWriter pw =  Files.newBufferedWriter(createDirectory(path, aClass, ".java"))) {
            writer = pw;
            try {
                printPackage();
                printTitle();
                printConstructors();
                printMethods();
                writer.write("}");
                writer.write(EOLN);
            } catch (IOException e) {
                throw new ImplerException("Unable to write to output file", e);
            }
        } catch (IOException e) {
            throw new ImplerException("Unable to create output file", e);
        } catch (SecurityException e) {
            throw new ImplerException("Unable to create directories", e);
        }
    }

    private void printPackage() throws IOException {
        if (mainClass.getPackage() != null) {
            writer.write("package " + mainClass.getPackage().getName() + ";" + EOLN + EOLN);
        }
    }
    private String printModifiers(int modifier) {
        return Modifier.toString(modifier & ~(Modifier.INTERFACE | Modifier.ABSTRACT | Modifier.TRANSIENT)) + " ";
    }
    private void printTitle() throws IOException {
        writer.write(printModifiers(mainClass.getModifiers()) + " class "
                + outputClassName + " " + (mainClass.isInterface() ? "implements " : "extends ") + mainClass.getSimpleName()
                        + " {" + EOLN);
    }
    private String printArgs(Class<?>[] argList) {
        StringBuilder s = new StringBuilder("(");
        for (int i = 0; i < argList.length; i++) {
            s.append(argList[i].getTypeName()).append(" ").append(varNames).append(i);
            if (i != argList.length - 1) {
                s.append(", ");
            }
        }
        s.append(") ");
        return s.toString();
    }
    private String printExceptions(Class<?>[] excList) {
        if (excList.length == 0) {
            return "";
        }
        StringBuilder s = new StringBuilder("throws ");
        for (int i = 0; i < excList.length; i++) {
            s.append(excList[i].getTypeName());
            if (i != excList.length - 1) {
                s.append(",");
            }
            s.append(" ");
        }
        return s.toString();
    }
    private void printConstructors() throws IOException, ImplerException {
        boolean flag = true;
        for (Constructor<?> constructor : mainClass.getDeclaredConstructors()) {
            if (!Modifier.isPrivate(constructor.getModifiers())) {
                flag = false;
                writer.write(TAB + printModifiers(mainClass.getModifiers()));
                writer.write(outputClassName);
                writer.write(printArgs(constructor.getParameterTypes()));
                writer.write(printExceptions(constructor.getExceptionTypes()));

                writer.write("{" + EOLN + TAB + TAB + "super(");
                for (int i = 0; i < constructor.getParameterCount(); i++) {
                    writer.write(varNames + i);
                    if (i != constructor.getParameterCount() - 1) {
                        writer.write(", ");
                    }
                }
                writer.write(");" + EOLN + TAB + "}" + EOLN + EOLN);
            }
        }
        if (!mainClass.isInterface() && flag) {
            throw new ImplerException("Only private constructors");
        }
    }
    private void addMethods(Set<MethodWrapper> set, Method[] methods) {
        Arrays.stream(methods).filter(method -> Modifier.isAbstract(method.getModifiers()))
                .map(MethodWrapper::new).collect(Collectors.toCollection(() -> set));
    }

    private void printMethods() throws IOException {
        Set<MethodWrapper> methods = new HashSet<>();
        addMethods(methods, mainClass.getMethods());
        while (mainClass != null) {
            addMethods(methods, mainClass.getDeclaredMethods());
            mainClass = mainClass.getSuperclass();
        }
        for (MethodWrapper method : methods) {
            writer.write(method + EOLN);
        }
    }

    private Path createDirectory(Path root, Class<?> aClass, String suffix) throws IOException {
        if (aClass.getPackage() != null) {
            root = root.resolve(aClass.getPackage().getName().replace('.', File.separatorChar));
        }
        Files.createDirectories(root);
        return root.resolve(outputClassName + suffix);
    }

    @Override
    public void implementJar(Class<?> aClass, Path path) throws ImplerException {
        nullCheck(aClass, path);
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
        } catch (IOException e) {
            throw new ImplerException("Unable to create output file", e);
        }
    }

    private class MethodWrapper {
        private  final Method method;
        private final int hash;

        private MethodWrapper(Method method) {
            this.method = method;
            hash = (method.getName() + printArgs(method.getParameterTypes())).hashCode();
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof MethodWrapper)) {
                return false;
            }
            MethodWrapper temp = (MethodWrapper) o;
            return temp.method.getName().equals(method.getName()) &&
                    Arrays.equals(temp.method.getParameterTypes(), method.getParameterTypes());
        }
        @Override
        public String toString() {
            return TAB + printModifiers(method.getModifiers()) + " "
                    + method.getReturnType().getTypeName() + " "
                    + method.getName() + printArgs(method.getParameterTypes()) + "{" + EOLN
                    + returnImpl(method.getReturnType()) + EOLN
                    + TAB + "}" + EOLN;
        }
    }
    private String returnImpl(Class<?> aClass) {
        StringBuilder s = new StringBuilder(TAB + TAB + "return ");
        if (!aClass.isPrimitive()) {
            s.append("null");
        } else if (aClass.equals(boolean.class)) {
            s.append("false");
        } else if (!aClass.equals(void.class)) {
            s.append("0");
        }
        return s.append(";").toString();
    }
    public static void main(String[] args) {
        try {
            new Implementor().implement(Class.forName(args[0]), Paths.get(args[1]));
        } catch (ImplerException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
