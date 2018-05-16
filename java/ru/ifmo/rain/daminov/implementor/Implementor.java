package ru.ifmo.rain.daminov.implementor;

import info.kgeorgiy.java.advanced.implementor.*;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

/**
 * Implementing classes for interfaces {@link Impler} and {@link JarImpler}
 */
public class Implementor implements Impler, JarImpler {
    /**
     * The class or interface to be implemented.
     */
    private Class<?> mainClass;
    /**
     * The name of generated class.
     */
    private String outputClassName;
    /**
     * The writer used to print the class into destination file.
     */
    private BufferedWriter writer;
    /**
     * Line separator for generated classes
     */
    private final static String EOLN = System.lineSeparator();
    /**
     * Template for methods and constructors arguments.
     */
    private final static String varNames = "var";
    /**
     * Indent for generated classes.
     */
    private final static String TAB = "    ";

    /**
     * Creates an Implementor Object.
     */
    public Implementor() {
    }

    /**
     * Checks if any of given arguments is <tt>null</tt>
     *
     * @param args list of arguments
     * @throws ImplerException if any arguments is <tt>null</tt>
     */
    private void nullCheck(Object... args) throws ImplerException {
        for (Object val : args) {
            if (val == null) {
                throw new ImplerException("Non null arguments expected");
            }
        }
    }

    /**
     * This method gets the existing Class/Interface and implements/expands it.
     * The generated class will have suffix Impl.
     *
     * @param aClass Class or Interface to implement.
     * @param path   The location of generated class.
     * @throws ImplerException {@link ImplerException} if the given class cannot be generated.
     *                         <ul>
     *                         <li> If the given aClass is not class of interface. </li>
     *                         <li> The aClass is final. </li>
     *                         <li> The process is not allowed to create files or directories. </li>
     *                         <li> Interface contains only private constructors. </li>
     *                         <li> The problems with I/O. </li>
     *                         </ul>
     */
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
        try (BufferedWriter pw = Files.newBufferedWriter(createDirectory(path, aClass, ".java"))) {
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

    /**
     * If the class mainClass isn't located in default package, prints concatenation of string "package " and given class package name.
     *
     * @throws IOException if an error occurred while writing to the destination file via {@link BufferedWriter}.
     */
    private void printPackage() throws IOException {
        if (mainClass.getPackage() != null) {
            writer.write("package " + mainClass.getPackage().getName() + ";" + EOLN + EOLN);
        }
    }

    /**
     * Generate string containing modifiers divided with space from the given int except {@link Modifier#ABSTRACT},
     * {@link Modifier#TRANSIENT}, {@link Modifier#INTERFACE}. Uses {@link Modifier} to convert to String.
     * The mask values representing the modifiers is described there.
     *
     * @param modifier the byte mask of the modifiers.
     * @return The string generated from given int.
     */
    private String printModifiers(int modifier) {
        return Modifier.toString(modifier & ~(Modifier.INTERFACE | Modifier.ABSTRACT | Modifier.TRANSIENT)) + " ";
    }

    /**
     * Prints header of generated mainClass.
     *
     * @throws IOException if an error occurred while writing to the destination file via {@link BufferedWriter}.
     */
    private void printTitle() throws IOException {
        writer.write(printModifiers(mainClass.getModifiers()) + " class "
                + outputClassName + " " + (mainClass.isInterface() ? "implements " : "extends ") + mainClass.getSimpleName()
                + " {" + EOLN);
    }

    /**
     * Generates the string, denoting the argument-list of method or constructor.
     * Arguments are named varName + i, where i is ordinal number of argument.
     *
     * @param argList The array of types of arguments.
     * @return The string, denoting arguments list.
     */
    private String printArgs(Class<?>[] argList) {
        StringBuilder s = new StringBuilder("(");
        for (int i = 0; i < argList.length; i++) {
            s.append(argList[i].getCanonicalName()).append(" ").append(varNames).append(i);
            if (i != argList.length - 1) {
                s.append(", ");
            }
        }
        s.append(") ");
        return s.toString();
    }

    /**
     * Generates the string of next pattern:
     * <tt> throws a, b, c, ... </tt> where a, b, c are some types.
     * This method generates exception for constructors by its exceptions array.
     *
     * @param excList The array of type of exceptions, thrown by constructor.
     * @return Generated String.
     */
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

    /**
     * Implements all constructors of the class/interface mainClass.
     *
     * @throws ImplerException If mainClass is class with no public constructors.
     * @throws IOException     if an error occurred while writing to the destination file via {@link BufferedWriter}.
     */
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

    /**
     * Implements all methods from class or interface mainClass and public and protected methods of its superclasses.
     *
     * @throws IOException if an error occurred while writing to the destination file via. {@link BufferedWriter}
     */
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

    /**
     * Filters given array of {@link Method}, leaving only declared as abstract and puts them
     * in given {@link Set}, after wrapping them to {@link MethodWrapper}
     *
     * @param methods given array of {@link Method}
     * @param set     {@link Set} where to store methods
     */
    private void addMethods(Set<MethodWrapper> set, Method[] methods) {
        Arrays.stream(methods).filter(method -> Modifier.isAbstract(method.getModifiers()))
                .map(MethodWrapper::new).collect(Collectors.toCollection(() -> set));
    }

    /**
     * By given Class object creates directory for its java source or object file.
     * It means that directories for packages are also created. If
     * directories exist, nothing happens. Then returns the relative path
     * to file.
     *
     * @param root   The location, where packages directories are created.
     * @param aClass The class object containing its packages data.
     * @param suffix File expansion. java-source or java-object file.
     * @return The relative path to given class file.
     * @throws IOException Directories are created using {@link Files#createDirectories(Path, FileAttribute[])}
     *                     and all its exceptions are thrown by this method.
     */
    private Path createDirectory(Path root, Class<?> aClass, String suffix) throws IOException {
        if (aClass.getPackage() != null) {
            root = root.resolve(aClass.getPackage().getName().replace('.', File.separatorChar));
        }
        Files.createDirectories(root);
        return root.resolve(outputClassName + suffix);
    }

    /**
     * Implements the given class and creates Jar-Archive with resulting class.
     *
     * @param aClass the given class.
     * @param path   destination of Jar-Archive.
     * @throws ImplerException <ul>
     *                         <li> I/O Exceptions while creating, reading or deleting files and directories. </li>
     *                         <li> Exceptions thrown by {@link Implementor#implement(Class, Path)}.</li>
     *                         </ul>
     * @see Implementor#implement(Class, Path)
     */
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
        Path temporaryDir = Paths.get(System.getProperty("user.dir")).resolve("temp");
        try {
            Path filePath = temporaryDir.relativize(createAndCompile(aClass, temporaryDir));
            createJarFile(temporaryDir, filePath, path);
            clean(temporaryDir);
        } catch (IOException e) {
            throw new ImplerException(e);
        }
    }

    /**
     * Creates Jar file in the given directory and stores the given file in it.
     *
     * @param dirs     The location of package of java-class that need to be stored in archive.
     * @param pathFile The absolute path to java-class.
     * @param path     The directory, where jar file is stored.
     * @throws IOException If error occurred while using {@link JarOutputStream} or opening fileStream  to
     *                     path.
     */
    private void createJarFile(Path dirs, Path pathFile, Path path) throws IOException {
        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        main.put(Attributes.Name.IMPLEMENTATION_VENDOR, "Nodir Daminov");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(path), manifest)) {
            out.putNextEntry(new ZipEntry(pathFile.toString().replace(File.separator, "/")));
            Files.copy(dirs.resolve(pathFile), out);
        }
    }

    /**
     * Remove the given file/directory and everything in it.
     *
     * @param root the path to the given file/directory.
     * @throws IOException       If error occurs while deleting files.
     * @throws SecurityException If the security manager denies access to the starting file. In the case of the.
     *                           default provider, the checkRead method is invoked to check read access to the directory.
     */
    private void clean(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Implements the given class in given directory, compiles it and stores its object file there.
     *
     * @param aClass       the given class.
     * @param temporaryDir directory to store output files.
     * @return Path to created object file.
     * @throws ImplerException      If error occurred while compiling or creating files.
     * @throws IOException          If error occurred while compiling or creating files.
     * @throws NullPointerException if compiler can't be accessed.
     * @see Implementor#implement(Class, Path)
     */
    private Path createAndCompile(Class<?> aClass, Path temporaryDir) throws ImplerException, IOException {
        implement(aClass, temporaryDir);
        JavaCompiler c = ToolProvider.getSystemJavaCompiler();
        createDirectory(temporaryDir, aClass, ",java");
        if (c.run(null, null, null, "-cp"
                , aClass.getPackage().getName() + File.pathSeparator + System.getProperty("java.class.path")
                , createDirectory(temporaryDir, aClass, ".java").toString()) != 0) {
            throw new ImplerException("Can't compile the given file");
        }
        return createDirectory(temporaryDir, aClass, ".class");
    }

    /**
     * The class-wrapper for java methods. Provides meaningful methods {@link MethodWrapper#equals(Object)},
     * {@link MethodWrapper#hashCode()} and {@link MethodWrapper#toString()} and is
     * used to contain methods in {@link HashSet}.
     */
    private class MethodWrapper {
        /**
         * The method, kept in this exemplar of {@link MethodWrapper}.
         */
        private final Method method;
        /**
         * Calculated hash for the method. Its value is stored during the first call of constructor.
         */
        private final int hash;

        /**
         * Creates exemplar of the class and stores given method in it.
         *
         * @param method given method.
         */
        private MethodWrapper(Method method) {
            this.method = method;
            hash = (method.getName() + printArgs(method.getParameterTypes())).hashCode();
        }

        /**
         * Returns hash code of method stored in this object.
         *
         * @return needed hash code.
         */
        @Override
        public int hashCode() {
            return hash;
        }

        /**
         * Checks for equality of methods in this wrappers and method in given object.
         *
         * @param o the given object.
         * @return <tt> true </tt>, if objects are equal, <tt>false </tt> otherwise.
         */
        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof MethodWrapper)) {
                return false;
            }
            MethodWrapper temp = (MethodWrapper) o;
            return temp.method.getName().equals(method.getName()) &&
                    Arrays.equals(temp.method.getParameterTypes(), method.getParameterTypes());
        }

        /**
         * Generates the string presentation of method in java source file.
         *
         * @return generated string.
         */
        @Override
        public String toString() {
            return TAB + printModifiers(method.getModifiers()) + " "
                    + method.getReturnType().getTypeName() + " "
                    + method.getName() + printArgs(method.getParameterTypes()) + "{" + EOLN
                    + returnImpl(method.getReturnType()) + EOLN
                    + TAB + "}" + EOLN;
        }
    }

    /**
     * Generates the string <tt>"return x;"</tt>, where x is 0, false, null or empty string.
     * Chooses the variant to fit returning type of method.
     *
     * @param aClass The returning type of method.
     * @return Generated string.
     */
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

    /**
     * Runs Class-Implementor (method implement) or Creates Jar(method jarImplement).
     *
     * @param args The array of arguments for Implementor.
     *             There are 2 variants of arguments for running main:
     *             <ul>
     *             <li> -jar fullClassName generatedFilesLocation. The jarImplement method called. </li>
     *             <li> fullClassName generatedFilesLocation. The implement method is called. </li>
     *             </ul>
     */
    public static void main(String[] args) {
        if (args == null || (args.length != 2 && args.length != 3)) {
            System.out.println("Two or three arguments expected");
            return;
        }
        for (String arg : args) {
            if (arg == null) {
                System.out.println("All arguments must be non-null");
            }
        }
        JarImpler implementor = new Implementor();
        try {
            if (args.length == 3) {
                implementor.implementJar(Class.forName(args[1]), Paths.get(args[2]));
            } else {
                implementor.implement(Class.forName(args[0]), Paths.get(args[1]));
            }
        } catch (InvalidPathException e) {
            System.out.println("Incorrect path to root: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.out.println("Incorrect class name: " + e.getMessage());
        } catch (ImplerException e) {
            System.out.println("An error occurred during implementation: " + e.getMessage());
        }
    }
}


//jar cfm Implemetor.jar ./out/production/java-advanced-2018/META-INF/MANIFEST ./out/production/java-advanced-2018/ru/ifmo/rain/daminov/implementor/Implementor
//java -cp ./../../../artifacts/JarImplementorTest.jar:./../../../lib/hamcrest-core-1.3.jar:./../../../lib/junit-4.11.jar:./../../../lib/jsoup-1.8.1.jar:./../../../lib/quickcheck-0.6.jar: info.kgeorgiy.java.advanced.implementor.Tester  jar-class ru.ifmo.rain.daminov.implementor.Implementor
//Class-Path: ./../artifacts/JarImplementorTest.jar:./../lib/hamcrest-core-1.3.jar:./../lib/junit-4.11.jar:./../lib/jsoup-1.8.1.jar:./../lib/quickcheck-0.6.jar: