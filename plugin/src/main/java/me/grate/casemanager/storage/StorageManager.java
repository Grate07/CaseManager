package me.grate.casemanager.storage;

import me.grate.casemanager.CaseManager;

import java.util.Locale;

public final class StorageManager {

    private final CaseManager plugin;

    private EvidenceStorage storage;

    public StorageManager(
            CaseManager plugin
    ) {
        this.plugin = plugin;
    }

    public void initialize() {

        String provider =
                plugin.getConfig().getString(
                        "storage.provider",
                        "supabase"
                );

        if (provider == null ||
                provider.isBlank()) {

            provider = "supabase";
        }

        provider =
                provider.trim()
                        .toLowerCase(Locale.ROOT);

        switch (provider) {

            case "supabase" -> {

                storage =
                        new SupabaseStorage(plugin);
            }

            default -> {

                throw new IllegalStateException(
                        "Unsupported evidence storage provider: "
                                + provider
                );
            }
        }

        if (storage.isAvailable()) {

            plugin.getLogger().info(
                    "Evidence storage provider: "
                            + storage.getProviderName()
            );

        } else {

            plugin.getLogger().warning(
                    "Evidence storage provider '"
                            + storage.getProviderName()
                            + "' is configured but not available."
            );
        }
    }

    public EvidenceStorage getStorage() {

        if (storage == null) {

            throw new IllegalStateException(
                    "StorageManager has not been initialized."
            );
        }

        return storage;
    }

    public boolean isAvailable() {

        return storage != null
                && storage.isAvailable();
    }

    public String getProviderName() {

        if (storage == null) {

            return "none";
        }

        return storage.getProviderName();
    }

    public void shutdown() {

        storage = null;
    }
}