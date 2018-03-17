package ru.ifmo.rain.daminov.arrayset;

import java.util.*;

import static java.util.Collections.binarySearch;

public class ArraySet<E> extends AbstractSet<E> implements NavigableSet<E> {
    private List<E> data;
    private Comparator<? super E> comparator;

    public ArraySet(ArraySet<E> other) {
        this(other.data, other.comparator);
    }

    public ArraySet() {
        this(Collections.emptyList(), null);
    }

    private ArraySet(List<E> other, Comparator<? super E> comparator) {
        data = other;
        this.comparator = comparator;
    }

    public ArraySet(Collection<? extends E> other) {
        this(other, null);
    }

    public ArraySet(Collection<? extends E> other, Comparator<? super E> comparator) {
        this.comparator = comparator;
        Set<E> set = new TreeSet<>(comparator);
        set.addAll(other);
        data = new ArrayList<>(set);
    }

    private int helpBinSearch(E x, int posit, int negat) {
        int pos = binarySearch(data, x, comparator);
        return (pos >= 0) ? (pos + posit) : (-pos - 1 + negat);
    }

    private E getValue(int pos, boolean thrw) {
        if (0 <= pos && pos < data.size()) {
            return data.get(pos);
        } else {
            if (!thrw) {
                return null;
            } else {
                throw new NoSuchElementException();
            }
        }
    }

    @Override
    public E lower(E x) {
        return getValue(helpBinSearch(x, -1, -1), false);
    }

    @Override
    public E floor(E x) {
        return getValue(helpBinSearch(x, 0, -1), false);
    }

    @Override
    public E ceiling(E x) {
        return getValue(helpBinSearch(x, 0, 0), false);
    }

    @Override
    public E higher(E x) {
        return getValue(helpBinSearch(x, 1, 0), false);
    }

    @Override
    public E pollFirst() {
        throw new UnsupportedOperationException("ArraySet is noy mutable");
    }

    @Override
    public E pollLast() {
        throw new UnsupportedOperationException("ArraySet is noy mutable");
    }

    @Override
    public int size() {
        return data.size();
    }


    @Override
    public boolean contains(Object x) {
        return binarySearch(data, (E) x, comparator) >= 0;
    }

    @Override
    public Iterator<E> iterator() {
        return Collections.unmodifiableList(data).iterator();
    }

    @Override
    public NavigableSet<E> descendingSet() {
        return new ArraySet<>(new ReversedArrayList<>(data), Collections.reverseOrder(comparator));
    }

    private class ReversedArrayList<T> extends AbstractList<T> implements RandomAccess {
        private List<T> data;
        private boolean isReversed;

        public ReversedArrayList(List<T> other) {
            if (other instanceof ReversedArrayList) {
                ReversedArrayList<T> tmp = (ReversedArrayList<T>) other;
                data = tmp.data;
                isReversed = !tmp.isReversed;
            } else {
                data = other;
                isReversed = true;
            }
        }

        @Override
        public T get(int i) {
            return (!isReversed) ? data.get(i) : data.get(size() - i - 1);
        }

        @Override
        public int size() {
            return data.size();
        }
    }

    @Override
    public Iterator<E> descendingIterator() {
        return (new ReversedArrayList<>(data)).iterator();
    }

    @Override
    public NavigableSet<E> subSet(E a, boolean b, E a1, boolean b1) {
        int l = helpBinSearch(a, b ? 0 : 1, 0);
        int r = helpBinSearch(a1, b1 ? 0 : -1, -1);
        return (l > r) ? Collections.emptyNavigableSet() : new ArraySet<>(data.subList(l, r + 1), comparator);
    }

    @Override
    public NavigableSet<E> headSet(E x, boolean b) {
        return data.isEmpty() ? Collections.emptyNavigableSet() : subSet(data.get(0), true, x, b);
    }

    @Override
    public NavigableSet<E> tailSet(E x, boolean b) {
        return data.isEmpty() ? Collections.emptyNavigableSet() : subSet(x, b, data.get(size() - 1), true);
    }

    @Override
    public Comparator<? super E> comparator() {
        return comparator;
    }

    @Override
    public SortedSet<E> subSet(E e, E e1) {
        return subSet(e, true, e1, false);
    }

    @Override
    public SortedSet<E> headSet(E e) {
        return headSet(e, false);
    }

    @Override
    public SortedSet<E> tailSet(E e) {
        return tailSet(e, true);
    }

    @Override
    public E first() {
        return getValue(0, true);
    }

    @Override
    public E last() {
        return getValue(data.size() - 1, true);
    }

}