/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package org.meltzg.fs;

public class Library {
    static {
        System.loadLibrary("jmtp");
    }

    private native int foo();
    public int someLibraryMethod() {
        return foo();
    }
}
