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

        int limit = 10;

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

        if (limit < 1 || limit > 50) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Limit must be between 1 and 50."
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
                            "Usage: /case evidence <add|list>"
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

            default:

                sender.sendMessage(
                        ChatColor.RED +
                                "Usage: /case evidence <add|list>"
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
                            .thenAccept(evidence ->
                                    Bukkit.getScheduler().runTask(
                                            plugin,
                                            () -> sendEvidenceList(
                                                    sender,
                                                    evidence
                                            )
                                    )
                            )
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
                                                    caseId,
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
                ChatColor.GREEN +
                        "Case status updated successfully."
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Case: #" +
                        caseFile.getId()
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "New status: " +
                        ChatColor.YELLOW +
                        caseFile.getStatus().name()
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
                        "Case: #" +
                        investigator.getCaseId()
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Investigator: " +
                        ChatColor.WHITE +
                        investigator.getInvestigatorName()
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
                        "Case: #" +
                        caseId
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Investigator: " +
                        ChatColor.WHITE +
                        investigatorName
        );
    }

    private void sendInvestigators(
            CommandSender sender,
            long caseId,
            List<CaseInvestigator> investigators
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.GOLD +
                        "Investigators — Case #" +
                        caseId
        );

        sender.sendMessage("");

        if (investigators.isEmpty()) {

            sender.sendMessage(
                    ChatColor.GRAY +
                            "No investigators are assigned."
            );

        } else {

            for (CaseInvestigator investigator :
                    investigators) {

                sender.sendMessage(
                        ChatColor.YELLOW +
                                "• " +
                                ChatColor.WHITE +
                                investigator.getInvestigatorName() +
                                ChatColor.GRAY +
                                " — assigned " +
                                investigator.getAssignedAt()
                );
            }
        }

        sender.sendMessage("");

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
                        "Evidence ID: #" +
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
            List<CaseEvidence> evidenceList
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.GOLD +
                        "Case Evidence"
        );

        sender.sendMessage("");

        if (evidenceList.isEmpty()) {

            sender.sendMessage(
                    ChatColor.GRAY +
                            "No evidence has been added."
            );

        } else {

            for (CaseEvidence evidence :
                    evidenceList) {

                sender.sendMessage(
                        ChatColor.YELLOW +
                                "#" +
                                evidence.getId() +
                                ChatColor.GRAY +
                                " [" +
                                evidence.getType() +
                                "] " +
                                ChatColor.WHITE +
                                evidence.getContent()
                );

                sender.sendMessage(
                        ChatColor.DARK_GRAY +
                                "Added by " +
                                evidence.getAddedByName() +
                                " at " +
                                evidence.getCreatedAt()
                );
            }
        }

        sender.sendMessage("");

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
                        "Note added successfully."
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Note ID: #" +
                        note.getId()
        );
    }

    private void sendCaseCreated(
            Player player,
            Case caseFile
    ) {

        player.sendMessage(
                ChatColor.GREEN +
                        "Case created successfully."
        );

        player.sendMessage(
                ChatColor.GRAY +
                        "Case ID: #" +
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
                        "Status: " +
                        ChatColor.YELLOW +
                        caseFile.getStatus().name()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Reason: " +
                        ChatColor.WHITE +
                        caseFile.getReason()
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
                        "Recent Cases"
        );

        sender.sendMessage("");

        if (cases.isEmpty()) {

            sender.sendMessage(
                    ChatColor.GRAY +
                            "No cases found."
            );

        } else {

            for (Case caseFile : cases) {

                sender.sendMessage(
                        ChatColor.YELLOW +
                                "#" +
                                caseFile.getId() +
                                ChatColor.GRAY +
                                " [" +
                                caseFile.getStatus().name() +
                                "] " +
                                ChatColor.WHITE +
                                caseFile.getTargetName()
                );

                sender.sendMessage(
                        ChatColor.DARK_GRAY +
                                "Reason: " +
                                caseFile.getReason()
                );
            }
        }

        sender.sendMessage("");

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void sendTimeline(
            CommandSender sender,
            List<CaseTimelineEntry> entries
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.GOLD +
                        "Case Timeline"
        );

        sender.sendMessage("");

        if (entries.isEmpty()) {

            sender.sendMessage(
                    ChatColor.GRAY +
                            "No timeline entries found."
            );

        } else {

            for (CaseTimelineEntry entry :
                    entries) {

                sender.sendMessage(
                        ChatColor.YELLOW +
                                entry.getAction()
                );

                sender.sendMessage(
                        ChatColor.GRAY +
                                "Actor: " +
                                ChatColor.WHITE +
                                (entry.getActorName() == null
                                        ? "System"
                                        : entry.getActorName())
                );

                if (entry.getDetails() != null
                        && !entry.getDetails().isBlank()) {

                    sender.sendMessage(
                            ChatColor.GRAY +
                                    "Details: " +
                                    ChatColor.WHITE +
                                    entry.getDetails()
                    );
                }

                sender.sendMessage(
                        ChatColor.DARK_GRAY +
                                "Time: " +
                                entry.getCreatedAt()
                );

                sender.sendMessage("");
            }
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

        if (sender.hasPermission("casemanager.create")) {
            sender.sendMessage(
                    ChatColor.YELLOW +
                            "/case create <player> <reason>"
            );
        }

        if (sender.hasPermission("casemanager.view")) {
            sender.sendMessage(
                    ChatColor.YELLOW +
                            "/case view <id>"
            );

            sender.sendMessage(
                    ChatColor.YELLOW +
                            "/case timeline <id>"
            );
        }

        if (sender.hasPermission("casemanager.list")) {
            sender.sendMessage(
                    ChatColor.YELLOW +
                            "/case list [limit]"
            );
        }

        if (sender.hasPermission("casemanager.note")) {
            sender.sendMessage(
                    ChatColor.YELLOW +
                            "/case note <id> <note>"
            );
        }

        if (sender.hasPermission("casemanager.evidence")) {
            sender.sendMessage(
                    ChatColor.YELLOW +
                            "/case evidence add <id> <type> <content>"
            );

            sender.sendMessage(
                    ChatColor.YELLOW +
                            "/case evidence list <id>"
            );
        }

        if (sender.hasPermission("casemanager.assign")) {
            sender.sendMessage(
                    ChatColor.YELLOW +
                            "/case assign <id> <player>"
            );
        }

        if (sender.hasPermission("casemanager.unassign")) {
            sender.sendMessage(
                    ChatColor.YELLOW +
                            "/case unassign <id> <player>"
            );
        }

        if (sender.hasPermission("casemanager.investigators")) {
            sender.sendMessage(
                    ChatColor.YELLOW +
                            "/case investigators <id>"
            );
        }

        if (sender.hasPermission("casemanager.status")) {
            sender.sendMessage(
                    ChatColor.YELLOW +
                            "/case status <id> <status>"
            );
        }

        sender.sendMessage("");

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

            List<String> commands =
                    new ArrayList<>();

            if (sender.hasPermission("casemanager.create")) {
                commands.add("create");
            }

            if (sender.hasPermission("casemanager.view")) {
                commands.add("view");
                commands.add("timeline");
            }

            if (sender.hasPermission("casemanager.list")) {
                commands.add("list");
            }

            if (sender.hasPermission("casemanager.note")) {
                commands.add("note");
            }

            if (sender.hasPermission("casemanager.evidence")) {
                commands.add("evidence");
            }

            if (sender.hasPermission("casemanager.assign")) {
                commands.add("assign");
            }

            if (sender.hasPermission("casemanager.unassign")) {
                commands.add("unassign");
            }

            if (sender.hasPermission("casemanager.investigators")) {
                commands.add("investigators");
            }

            if (sender.hasPermission("casemanager.status")) {
                commands.add("status");
            }

            return filter(
                    commands,
                    args[0]
            );
        }

        String subCommand =
                args[0].toLowerCase();

        if (subCommand.equals("create")
                && args.length == 2
                && sender.hasPermission("casemanager.create")) {

            List<String> players =
                    new ArrayList<>();

            for (Player player :
                    Bukkit.getOnlinePlayers()) {

                players.add(
                        player.getName()
                );
            }

            return filter(
                    players,
                    args[1]
            );
        }

        if (subCommand.equals("list")
                && args.length == 2
                && sender.hasPermission("casemanager.list")) {

            return filter(
                    Arrays.asList(
                            "10",
                            "20",
                            "30",
                            "40",
                            "50"
                    ),
                    args[1]
            );
        }

        if ((subCommand.equals("view")
                || subCommand.equals("timeline"))
                && args.length == 2
                && sender.hasPermission("casemanager.view")) {

            return Collections.emptyList();
        }

        if (subCommand.equals("note")
                && args.length == 2
                && sender.hasPermission("casemanager.note")) {

            return Collections.emptyList();
        }

        if (subCommand.equals("evidence")
                && args.length == 2
                && sender.hasPermission("casemanager.evidence")) {

            return filter(
                    Arrays.asList(
                            "add",
                            "list"
                    ),
                    args[1]
            );
        }

        if (subCommand.equals("evidence")
                && args.length == 3
                && args[1].equalsIgnoreCase("add")
                && sender.hasPermission("casemanager.evidence")) {

            return filter(
                    evidenceTypes,
                    args[2]
            );
        }

        if (subCommand.equals("assign")
                && args.length == 2
                && sender.hasPermission("casemanager.assign")) {

            return Collections.emptyList();
        }

        if (subCommand.equals("assign")
                && args.length == 3
                && sender.hasPermission("casemanager.assign")) {

            return onlinePlayers(
                    args[2]
            );
        }

        if (subCommand.equals("unassign")
                && args.length == 2
                && sender.hasPermission("casemanager.unassign")) {

            return Collections.emptyList();
        }

        if (subCommand.equals("unassign")
                && args.length == 3
                && sender.hasPermission("casemanager.unassign")) {

            return onlinePlayers(
                    args[2]
            );
        }

        if (subCommand.equals("investigators")
                && args.length == 2
                && sender.hasPermission("casemanager.investigators")) {

            return Collections.emptyList();
        }

        if (subCommand.equals("status")
                && args.length == 2
                && sender.hasPermission("casemanager.status")) {

            return Collections.emptyList();
        }

        if (subCommand.equals("status")
                && args.length == 3
                && sender.hasPermission("casemanager.status")) {

            return filter(
                    statusNames,
                    args[2]
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

            players.add(
                    player.getName()
            );
        }

        return filter(
                players,
                input
        );
    }

    private List<String> filter(
            List<String> values,
            String input
    ) {

        List<String> result =
                new ArrayList<>();

        String lowerInput =
                input.toLowerCase();

        for (String value : values) {

            if (value.toLowerCase()
                    .startsWith(lowerInput)) {

                result.add(value);
            }
        }

        return result;
    }
}