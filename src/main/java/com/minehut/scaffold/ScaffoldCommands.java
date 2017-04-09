package com.minehut.scaffold;

import com.google.common.base.Joiner;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandNumberFormatException;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import org.apache.commons.io.FileUtils;
import org.bukkit.*;
import org.bukkit.World.Environment;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import java.util.*;

public class ScaffoldCommands {
    private static final Random random = new Random();

    @CommandPermissions("scaffold.command.lock")
    @Command(aliases = "lock", desc = "Lock a world at this time.", min = 1, max = 1, usage = "<world>")
    public static void lock(CommandContext cmd, CommandSender sender) {
        if (!sender.hasPermission("scaffold.command.lock")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return;
        }
        String current = "---";
        if (sender instanceof Player)
            current = ((Player) sender).getWorld().getName();

        String worldName = cmd.getString(0, current);
        ScaffoldWorld wrapper = ScaffoldWorld.ofSearch(worldName);

        if (!wrapper.isCreated()) {
            sender.sendMessage(ChatColor.RED + "World not found.");
            return;
        }

        if (!wrapper.isOpen()) {
            sender.sendMessage(ChatColor.RED + "World not open.");
            return;
        }

        boolean locked = Scaffold.instance().toggleLock(wrapper);
        if (locked)
            sender.sendMessage(ChatColor.GOLD + "Locked " + wrapper.getName() + " to current time.");
        else
            sender.sendMessage(ChatColor.GOLD + "Unlocked " + wrapper.getName() + ".");
    }

    @CommandPermissions("scaffold.command.archive")
    @Command(aliases = "archive", desc = "Archive and delete a world (use -k to keep).", min = 1, max = 1, usage = "<world>", flags = "k")
    public static void archive(CommandContext cmd, CommandSender sender) {
        if (!sender.hasPermission("scaffold.command.archive")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return;
        }
        ScaffoldWorld wrapper = ScaffoldWorld.ofSearch(cmd.getString(0));

        if (!wrapper.isCreated()) {
            sender.sendMessage(ChatColor.RED + "World has not been created.");
            return;
        }

        boolean delete = !cmd.hasFlag('k');

        if (wrapper.isOpen() && delete) {
            sender.sendMessage(ChatColor.RED + "World must be closed to archive and delete.");
            return;
        }

        World world = wrapper.getWorld().get();
        world.save();

        File folder = wrapper.getFolder();
        File archives = new File("scaffold-archives");
        String unique = UUID.randomUUID().toString().substring(0, 6);
        File archive = new File(archives, folder.getName() + "-" + unique);

        if (!archives.exists())
            archives.mkdir();

        try {
            FileUtils.copyDirectory(folder, archive);
            if (delete) {
                FileUtils.deleteDirectory(folder);
                sender.sendMessage(ChatColor.GOLD + "You have deleted and archived \"" + wrapper.getName() + "\".");
            }
            else {
                sender.sendMessage(ChatColor.GOLD + "You have archived \"" + wrapper.getName() + "\".");
            }
        } catch (IOException e) {
            e.printStackTrace();
            sender.sendMessage(ChatColor.RED + "An error has occurred. See the server logs.");
        }
    }

    @CommandPermissions("scaffold.command.create")
    @Command(aliases = "create", desc = "Create a new world.", min = 1, max = 1, usage = "<world>", flags = "te")
    public static void create(CommandContext cmd, CommandSender sender) throws CommandNumberFormatException {
        if (!sender.hasPermission("scaffold.command.create")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return;
        }
        ScaffoldWorld wrapper = ScaffoldWorld.ofSearch(cmd.getString(0));

        if (wrapper.isCreated()) {
            sender.sendMessage(ChatColor.RED + "World already created.");
            return;
        }

        if (wrapper.isOpen()) {
            sender.sendMessage(ChatColor.RED + "World already open.");
            return;
        }

        WorldType type = WorldType.valueOf(cmd.getFlag('t', "FLAT"));
        Environment env = Environment.valueOf(cmd.getFlag('t', "NORMAL"));
        long seed = cmd.getFlagInteger('s', random.nextInt(500000000));

        wrapper.create(type, env, seed);
        sender.sendMessage(ChatColor.GOLD + "Created world \"" + wrapper.getName() + "\".");
    }

    @CommandPermissions("scaffold.command.open")
    @Command(aliases = "open", desc = "Open a world.", min = 1, max = 1, usage = "<world>")
    public static void open(CommandContext cmd, CommandSender sender) {
        if (!sender.hasPermission("scaffold.command.open")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return;
        }
        ScaffoldWorld wrapper = ScaffoldWorld.ofSearch(cmd.getString(0));

        if (!wrapper.isCreated()) {
            sender.sendMessage(ChatColor.RED + "World has not been created.");
            return;
        }

        if (!wrapper.isOpen()) {
            wrapper.load();
            sender.sendMessage(ChatColor.GOLD + "Opened world \"" + wrapper.getName() + "\".");
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            player.teleport(wrapper.getWorld().get().getSpawnLocation());
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMEN_TELEPORT, 1, 1);
            player.setGameMode(GameMode.CREATIVE);
            player.setAllowFlight(true);
            player.setFlying(true);
            sender.sendMessage(ChatColor.GOLD + "Teleported to world \"" + wrapper.getName() + "\".");
        }
    }

    @CommandPermissions("scaffold.command.close")
    @Command(aliases = "close", desc = "Close a world.", min = 1, max = 1, usage = "<world>")
    public static void close(CommandContext cmd, CommandSender sender) {
        ScaffoldWorld wrapper = ScaffoldWorld.ofSearch(cmd.getString(0));

        if (!sender.hasPermission("scaffold.command.close")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return;
        }

        if (!wrapper.isCreated()) {
            sender.sendMessage(ChatColor.RED + "World has not been created.");
            return;
        }

        if (!wrapper.isOpen()) {
            sender.sendMessage(ChatColor.RED + "World is not opened.");
            return;
        }

        World main = Bukkit.getWorlds().get(0);

        for (Entity entity : wrapper.getWorld().get().getEntities()) {
            if (entity instanceof Player) {
                Player player = (Player) entity;
                player.sendMessage(ChatColor.RED + sender.getName() + " is unloading this world... Teleporting elsewhere!");
                player.teleport(main.getSpawnLocation());
            }
        }

        boolean unloaded = wrapper.unload();

        if (unloaded)
            sender.sendMessage(ChatColor.GOLD + "Closed world \"" + wrapper.getName() + "\".");
        else
            sender.sendMessage(ChatColor.GOLD + "Failed to unload world \"" + wrapper.getName() + "\".");
    }

    @CommandPermissions("scaffold.command.export")
    @Command(aliases = "export", desc = "Export a world.", max = 1, usage = "<world>")
    public static void upload(CommandContext cmd, CommandSender sender) {
        if (!sender.hasPermission("scaffold.command.upload")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return;
        }

        String current = "---";
        if (sender instanceof Player)
            current = ((Player) sender).getWorld().getName();

        String worldName = cmd.getString(0, current);
        ScaffoldWorld wrapper = ScaffoldWorld.ofSearch(worldName);

        if (!wrapper.isCreated()) {
            sender.sendMessage(ChatColor.RED + "World not found.");
            return;
        }

        Scaffold.instance().async(() -> {
            sender.sendMessage(ChatColor.YELLOW + "Compressing world...");
            String randy = UUID.randomUUID().toString().substring(0, 3);
            File zip = new File(wrapper.getName()  + "-" + randy + ".zip");

            try {
                Zip.create(wrapper.getFolder(), zip);
            } catch (Exception e) {
                e.printStackTrace();
                sender.sendMessage(ChatColor.RED + "Failed to compress.");
                return;
            }

                sender.sendMessage(ChatColor.YELLOW + "Uploading world...");
                try {
                    HttpResponse<String> response = Unirest.post("https://transfer.sh/").header("Max-Downloads", "1").header("Max-Days", "3").field("upload-file", zip).asString();
                    String link = response.getBody();
                    zip.delete();
                    sender.sendMessage(ChatColor.GOLD + "Upload complete: " + link);
                } catch (Exception e) {
                    e.printStackTrace();
                    sender.sendMessage(ChatColor.RED + "Failed to upload, see the server logs.");
                    return;
                }

        });
    }

    @CommandPermissions("scaffold.command.import")
    @Command(aliases = "import", desc = "Import a world.", min = 2, max = 2, usage = "<.zip file link> <world name>")
    public static void download(CommandContext cmd, CommandSender sender) {
        if (!sender.hasPermission("scaffold.command.import")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return;
        }

        String link = cmd.getString(0);
        ScaffoldWorld wrapper = ScaffoldWorld.ofSearch(cmd.getString(1));

        if (wrapper.isCreated()) {
            sender.sendMessage(ChatColor.RED + "World already created.");
            return;
        }

        if (wrapper.getFolder().exists()) try {
            FileUtils.forceDelete(wrapper.getFolder());
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        Bukkit.broadcastMessage(ChatColor.YELLOW + "World import by " + sender.getName() + " beginning...");

        Scaffold.instance().async(() -> {
            try {
                File temp = new File(UUID.randomUUID().toString() + ".zip");
                Files.copy(new URL(cmd.getString(0)).openConnection().getInputStream(), temp.toPath());
                Zip.extract(temp, wrapper.getFolder());
                FileUtils.forceDelete(temp);
                File levelDatParent = findLevelDat(wrapper.getFolder());
                System.out.println("Found level.dat in " + levelDatParent.getName());
                if (!levelDatParent.getPath().equals(wrapper.getFolder().getPath())) {
                    FileUtils.copyDirectory(levelDatParent, wrapper.getFolder());
                    FileUtils.deleteDirectory(levelDatParent);
                }
                if (!wrapper.isCreated()) {
                    sender.sendMessage(ChatColor.RED + "Invalid zipped world, no level.dat found.");
                    FileUtils.deleteDirectory(wrapper.getFolder());
                    return;
                }

                Scaffold.instance().sync(() -> {
                        wrapper.load();
                        sender.sendMessage(ChatColor.GOLD + "World imported and opened!");
                });
            } catch (Exception e) {
                e.printStackTrace();
                sender.sendMessage(ChatColor.RED + e.getMessage());
                sender.sendMessage(ChatColor.RED + "Failed to import, see server logs.");
            }
        });
    }

    private static File findLevelDat(File file) {
        System.out.println("Searching " + file.getName());
        if (new File(file, "level.dat").exists()) return file;
        File[] listFiles = file.listFiles();
        if (listFiles != null) {
            for (File  f : listFiles) {
                File next = findLevelDat(f);
                if (next != null && next.exists()) return next;
            }
        }
        return null;
    }

    @CommandPermissions("scaffold.command.worlds")
    @Command(aliases = "worlds", desc = "Show all worlds.", max = 1, flags = "l", help = "(search)")
    public static void worlds(CommandContext cmd, CommandSender sender) {
        if (!sender.hasPermission("scaffold.command.worlds")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return;
        }
        List<ScaffoldWorld> all = new ArrayList<>();

        File scaffold = new File("scaffold");
        if (scaffold.exists()) {
            File[] contents = scaffold.listFiles();
            if (contents != null) {
                for (File folder : contents) {
                    ScaffoldWorld world = new ScaffoldWorld(folder.getName());
                    if (world.isCreated())
                        all.add(world);
                }
            }
        }

        all.sort(Comparator.comparing(ScaffoldWorld::getName));

        String prefix = "Worlds";

        if (cmd.hasFlag('o')) {
            all.removeIf(wrapper -> !wrapper.isOpen());
            prefix = "Opened worlds";
        }

        if (cmd.argsLength() == 1) {
            String query = cmd.getString(0).toLowerCase();
            all.removeIf(world -> !world.getName().toLowerCase().contains(query));
            prefix += " (matching \"" + cmd.getString(0) + "\")";
        }

        List<String> names = new ArrayList<>();

        for (ScaffoldWorld wrapper : all) {
            if (wrapper.isOpen())
                names.add(ChatColor.GREEN + wrapper.getName());
            else
                names.add(ChatColor.RED + wrapper.getName());
        }


        String list = Joiner.on(ChatColor.WHITE + ", ").join(names);
        sender.sendMessage(ChatColor.GRAY + prefix + ": " + list);
    }
}
