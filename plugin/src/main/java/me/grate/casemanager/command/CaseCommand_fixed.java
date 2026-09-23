package me.grate.casemanager.command;

import me.grate.casemanager.CaseManager;
import me.grate.casemanager.casefile.Case;
import me.grate.casemanager.casefile.CaseEvidence;
import me.grate.casemanager.casefile.CaseInvestigator;
import me.grate.casemanager.casefile.CaseNote;
import me.grate.casemanager.casefile.CaseStatus;
import me.grate.casemanager.casefile.CaseTimelineEntry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CaseCommand implements CommandExecutor, TabCompleter {

    private final CaseManager plugin;

    private final List<String> evidenceTypes = Arrays.asList(
            "SCREENSHOT",
            "VIDEO",
            "CHAT_LOG",
            "ANTI_CHEAT",
            "COREPROTECT",
            "LITEBANS",
            "OBSERVATION",
            "OTHER"
    );

    private final List<String> evidenceCollectionTypes =
            Arrays.asList(
                    "all",
                    "coreprotect",
                    "vulcan",
                    "litebans"
            );

    private final List<String> statusNames = Arrays.asList(
            "OPEN",
            "INVESTIGATING",
            "WAITING_FOR_EVIDENCE",
            "ESCALATED",
            "RESOLVED",
            "CLOSED"
    );

    public CaseCommand(CaseManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand =
                args[0].toLowerCase();

        switch (subCommand) {

            case "create":
                handleCreate(sender, args);
                break;

            case "view":
                handleView(sender, args);
                break;

            case "list":
                handleList(sender, args);
                break;

            case "timeline":
                handleTimeline(sender, args);
                break;

            case "note":
                handleNote(sender, args);
                break;

            case "evidence":
                handleEvidence(sender, args);
                break;

            case "assign":
                handleAssign(sender, args);
                break;

            case "unassign":
                handleUnassign(sender, args);
                break;

            case "investigators":
                handleInvestigators(sender, args);
                break;

            case "status":
                handleStatus(sender, args);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleCreate(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission(
                "casemanager.create"
        )) {

            sendNoPermission(sender);
            return;
        }

        if (!(sender instanceof Player player)) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Only players can create cases."
            );

            return;
        }

        if (args.length < 3) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Usage: /case create <player> <reason>"
            );

            return;
        }

        String targetName =
                args[1];

        OfflinePlayer target =
                Bukkit.getOfflinePlayer(
                        targetName
                );

        if (!target.isOnline()
                && !target.hasPlayedBefore()) {

            sender.sendMessage(
                    ChatColor.RED +
                            "That player could not be found."
            );

            return;
        }

        String reason =
                String.join(
                        " ",
                        Arrays.copyOfRange(
                                args,
                                2,
                                args.length
                        )
                );

        if (reason.isBlank()) {

            sender.sendMessage(
                    ChatColor.RED +
                            "The reason cannot be empty."
            );

            return;
        }

        plugin.getCaseService()
                .createCase(
                        target.getUniqueId(),
                        target.getName(),
                        player.getUniqueId(),
                        player.getName(),
                        reason
                )
                .thenAccept(caseFile ->
                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> sendCaseCreated(
                                        player,
                                        caseFile
                                )
                        )
                )
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> player.sendMessage(
                                    ChatColor.RED +
                                            "Failed to create the case."
                            )
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void handleView(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission(
                "casemanager.view"
        )) {

            sendNoPermission(sender);
            return;
        }

        if (args.length < 2) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Usage: /case view <id>"
            );

            return;
        }

        long caseId;

        try {

            caseId =
                    Long.parseLong(
                            args[1]
                    );

        } catch (NumberFormatException exception) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Case ID must be a number."
            );

            return;
        }

        plugin.getCaseService()
                .getCase(caseId)
                .thenAccept(caseFile ->
                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> {

                                    if (caseFile == null) {

                                        sender.sendMessage(
                                                ChatColor.RED +
                                                        "Case #" +
                                                        caseId +
                                                        " does not exist."
                                        );

                                        return;
                                    }

                                    sendCaseDetails(
                                            sender,
                                            caseFile
                                    );
                                }
                        )
                )
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sender.sendMessage(
                                    ChatColor.RED +
                                            "Failed to retrieve the case."
                            )
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void handleList(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission(
                "casemanager.list"
        )) {

            sendNoPermission(sender);
            return;
        }

        int configuredLimit = Math.max(
                1,
                plugin.getConfig().getInt(
                        "cases.list-limit",
                        50
                )
        );

        int limit = Math.min(10, configuredLimit);

        if (args.length >= 2) {

            try {

                limit =
                        Integer.parseInt(
                                args[1]
                        );

            } catch (NumberFormatException exception) {

                sender.sendMessage(
                        ChatColor.RED +
                                "Limit must be a number."
                );

                return;
            }
        }

        if (limit < 1 || limit > configuredLimit) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Limit must be between 1 and " +
                            configuredLimit +
                            "."
            );

            return;
        }

        plugin.getCaseService()
                .getCases(limit)
                .thenAccept(cases ->
                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> sendCaseList(
                                        sender,
                                        cases
                                )
                        )
                )
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sender.sendMessage(
                                    ChatColor.RED +
                                            "Failed to retrieve cases."
                            )
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void handleTimeline(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission(
                "casemanager.view"
        )) {

            sendNoPermission(sender);
            return;
        }

        if (args.length < 2) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Usage: /case timeline <id>"
            );

            return;
        }

        long caseId;

        try {

            caseId =
                    Long.parseLong(
                            args[1]
                    );

        } catch (NumberFormatException exception) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Case ID must be a number."
            );

            return;
        }

        plugin.getCaseService()
                .getCase(caseId)
                .thenAccept(caseFile -> {

                    if (caseFile == null) {

                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> sender.sendMessage(
                                        ChatColor.RED +
                                                "Case #" +
                                                caseId +
                                                " does not exist."
                                )
                        );

                        return;
                    }

                    plugin.getCaseTimelineService()
                            .getTimeline(caseId)
                            .thenAccept(entries ->
                                    Bukkit.getScheduler().runTask(
                                            plugin,
                                            () -> sendTimeline(
                                                    sender,
                                                    caseFile,
                                                    entries
                                            )
                                    )
                            );

                })
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sender.sendMessage(
                                    ChatColor.RED +
                                            "Failed to retrieve the timeline."
                            )
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void handleNote(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission(
                "casemanager.note"
        )) {

            sendNoPermission(sender);
            return;
        }

        if (!(sender instanceof Player player)) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Only players can add notes."
            );

            return;
        }

        if (args.length < 3) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Usage: /case note <id> <note>"
            );

            return;
        }

        long caseId;

        try {

            caseId =
                    Long.parseLong(
                            args[1]
                    );

        } catch (NumberFormatException exception) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Case ID must be a number."
            );

            return;
        }

        String content =
                String.join(
                        " ",
                        Arrays.copyOfRange(
                                args,
                                2,
                                args.length
                        )
                );

        if (content.isBlank()) {

            sender.sendMessage(
                    ChatColor.RED +
                            "The note cannot be empty."
            );

            return;
        }

        plugin.getCaseService()
                .getCase(caseId)
                .thenAccept(caseFile -> {

                    if (caseFile == null) {

                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> sender.sendMessage(
                                        ChatColor.RED +
                                                "Case #" +
                                                caseId +
                                                " does not exist."
                                )
                        );

                        return;
                    }

                    plugin.getCaseNoteService()
                            .addNote(
                                    caseId,
                                    player.getUniqueId(),
                                    player.getName(),
                                    content
                            )
                            .thenAccept(note ->
                                    Bukkit.getScheduler().runTask(
                                            plugin,
                                            () -> sendNoteCreated(
                                                    player,
                                                    note
                                            )
                                    )
                            )
                            .exceptionally(exception -> {

                                Bukkit.getScheduler().runTask(
                                        plugin,
                                        () -> player.sendMessage(
                                                ChatColor.RED +
                                                        "Failed to add the note."
                                        )
                                );

                                exception.printStackTrace();

                                return null;
                            });

                })
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sender.sendMessage(
                                    ChatColor.RED +
                                            "Failed to retrieve the case."
                            )
                    );

                    exception.printStackTrace();

                    return null;
                });
    }
    private void handleEvidence(
            CommandSender sender,
            String[] args
    ) {

        if (args.length < 2) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Usage: /case evidence <add|list|collect>"
            );

            return;
        }

        String action =
                args[1].toLowerCase();

        switch (action) {

            case "add":
                handleEvidenceAdd(
                        sender,
                        args
                );
                break;

            case "list":
                handleEvidenceList(
                        sender,
                        args
                );
                break;

            case "collect":
                handleEvidenceCollect(
                        sender,
                        args
                );
                break;

            default:

                sender.sendMessage(
                        ChatColor.RED +
                                "Usage: /case evidence <add|list|collect>"
                );

                break;
        }
    }

    private void handleEvidenceAdd(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission(
                "casemanager.evidence"
        )) {

            sendNoPermission(sender);
            return;
        }

        if (!(sender instanceof Player player)) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Only players can add evidence."
            );

            return;
        }

        if (args.length < 5) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Usage: /case evidence add <id> <type> <content>"
            );

            return;
        }

        long caseId;

        try {

            caseId =
                    Long.parseLong(
                            args[2]
                    );

        } catch (NumberFormatException exception) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Case ID must be a number."
            );

            return;
        }

        String type =
                args[3].toUpperCase();

        if (!evidenceTypes.contains(type)) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Invalid evidence type."
            );

            sender.sendMessage(
                    ChatColor.GRAY +
                            "Available: " +
                            String.join(
                                    ", ",
                                    evidenceTypes
                            )
            );

            return;
        }

        String content =
                String.join(
                        " ",
                        Arrays.copyOfRange(
                                args,
                                4,
                                args.length
                        )
                );

        if (content.isBlank()) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Evidence content cannot be empty."
            );

            return;
        }

        int maxEvidenceContentLength = Math.max(
                1,
                plugin.getConfig().getInt(
                        "evidence.max-content-length",
                        10000
                )
        );

        if (content.length() > maxEvidenceContentLength) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Evidence content is too long. Maximum length: " +
                            maxEvidenceContentLength +
                            " characters."
            );

            return;
        }

        plugin.getCaseService()
                .getCase(caseId)
                .thenAccept(caseFile -> {

                    if (caseFile == null) {

                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> sender.sendMessage(
                                        ChatColor.RED +
                                                "Case #" +
                                                caseId +
                                                " does not exist."
                                )
                        );

                        return;
                    }

                    plugin.getCaseEvidenceService()
                            .addEvidence(
                                    caseId,
                                    player.getUniqueId(),
                                    player.getName(),
                                    type,
                                    content
                            )
                            .thenAccept(evidence ->
                                    Bukkit.getScheduler().runTask(
                                            plugin,
                                            () -> sendEvidenceCreated(
                                                    player,
                                                    evidence
                                            )
                                    )
                            )
                            .exceptionally(exception -> {

                                Bukkit.getScheduler().runTask(
                                        plugin,
                                        () -> player.sendMessage(
                                                ChatColor.RED +
                                                        "Failed to add evidence."
                                        )
                                );

                                exception.printStackTrace();

                                return null;
                            });

                })
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sender.sendMessage(
                                    ChatColor.RED +
                                            "Failed to retrieve the case."
                            )
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void handleEvidenceList(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission(
                "casemanager.evidence"
        )) {

            sendNoPermission(sender);
            return;
        }

        if (args.length < 3) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Usage: /case evidence list <id>"
            );

            return;
        }

        long caseId;

        try {

            caseId =
                    Long.parseLong(
                            args[2]
                    );

        } catch (NumberFormatException exception) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Case ID must be a number."
            );

            return;
        }

        plugin.getCaseService()
                .getCase(caseId)
                .thenAccept(caseFile -> {

                    if (caseFile == null) {

                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> sender.sendMessage(
                                        ChatColor.RED +
                                                "Case #" +
                                                caseId +
                                                " does not exist."
                                )
                        );

                        return;
                    }

                    plugin.getCaseEvidenceService()
                            .getEvidence(caseId)
                            .thenAccept(evidence -> {

                                int configuredLimit = Math.max(
                                        1,
                                        plugin.getConfig().getInt(
                                                "evidence.list-limit",
                                                100
                                        )
                                );

                                List<CaseEvidence> visibleEvidence =
                                        evidence;

                                if (evidence.size() > configuredLimit) {

                                    visibleEvidence =
                                            new ArrayList<>(
                                                    evidence.subList(
                                                            0,
                                                            configuredLimit
                                                    )
                                            );
                                }

                                List<CaseEvidence> finalEvidence =
                                        visibleEvidence;

                                Bukkit.getScheduler().runTask(
                                        plugin,
                                        () -> sendEvidenceList(
                                                sender,
                                                caseFile,
                                                finalEvidence
                                        )
                                );
                            })
                            .exceptionally(exception -> {

                                Bukkit.getScheduler().runTask(
                                        plugin,
                                        () -> sender.sendMessage(
                                                ChatColor.RED +
                                                        "Failed to retrieve evidence."
                                        )
                                );

                                exception.printStackTrace();

                                return null;
                            });

                })
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sender.sendMessage(
                                    ChatColor.RED +
                                            "Failed to retrieve the case."
                            )
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void handleEvidenceCollect(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission(
                "casemanager.evidence"
        )) {

            sendNoPermission(sender);
            return;
        }

        if (!(sender instanceof Player player)) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Only players can collect evidence."
            );

            return;
        }

        if (args.length < 4) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Usage: /case evidence collect <id> <all|coreprotect|vulcan|litebans>"
            );

            return;
        }

        long caseId;

        try {

            caseId =
                    Long.parseLong(
                            args[2]
                    );

        } catch (NumberFormatException exception) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Case ID must be a number."
            );

            return;
        }

        String source =
                args[3].toLowerCase();

        if (!evidenceCollectionTypes.contains(source)) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Invalid evidence source."
            );

            sender.sendMessage(
                    ChatColor.GRAY +
                            "Available: " +
                            String.join(
                                    ", ",
                                    evidenceCollectionTypes
                            )
            );

            return;
        }

        plugin.getCaseService()
                .getCase(caseId)
                .thenAccept(caseFile -> {

                    if (caseFile == null) {

                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> sender.sendMessage(
                                        ChatColor.RED +
                                                "Case #" +
                                                caseId +
                                                " does not exist."
                                )
                        );

                        return;
                    }

                    sendEvidenceCollectionStarted(
                            player,
                            caseFile,
                            source
                    );

                    collectEvidence(
                            player,
                            caseFile,
                            source
                    );

                })
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> player.sendMessage(
                                    ChatColor.RED +
                                            "Failed to retrieve the case."
                            )
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void collectEvidence(
            Player player,
            Case caseFile,
            String source
    ) {

        switch (source) {

            case "coreprotect":

                collectCoreProtect(
                        player,
                        caseFile
                );

                break;

            case "vulcan":

                collectVulcan(
                        player,
                        caseFile
                );

                break;

            case "litebans":

                collectLiteBans(
                        player,
                        caseFile
                );

                break;

            case "all":

                collectAll(
                        player,
                        caseFile
                );

                break;

            default:

                player.sendMessage(
                        ChatColor.RED +
                                "Unknown evidence source."
                );

                break;
        }
    }

    private void collectCoreProtect(
            Player player,
            Case caseFile
    ) {

        if (!plugin.getEvidenceIntegrationService()
                .isCoreProtectAvailable()) {

            player.sendMessage(
                    ChatColor.RED +
                            "CoreProtect integration is not available."
            );

            return;
        }

        plugin.getEvidenceIntegrationService()
                .collectCoreProtectEvidence(
                        caseFile.getId(),
                        caseFile.getTargetUuid(),
                        caseFile.getTargetName(),
                        plugin.getConfig().getInt(
                                "integrations.coreprotect.evidence.time-seconds",
                                86400
                        ),
                        plugin.getConfig().getInt(
                                "integrations.coreprotect.evidence.limit",
                                50
                        ),
                        player.getUniqueId(),
                        player.getName()
                )
                .thenAccept(success ->
                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> {

                                    if (success) {

                                        player.sendMessage(
                                                ChatColor.GREEN +
                                                        "CoreProtect evidence collected successfully."
                                        );

                                    } else {

                                        player.sendMessage(
                                                ChatColor.YELLOW +
                                                        "CoreProtect returned no evidence."
                                        );
                                    }
                                }
                        )
                )
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> player.sendMessage(
                                    ChatColor.RED +
                                            "Failed to collect CoreProtect evidence."
                            )
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void collectVulcan(
            Player player,
            Case caseFile
    ) {

        if (!plugin.getEvidenceIntegrationService()
                .isVulcanAvailable()) {

            player.sendMessage(
                    ChatColor.RED +
                            "Vulcan integration is not available."
            );

            return;
        }

        plugin.getEvidenceIntegrationService()
                .collectVulcanEvidence(
                        caseFile.getId(),
                        caseFile.getTargetUuid(),
                        caseFile.getTargetName(),
                        player.getUniqueId(),
                        player.getName()
                )
                .thenAccept(success ->
                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> {

                                    if (success) {

                                        player.sendMessage(
                                                ChatColor.GREEN +
                                                        "Vulcan evidence collected successfully."
                                        );

                                    } else {

                                        player.sendMessage(
                                                ChatColor.YELLOW +
                                                        "Vulcan returned no evidence."
                                        );
                                    }
                                }
                        )
                )
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> player.sendMessage(
                                    ChatColor.RED +
                                            "Failed to collect Vulcan evidence."
                            )
                    );

                    exception.printStackTrace();

                    return null;
                });
    }
    private void collectLiteBans(
            Player player,
            Case caseFile
    ) {

        if (!plugin.getEvidenceIntegrationService()
                .isLiteBansAvailable()) {

            player.sendMessage(
                    ChatColor.RED +
                            "LiteBans integration is not available."
            );

            return;
        }

        plugin.getEvidenceIntegrationService()
                .collectLiteBansEvidence(
                        caseFile.getId(),
                        caseFile.getTargetUuid(),
                        caseFile.getTargetName(),
                        plugin.getConfig().getInt(
                                "integrations.litebans.evidence-limit",
                                20
                        ),
                        player.getUniqueId(),
                        player.getName()
                )
                .thenAccept(success ->
                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> {

                                    if (success) {

                                        player.sendMessage(
                                                ChatColor.GREEN +
                                                        "LiteBans evidence collected successfully."
                                        );

                                    } else {

                                        player.sendMessage(
                                                ChatColor.YELLOW +
                                                        "LiteBans returned no punishment history."
                                        );
                                    }
                                }
                        )
                )
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> player.sendMessage(
                                    ChatColor.RED +
                                            "Failed to collect LiteBans evidence."
                            )
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void collectAll(
            Player player,
            Case caseFile
    ) {

        boolean coreProtectAvailable =
                plugin.getEvidenceIntegrationService()
                        .isCoreProtectAvailable();

        boolean vulcanAvailable =
                plugin.getEvidenceIntegrationService()
                        .isVulcanAvailable();

        boolean liteBansAvailable =
                plugin.getEvidenceIntegrationService()
                        .isLiteBansAvailable();

        if (!coreProtectAvailable &&
                !vulcanAvailable &&
                !liteBansAvailable) {

            player.sendMessage(
                    ChatColor.RED +
                            "No evidence integrations are currently available."
            );

            return;
        }

        player.sendMessage(
                ChatColor.YELLOW +
                        "Collecting evidence from all available integrations..."
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Available: " +
                        plugin.getEvidenceIntegrationService()
                                .getAvailableIntegrations()
        );

        plugin.getEvidenceIntegrationService()
                .collectAllEvidence(
                        caseFile.getId(),
                        caseFile.getTargetUuid(),
                        caseFile.getTargetName(),
                        plugin.getConfig().getInt(
                                "integrations.coreprotect.evidence.time-seconds",
                                86400
                        ),
                        plugin.getConfig().getInt(
                                "integrations.coreprotect.evidence.limit",
                                50
                        ),
                        plugin.getConfig().getInt(
                                "integrations.litebans.evidence-limit",
                                20
                        ),
                        player.getUniqueId(),
                        player.getName()
                )
                .thenAccept(success ->
                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> {

                                    if (success) {

                                        player.sendMessage(
                                                ChatColor.GREEN +
                                                        "Evidence collection completed."
                                        );

                                        player.sendMessage(
                                                ChatColor.GRAY +
                                                        "Use " +
                                                        ChatColor.WHITE +
                                                        "/case evidence list " +
                                                        caseFile.getId() +
                                                        ChatColor.GRAY +
                                                        " to view the collected evidence."
                                        );

                                    } else {

                                        player.sendMessage(
                                                ChatColor.YELLOW +
                                                        "Evidence collection completed, but no evidence was collected."
                                        );
                                    }
                                }
                        )
                )
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> player.sendMessage(
                                    ChatColor.RED +
                                            "Evidence collection failed."
                            )
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void sendEvidenceCollectionStarted(
            Player player,
            Case caseFile,
            String source
    ) {

        player.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        player.sendMessage(
                ChatColor.GOLD +
                        "Evidence Collection"
        );

        player.sendMessage("");

        player.sendMessage(
                ChatColor.GRAY +
                        "Case: " +
                        ChatColor.WHITE +
                        "#" +
                        caseFile.getId()
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Target: " +
                        ChatColor.WHITE +
                        caseFile.getTargetName()
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Source: " +
                        ChatColor.WHITE +
                        source.toUpperCase()
        );

        player.sendMessage(
                ChatColor.YELLOW +
                        "Collection started..."
        );

        player.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void handleStatus(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission(
                "casemanager.status"
        )) {

            sendNoPermission(sender);
            return;
        }

        if (!(sender instanceof Player player)) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Only players can change case status."
            );

            return;
        }

        if (args.length < 3) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Usage: /case status <id> <status>"
            );

            return;
        }

        long caseId;

        try {

            caseId =
                    Long.parseLong(
                            args[1]
                    );

        } catch (NumberFormatException exception) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Case ID must be a number."
            );

            return;
        }

        CaseStatus newStatus;

        try {

            newStatus =
                    CaseStatus.valueOf(
                            args[2].toUpperCase()
                    );

        } catch (IllegalArgumentException exception) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Invalid case status."
            );

            sender.sendMessage(
                    ChatColor.GRAY +
                            "Available: " +
                            String.join(
                                    ", ",
                                    statusNames
                            )
            );

            return;
        }

        plugin.getCaseService()
                .updateStatus(
                        caseId,
                        newStatus,
                        player.getUniqueId(),
                        player.getName()
                )
                .thenAccept(caseFile ->
                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> {

                                    if (caseFile == null) {

                                        player.sendMessage(
                                                ChatColor.RED +
                                                        "Case #" +
                                                        caseId +
                                                        " does not exist."
                                        );

                                        return;
                                    }

                                    sendStatusUpdated(
                                            player,
                                            caseFile
                                    );
                                }
                        )
                )
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> player.sendMessage(
                                    ChatColor.RED +
                                            "Failed to update the case status."
                            )
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void handleAssign(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission(
                "casemanager.assign"
        )) {

            sendNoPermission(sender);
            return;
        }

        if (!(sender instanceof Player player)) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Only players can assign investigators."
            );

            return;
        }

        if (args.length < 3) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Usage: /case assign <id> <player>"
            );

            return;
        }

        long caseId;

        try {

            caseId =
                    Long.parseLong(
                            args[1]
                    );

        } catch (NumberFormatException exception) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Case ID must be a number."
            );

            return;
        }

        OfflinePlayer investigator =
                Bukkit.getOfflinePlayer(
                        args[2]
                );

        if (!investigator.isOnline()
                && !investigator.hasPlayedBefore()) {

            sender.sendMessage(
                    ChatColor.RED +
                            "That player could not be found."
            );

            return;
        }

        plugin.getCaseService()
                .getCase(caseId)
                .thenAccept(caseFile -> {

                    if (caseFile == null) {

                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> sender.sendMessage(
                                        ChatColor.RED +
                                                "Case #" +
                                                caseId +
                                                " does not exist."
                                )
                        );

                        return;
                    }

                    plugin.getCaseInvestigatorService()
                            .assignInvestigator(
                                    caseId,
                                    investigator.getUniqueId(),
                                    investigator.getName(),
                                    player.getUniqueId(),
                                    player.getName()
                            )
                            .thenAccept(assigned ->
                                    Bukkit.getScheduler().runTask(
                                            plugin,
                                            () -> sendInvestigatorAssigned(
                                                    player,
                                                    assigned
                                            )
                                    )
                            )
                            .exceptionally(exception -> {

                                Bukkit.getScheduler().runTask(
                                        plugin,
                                        () -> player.sendMessage(
                                                ChatColor.RED +
                                                        "Failed to assign the investigator."
                                        )
                                );

                                exception.printStackTrace();

                                return null;
                            });

                })
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sender.sendMessage(
                                    ChatColor.RED +
                                            "Failed to retrieve the case."
                            )
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void handleUnassign(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission(
                "casemanager.unassign"
        )) {

            sendNoPermission(sender);
            return;
        }

        if (!(sender instanceof Player player)) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Only players can unassign investigators."
            );

            return;
        }

        if (args.length < 3) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Usage: /case unassign <id> <player>"
            );

            return;
        }

        long caseId;

        try {

            caseId =
                    Long.parseLong(
                            args[1]
                    );

        } catch (NumberFormatException exception) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Case ID must be a number."
            );

            return;
        }

        OfflinePlayer investigator =
                Bukkit.getOfflinePlayer(
                        args[2]
                );

        if (!investigator.isOnline()
                && !investigator.hasPlayedBefore()) {

            sender.sendMessage(
                    ChatColor.RED +
                            "That player could not be found."
            );

            return;
        }

        plugin.getCaseService()
                .getCase(caseId)
                .thenAccept(caseFile -> {

                    if (caseFile == null) {

                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> sender.sendMessage(
                                        ChatColor.RED +
                                                "Case #" +
                                                caseId +
                                                " does not exist."
                                )
                        );

                        return;
                    }

                    plugin.getCaseInvestigatorService()
                            .unassignInvestigator(
                                    caseId,
                                    investigator.getUniqueId(),
                                    player.getUniqueId(),
                                    player.getName()
                            )
                            .thenAccept(success ->
                                    Bukkit.getScheduler().runTask(
                                            plugin,
                                            () -> {

                                                if (success) {

                                                    sendInvestigatorUnassigned(
                                                            player,
                                                            investigator.getName(),
                                                            caseId
                                                    );

                                                } else {

                                                    player.sendMessage(
                                                            ChatColor.YELLOW +
                                                                    investigator.getName() +
                                                                    " is not assigned to case #" +
                                                                    caseId +
                                                                    "."
                                                    );
                                                }
                                            }
                                    )
                            )
                            .exceptionally(exception -> {

                                Bukkit.getScheduler().runTask(
                                        plugin,
                                        () -> player.sendMessage(
                                                ChatColor.RED +
                                                        "Failed to unassign the investigator."
                                        )
                                );

                                exception.printStackTrace();

                                return null;
                            });

                })
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sender.sendMessage(
                                    ChatColor.RED +
                                            "Failed to retrieve the case."
                            )
                    );

                    exception.printStackTrace();

                    return null;
                });
    }
    private void handleInvestigators(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission(
                "casemanager.investigators"
        )) {

            sendNoPermission(sender);
            return;
        }

        if (args.length < 2) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Usage: /case investigators <id>"
            );

            return;
        }

        long caseId;

        try {

            caseId =
                    Long.parseLong(
                            args[1]
                    );

        } catch (NumberFormatException exception) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Case ID must be a number."
            );

            return;
        }

        plugin.getCaseService()
                .getCase(caseId)
                .thenAccept(caseFile -> {

                    if (caseFile == null) {

                        Bukkit.getScheduler().runTask(
                                plugin,
                                () -> sender.sendMessage(
                                        ChatColor.RED +
                                                "Case #" +
                                                caseId +
                                                " does not exist."
                                )
                        );

                        return;
                    }

                    plugin.getCaseInvestigatorService()
                            .getInvestigators(caseId)
                            .thenAccept(investigators ->
                                    Bukkit.getScheduler().runTask(
                                            plugin,
                                            () -> sendInvestigators(
                                                    sender,
                                                    caseFile,
                                                    investigators
                                            )
                                    )
                            )
                            .exceptionally(exception -> {

                                Bukkit.getScheduler().runTask(
                                        plugin,
                                        () -> sender.sendMessage(
                                                ChatColor.RED +
                                                        "Failed to retrieve investigators."
                                        )
                                );

                                exception.printStackTrace();

                                return null;
                            });

                })
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sender.sendMessage(
                                    ChatColor.RED +
                                            "Failed to retrieve the case."
                            )
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void sendStatusUpdated(
            Player player,
            Case caseFile
    ) {

        player.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        player.sendMessage(
                ChatColor.GREEN +
                        "Case Status Updated"
        );

        player.sendMessage("");

        player.sendMessage(
                ChatColor.GRAY +
                        "Case: " +
                        ChatColor.WHITE +
                        "#" +
                        caseFile.getId()
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Status: " +
                        ChatColor.YELLOW +
                        caseFile.getStatus().name()
        );

        player.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void sendInvestigatorAssigned(
            Player player,
            CaseInvestigator investigator
    ) {

        player.sendMessage(
                ChatColor.GREEN +
                        "Investigator assigned successfully."
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Player: " +
                        ChatColor.WHITE +
                        investigator.getInvestigatorName()
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Case: " +
                        ChatColor.WHITE +
                        "#" +
                        investigator.getCaseId()
        );
    }

    private void sendInvestigatorUnassigned(
            Player player,
            String investigatorName,
            long caseId
    ) {

        player.sendMessage(
                ChatColor.GREEN +
                        "Investigator unassigned successfully."
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Player: " +
                        ChatColor.WHITE +
                        investigatorName
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Case: " +
                        ChatColor.WHITE +
                        "#" +
                        caseId
        );
    }

    private void sendInvestigators(
            CommandSender sender,
            Case caseFile,
            List<CaseInvestigator> investigators
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.GOLD +
                        "Case #" +
                        caseFile.getId() +
                        " Investigators"
        );

        sender.sendMessage("");

        if (investigators == null ||
                investigators.isEmpty()) {

            sender.sendMessage(
                    ChatColor.GRAY +
                            "No investigators are assigned."
            );

        } else {

            for (CaseInvestigator investigator :
                    investigators) {

                sender.sendMessage(
                        ChatColor.GRAY +
                                "• " +
                                ChatColor.WHITE +
                                investigator.getInvestigatorName() +
                                ChatColor.DARK_GRAY +
                                " — assigned " +
                                ChatColor.GRAY +
                                investigator.getAssignedAt()
                );
            }
        }

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void sendEvidenceCreated(
            Player player,
            CaseEvidence evidence
    ) {

        player.sendMessage(
                ChatColor.GREEN +
                        "Evidence added successfully."
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Evidence ID: " +
                        ChatColor.WHITE +
                        "#" +
                        evidence.getId()
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Type: " +
                        ChatColor.WHITE +
                        evidence.getType()
        );
    }

    private void sendEvidenceList(
            CommandSender sender,
            Case caseFile,
            List<CaseEvidence> evidenceList
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.GOLD +
                        "Case #" +
                        caseFile.getId() +
                        " Evidence"
        );

        sender.sendMessage("");

        if (evidenceList == null ||
                evidenceList.isEmpty()) {

            sender.sendMessage(
                    ChatColor.GRAY +
                            "No evidence has been added to this case."
            );

            sender.sendMessage(
                    ChatColor.DARK_GRAY +
                            "━━━━━━━━━━━━━━━━━━━━"
            );

            return;
        }

        for (CaseEvidence evidence :
                evidenceList) {

            sender.sendMessage(
                    ChatColor.YELLOW +
                            "#" +
                            evidence.getId() +
                            ChatColor.GRAY +
                            " [" +
                            ChatColor.WHITE +
                            evidence.getType() +
                            ChatColor.GRAY +
                            "]"
            );

            sender.sendMessage(
                    ChatColor.GRAY +
                            "Added by: " +
                            ChatColor.WHITE +
                            evidence.getAddedByName()
            );

            sender.sendMessage(
                    ChatColor.GRAY +
                            "Content: " +
                            ChatColor.WHITE +
                            evidence.getContent()
            );

            sender.sendMessage(
                    ChatColor.DARK_GRAY +
                            "Added: " +
                            evidence.getCreatedAt()
            );

            sender.sendMessage("");
        }

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void sendNoteCreated(
            Player player,
            CaseNote note
    ) {

        player.sendMessage(
                ChatColor.GREEN +
                        "Case note added successfully."
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Note ID: " +
                        ChatColor.WHITE +
                        "#" +
                        note.getId()
        );
    }

    private void sendCaseCreated(
            Player player,
            Case caseFile
    ) {

        player.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        player.sendMessage(
                ChatColor.GREEN +
                        "Case Created"
        );

        player.sendMessage("");

        player.sendMessage(
                ChatColor.GRAY +
                        "Case ID: " +
                        ChatColor.WHITE +
                        "#" +
                        caseFile.getId()
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Target: " +
                        ChatColor.WHITE +
                        caseFile.getTargetName()
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Reason: " +
                        ChatColor.WHITE +
                        caseFile.getReason()
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Status: " +
                        ChatColor.YELLOW +
                        caseFile.getStatus().name()
        );

        player.sendMessage("");

        player.sendMessage(
                ChatColor.GRAY +
                        "View it with " +
                        ChatColor.WHITE +
                        "/case view " +
                        caseFile.getId()
        );

        player.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void sendCaseDetails(
            CommandSender sender,
            Case caseFile
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.GOLD +
                        "Case #" +
                        caseFile.getId()
        );

        sender.sendMessage("");

        sender.sendMessage(
                ChatColor.GRAY +
                        "Target: " +
                        ChatColor.WHITE +
                        caseFile.getTargetName()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Target UUID: " +
                        ChatColor.WHITE +
                        caseFile.getTargetUuid()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Created by: " +
                        ChatColor.WHITE +
                        caseFile.getCreatorName()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Reason: " +
                        ChatColor.WHITE +
                        caseFile.getReason()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Status: " +
                        ChatColor.YELLOW +
                        caseFile.getStatus().name()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Created: " +
                        ChatColor.WHITE +
                        caseFile.getCreatedAt()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Updated: " +
                        ChatColor.WHITE +
                        caseFile.getUpdatedAt()
        );

        sender.sendMessage("");

        sender.sendMessage(
                ChatColor.GRAY +
                        "Timeline: " +
                        ChatColor.WHITE +
                        "/case timeline " +
                        caseFile.getId()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Evidence: " +
                        ChatColor.WHITE +
                        "/case evidence list " +
                        caseFile.getId()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Investigators: " +
                        ChatColor.WHITE +
                        "/case investigators " +
                        caseFile.getId()
        );

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void sendCaseList(
            CommandSender sender,
            List<Case> cases
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.GOLD +
                        "Moderation Cases"
        );

        sender.sendMessage("");

        if (cases == null || cases.isEmpty()) {

            sender.sendMessage(
                    ChatColor.GRAY +
                            "No cases found."
            );

            sender.sendMessage(
                    ChatColor.DARK_GRAY +
                            "━━━━━━━━━━━━━━━━━━━━"
            );

            return;
        }

        for (Case caseFile : cases) {

            sender.sendMessage(
                    ChatColor.YELLOW +
                            "#" +
                            caseFile.getId() +
                            ChatColor.GRAY +
                            " | " +
                            ChatColor.WHITE +
                            caseFile.getTargetName() +
                            ChatColor.GRAY +
                            " | " +
                            ChatColor.YELLOW +
                            caseFile.getStatus().name()
            );

            sender.sendMessage(
                    ChatColor.GRAY +
                            "  Reason: " +
                            ChatColor.WHITE +
                            caseFile.getReason()
            );
        }

        sender.sendMessage("");

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void sendTimeline(
            CommandSender sender,
            Case caseFile,
            List<CaseTimelineEntry> timeline
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.GOLD +
                        "Case #" +
                        caseFile.getId() +
                        " Timeline"
        );

        sender.sendMessage("");

        if (timeline == null ||
                timeline.isEmpty()) {

            sender.sendMessage(
                    ChatColor.GRAY +
                            "No timeline entries found."
            );

            sender.sendMessage(
                    ChatColor.DARK_GRAY +
                            "━━━━━━━━━━━━━━━━━━━━"
            );

            return;
        }

        for (CaseTimelineEntry entry :
                timeline) {

            String actor =
                    entry.getActorName() == null
                            ? "System"
                            : entry.getActorName();

            sender.sendMessage(
                    ChatColor.YELLOW +
                            entry.getAction()
            );

            sender.sendMessage(
                    ChatColor.GRAY +
                            "Actor: " +
                            ChatColor.WHITE +
                            actor
            );

            if (entry.getDetails() != null &&
                    !entry.getDetails().isBlank()) {

                sender.sendMessage(
                        ChatColor.GRAY +
                                "Details: " +
                                ChatColor.WHITE +
                                entry.getDetails()
                );
            }

            sender.sendMessage(
                    ChatColor.DARK_GRAY +
                            entry.getCreatedAt().toString()
            );

            sender.sendMessage("");
        }

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void sendHelp(
            CommandSender sender
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.GOLD +
                        "CaseManager Commands"
        );

        sender.sendMessage("");

        sender.sendMessage(
                ChatColor.YELLOW +
                        "/case create <player> <reason>"
        );

        sender.sendMessage(
                ChatColor.YELLOW +
                        "/case view <id>"
        );

        sender.sendMessage(
                ChatColor.YELLOW +
                        "/case list"
        );

        sender.sendMessage(
                ChatColor.YELLOW +
                        "/case timeline <id>"
        );

        sender.sendMessage(
                ChatColor.YELLOW +
                        "/case note <id> <note>"
        );

        sender.sendMessage(
                ChatColor.YELLOW +
                        "/case evidence add <id> <type> <content>"
        );

        sender.sendMessage(
                ChatColor.YELLOW +
                        "/case evidence list <id>"
        );

        sender.sendMessage(
                ChatColor.YELLOW +
                        "/case evidence collect <id> <all|coreprotect|vulcan|litebans>"
        );

        sender.sendMessage(
                ChatColor.YELLOW +
                        "/case investigators <id>"
        );

        sender.sendMessage(
                ChatColor.YELLOW +
                        "/case status <id> <status>"
        );

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void sendNoPermission(
            CommandSender sender
    ) {

        sender.sendMessage(
                ChatColor.RED +
                        "You do not have permission to use this command."
        );
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {

        if (args.length == 1) {

            return filter(
                    Arrays.asList(
                            "create",
                            "view",
                            "list",
                            "timeline",
                            "note",
                            "evidence",
                            "assign",
                            "unassign",
                            "investigators",
                            "status"
                    ),
                    args[0]
            );
        }

        if (args.length >= 2 &&
                args[0].equalsIgnoreCase("evidence")) {

            if (args.length == 2) {

                return filter(
                        Arrays.asList(
                                "add",
                                "list",
                                "collect"
                        ),
                        args[1]
                );
            }

            if (args[1].equalsIgnoreCase("add")) {

                if (args.length == 3) {

                    return filter(
                            evidenceTypes,
                            args[2]
                    );
                }
            }

            if (args[1].equalsIgnoreCase("collect")) {

                if (args.length == 4) {

                    return filter(
                            evidenceCollectionTypes,
                            args[3]
                    );
                }
            }

            return Collections.emptyList();
        }

        if (args.length == 2 &&
                args[0].equalsIgnoreCase("status")) {

            return filter(
                    statusNames,
                    args[1]
            );
        }

        if (args.length == 2 &&
                (args[0].equalsIgnoreCase("view")
                        || args[0].equalsIgnoreCase("timeline")
                        || args[0].equalsIgnoreCase("note")
                        || args[0].equalsIgnoreCase("investigators"))) {

            return Collections.emptyList();
        }

        if (args.length == 2 &&
                (args[0].equalsIgnoreCase("assign")
                        || args[0].equalsIgnoreCase("unassign"))) {

            return Collections.emptyList();
        }

        if (args.length == 2 &&
                args[0].equalsIgnoreCase("create")) {

            return onlinePlayers(
                    args[1]
            );
        }

        return Collections.emptyList();
    }

    private List<String> onlinePlayers(
            String input
    ) {

        List<String> players =
                new ArrayList<>();

        for (Player player :
                Bukkit.getOnlinePlayers()) {

            if (player.getName()
                    .toLowerCase()
                    .startsWith(
                            input.toLowerCase()
                    )) {

                players.add(
                        player.getName()
                );
            }
        }

        return players;
    }

    private List<String> filter(
            List<String> values,
            String input
    ) {

        if (input == null ||
                input.isEmpty()) {

            return new ArrayList<>(
                    values
            );
        }

        List<String> result =
                new ArrayList<>();

        for (String value : values) {

            if (value.toLowerCase()
                    .startsWith(
                            input.toLowerCase()
                    )) {

                result.add(value);
            }
        }

        return result;
    }
}