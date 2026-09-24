package me.grate.casemanager.storage;

import java.util.concurrent.CompletableFuture;

public interface EvidenceStorage {

    CompletableFuture<StorageUploadResult> upload(
            String path,
            byte[] data,
            String mimeType
    );

    CompletableFuture<Boolean> delete(
            String path
    );

    CompletableFuture<String> createSignedUrl(
            String path,
            long expiresInSeconds
    );

    String getPublicUrl(
            String path
    );

    String getProviderName();

    boolean isAvailable();


    record StorageUploadResult(
            String path,
            long fileSize,
            String mimeType
    ) {
    }
}