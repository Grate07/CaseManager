package me.grate.casemanager.integration;

import me.grate.casemanager.CaseManager;
import me.grate.casemanager.casefile.CaseEvidenceService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class EvidenceIntegrationService {

    private final CaseManager plugin;
    private final IntegrationManager integrationManager;
    private final CaseEvidenceService evidenceService;

    public EvidenceIntegrationService(
            CaseManager plugin,
            IntegrationManager integrationManager,
            CaseEvidenceService evidenceService
    ) {
        this.plugin = plugin;
        this.integrationManager = integrationManager;
        this.evidenceService = evidenceService;
    }

    /*
     * ============================================================
     * CoreProtect
     * ============================================================
     */

    public CompletableFuture<Boolean> collectCoreProtectEvidence(
            long caseId,
            UUID targetUuid,
            String targetName,
            int timeSeconds,
            int limit,
            UUID addedByUuid,
            String addedByName
    ) {

        if (!integrationManager.isCoreProtectAvailable()) {
            return CompletableFuture.completedFuture(false);
        }

        if (caseId <= 0 ||
                targetName == null ||
                targetName.isBlank() ||
                addedByUuid == null ||
                timeSeconds <= 0 ||
                limit <= 0) {

            return CompletableFuture.completedFuture(false);
        }

        CoreProtectIntegration coreProtect =
                integrationManager.getCoreProtect();

        if (coreProtect == null) {
            return CompletableFuture.completedFuture(false);
        }

        return coreProtect
                .lookupPlayerHistory(
                        targetName,
                        timeSeconds,
                        limit
                )
                .thenCompose(records -> {

                    if (records == null ||
                            records.isEmpty()) {

                        return CompletableFuture.completedFuture(false);
                    }

                    return addCoreProtectRecords(
                            caseId,
                            records,
                            addedByUuid,
                            addedByName
                    );
                })
                .exceptionally(exception -> {

                    plugin.getLogger().warning(
                            "Failed to collect CoreProtect evidence: " +
                                    getRootMessage(exception)
                    );

                    return false;
                });
    }

    private CompletableFuture<Boolean> addCoreProtectRecords(
            long caseId,
            List<CoreProtectIntegration.CoreProtectRecord> records,
            UUID addedByUuid,
            String addedByName
    ) {

        CompletableFuture<Boolean> result =
                CompletableFuture.completedFuture(false);

        for (
                CoreProtectIntegration.CoreProtectRecord record :
                records
        ) {

            if (record == null) {
                continue;
            }

            result =
                    result.thenCompose(
                            alreadyAdded ->
                                    addEvidence(
                                            caseId,
                                            "COREPROTECT",
                                            record.toEvidenceText(),
                                            addedByUuid,
                                            addedByName
                                    ).thenApply(
                                            added ->
                                                    alreadyAdded || added
                                    )
                    );
        }

        return result;
    }

    /*
     * ============================================================
     * LiteBans
     * ============================================================
     */

    public CompletableFuture<Boolean> collectLiteBansEvidence(
            long caseId,
            UUID targetUuid,
            String targetName,
            int limit,
            UUID addedByUuid,
            String addedByName
    ) {

        if (!integrationManager.isLiteBansAvailable()) {
            return CompletableFuture.completedFuture(false);
        }

        if (caseId <= 0 ||
                targetUuid == null ||
                addedByUuid == null ||
                limit <= 0) {

            return CompletableFuture.completedFuture(false);
        }

        LiteBansIntegration liteBans =
                integrationManager.getLiteBans();

        if (liteBans == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture
                .supplyAsync(
                        () ->
                                liteBans.getPunishmentHistory(
                                        targetUuid,
                                        limit
                                )
                )
                .thenCompose(history -> {

                    if (history == null ||
                            history.isEmpty()) {

                        return CompletableFuture.completedFuture(false);
                    }

                    return addLiteBansRecords(
                            caseId,
                            history,
                            addedByUuid,
                            addedByName
                    );
                })
                .exceptionally(exception -> {

                    plugin.getLogger().warning(
                            "Failed to collect LiteBans evidence: " +
                                    getRootMessage(exception)
                    );

                    return false;
                });
    }

    private CompletableFuture<Boolean> addLiteBansRecords(
            long caseId,
            List<LiteBansIntegration.LiteBansPunishment> records,
            UUID addedByUuid,
            String addedByName
    ) {

        CompletableFuture<Boolean> result =
                CompletableFuture.completedFuture(false);

        for (
                LiteBansIntegration.LiteBansPunishment record :
                records
        ) {

            if (record == null) {
                continue;
            }

            result =
                    result.thenCompose(
                            alreadyAdded ->
                                    addEvidence(
                                            caseId,
                                            "LITEBANS",
                                            record.toEvidenceText(),
                                            addedByUuid,
                                            addedByName
                                    ).thenApply(
                                            added ->
                                                    alreadyAdded || added
                                    )
                    );
        }

        return result;
    }

    /*
     * ============================================================
     * Vulcan
     * ============================================================
     */

    public CompletableFuture<Boolean> collectVulcanEvidence(
            long caseId,
            UUID targetUuid,
            String targetName,
            UUID addedByUuid,
            String addedByName
    ) {

        if (!integrationManager.isVulcanAvailable()) {
            return CompletableFuture.completedFuture(false);
        }

        if (caseId <= 0 ||
                targetUuid == null ||
                addedByUuid == null) {

            return CompletableFuture.completedFuture(false);
        }

        VulcanIntegration vulcan =
                integrationManager.getVulcan();

        if (vulcan == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture
                .supplyAsync(
                        () ->
                                vulcan.createEvidenceSummary(
                                        targetUuid,
                                        targetName
                                )
                )
                .thenCompose(summary -> {

                    /*
                     * Do not save empty or placeholder evidence.
                     */
                    if (summary == null ||
                            summary.isBlank()) {

                        return CompletableFuture.completedFuture(false);
                    }

                    /*
                     * The current Vulcan integration only returns
                     * usable data when its developer API is actually
                     * available.
                     */
                    if (summary.contains(
                            "Developer API data collection is not configured"
                    )) {

                        return CompletableFuture.completedFuture(false);
                    }

                    if (summary.contains(
                            "Vulcan integration is unavailable"
                    )) {

                        return CompletableFuture.completedFuture(false);
                    }

                    return addEvidence(
                            caseId,
                            "ANTI_CHEAT",
                            summary,
                            addedByUuid,
                            addedByName
                    );
                })
                .exceptionally(exception -> {

                    plugin.getLogger().warning(
                            "Failed to collect Vulcan evidence: " +
                                    getRootMessage(exception)
                    );

                    return false;
                });
    }

    /*
     * ============================================================
     * Collect everything
     * ============================================================
     */

    public CompletableFuture<Boolean> collectAllEvidence(
            long caseId,
            UUID targetUuid,
            String targetName,
            int coreProtectTimeSeconds,
            int coreProtectLimit,
            int punishmentLimit,
            UUID addedByUuid,
            String addedByName
    ) {

        /*
         * Each integration is isolated.
         *
         * If CoreProtect fails, LiteBans still gets a chance.
         * If LiteBans fails, Vulcan still gets a chance.
         */
        return collectCoreProtectEvidence(
                caseId,
                targetUuid,
                targetName,
                coreProtectTimeSeconds,
                coreProtectLimit,
                addedByUuid,
                addedByName
        )
        .exceptionally(
                exception -> {

                    plugin.getLogger().warning(
                            "CoreProtect evidence collection failed: " +
                                    getRootMessage(exception)
                    );

                    return false;
                }
        )
        .thenCompose(coreProtectSuccess ->

                collectLiteBansEvidence(
                        caseId,
                        targetUuid,
                        targetName,
                        punishmentLimit,
                        addedByUuid,
                        addedByName
                )
                .exceptionally(
                        exception -> {

                            plugin.getLogger().warning(
                                    "LiteBans evidence collection failed: " +
                                            getRootMessage(exception)
                            );

                            return false;
                        }
                )
                .thenCompose(liteBansSuccess ->

                        collectVulcanEvidence(
                                caseId,
                                targetUuid,
                                targetName,
                                addedByUuid,
                                addedByName
                        )
                        .exceptionally(
                                exception -> {

                                    plugin.getLogger().warning(
                                            "Vulcan evidence collection failed: " +
                                                    getRootMessage(exception)
                                    );

                                    return false;
                                }
                        )
                        .thenApply(
                                vulcanSuccess ->
                                        coreProtectSuccess ||
                                                liteBansSuccess ||
                                                vulcanSuccess
                        )
                )
        );
    }

    /*
     * ============================================================
     * Evidence database helper
     * ============================================================
     */

    private CompletableFuture<Boolean> addEvidence(
            long caseId,
            String type,
            String content,
            UUID addedByUuid,
            String addedByName
    ) {

        if (caseId <= 0 ||
                type == null ||
                type.isBlank() ||
                content == null ||
                content.isBlank() ||
                addedByUuid == null) {

            return CompletableFuture.completedFuture(false);
        }

        return evidenceService
                .addEvidence(
                        caseId,
                        addedByUuid,
                        addedByName,
                        type,
                        content
                )
                .thenApply(
                        evidence -> evidence != null
                )
                .exceptionally(
                        exception -> {

                            plugin.getLogger().warning(
                                    "Failed to add integration evidence: " +
                                            getRootMessage(exception)
                            );

                            return false;
                        }
                );
    }

    /*
     * ============================================================
     * Availability
     * ============================================================
     */

    public boolean isCoreProtectAvailable() {

        return integrationManager
                .isCoreProtectAvailable();
    }

    public boolean isVulcanAvailable() {

        return integrationManager
                .isVulcanAvailable();
    }

    public boolean isLiteBansAvailable() {

        return integrationManager
                .isLiteBansAvailable();
    }

    public String getAvailableIntegrations() {

        StringBuilder builder =
                new StringBuilder();

        if (isCoreProtectAvailable()) {

            builder.append(
                    "CoreProtect"
            );
        }

        if (isVulcanAvailable()) {

            appendSeparator(builder);

            builder.append(
                    "Vulcan"
            );
        }

        if (isLiteBansAvailable()) {

            appendSeparator(builder);

            builder.append(
                    "LiteBans"
            );
        }

        if (builder.isEmpty()) {

            return "None";
        }

        return builder.toString();
    }

    private void appendSeparator(
            StringBuilder builder
    ) {

        if (!builder.isEmpty()) {

            builder.append(", ");
        }
    }

    /*
     * ============================================================
     * Error handling
     * ============================================================
     */

    private String getRootMessage(
            Throwable throwable
    ) {

        if (throwable == null) {
            return "Unknown error";
        }

        Throwable current =
                throwable;

        while (current.getCause() != null) {

            current =
                    current.getCause();
        }

        String message =
                current.getMessage();

        if (message == null ||
                message.isBlank()) {

            return current
                    .getClass()
                    .getSimpleName();
        }

        return message;
    }
}