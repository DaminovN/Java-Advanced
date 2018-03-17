package ru.ifmo.rain.daminov.student;

import info.kgeorgiy.java.advanced.student.*;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class StudentDB implements StudentGroupQuery {

    private Comparator<Student> comparator = Comparator.comparing(Student::getLastName)
            .thenComparing(Student::getFirstName).thenComparing(Student::getId);

    private <T> List<T> getLst(Stream<T> stream) {
        return stream.collect(Collectors.toList());
    }

    private List<String> getMappedList(List<Student> list, Function<Student, String> mapper) {
        return getLst(list.stream().map(mapper));
    }

    @Override
    public List<String> getFirstNames(List<Student> list) {
        return getMappedList(list, Student::getFirstName);
    }

    @Override
    public List<String> getLastNames(List<Student> list) {
        return getMappedList(list, Student::getLastName);
    }

    @Override
    public List<String> getGroups(List<Student> list) {
        return getMappedList(list, Student::getGroup);
    }

    @Override
    public List<String> getFullNames(List<Student> list) {
        return getMappedList(list, student -> String.format("%s %s", student.getFirstName(), student.getLastName()));
    }

    @Override
    public Set<String> getDistinctFirstNames(List<Student> list) {
        return (getFirstNames(list)).stream().collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public String getMinStudentFirstName(List<Student> list) {
        return list.stream().min(Student::compareTo).map(Student::getFirstName).orElse("");
    }

    private List<Student> sort(Stream<Student> stream, Comparator<Student> comparator) {
        return getLst(stream.sorted(comparator));
    }

    @Override
    public List<Student> sortStudentsById(Collection<Student> collection) {
        return sort(collection.stream(), Student::compareTo);
    }

    @Override
    public List<Student> sortStudentsByName(Collection<Student> collection) {
        return sort(collection.stream(), comparator);
    }

    private Stream<Student> filter(Collection<Student> collection, Predicate<Student> predicate) {
        return collection.stream().filter(predicate);
    }

    @Override
    public List<Student> findStudentsByFirstName(Collection<Student> collection, String s) {
        return sort(filter(collection, x -> s.equals(x.getFirstName())), comparator);
    }

    @Override
    public List<Student> findStudentsByLastName(Collection<Student> collection, String s) {
        return sort(filter(collection, x -> s.equals(x.getLastName())), comparator);
    }

    @Override
    public List<Student> findStudentsByGroup(Collection<Student> collection, String s) {
        return sort(filter(collection, x -> s.equals(x.getGroup())), comparator);
    }

    @Override
    public Map<String, String> findStudentNamesByGroup(Collection<Student> collection, String s) {
        return filter(collection, x -> s.equals(x.getGroup())).collect(Collectors.toMap(Student::getLastName
                , Student::getFirstName, BinaryOperator.minBy(String::compareTo)));
    }


    private List<Group> getSortedGroup(Collection<Student> collection, Comparator<Student> func) {
        return getLst(collection.stream().collect(Collectors.groupingBy(Student::getGroup, TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(a -> new Group(a.getKey(), sort(a.getValue().stream(), func))));
    }


    @Override
    public List<Group> getGroupsByName(Collection<Student> collection) {
        return getSortedGroup(collection, comparator);
    }

    @Override
    public List<Group> getGroupsById(Collection<Student> collection) {
        return getSortedGroup(collection, Comparator.comparing(Student::getId));
    }

    private String getGroup(Collection<Student> collection, ToIntFunction<List<Student>> func) {
        return getGroupsById(collection).stream()
                .max(
                        Comparator.comparing(
                                Group::getStudents, Comparator.comparingInt(func)
                        ).thenComparing(Group::getName, Collections.reverseOrder(String::compareTo))
                ).map(Group::getName).orElse("");
    }

    @Override
    public String getLargestGroup(Collection<Student> collection) {
        return getGroup(collection, List::size);
    }

    @Override
    public String getLargestGroupFirstName(Collection<Student> collection) {
        return getGroup(collection, a -> getDistinctFirstNames(a).size());
    }
}
