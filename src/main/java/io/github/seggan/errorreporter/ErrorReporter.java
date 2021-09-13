package io.github.seggan.errorreporter;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ErrorReporter {

    private static final URL URL;

    static {
        try {
            URL = new URL("https://error-reports.seggan.workers.dev");
        } catch (MalformedURLException e) {
            sneakyThrow(e);
            throw null;
        }
    }

    private final String user;
    private final String repo;
    private final Supplier<String> versionSupplier;
    private Predicate<JsonObject> preSend;

    public ErrorReporter(@NotNull String user, @NotNull String repo, @NotNull Supplier<String> versionSupplier) {
        this.user = user;
        this.repo = repo;
        this.versionSupplier = versionSupplier;
    }

    public static void main(String[] args) {
        new ErrorReporter("", "", () -> "No version").sendError(new IOException(), false);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    /**
     * Reports the {@link Throwable} given and optionally retrhows the {@link Throwable}
     *
     * @param throwable the {@link Throwable} to report
     * @param rethrow whether the method should rethrow the {@link Throwable}
     * @throws ReportException if the report failed
     */
    public void sendError(@NotNull Throwable throwable, boolean rethrow) throws ReportException {
        try {
            HttpURLConnection connection = (HttpURLConnection) URL.openConnection();
            JsonObject object = new JsonObject();
            String version = versionSupplier.get();
            object.add("Version", new JsonPrimitive(version));
            try (StringWriter writer = new StringWriter();
                 PrintWriter printWriter = new PrintWriter(writer)) {
                throwable.printStackTrace(printWriter);
                String asString = writer.toString();
                object.add("Error", new JsonPrimitive("```\n" + asString + "\n```"));
                object.add("Hashcode", new JsonPrimitive(Integer.toHexString(asString.hashCode()) + '-' + Integer.toHexString(version.hashCode())));
            }
            if (preSend != null && preSend.test(object)) {
                if (rethrow) {
                    sneakyThrow(throwable);
                }
                return;
            }
            String s = object.toString();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Length", Integer.toString(s.length()));
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/42.0.2311.135 Safari/537.36 Edge/12.246");
            connection.setRequestProperty("User", user);
            connection.setRequestProperty("Repo", repo);
            connection.setRequestProperty("Version", "1");
            connection.getOutputStream().write(s.getBytes(StandardCharsets.UTF_8));
            connection.getOutputStream().flush();
            connection.connect();
            int code = connection.getResponseCode();
            switch (code) {
                case 500:
                    throw new ReportException("Server error");
                case 404:
                    throw new ReportException("User/Repository not found");
                case 410:
                    throw new ReportException("Repository has issues disabled");
                case 503:
                    throw new ReportException("GitHub service down");
                case 400:
                    throw new ReportException("Bad request; maybe using wrong API version?");
            }
            connection.disconnect();
        } catch (IOException e) {
            throw new ReportException(e);
        }

        if (rethrow) {
            sneakyThrow(throwable);
        }
    }

    /**
     * Reports the {@link Throwable} and rethrows it
     *
     * @param throwable the {@link Throwable to report}
     */
    public void sendError(@NotNull Throwable throwable) throws ReportException {
        sendError(throwable, true);
    }

    /**
     * Executes the given code, or else reports the thrown {@link Exception}
     *
     * @param runnable the {@link ThrowingRunnable} to run
     * @param rethrow whether the method should rethrow the {@link Exception} thrown, if any
     */
    public void executeOrElseReport(@NotNull ThrowingRunnable runnable, boolean rethrow) {
        try {
            runnable.run();
        } catch (Exception e) {
            sendError(e, rethrow);
        }
    }

    /**
     * Executes the given code, or else reports the thrown {@link Exception} and retrhows it
     *
     * @param runnable the {@link ThrowingRunnable} to run
     */
    public void executeOrElseReport(@NotNull ThrowingRunnable runnable) {
        executeOrElseReport(runnable, true);
    }

    /**
     * Sets a {@link Predicate} to use before sending the data. The predicate
     * returns whether it should send or not
     *
     * @param preSend the predicate
     * @return this object
     */
    public ErrorReporter preSend(@Nullable Predicate<JsonObject> preSend) {
        this.preSend = preSend;
        return this;
    }
}
