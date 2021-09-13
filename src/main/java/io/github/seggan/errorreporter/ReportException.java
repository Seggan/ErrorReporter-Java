package io.github.seggan.errorreporter;

/**
 * An {@link RuntimeException} thrown if there is an error while
 * executing {@link ErrorReporter#sendError(Throwable, boolean)}
 */
public class ReportException extends RuntimeException {

    public ReportException(String message) {
        super(message);
    }

    public ReportException(Throwable cause) {
        super(cause);
    }
}
