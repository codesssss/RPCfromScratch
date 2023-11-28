package org.tic;

/**
 * @author Xuhang Shi
 * @date 24/11/2023 12:00 pm
 */
public class Main {
    public static void main(String[] args) {
        new NettyServer(8889).run();
    }
}