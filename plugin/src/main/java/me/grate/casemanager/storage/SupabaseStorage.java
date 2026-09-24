package me.grate.casemanager.storage;

import me.grate.casemanager.CaseManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class SupabaseStorage implements EvidenceStorage {

    private final CaseManager plugin;

    private final HttpClient httpClient;

    private final String supabaseUrl;
    private final String secretKey;
    private final String bucket;

    private final boolean enabled;

    public SupabaseStorage(
            CaseManager plugin
    ) {
        this.plugin = plugin;

        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                java.time.Duration.ofSeconds(10)
                        )
                        .build();

        this.enabled =
                plugin.getConfig().getBoolean(
                        "storage.enabled",
                        true
                );

        this.bucket =
                plugin.getConfig().getString(
                        "storage.bucket",
                        "case-evidence"
                );

        /*
         * The URL and secret key can be supplied through
         * environment variables.
         *
         * This prevents a real Supabase secret from having
         * to be committed into GitHub.
         *
         * Environment variables:
         *
         * CASEMANAGER_SUPABASE_URL
         * CASEMANAGER_SUPABASE_SECRET_KEY
         */

        String configuredUrl =
                plugin.getConfig().getString(
                        "storage.supabase.url",
                        ""
                );

        String configuredSecret =
                plugin.getConfig().getString(
                        "storage.supabase.secret-key",
                        ""
                );

        String environmentUrl =
                System.getenv(
                        "CASEMANAGER_SUPABASE_URL"
                );

        String environmentSecret =
                System.getenv(
                        "CASEMANAGER_SUPABASE_SECRET_KEY"
                );

        if (environmentUrl != null &&
                !environmentUrl.isBlank()) {

            configuredUrl =
                    environmentUrl;
        }

        if (environmentSecret != null &&
                !environmentSecret.isBlank()) {

            configuredSecret =
                    environmentSecret;
        }

        this.supabaseUrl =
                normalizeBaseUrl(
                        configuredUrl
                );

        this.secretKey =
                configuredSecret == null
                        ? ""
                        : configuredSecret.trim();

        if (!enabled) {

            plugin.getLogger().info(
                    "Supabase Storage is disabled in configuration."
            );

        } else if (supabaseUrl.isBlank()) {

            plugin.getLogger().warning(
                    "Supabase Storage is enabled but no Supabase URL "
                            + "has been configured."
            );

        } else if (secretKey.isBlank()) {

            plugin.getLogger().warning(
                    "Supabase Storage is enabled but no Supabase "
                            + "secret key has been configured."
            );

        } else {

            plugin.getLogger().info(
                    "Supabase Storage provider initialized."
            );
        }
    }

    @Override
    public CompletableFuture<StorageUploadResult> upload(
            String path,
            byte[] data,
            String mimeType
    ) {

        if (!isAvailable()) {

            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "Supabase Storage is not configured."
                    )
            );
        }

        if (path == null ||
                path.isBlank()) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Storage path cannot be empty."
                    )
            );
        }

        if (data == null) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Storage data cannot be null."
                    )
            );
        }

        if (mimeType == null ||
                mimeType.isBlank()) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "MIME type cannot be empty."
                    )
            );
        }

        String normalizedPath =
                normalizeObjectPath(path);

        String url =
                supabaseUrl +
                        "/storage/v1/object/" +
                        encodePath(bucket) +
                        "/" +
                        encodePath(normalizedPath);

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(
                                java.time.Duration.ofMinutes(5)
                        )
                        .header(
                                "Authorization",
                                "Bearer " + secretKey
                        )
                        .header(
                                "apikey",
                                secretKey
                        )
                        .header(
                                "Content-Type",
                                mimeType
                        )
                        .header(
                                "x-upsert",
                                "false"
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofByteArray(
                                        data
                                )
                        )
                        .build();

        return httpClient
                .sendAsync(
                        request,
                        HttpResponse.BodyHandlers.ofString(
                                StandardCharsets.UTF_8
                        )
                )
                .orTimeout(
                        5,
                        TimeUnit.MINUTES
                )
                .thenApply(response -> {

                    int status =
                            response.statusCode();

                    if (status < 200 ||
                            status >= 300) {

                        throw new IllegalStateException(
                                "Supabase Storage upload failed "
                                        + "(HTTP "
                                        + status
                                        + "): "
                                        + response.body()
                        );
                    }

                    return new StorageUploadResult(
                            normalizedPath,
                            data.length,
                            mimeType
                    );
                });
    }

    @Override
    public CompletableFuture<Boolean> delete(
            String path
    ) {

        if (!isAvailable()) {

            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "Supabase Storage is not configured."
                    )
            );
        }

        if (path == null ||
                path.isBlank()) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Storage path cannot be empty."
                    )
            );
        }

        String normalizedPath =
                normalizeObjectPath(path);

        String url =
                supabaseUrl +
                        "/storage/v1/object/" +
                        encodePath(bucket) +
                        "/" +
                        encodePath(normalizedPath);

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(
                                java.time.Duration.ofSeconds(30)
                        )
                        .header(
                                "Authorization",
                                "Bearer " + secretKey
                        )
                        .header(
                                "apikey",
                                secretKey
                        )
                        .DELETE()
                        .build();

        return httpClient
                .sendAsync(
                        request,
                        HttpResponse.BodyHandlers.ofString(
                                StandardCharsets.UTF_8
                        )
                )
                .orTimeout(
                        30,
                        TimeUnit.SECONDS
                )
                .thenApply(response -> {

                    int status =
                            response.statusCode();

                    if (status >= 200 &&
                            status < 300) {

                        return true;
                    }

                    if (status == 404) {

                        return false;
                    }

                    throw new IllegalStateException(
                            "Supabase Storage delete failed "
                                    + "(HTTP "
                                    + status
                                    + "): "
                                    + response.body()
                    );
                });
    }

    @Override
    public CompletableFuture<String> createSignedUrl(
            String path,
            long expiresInSeconds
    ) {

        if (!isAvailable()) {

            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "Supabase Storage is not configured."
                    )
            );
        }

        if (path == null ||
                path.isBlank()) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Storage path cannot be empty."
                    )
            );
        }

        if (expiresInSeconds <= 0) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Signed URL expiration must be greater than zero."
                    )
            );
        }

        String normalizedPath =
                normalizeObjectPath(path);

        String url =
                supabaseUrl +
                        "/storage/v1/object/sign/" +
                        encodePath(bucket) +
                        "/" +
                        encodePath(normalizedPath);

        String body =
                "{\"expiresIn\":" +
                        expiresInSeconds +
                        "}";

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(
                                java.time.Duration.ofSeconds(30)
                        )
                        .header(
                                "Authorization",
                                "Bearer " + secretKey
                        )
                        .header(
                                "apikey",
                                secretKey
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        body,
                                        StandardCharsets.UTF_8
                                )
                        )
                        .build();

        return httpClient
                .sendAsync(
                        request,
                        HttpResponse.BodyHandlers.ofString(
                                StandardCharsets.UTF_8
                        )
                )
                .orTimeout(
                        30,
                        TimeUnit.SECONDS
                )
                .thenApply(response -> {

                    int status =
                            response.statusCode();

                    if (status < 200 ||
                            status >= 300) {

                        throw new IllegalStateException(
                                "Supabase Storage signed URL "
                                        + "creation failed "
                                        + "(HTTP "
                                        + status
                                        + "): "
                                        + response.body()
                        );
                    }

                    return extractSignedUrl(
                            response.body()
                    );
                });
    }

    @Override
    public String getPublicUrl(
            String path
    ) {

        if (path == null ||
                path.isBlank()) {

            throw new IllegalArgumentException(
                    "Storage path cannot be empty."
            );
        }

        String normalizedPath =
                normalizeObjectPath(path);

        return supabaseUrl +
                "/storage/v1/object/public/" +
                encodePath(bucket) +
                "/" +
                encodePath(normalizedPath);
    }

    @Override
    public String getProviderName() {
        return "supabase";
    }

    @Override
    public boolean isAvailable() {

        return enabled
                && !supabaseUrl.isBlank()
                && !secretKey.isBlank()
                && !bucket.isBlank();
    }

    private String extractSignedUrl(
            String responseBody
    ) {

        /*
         * Supabase returns a JSON object containing a signed
         * URL. We intentionally avoid adding a JSON dependency
         * just for this small response.
         */

        String key =
                "\"signedURL\"";

        int keyIndex =
                responseBody.indexOf(key);

        if (keyIndex < 0) {

            key = "\"signedUrl\"";

            keyIndex =
                    responseBody.indexOf(key);
        }

        if (keyIndex < 0) {

            throw new IllegalStateException(
                    "Supabase did not return a signed URL: "
                            + responseBody
            );
        }

        int colon =
                responseBody.indexOf(
                        ':',
                        keyIndex + key.length()
                );

        if (colon < 0) {

            throw new IllegalStateException(
                    "Invalid Supabase signed URL response."
            );
        }

        int firstQuote =
                responseBody.indexOf(
                        '"',
                        colon + 1
                );

        if (firstQuote < 0) {

            throw new IllegalStateException(
                    "Invalid Supabase signed URL response."
            );
        }

        int secondQuote =
                findClosingQuote(
                        responseBody,
                        firstQuote + 1
                );

        if (secondQuote < 0) {

            throw new IllegalStateException(
                    "Invalid Supabase signed URL response."
            );
        }

        String signedUrl =
                responseBody.substring(
                        firstQuote + 1,
                        secondQuote
                );

        return supabaseUrl +
                signedUrl;
    }

    private int findClosingQuote(
            String value,
            int start
    ) {

        boolean escaped = false;

        for (int i = start;
             i < value.length();
             i++) {

            char character =
                    value.charAt(i);

            if (escaped) {

                escaped = false;
                continue;
            }

            if (character == '\\') {

                escaped = true;
                continue;
            }

            if (character == '"') {

                return i;
            }
        }

        return -1;
    }

    private String normalizeBaseUrl(
            String value
    ) {

        if (value == null) {
            return "";
        }

        String normalized =
                value.trim();

        while (normalized.endsWith("/")) {

            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 1
                    );
        }

        return normalized;
    }

    private String normalizeObjectPath(
            String path
    ) {

        String normalized =
                path.trim()
                        .replace('\\', '/');

        while (normalized.startsWith("/")) {

            normalized =
                    normalized.substring(1);
        }

        while (normalized.contains("//")) {

            normalized =
                    normalized.replace(
                            "//",
                            "/"
                    );
        }

        if (normalized.isBlank()) {

            throw new IllegalArgumentException(
                    "Storage path cannot be empty."
            );
        }

        if (normalized.contains("..")) {

            throw new IllegalArgumentException(
                    "Storage path cannot contain '..'."
            );
        }

        return normalized;
    }

    private String encodePath(
            String value
    ) {

        /*
         * Encode individual path segments while preserving
         * the '/' separators between folders.
         */

        String[] parts =
                value.split("/");

        StringBuilder result =
                new StringBuilder();

        for (int i = 0;
             i < parts.length;
             i++) {

            if (i > 0) {
                result.append('/');
            }

            result.append(
                    java.net.URLEncoder
                            .encode(
                                    parts[i],
                                    StandardCharsets.UTF_8
                            )
                            .replace(
                                    "+",
                                    "%20"
                            )
            );
        }

        return result.toString();
    }
}