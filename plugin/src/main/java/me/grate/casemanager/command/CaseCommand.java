package me.grate.casemanager.command;

import me.grate.casemanager.CaseManager;
import me.grate.casemanager.casefile.Case;
import me.grate.casemanager.casefile.CaseService;
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

        switch (args[0].toLowerCase()) {

            case "create" -> handleCreate(sender, args);

            case "view" -> handleView(sender, args);

            case "list" -> handleList(sender, args);

            default -> sendHelp(sender);
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

        if (!(sender instanceof Player creator)) {
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

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(
                    ChatColor.RED +
                            "That player could not be found."
            );
            return;
        }

        String reason = String.join(
                " ",
                Arrays.copyOfRange(args, 2, args.length)
        );

        if (reason.length() < 3) {
            sender.sendMessage(
                    ChatColor.RED +
                            "The reason must contain at least 3 characters."
            );
            return;
        }

        if (reason.length() > 1000) {
            sender.sendMessage(
                    ChatColor.RED +
                            "The reason cannot exceed 1000 characters."
            );
            return;
        }

        String actualTargetName = target.getName();

        if (actualTargetName == null) {
            actualTargetName = targetName;
        }

        sender.sendMessage(
                ChatColor.GRAY +
                        "Creating case..."
        );

        CaseService caseService =
                plugin.getCaseService();

        CompletableFuture<Case> future =
                caseService.createCase(
                        target.getUniqueId(),
                        actualTargetName,
                        creator.getUniqueId(),
                        creator.getName(),
                        reason
                );

        future.thenAccept(caseFile -> {

            Bukkit.getScheduler().runTask(
                    plugin,
                    () -> sendCaseCreated(
                            sender,
                            caseFile
                    )
            );

        }).exceptionally(exception -> {

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
                                            "Failed to load case #" +
                                            caseId +
                                            "."
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
                                "The limit must be a number."
                );
                return;
            }
        }

        if (limit < 1) {
            sender.sendMessage(
                    ChatColor.RED +
                            "The limit must be at least 1."
            );
            return;
        }

        if (limit > 50) {
            sender.sendMessage(
                    ChatColor.RED +
                            "The maximum limit is 50."
            );
            return;
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

    private void sendCaseCreated(
            CommandSender sender,
            Case caseFile
    ) {

        sender.sendMessage("");

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.DARK_AQUA +
                        "CaseManager"
        );

        sender.sendMessage(
                ChatColor.GREEN +
                        "✓ " +
                        ChatColor.GRAY +
                        "Case " +
                        ChatColor.WHITE +
                        "#" +
                        caseFile.getId() +
                        ChatColor.GRAY +
                        " created."
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Target: " +
                        ChatColor.WHITE +
                        caseFile.getTargetName()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Status: " +
                        ChatColor.WHITE +
                        caseFile.getStatus().name()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Reason: " +
                        ChatColor.WHITE +
                        caseFile.getReason()
        );

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage("");
    }

    private void sendCaseDetails(
            CommandSender sender,
            Case caseFile
    ) {

        sender.sendMessage("");

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.DARK_AQUA +
                        "CASE #" +
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
                        ChatColor.WHITE +
                        caseFile.getStatus().name()
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "Reason: " +
                        ChatColor.WHITE +
                        caseFile.getReason()
        );

        sender.sendMessage("");

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

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage("");
    }

    private void sendCaseList(
            CommandSender sender,
            List<Case> cases
    ) {

        sender.sendMessage("");

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.DARK_AQUA +
                        "CASE LIST"
        );

        sender.sendMessage("");

        if (cases.isEmpty()) {

            sender.sendMessage(
                    ChatColor.GRAY +
                            "No cases have been created yet."
            );

        } else {

            for (Case caseFile : cases) {

                sender.sendMessage(
                        ChatColor.WHITE +
                                "#" +
                                caseFile.getId() +
                                ChatColor.GRAY +
                                " | " +
                                ChatColor.WHITE +
                                caseFile.getTargetName() +
                                ChatColor.GRAY +
                                " | " +
                                ChatColor.WHITE +
                                caseFile.getStatus().name()
                );

                sender.sendMessage(
                        ChatColor.DARK_GRAY +
                                "   " +
                                caseFile.getReason()
                );
            }
        }

        sender.sendMessage("");

        sender.sendMessage(
                ChatColor.DARK_GRAY +
                        "━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage("");
    }

    private void sendHelp(CommandSender sender) {

        sender.sendMessage("");

        sender.sendMessage(
                ChatColor.DARK_AQUA +
                        "CaseManager"
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "/case create <player> <reason>"
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "/case view <id>"
        );

        sender.sendMessage(
                ChatColor.GRAY +
                        "/case list [limit]"
        );

        sender.sendMessage("");
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
            }

            if (sender.hasPermission(
                    "casemanager.list"
            )) {
                suggestions.add("list");
            }

            return filter(
                    suggestions,
                    args[0]
            );
        }

        if (args.length == 2 &&
                args[0].equalsIgnoreCase("create") &&
                sender.hasPermission(
                        "casemanager.create"
                )) {

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

        if (args.length == 2 &&
                args[0].equalsIgnoreCase("list") &&
                sender.hasPermission(
                        "casemanager.list"
                )) {

            return filter(
                    List.of(
                            "5",
                            "10",
                            "20",
                            "50"
                    ),
                    args[1]
            );
        }

        return Collections.emptyList();
    }

    private List<String> filter(
            List<String> values,
            String input
    ) {

        String lowerInput =
                input.toLowerCase();

        return values.stream()
                .filter(value ->
                        value.toLowerCase()
                                .startsWith(lowerInput)
                )
                .sorted()
                .toList();
    }
                            }
