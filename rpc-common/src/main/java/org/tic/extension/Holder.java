package org.tic.extension;

/**
 * @author codesssss
 * @date 18/6/2024 11:15 pm
 */
public class Holder<T> {

    private volatile T value;

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }
}
