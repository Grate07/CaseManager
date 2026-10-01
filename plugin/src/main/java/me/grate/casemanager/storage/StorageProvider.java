package me.grate.casemanager.storage;

public interface StorageProvider {

    String getName();

    boolean isAvailable();

    void initialize();

    StorageObject upload(
            String path,
            byte[] data,
            String contentType
    ) throws java.io.IOException, InterruptedException;

    String createSignedUrl(
            String path,
            int expiresInSeconds
    ) throws java.io.IOException, InterruptedException;

    void delete(
            String path
    ) throws java.io.IOException, InterruptedException;

    void shutdown();

    record StorageObject(
            String path,
            long size,
            String contentType
    ) {
    }

    static String normalizeBaseUrl(String url) {

        if (url == null || url.isBlank()) {
            return "";
        }

        String value = url.trim();

        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        return value;
    }

    static String encodePath(String path) {

        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "Storage path cannot be empty."
            );
        }

        StringBuilder result = new StringBuilder();

        for (String part : path.split("/", -1)) {

            if (part.isEmpty()) {
                continue;
            }

            if (result.length() > 0) {
                result.append('/');
            }

            result.append(
                    encodeComponent(part)
            );
        }

        return result.toString();
    }

    static String encodeComponent(String value) {

        StringBuilder result = new StringBuilder();

        for (byte b :
                value.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                )) {

            int c = b & 0xFF;

            if (
                    (c >= 'a' && c <= 'z') ||
                    (c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') ||
                    c == '-' ||
                    c == '_' ||
                    c == '.' ||
                    c == '~'
            ) {

                result.append((char) c);

            } else {

                result.append('%');

                result.append(
                        String.format("%02X", c)
                );
            }
        }

        return result.toString();
    }
}