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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {

            case "create" -> handleCreate(sender, args);

            case "view" -> handleView(sender, args);

            case "list" -> handleList(sender, args);

            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {

        if (!sender.hasPermission("casemanager.create")) {
            sendNoPermission(sender);
            return;
        }

        if (!(sender instanceof Player creator)) {
            sender.sendMessage(
                    ChatColor.RED + "Only players can create cases."
            );
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(
                    ChatColor.RED
                            + "Usage: /case create <player> <reason>"
            );
            return;
        }

        String targetName = args[1];

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(
                    ChatColor.RED
                            + "That player could not be found."
            );
            return;
        }

        String reason = String.join(
                " ",
                java.util.Arrays.copyOfRange(args, 2, args.length)
        );

        if (reason.length() < 3) {
            sender.sendMessage(
                    ChatColor.RED
                            + "The reason must contain at least 3 characters."
            );
            return;
        }

        if (reason.length() > 1000) {
            sender.sendMessage(
                    ChatColor.RED
                            + "The reason cannot exceed 1000 characters."
            );
            return;
        }

        UUID targetUuid = target.getUniqueId();

        String actualTargetName = target.getName();

        if (actualTargetName == null) {
            actualTargetName = targetName;
        }

        sender.sendMessage(
                ChatColor.GRAY
                        + "Creating case..."
        );

        CaseService caseService = plugin.getCaseService();

        CompletableFuture<Case> future = caseService.createCase(
                targetUuid,
                actualTargetName,
                creator.getUniqueId(),
                creator.getName(),
                reason
        );

        future.thenAccept(caseFile -> {

            Bukkit.getScheduler().runTask(
                    plugin,
                    () -> {

                        sender.sendMessage("");

                        sender.sendMessage(
                                ChatColor.DARK_GRAY
                                        + "━━━━━━━━━━━━━━━━━━━━"
                        );

                        sender.sendMessage(
                                ChatColor.DARK_AQUA
                                        + "CaseManager"
                        );

                        sender.sendMessage(
                                ChatColor.GRAY
                                        + "Case "
                                        + ChatColor.WHITE
                                        + "#"
                                        + caseFile.getId()
                                        + ChatColor.GRAY
                                        + " created successfully."
                        );

                        sender.sendMessage(
                                ChatColor.GRAY
                                        + "Target: "
                                        + ChatColor.WHITE
                                        + caseFile.getTargetName()
                        );

                        sender.sendMessage(
                                ChatColor.GRAY
                                        + "Status: "
                                        + ChatColor.WHITE
                                        + caseFile.getStatus().name()
                        );

                        sender.sendMessage(
                                ChatColor.GRAY
                                        + "Reason: "
                                        + ChatColor.WHITE
                                        + caseFile.getReason()
                        );

                        sender.sendMessage(
                                ChatColor.DARK_GRAY
                                        + "━━━━━━━━━━━━━━━━━━━━"
                        );

                        sender.sendMessage("");
                    }
            );

        }).exceptionally(exception -> {

            Bukkit.getScheduler().runTask(
                    plugin,
                    () -> sender.sendMessage(
                            ChatColor.RED
                                    + "Failed to create the case."
                    )
            );

            plugin.getLogger().severe(
                    "Failed to create a case:"
            );

            exception.printStackTrace();

            return null;
        });
    }

    private void handleView(CommandSender sender, String[] args) {

        if (!sender.hasPermission("casemanager.view")) {
            sendNoPermission(sender);
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(
                    ChatColor.RED
                            + "Usage: /case view <id>"
            );
            return;
        }

        long caseId;

        try {
            caseId = Long.parseLong(args[1]);
        } catch (NumberFormatException exception) {

            sender.sendMessage(
                    ChatColor.RED
                            + "Case ID must be a number."
            );

            return;
        }

        if (caseId <= 0) {
            sender.sendMessage(
                    ChatColor.RED
                            + "Invalid case ID."
            );

            return;
        }

        /*
         * Case retrieval will be implemented in the next database layer.
         */

        sender.sendMessage(
                ChatColor.YELLOW
                        + "Case #"
                        + caseId
                        + ChatColor.GRAY
                        + " viewing will be available shortly."
        );
    }

    private void handleList(CommandSender sender, String[] args) {

        if (!sender.hasPermission("casemanager.list")) {
            sendNoPermission(sender);
            return;
        }

        /*
         * Case listing will be implemented after the
         * case retrieval service is added.
         */

        sender.sendMessage(
                ChatColor.YELLOW
                        + "Case listing will be available shortly."
        );
    }

    private void sendHelp(CommandSender sender) {

        sender.sendMessage("");

        sender.sendMessage(
                ChatColor.DARK_AQUA
                        + "CaseManager"
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "/case create <player> <reason>"
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "/case view <id>"
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "/case list"
        );

        sender.sendMessage("");
    }

    private void sendNoPermission(CommandSender sender) {

        sender.sendMessage(
                ChatColor.RED
                        + "You do not have permission to use this command."
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

            List<String> suggestions = new ArrayList<>();

            if (sender.hasPermission("casemanager.create")) {
                suggestions.add("create");
            }

            if (sender.hasPermission("casemanager.view")) {
                suggestions.add("view");
            }

            if (sender.hasPermission("casemanager.list")) {
                suggestions.add("list");
            }

            return filter(suggestions, args[0]);
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("create")
                && sender.hasPermission("casemanager.create")) {

            List<String> players = new ArrayList<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
                players.add(player.getName());
            }

            return filter(players, args[1]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(
            List<String> values,
            String input
    ) {

        String lowerInput = input.toLowerCase();

        return values.stream()
                .filter(value ->
                        value.toLowerCase().startsWith(lowerInput)
                )
                .sorted()
                .toList();
    }
                      }
