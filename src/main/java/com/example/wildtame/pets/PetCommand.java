package com.example.wildtame.pets;

import com.example.wildtame.MenuIcons;
import com.example.wildtame.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PetCommand implements CommandExecutor, TabCompleter {

    private static final List<String> USER_SUBCOMMANDS =
            List.of("info", "llamar", "renombrar", "menu", "top", "ayuda");

    private static final String USER_PERMISSION = "wildtame.user";
    private static final String ADMIN_LEVEL_PERMISSION = "wildtame.admin.nivel";
    private static final String ADMIN_RELOAD_PERMISSION = "wildtame.admin.reload";
    private static final String ADMIN_CLEANUP_PERMISSION = "wildtame.admin.limpiar";

    private static final String DIVIDER = "§8§m                                        ";

    private final PetManager petManager;
    private final MainMenuGUI mainMenuGUI;
    private final PetGUI petGUI;
    private final Messages messages;
    private final MenuIcons menuIcons;

    public PetCommand(PetManager petManager, MainMenuGUI mainMenuGUI, PetGUI petGUI, Messages messages, MenuIcons menuIcons) {
        this.petManager = petManager;
        this.mainMenuGUI = mainMenuGUI;
        this.petGUI = petGUI;
        this.messages = messages;
        this.menuIcons = menuIcons;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("top")) {
            petManager.sendTopPets(sender, 10);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            handleAdminSubcommand(sender, args);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("command.players-only"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("ayuda")) {
            sendHelp(player);
            return true;
        }

        if (!player.hasPermission(USER_PERMISSION)) {
            player.sendMessage(messages.get("command.no-permission"));
            return true;
        }
        handleUserSubcommand(player, args);
        return true;
    }

    /** /wildtame admin <reload|nivel|limpiar> — namespace propio para no mezclarse con los comandos normales. */
    private void handleAdminSubcommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.get("command.usage-admin"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission(ADMIN_RELOAD_PERMISSION)) {
                    sender.sendMessage(messages.get("command.no-permission"));
                } else {
                    petManager.loadConfigValues();
                    messages.load();
                    menuIcons.load();
                    sender.sendMessage(messages.get("command.reload-done"));
                }
            }
            case "limpiar" -> {
                if (!sender.hasPermission(ADMIN_CLEANUP_PERMISSION)) {
                    sender.sendMessage(messages.get("command.no-permission"));
                } else {
                    int removed = petManager.cleanupGhostPets();
                    sender.sendMessage(messages.get("command.cleanup-done", Map.of("count", String.valueOf(removed))));
                }
            }
            case "nivel" -> {
                if (!sender.hasPermission(ADMIN_LEVEL_PERMISSION)) {
                    sender.sendMessage(messages.get("command.no-permission"));
                } else if (args.length < 5) {
                    sender.sendMessage(messages.get("command.usage-level"));
                } else {
                    setPetLevel(sender, args[2], args[3], args[4]);
                }
            }
            default -> sender.sendMessage(messages.get("command.usage-admin"));
        }
    }

    private void handleUserSubcommand(Player player, String[] args) {
        switch (args[0].toLowerCase()) {
            case "info" -> {
                PetData data = petManager.getDataForOwner(player.getUniqueId());
                if (data == null) {
                    player.sendMessage(messages.get("command.no-active-pet-hint"));
                } else {
                    petGUI.open(player, data);
                }
            }
            case "llamar" -> {
                PetData data = petManager.getDataForOwner(player.getUniqueId());
                if (data == null) {
                    player.sendMessage(messages.get("command.no-active-pet-hint"));
                } else {
                    petManager.callPet(player, data);
                }
            }
            case "renombrar" -> {
                PetData data = petManager.getDataForOwner(player.getUniqueId());
                if (data == null) {
                    player.sendMessage(messages.get("command.no-active-pet-hint"));
                } else if (args.length < 2) {
                    player.sendMessage(messages.get("command.usage-rename"));
                } else {
                    petManager.renamePet(player, data, args[1]);
                }
            }
            case "menu" -> mainMenuGUI.open(player);
            default -> {
                player.sendMessage(messages.get("command.unknown-subcommand"));
                sendHelp(player);
            }
        }
    }

    private void setPetLevel(CommandSender admin, String targetName, String petName, String levelStr) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            admin.sendMessage(messages.get("command.player-not-found"));
            return;
        }
        int level;
        try {
            level = Integer.parseInt(levelStr);
        } catch (NumberFormatException e) {
            admin.sendMessage(messages.get("command.level-not-a-number"));
            return;
        }
        if (!petManager.setPetLevel(target.getUniqueId(), petName, level)) {
            admin.sendMessage(messages.get("command.pet-not-found-for-level", Map.of("player", target.getName())));
            return;
        }
        int applied = Math.max(1, level);
        admin.sendMessage(messages.get("command.level-set-admin",
                Map.of("pet", petName, "player", target.getName(), "level", String.valueOf(applied))));
        target.sendMessage(messages.get("command.level-set-target",
                Map.of("pet", petName, "level", String.valueOf(applied))));
    }

    private void sendHelp(Player player) {
        player.sendMessage(DIVIDER);
        player.sendMessage(messages.get("command.help-title"));
        player.sendMessage(messages.get("command.help-main-line"));
        player.sendMessage("");
        player.sendMessage(messages.get("command.help-info-line"));
        player.sendMessage(messages.get("command.help-llamar-line"));
        player.sendMessage(messages.get("command.help-renombrar-line"));
        player.sendMessage(messages.get("command.help-top-line"));

        boolean hasAnyAdminPermission = player.hasPermission(ADMIN_LEVEL_PERMISSION)
                || player.hasPermission(ADMIN_RELOAD_PERMISSION) || player.hasPermission(ADMIN_CLEANUP_PERMISSION);
        if (hasAnyAdminPermission) {
            player.sendMessage("");
            if (player.hasPermission(ADMIN_RELOAD_PERMISSION)) {
                player.sendMessage(messages.get("command.help-admin-reload-line"));
            }
            if (player.hasPermission(ADMIN_LEVEL_PERMISSION)) {
                player.sendMessage(messages.get("command.help-admin-nivel-line"));
            }
            if (player.hasPermission(ADMIN_CLEANUP_PERMISSION)) {
                player.sendMessage(messages.get("command.help-admin-limpiar-line"));
            }
        }
        player.sendMessage(DIVIDER);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> options = new ArrayList<>(USER_SUBCOMMANDS);
            if (sender.hasPermission(ADMIN_LEVEL_PERMISSION) || sender.hasPermission(ADMIN_RELOAD_PERMISSION)
                    || sender.hasPermission(ADMIN_CLEANUP_PERMISSION)) {
                options.add("admin");
            }
            return options.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            String prefix = args[1].toLowerCase();
            List<String> options = new ArrayList<>();
            if (sender.hasPermission(ADMIN_RELOAD_PERMISSION)) {
                options.add("reload");
            }
            if (sender.hasPermission(ADMIN_LEVEL_PERMISSION)) {
                options.add("nivel");
            }
            if (sender.hasPermission(ADMIN_CLEANUP_PERMISSION)) {
                options.add("limpiar");
            }
            return options.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("nivel")) {
            String prefix = args[2].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("nivel")) {
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                return new ArrayList<>();
            }
            String prefix = args[3].toLowerCase();
            List<String> names = new ArrayList<>();
            PetData active = petManager.getDataForOwner(target.getUniqueId());
            if (active != null) {
                names.add(active.name);
            }
            for (PetData stored : petManager.getStoredPets(target.getUniqueId())) {
                names.add(stored.name);
            }
            return names.stream().distinct().filter(n -> n.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
