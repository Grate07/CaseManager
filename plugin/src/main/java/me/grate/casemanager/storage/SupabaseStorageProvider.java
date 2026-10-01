package me.grate.casemanager.storage;

import me.grate.casemanager.CaseManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SupabaseStorageProvider
        implements StorageProvider {

    private static final Pattern SIGNED_URL_PATTERN =
            Pattern.compile(
                    "\"signedURL\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\""
                            + "|"
                            + "\"signedUrl\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\""
            );

    private final CaseManager plugin;

    private final HttpClient httpClient;

    private String baseUrl;
    private String secretKey;
    private String bucket;

    private boolean available;

    public SupabaseStorageProvider(
            CaseManager plugin
    ) {

        this.plugin = plugin;

        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(15)
                        )
                        .build();
    }

    @Override
    public String getName() {

        return "Supabase Storage";
    }

    @Override
    public boolean isAvailable() {

        return available;
    }

    @Override
    public void initialize() {

        available = false;

        baseUrl =
                resolve(
                        "storage.supabase.url",
                        "CASEMANAGER_SUPABASE_URL"
                );

        secretKey =
                resolve(
                        "storage.supabase.secret-key",
                        "CASEMANAGER_SUPABASE_SECRET_KEY"
                );

        bucket =
                plugin.getConfig()
                        .getString(
                                "storage.bucket",
                                "case-evidence"
                        );

        if (baseUrl.isBlank()) {

            plugin.getLogger().warning(
                    "Supabase Storage URL is not configured."
            );

            plugin.getLogger().warning(
                    "Set storage.supabase.url or CASEMANAGER_SUPABASE_URL."
            );

            return;
        }

        if (secretKey.isBlank()) {

            plugin.getLogger().warning(
                    "Supabase Storage secret key is not configured."
            );

            plugin.getLogger().warning(
                    "Set storage.supabase.secret-key or CASEMANAGER_SUPABASE_SECRET_KEY."
            );

            return;
        }

        if (bucket == null ||
                bucket.isBlank()) {

            plugin.getLogger().warning(
                    "Supabase Storage bucket is not configured."
            );

            return;
        }

        baseUrl =
                StorageProvider.normalizeBaseUrl(
                        baseUrl
                );

        bucket =
                bucket.trim();

        available = true;

        plugin.getLogger().info(
                "Supabase Storage provider initialized for bucket '"
                        + bucket
                        + "'."
        );
    }

    @Override
    public StorageObject upload(
            String path,
            byte[] data,
            String contentType
    ) throws IOException, InterruptedException {

        requireAvailable();

        if (data == null ||
                data.length == 0) {

            throw new IllegalArgumentException(
                    "Storage data cannot be empty."
            );
        }

        String safePath =
                StorageProvider.encodePath(
                        path
                );

        String url =
                objectUrl(
                        safePath
                );

        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(url)
                        )
                        .timeout(
                                Duration.ofMinutes(5)
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
                                contentType == null ||
                                        contentType.isBlank()
                                        ? "application/octet-stream"
                                        : contentType
                        )
                        .header(
                                "x-upsert",
                                "false"
                        )
                        .PUT(
                                HttpRequest.BodyPublishers
                                        .ofByteArray(data)
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        ensureSuccess(
                response,
                "upload"
        );

        return new StorageObject(
                path,
                data.length,
                contentType
        );
    }

    @Override
    public String createSignedUrl(
            String path,
            int expiresInSeconds
    ) throws IOException, InterruptedException {

        requireAvailable();

        if (expiresInSeconds <= 0) {

            throw new IllegalArgumentException(
                    "Signed URL expiry must be greater than zero."
            );
        }

        String safePath =
                StorageProvider.encodePath(
                        path
                );

        String url =
                baseUrl
                        + "/storage/v1/object/sign/"
                        + StorageProvider.encodeComponent(
                                bucket
                        )
                        + "/"
                        + safePath;

        String body =
                "{\"expiresIn\":"
                        + expiresInSeconds
                        + "}";

        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(url)
                        )
                        .timeout(
                                Duration.ofSeconds(30)
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
                                HttpRequest.BodyPublishers
                                        .ofString(body)
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        ensureSuccess(
                response,
                "signed URL creation"
        );

        String signedUrl =
                extractSignedUrl(
                        response.body()
                );

        if (
                signedUrl.startsWith("http://") ||
                signedUrl.startsWith("https://")
        ) {

            return signedUrl;
        }

        if (signedUrl.startsWith("/")) {

            return baseUrl + signedUrl;
        }

        return baseUrl + "/" + signedUrl;
    }

    @Override
    public void delete(
            String path
    ) throws IOException, InterruptedException {

        requireAvailable();

        String safePath =
                StorageProvider.encodePath(
                        path
                );

        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        objectUrl(
                                                safePath
                                        )
                                )
                        )
                        .timeout(
                                Duration.ofSeconds(30)
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

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        ensureSuccess(
                response,
                "delete"
        );
    }

    @Override
    public void shutdown() {

        available = false;
    }

    private String objectUrl(
            String encodedPath
    ) {

        return baseUrl
                + "/storage/v1/object/"
                + StorageProvider.encodeComponent(
                        bucket
                )
                + "/"
                + encodedPath;
    }

    private void requireAvailable() {

        if (!available) {

            throw new IllegalStateException(
                    "Supabase Storage provider is not available."
            );
        }
    }

    private void ensureSuccess(
            HttpResponse<String> response,
            String operation
    ) throws IOException {

        int status =
                response.statusCode();

        if (
                status < 200 ||
                status >= 300
        ) {

            String body =
                    response.body();

            if (
                    body != null &&
                    body.length() > 500
            ) {

                body =
                        body.substring(
                                0,
                                500
                        );
            }

            throw new IOException(
                    "Supabase Storage "
                            + operation
                            + " failed with HTTP "
                            + status
                            + ": "
                            + body
            );
        }
    }

    private String extractSignedUrl(
            String json
    ) throws IOException {

        Matcher matcher =
                SIGNED_URL_PATTERN.matcher(
                        json == null
                                ? ""
                                : json
                );

        if (!matcher.find()) {

            throw new IOException(
                    "Supabase did not return a signed URL."
            );
        }

        String value =
                matcher.group(1) != null
                        ? matcher.group(1)
                        : matcher.group(2);

        return value
                .replace(
                        "\\/",
                        "/"
                )
                .replace(
                        "\\\"",
                        "\""
                )
                .replace(
                        "\\\\",
                        "\\"
                );
    }

    private String resolve(
            String configPath,
            String environmentVariable
    ) {

        String environmentValue =
                System.getenv(
                        environmentVariable
                );

        if (
                environmentValue != null &&
                !environmentValue.isBlank()
        ) {

            return environmentValue.trim();
        }

        String configValue =
                plugin.getConfig()
                        .getString(
                                configPath,
                                ""
                        );

        return configValue == null
                ? ""
                : configValue.trim();
    }
}