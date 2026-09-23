package me.grate.casemanager.command;

import me.grate.casemanager.CaseManager;
import me.grate.casemanager.casefile.Case;
import me.grate.casemanager.casefile.CaseEvidence;
import me.grate.casemanager.casefile.CaseNote;
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
import java.util.concurrent.CompletableFuture;

public final class CaseCommand implements CommandExecutor, TabCompleter {

    private final CaseManager plugin;

    private final List<String> evidenceTypes =
            Arrays.asList(
                    "SCREENSHOT",
                    "VIDEO",
                    "CHAT_LOG",
                    "ANTI_CHEAT",
                    "COREPROTECT",
                    "LITEBANS",
                    "OBSERVATION",
                    "OTHER"
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

        if (!sender.hasPermission("casemanager.create")) {
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

        String targetName = args[1];

        OfflinePlayer target =
                Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore()
                && !target.isOnline()) {

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
                            "You must provide a reason."
            );
            return;
        }

        sender.sendMessage(
                ChatColor.GRAY +
                        "Creating case..."
        );

        plugin.getCaseService()
                .createCase(
                        target.getUniqueId(),
                        target.getName() == null
                                ? targetName
                                : target.getName(),
                        player.getUniqueId(),
                        player.getName(),
                        reason
                )
                .thenAccept(caseFile -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sendCaseCreated(
                                    sender,
                                    caseFile
                            )
                    );

                })
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sender.sendMessage(
                                    ChatColor.RED +
                                            "Failed to create the case."
                            )
                    );

                    plugin.getLogger().severe(
                            "Failed to create a case."
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void handleView(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission("casemanager.view")) {
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
            caseId = Long.parseLong(args[1]);
        } catch (NumberFormatException exception) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Case ID must be a number."
            );

            return;
        }

        if (caseId <= 0) {
            sender.sendMessage(
                    ChatColor.RED +
                            "Invalid case ID."
            );
            return;
        }

        sender.sendMessage(
                ChatColor.GRAY +
                        "Loading case #" +
                        caseId +
                        "..."
        );

        plugin.getCaseService()
                .getCase(caseId)
                .thenAccept(caseFile -> {

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
                    );

                })
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sender.sendMessage(
                                    ChatColor.RED +
                                            "Failed to load the case."
                            )
                    );

                    plugin.getLogger().severe(
                            "Failed to load case #" +
                                    caseId
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void handleList(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission("casemanager.list")) {
            sendNoPermission(sender);
            return;
        }

        int limit = 10;

        if (args.length >= 2) {

            try {
                limit = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {

                sender.sendMessage(
                        ChatColor.RED +
                                "Limit must be a number."
                );

                return;
            }
        }

        if (limit < 1) {
            sender.sendMessage(
                    ChatColor.RED +
                            "Limit must be at least 1."
            );
            return;
        }

        if (limit > 50) {
            limit = 50;
        }

        final int finalLimit = limit;

        sender.sendMessage(
                ChatColor.GRAY +
                        "Loading cases..."
        );

        plugin.getCaseService()
                .getCases(finalLimit)
                .thenAccept(cases -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sendCaseList(
                                    sender,
                                    cases
                            )
                    );

                })
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sender.sendMessage(
                                    ChatColor.RED +
                                            "Failed to load cases."
                            )
                    );

                    plugin.getLogger().severe(
                            "Failed to load case list."
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void handleTimeline(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission("casemanager.view")) {
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
            caseId = Long.parseLong(args[1]);
        } catch (NumberFormatException exception) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Case ID must be a number."
            );

            return;
        }

        if (caseId <= 0) {
            sender.sendMessage(
                    ChatColor.RED +
                            "Invalid case ID."
            );
            return;
        }

        sender.sendMessage(
                ChatColor.GRAY +
                        "Loading timeline for case #" +
                        caseId +
                        "..."
        );

        plugin.getCaseService()
                .getCase(caseId)
                .thenCompose(caseFile -> {

                    if (caseFile == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException(
                                        "Case does not exist."
                                )
                        );
                    }

                    return plugin
                            .getCaseTimelineService()
                            .getTimeline(caseId);
                })
                .thenAccept(entries -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sendTimeline(
                                    sender,
                                    caseId,
                                    entries
                            )
                    );

                })
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sender.sendMessage(
                                    ChatColor.RED +
                                            "Failed to load case timeline."
                            )
                    );

                    plugin.getLogger().severe(
                            "Failed to load timeline for case #" +
                                    caseId
                    );

                    exception.printStackTrace();

                    return null;
                });
    }
}
    private void handleNote(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission("casemanager.note")) {
            sendNoPermission(sender);
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    ChatColor.RED +
                            "Only players can add case notes."
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
            caseId = Long.parseLong(args[1]);
        } catch (NumberFormatException exception) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Case ID must be a number."
            );

            return;
        }

        if (caseId <= 0) {
            sender.sendMessage(
                    ChatColor.RED +
                            "Invalid case ID."
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
                            "Note cannot be empty."
            );
            return;
        }

        sender.sendMessage(
                ChatColor.GRAY +
                        "Adding note to case #" +
                        caseId +
                        "..."
        );

        plugin.getCaseService()
                .getCase(caseId)
                .thenCompose(caseFile -> {

                    if (caseFile == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException(
                                        "Case does not exist."
                                )
                        );
                    }

                    return plugin
                            .getCaseNoteService()
                            .addNote(
                                    caseId,
                                    player.getUniqueId(),
                                    player.getName(),
                                    content
                            );
                })
                .thenAccept(note -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sendNoteCreated(
                                    sender,
                                    note
                            )
                    );

                })
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sender.sendMessage(
                                    ChatColor.RED +
                                            "Failed to add the note. " +
                                            "Make sure the case exists."
                            )
                    );

                    plugin.getLogger().severe(
                            "Failed to add note to case #" +
                                    caseId
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void handleEvidence(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission("casemanager.evidence")) {
            sendNoPermission(sender);
            return;
        }

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
                                "Unknown evidence action."
                );

                sender.sendMessage(
                        ChatColor.GRAY +
                                "Usage: /case evidence <add|list>"
                );
                break;
        }
    }

    private void handleEvidenceAdd(
            CommandSender sender,
            String[] args
    ) {

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
            caseId = Long.parseLong(args[2]);
        } catch (NumberFormatException exception) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Case ID must be a number."
            );

            return;
        }

        if (caseId <= 0) {
            sender.sendMessage(
                    ChatColor.RED +
                            "Invalid case ID."
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
                            "Available types: " +
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

        sender.sendMessage(
                ChatColor.GRAY +
                        "Adding evidence to case #" +
                        caseId +
                        "..."
        );

        plugin.getCaseService()
                .getCase(caseId)
                .thenCompose(caseFile -> {

                    if (caseFile == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException(
                                        "Case does not exist."
                                )
                        );
                    }

                    return plugin
                            .getCaseEvidenceService()
                            .addEvidence(
                                    caseId,
                                    player.getUniqueId(),
                                    player.getName(),
                                    type,
                                    content
                            );
                })
                .thenAccept(evidence -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sendEvidenceCreated(
                                    sender,
                                    evidence
                            )
                    );

                })
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sender.sendMessage(
                                    ChatColor.RED +
                                            "Failed to add evidence. " +
                                            "Make sure the case exists."
                            )
                    );

                    plugin.getLogger().severe(
                            "Failed to add evidence to case #" +
                                    caseId
                    );

                    exception.printStackTrace();

                    return null;
                });
    }

    private void handleEvidenceList(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission("casemanager.evidence")) {
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
            caseId = Long.parseLong(args[2]);
        } catch (NumberFormatException exception) {

            sender.sendMessage(
                    ChatColor.RED +
                            "Case ID must be a number."
            );

            return;
        }

        if (caseId <= 0) {
            sender.sendMessage(
                    ChatColor.RED +
                            "Invalid case ID."
            );
            return;
        }

        sender.sendMessage(
                ChatColor.GRAY +
                        "Loading evidence for case #" +
                        caseId +
                        "..."
        );

        plugin.getCaseService()
                .getCase(caseId)
                .thenCompose(caseFile -> {

                    if (caseFile == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException(
                                        "Case does not exist."
                                )
                        );
                    }

                    return plugin
                            .getCaseEvidenceService()
                            .getEvidence(caseId);
                })
                .thenAccept(evidenceList -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sendEvidenceList(
                                    sender,
                                    caseId,
                                    evidenceList
                            )
                    );

                })
                .exceptionally(exception -> {

                    Bukkit.getScheduler().runTask(
                            plugin,
                            () -> sender.sendMessage(
                                    ChatColor.RED +
                                            "Failed to load case evidence."
                            )
                    );

                    plugin.getLogger().severe(
                            "Failed to load evidence for case #" +
                                    caseId
                    );

                    exception.printStackTrace();

                    return null;
                });
    }
    private void sendEvidenceCreated(
            CommandSender sender,
            CaseEvidence evidence
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.DARK_AQUA +
                        "Case Evidence Added"
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Evidence ID: " +
                        ChatColor.WHITE +
                        "#" +
                        evidence.getId()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Case: " +
                        ChatColor.WHITE +
                        "#" +
                        evidence.getCaseId()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Type: " +
                        ChatColor.AQUA +
                        evidence.getType()
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
                ChatColor.GRAY +
                        "Added: " +
                        ChatColor.WHITE +
                        evidence.getCreatedAt()
        );

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void sendEvidenceList(
            CommandSender sender,
            long caseId,
            List<CaseEvidence> evidenceList
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.DARK_AQUA +
                        "Evidence — Case #" +
                        caseId
        );

        sender.sendMessage("");

        if (evidenceList.isEmpty()) {

            sender.sendMessage(
                    ChatColor.GRAY +
                            "No evidence has been added to this case."
            );

            sender.sendMessage(
                    ChatColor.DARK_GRAY +
                            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            );

            return;
        }

        for (CaseEvidence evidence : evidenceList) {

            sender.sendMessage(
                    ChatColor.AQUA +
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
                            "  Added by: " +
                            ChatColor.WHITE +
                            evidence.getAddedByName()
            );

            sender.sendMessage(
                    ChatColor.GRAY +
                            "  Content: " +
                            ChatColor.WHITE +
                            evidence.getContent()
            );

            sender.sendMessage(
                    ChatColor.GRAY +
                            "  Time: " +
                            ChatColor.WHITE +
                            evidence.getCreatedAt()
            );

            sender.sendMessage("");
        }

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void sendNoteCreated(
            CommandSender sender,
            CaseNote note
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.DARK_AQUA +
                        "Case Note Added"
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Note ID: " +
                        ChatColor.WHITE +
                        "#" +
                        note.getId()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Case: " +
                        ChatColor.WHITE +
                        "#" +
                        note.getCaseId()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Author: " +
                        ChatColor.WHITE +
                        note.getAuthorName()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Content: " +
                        ChatColor.WHITE +
                        note.getContent()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Created: " +
                        ChatColor.WHITE +
                        note.getCreatedAt()
        );

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void sendCaseCreated(
            CommandSender sender,
            Case caseFile
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.DARK_AQUA +
                        "Case Created"
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Case ID: " +
                        ChatColor.AQUA +
                        "#" +
                        caseFile.getId()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Target: " +
                        ChatColor.WHITE +
                        caseFile.getTargetName()
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
                        ChatColor.GREEN +
                        caseFile.getStatus().name()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Created by: " +
                        ChatColor.WHITE +
                        caseFile.getCreatorName()
        );

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void sendCaseDetails(
            CommandSender sender,
            Case caseFile
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.DARK_AQUA +
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

        sender.sendMessage("");

        sender.sendMessage(
                ChatColor.GRAY +
                        "Creator: " +
                        ChatColor.WHITE +
                        caseFile.getCreatorName()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Creator UUID: " +
                        ChatColor.WHITE +
                        caseFile.getCreatorUuid()
        );

        sender.sendMessage("");

        sender.sendMessage(
                ChatColor.GRAY +
                        "Reason:"
        );

        sender.sendMessage(
                ChatColor.WHITE +
                        "  " +
                        caseFile.getReason()
        );

        sender.sendMessage("");

        sender.sendMessage(
                ChatColor.GRAY +
                        "Status: " +
                        ChatColor.AQUA +
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
                        "Available actions:"
        );

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "  /case timeline " +
                        caseFile.getId()
        );

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "  /case note " +
                        caseFile.getId() +
                        " <note>"
        );

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "  /case evidence list " +
                        caseFile.getId()
        );

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void sendCaseList(
            CommandSender sender,
            List<Case> cases
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.DARK_AQUA +
                        "Moderation Cases"
        );

        sender.sendMessage("");

        if (cases.isEmpty()) {

            sender.sendMessage(
                    ChatColor.GRAY +
                            "No cases have been created yet."
            );

            sender.sendMessage(
                    ChatColor.DARK_GRAY +
                            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            );

            return;
        }

        for (Case caseFile : cases) {

            sender.sendMessage(
                    ChatColor.AQUA +
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
                    ChatColor.DARK_GRAY +
                            "  Reason: " +
                            ChatColor.GRAY +
                            caseFile.getReason()
            );

            sender.sendMessage(
                    ChatColor.DARK_GRAY +
                            "  Created by: " +
                            ChatColor.GRAY +
                            caseFile.getCreatorName()
            );

            sender.sendMessage("");
        }

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }
    private void sendTimeline(
            CommandSender sender,
            long caseId,
            List<CaseTimelineEntry> entries
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.DARK_AQUA +
                        "Case Timeline — #" +
                        caseId
        );

        sender.sendMessage("");

        if (entries.isEmpty()) {

            sender.sendMessage(
                    ChatColor.GRAY +
                            "No timeline entries found."
            );

            sender.sendMessage(
                    ChatColor.DARK_GRAY +
                            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            );

            return;
        }

        for (CaseTimelineEntry entry : entries) {

            String actor =
                    entry.getActorName() == null
                            ? "System"
                            : entry.getActorName();

            sender.sendMessage(
                    ChatColor.AQUA +
                            "[" +
                            entry.getCreatedAt() +
                            "]"
            );

            sender.sendMessage(
                    ChatColor.GRAY +
                            "  Action: " +
                            ChatColor.WHITE +
                            entry.getAction()
            );

            sender.sendMessage(
                    ChatColor.GRAY +
                            "  Actor: " +
                            ChatColor.WHITE +
                            actor
            );

            if (entry.getDetails() != null
                    && !entry.getDetails().isBlank()) {

                sender.sendMessage(
                        ChatColor.GRAY +
                                "  Details: " +
                                ChatColor.WHITE +
                                entry.getDetails()
                );
            }

            sender.sendMessage("");
        }

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void sendHelp(
            CommandSender sender
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.DARK_AQUA +
                        "CaseManager"
        );

        sender.sendMessage("");

        if (sender.hasPermission("casemanager.create")) {

            sender.sendMessage(
                    ChatColor.AQUA +
                            "/case create <player> <reason>"
            );
        }

        if (sender.hasPermission("casemanager.view")) {

            sender.sendMessage(
                    ChatColor.AQUA +
                            "/case view <id>"
            );

            sender.sendMessage(
                    ChatColor.AQUA +
                            "/case timeline <id>"
            );
        }

        if (sender.hasPermission("casemanager.list")) {

            sender.sendMessage(
                    ChatColor.AQUA +
                            "/case list [limit]"
            );
        }

        if (sender.hasPermission("casemanager.note")) {

            sender.sendMessage(
                    ChatColor.AQUA +
                            "/case note <id> <note>"
            );
        }

        if (sender.hasPermission("casemanager.evidence")) {

            sender.sendMessage(
                    ChatColor.AQUA +
                            "/case evidence add <id> <type> <content>"
            );

            sender.sendMessage(
                    ChatColor.AQUA +
                            "/case evidence list <id>"
            );
        }

        sender.sendMessage("");

        sender.sendMessage(
                ChatColor.GRAY +
                        "Use /case <command> for moderation case management."
        );

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
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

            List<String> suggestions =
                    new ArrayList<>();

            if (sender.hasPermission(
                    "casemanager.create"
            )) {
                suggestions.add("create");
            }

            if (sender.hasPermission(
                    "casemanager.view"
            )) {
                suggestions.add("view");
                suggestions.add("timeline");
            }

            if (sender.hasPermission(
                    "casemanager.list"
            )) {
                suggestions.add("list");
            }

            if (sender.hasPermission(
                    "casemanager.note"
            )) {
                suggestions.add("note");
            }

            if (sender.hasPermission(
                    "casemanager.evidence"
            )) {
                suggestions.add("evidence");
            }

            return filter(
                    suggestions,
                    args[0]
            );
        }

        String subCommand =
                args[0].toLowerCase();

        if (subCommand.equals("create")
                && args.length == 2) {

            if (!sender.hasPermission(
                    "casemanager.create"
            )) {
                return Collections.emptyList();
            }

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
                && args.length == 2) {

            if (!sender.hasPermission(
                    "casemanager.list"
            )) {
                return Collections.emptyList();
            }

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

        if (subCommand.equals("evidence")) {

            if (!sender.hasPermission(
                    "casemanager.evidence"
            )) {
                return Collections.emptyList();
            }

            if (args.length == 2) {

                List<String> suggestions =
                        Arrays.asList(
                                "add",
                                "list"
                        );

                return filter(
                        suggestions,
                        args[1]
                );
            }

            if (args.length == 3
                    && args[1].equalsIgnoreCase("add")) {

                return Collections.emptyList();
            }

            if (args.length == 3
                    && args[1].equalsIgnoreCase("list")) {

                return Collections.emptyList();
            }

            if (args.length == 4
                    && args[1].equalsIgnoreCase("add")) {

                return filter(
                        evidenceTypes,
                        args[3]
                );
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(
            List<String> values,
            String input
    ) {

        if (input == null
                || input.isBlank()) {

            return new ArrayList<>(values);
        }

        String lowerInput =
                input.toLowerCase();

        List<String> result =
                new ArrayList<>();

        for (String value : values) {

            if (value
                    .toLowerCase()
                    .startsWith(lowerInput)) {

                result.add(value);
            }
        }

        return result;
    }
}