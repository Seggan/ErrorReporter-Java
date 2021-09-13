package io.github.seggan.errorreporter;

import java.util.concurrent.Callable;

/**
 * A class halfway between a {@link Runnable} and a {@link Callable}. The {@link #run()} returns void
 * but <i>can</i> throw a checked exception
 */
@FunctionalInterface
public interface ThrowingRunnable {

    /**
     * The "main" method, running the code
     *
     * @throws Exception if any exception is thrown
     */
    void run() throws Exception;
}
