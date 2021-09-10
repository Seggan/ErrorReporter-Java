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

    public void sendError(@NotNull Throwable throwable, boolean rethrow) {
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
            }
            connection.disconnect();
        } catch (IOException e) {
            sneakyThrow(e);
        }

        if (rethrow) {
            sneakyThrow(throwable);
        }
    }

    public void sendError(@NotNull Throwable throwable) {
        sendError(throwable, true);
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
