// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

/**
 * Array list.  This calls the {@code Unsafe.free} method
 * for explicitly deallocating unused memory.
 */
public final class CArrayList<T> implements Cloneable { // currently not implements List<T>
    private Object[] elements;
    private int size;

    public CArrayList() {
        elements = new Object[8];
        size = 0;
    }

    public int size() { return size; }

    public boolean isEmpty() { return size == 0; }

    private void ensureCapacity(int index) {
        Object[] oldElements = elements;
        int len = oldElements.length;
        if (len <= index) {
            int newLen = len + (len >> 1);
            Object[] newElements = new Object[newLen];
            for (int i = 0; i < len; i++)
                newElements[i] = oldElements[i];

            elements = newElements;
            Unsafe.free(oldElements);
        }
    }

    public boolean contains(Object obj) {
        return indexOf(obj) >= 0;
    }

    public boolean add(T e) {
        ensureCapacity(size);
        elements[size++] = e;
        return true;
    }

    public void clear() {
        size = 0;
    }

    public T get(int index) {
        if (index < size)
            return (T)elements[index];
        else
            return null;
    }

    public T set(int index, T element) {
        ensureCapacity(index);
        Object old = elements[index];
        elements[index] = element;
        return (T)old;
    }

    public int indexOf(Object obj) {
        for (int i = 0; i < size; i++)
            if (elements[i] == obj)
                return i;

        return -1;
    }

    public int lastIndexOf(Object obj) {
        int found = -1;
        for (int i = 0; i < size; i++)
            if (elements[i] == obj)
                found = i;

        return found;
    }

    /*
    public Iterator<T> iterator() {
        throw new RuntimeException("not implemented");
    }

    public Object[] toArray() {
        throw new RuntimeException("not implemented");
    }

    public <T> T[] toArray(T[] a) {
        throw new RuntimeException("not implemented");
    }

    public boolean remove(Object o) {
        throw new RuntimeException("not implemented");
    }

    public boolean containsAll(Collection<?> c) {
        throw new RuntimeException("not implemented");
    }

    public boolean addAll(Collection<? extends T> c) {
        throw new RuntimeException("not implemented");
    }

    public boolean addAll(int index, Collection<? extends T> c) {
        throw new RuntimeException("not implemented");
    }

    public boolean removeAll(Collection<?> c) {
        throw new RuntimeException("not implemented");
    }

    public boolean retainAll(Collection<?> c) {
        throw new RuntimeException("not implemented");
    }

    public void add(int index, T element) {
        throw new RuntimeException("not implemented");
    }

    public T remove(int index) {
        throw new RuntimeException("not implemented");
    }

    public ListIterator<T> listIterator() {
        throw new RuntimeException("not implemented");
    }

    public ListIterator<T> listIterator(int index) {
        throw new RuntimeException("not implemented");
    }

    public List<T> subList(int fromIndex, int toIndex) {
        throw new RuntimeException("not implemented");
    }

    public Spliterator<T> spliterator() {
        throw new RuntimeException("not implemented");
    }
    */
}
