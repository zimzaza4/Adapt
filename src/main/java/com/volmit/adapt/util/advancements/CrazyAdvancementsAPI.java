/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.adapt.util.advancements;

import com.google.gson.*;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.advancements.advancement.*;
import com.volmit.adapt.util.advancements.advancement.AdvancementDisplay.AdvancementFrame;
import com.volmit.adapt.util.advancements.advancement.criteria.CriteriaType;
import com.volmit.adapt.util.advancements.advancement.progress.GenericResult;
import com.volmit.adapt.util.advancements.advancement.progress.GrantCriteriaResult;
import com.volmit.adapt.util.advancements.advancement.serialized.SerializedAdvancement;
import com.volmit.adapt.util.advancements.advancement.serialized.SerializedAdvancementDisplay;
import com.volmit.adapt.util.advancements.command.ProgressChangeOperation;
import com.volmit.adapt.util.advancements.item.CustomItem;
import com.volmit.adapt.util.advancements.item.SerializedCustomItem;
import com.volmit.adapt.util.advancements.manager.AdvancementManager;
import com.volmit.adapt.util.advancements.packet.AdvancementsPacket;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionInstance;
import net.minecraft.advancements.critereon.LootSerializationContext;
import net.minecraft.network.protocol.game.PacketPlayOutSelectAdvancementTab;
import net.minecraft.resources.MinecraftKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Represents the API's Plugin
 *
 * @author Axel
 */
public class CrazyAdvancementsAPI extends JavaPlugin implements Listener {

    public static final String API_NAMESPACE = "crazy_advancements";
    /**
     * Criterion Instance for Internal Use
     */
    public static final Criterion CRITERION = new Criterion(new CriterionInstance() {
        @Override
        public JsonObject a(LootSerializationContext arg0) {
            return null;
        }

        @Override
        public MinecraftKey a() {
            return new MinecraftKey("", "");
        }
    });
    private static final Gson gson;
    private static final List<String> SELECTORS = Arrays.asList("@a", "@p", "@s", "@r");
    private static final HashMap<String, NameKey> activeTabs = new HashMap<>();
    private static CrazyAdvancementsAPI instance;
    private static AdvancementPacketReceiver packetReciever;

    static {
        gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    }

    private final List<CustomItem> customItems = new ArrayList<>();
    private final String noPermission = "I'm sorry but you do not have permission to perform this command. Please contact the server administrator if you believe that this is an error.";
    private final String commandIncompatible = "This Command is incompatible with your Arguments!";
    private AdvancementManager fileAdvancementManager;

    /**
     * Gets the Instance
     *
     * @return The Instance
     */
    public static CrazyAdvancementsAPI getInstance() {
        return instance;
    }

    /**
     * Gets the Gson Instance
     *
     * @return The Gson Instance
     */
    public static Gson getGson() {
        return gson;
    }

    /**
     * Clears the active tab
     *
     * @param player The player whose Tab should be cleared
     */
    public static void clearActiveTab(Player player) {
        setActiveTab(player, null, true);
    }

    /**
     * Sets the active tab
     *
     * @param player          The player whose Tab should be changed
     * @param rootAdvancement The name of the tab to change to
     */
    public static void setActiveTab(Player player, String rootAdvancement) {
        setActiveTab(player, new NameKey(rootAdvancement));
    }

    /**
     * Sets the active tab
     *
     * @param player          The player whose Tab should be changed
     * @param rootAdvancement The name of the tab to change to
     */
    public static void setActiveTab(Player player, @Nullable NameKey rootAdvancement) {
        setActiveTab(player, rootAdvancement, true);
    }

    static void setActiveTab(Player player, NameKey rootAdvancement, boolean update) {
        if (update) {
            PacketPlayOutSelectAdvancementTab packet = new PacketPlayOutSelectAdvancementTab(rootAdvancement == null ? null : rootAdvancement.getMinecraftKey());
            ((CraftPlayer) player).getHandle().b.a(packet);
        }
        activeTabs.put(player.getUniqueId().toString(), rootAdvancement);
    }

    /**
     * Gets the active tab
     *
     * @param player Player to check
     * @return The active Tab
     */
    public static NameKey getActiveTab(Player player) {
        return activeTabs.get(player.getUniqueId().toString());
    }

    /**
     * Reloads the API<br>
     * Currently reloads JSON Advancements and Custom Item Definitions
     */
    public void reload() {
        loadCustomItems();
        reloadFileAdvancements();
    }

    private void reloadFileAdvancements() {
        if (fileAdvancementManager != null) {
            for (Player player : new ArrayList<>(fileAdvancementManager.getPlayers())) {
                fileAdvancementManager.removePlayer(player);
            }
            fileAdvancementManager.resetAccessible();
        }
        fileAdvancementManager = new AdvancementManager(new NameKey(API_NAMESPACE, "file"));
        fileAdvancementManager.makeAccessible();
        loadFileAdvancements();

        for (Player player : Bukkit.getOnlinePlayers()) {
            packetReciever.initPlayer(player);
            fileAdvancementManager.loadProgress(player);
            fileAdvancementManager.addPlayer(player);
        }
    }

    @Override
    public void onLoad() {
        instance = this;
        loadCustomItems();
        fileAdvancementManager = new AdvancementManager(new NameKey(API_NAMESPACE, "file"));
        fileAdvancementManager.makeAccessible();
        loadFileAdvancements();
    }

    private void loadCustomItems() {
        File location = new File(getDataFolder().getAbsolutePath() + File.separator + "custom_items" + File.separator);

        customItems.clear();

        location.mkdirs();
        File[] files = location.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                String namespace = file.getName();
                customItems.addAll(loadCustomItemsFromNamespace(namespace, "", file));
            }
        }

        System.out.println("Loaded " + customItems.size() + " Custom Items");
    }

    private List<CustomItem> loadCustomItemsFromNamespace(String namespace, String path, File location) {
        File[] files = location.listFiles();

        List<CustomItem> items = new ArrayList<>();

        for (File file : files) {
            if (file.isDirectory()) {
                items.addAll(loadCustomItemsFromNamespace(namespace, path + file.getName() + "/", file));
            } else if (file.isFile() && file.getName().endsWith(".json")) {
                FileReader os = null;
                try {
                    os = new FileReader(file);

                    JsonElement element = JsonParser.parseReader(os);
                    os.close();

                    SerializedCustomItem item = gson.fromJson(element, SerializedCustomItem.class);

                    String fileName = file.getName();
                    String key = fileName.substring(0, fileName.length() - 5);//Remove .json
                    items.add(item.deserialize(new NameKey(namespace, path + key)));
                } catch (Exception e) {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                    System.err.println("Unable to load Custom Item from File " + namespace + "/" + file.getName() + ": " + e.getLocalizedMessage());
                }
            }
        }
        return items;
    }

    private void loadFileAdvancements() {
        File location = new File(getDataFolder().getAbsolutePath() + File.separator + "advancements" + File.separator);

        HashMap<NameKey, SerializedAdvancement> advancements = new HashMap<NameKey, SerializedAdvancement>();

        location.mkdirs();
        File[] files = location.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                String namespace = file.getName();
                advancements.putAll(loadAdvancementsFromNamespace(namespace, "", file));
            }
        }

        List<NameKey> missingAdvancements = new ArrayList<>(advancements.keySet());
        HashMap<NameKey, Advancement> createdAdvancements = new HashMap<NameKey, Advancement>();

        while (missingAdvancements.size() > 0) {
            Iterator<NameKey> missingIterator = missingAdvancements.iterator();
            int processedAdvancements = 0;

            while (missingIterator.hasNext()) {
                NameKey name = missingIterator.next();
                SerializedAdvancement serializedAdvancement = advancements.get(name);
                NameKey parent = serializedAdvancement.getParent();

                if (parent == null || createdAdvancements.containsKey(parent)) {
                    SerializedAdvancementDisplay serializedAdvancementDisplay = serializedAdvancement.getDisplay();

                    //Generate Display
                    ItemStack icon = getItemStack(serializedAdvancementDisplay.getIcon());
                    JSONMessage title = new JSONMessage(serializedAdvancementDisplay.getTitle().deserialize());
                    JSONMessage description = new JSONMessage(serializedAdvancementDisplay.getDescription().deserialize());
                    AdvancementFrame frame = AdvancementFrame.parse(serializedAdvancementDisplay.getFrame());
                    AdvancementVisibility visibility = AdvancementVisibility.parseVisibility(serializedAdvancementDisplay.getVisibility());

                    AdvancementDisplay display = new AdvancementDisplay(icon, title, description, frame, visibility);

                    if (serializedAdvancementDisplay.getBackgroundTexture() != null) {
                        display.setBackgroundTexture(serializedAdvancementDisplay.getBackgroundTexture());
                    }

                    display.setX(serializedAdvancementDisplay.getX());
                    display.setY(serializedAdvancementDisplay.getY());

                    //Generate Advancement
                    List<AdvancementFlag> flags = new ArrayList<>();
                    if (serializedAdvancement.getFlags() != null) {
                        for (String flagName : serializedAdvancement.getFlags()) {
                            flags.add(AdvancementFlag.valueOf(flagName.toUpperCase(Locale.ROOT)));
                        }
                    }

                    Advancement advancement = new Advancement(parent == null ? null : createdAdvancements.get(parent), name, display, flags.toArray(AdvancementFlag[]::new));
                    if (serializedAdvancement.getCriteria() != null) {
                        advancement.setCriteria(serializedAdvancement.getCriteria().deserialize());
                    }
                    advancement.setReward(serializedAdvancement.getReward());

                    //Register
                    fileAdvancementManager.addAdvancement(advancement);
                    missingIterator.remove();
                    createdAdvancements.put(name, advancement);
                    processedAdvancements++;
                }
            }

            //Abort adding Advancements if no advancements were able to be processed
            if (processedAdvancements == 0) {
                for (NameKey name : missingAdvancements) {
                    System.err.println("Unable to load Advancement " + name + ": Parent does not exist");
                }
                break;
            }
        }
    }

    private HashMap<NameKey, SerializedAdvancement> loadAdvancementsFromNamespace(String namespace, String path, File location) {
        File[] files = location.listFiles();

        HashMap<NameKey, SerializedAdvancement> advancements = new HashMap<NameKey, SerializedAdvancement>();

        for (File file : files) {
            if (file.isDirectory()) {
                advancements.putAll(loadAdvancementsFromNamespace(namespace, path + file.getName() + "/", file));
            } else if (file.isFile() && file.getName().endsWith(".json")) {
                FileReader os = null;
                try {
                    os = new FileReader(file);

                    JsonElement element = JsonParser.parseReader(os);
                    os.close();

                    SerializedAdvancement advancement = gson.fromJson(element, SerializedAdvancement.class);

                    String fileName = file.getName();
                    String key = fileName.substring(0, fileName.length() - 5);//Remove .json
                    advancements.put(new NameKey(namespace, path + key), advancement);
                } catch (Exception e) {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                    System.err.println("Unable to load Advancement from File " + namespace + "/" + file.getName() + ": " + e.getLocalizedMessage());
                }
            }
        }
        return advancements;
    }

    @Override
    public void onEnable() {
        //Init Packet Receiver
        packetReciever = new AdvancementPacketReceiver();

        for (Player player : Bukkit.getOnlinePlayers()) {
            packetReciever.initPlayer(player);
            fileAdvancementManager.loadProgress(player);
            fileAdvancementManager.addPlayer(player);
        }

        //Register Events
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            AdvancementsPacket packet = new AdvancementsPacket(player, true, null, null);
            packet.send();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {

        Player player = e.getPlayer();
        packetReciever.initPlayer(player);

        //Add Player to File Advancement Manager
        fileAdvancementManager.loadProgress(player);
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {

            @Override
            public void run() {
                fileAdvancementManager.addPlayer(player);
            }
        }, 2);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        packetReciever.close(p, packetReciever.getHandlers().get(p.getName()));

        //Unload Progress in the File Advancement Manager
        fileAdvancementManager.unloadProgress(p.getUniqueId());
        fileAdvancementManager.unloadVisibilityStatus(p.getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("showtoast")) {
            if (sender.hasPermission("crazyadvancements.command.*") || sender.hasPermission("crazyadvancements.command.showtoast")) {
                if (args.length >= 3) {
                    try {
                        List<Player> players = new ArrayList<>();
                        for (Entity entity : Bukkit.selectEntities(sender, args[0])) {
                            if (entity instanceof Player) {
                                players.add((Player) entity);
                            }
                        }

                        if (players.size() > 0) {
                            ItemStack stack = getItemStack(args[1], sender);

                            if (stack != null) {
                                int messageStartIndex = 2;
                                AdvancementFrame frame = AdvancementFrame.parseStrict(args[2]);
                                if (frame == null) {
                                    frame = AdvancementFrame.TASK;
                                } else {
                                    messageStartIndex = 3;
                                }
                                String message = args[messageStartIndex];
                                if (args.length > messageStartIndex + 1) {
                                    for (int i = messageStartIndex + 1; i < args.length; i++) {
                                        message += " " + args[i];
                                    }
                                }

                                for (Player player : players) {
                                    ToastNotification toast = new ToastNotification(stack, message, frame);
                                    toast.send(player);
                                }

                                Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), players.size() == 1 ? "Successfully displayed Toast to " + players.get(0).getName() + "!" : "Successfully displayed Toast to " + players.size() + "Players!");
                            } else {
                                Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "'" + args[1] + "' isn't a valid Item Material");
                            }
                        } else {
                            Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), args[0].startsWith("@") ? "No Player found for Selector " + args[0] + "" : "Can't find Player '" + args[0] + "'");
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), commandIncompatible);
                    }


                } else {
                    Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "Usage: " + cmd.getUsage());
                }
            } else {
                Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), noPermission);
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("grant") || cmd.getName().equalsIgnoreCase("revoke")) {
            boolean grant = cmd.getName().equalsIgnoreCase("grant");
            if (sender.hasPermission("crazyadvancements.command.*") || sender.hasPermission("crazyadvancements.command.grantrevoke")) {
                if (args.length >= 3) {
                    try {
                        List<Player> players = new ArrayList<>();
                        for (Entity entity : Bukkit.selectEntities(sender, args[0])) {
                            if (entity instanceof Player) {
                                players.add((Player) entity);
                            }
                        }

                        if (players.size() > 0) {
                            AdvancementManager manager = AdvancementManager.getAccessibleManager(new NameKey(args[1]));

                            if (manager != null) {
                                for (Player player : players) {
                                    if (manager.getPlayers().contains(player)) {
                                        Advancement advancement = manager.getAdvancement(new NameKey(args[2]));

                                        if (advancement != null) {
                                            if (args.length >= 4) {

                                                String[] convertedCriteria = Arrays.copyOfRange(args, 3, args.length);

                                                boolean success = false;

                                                if (grant) {
                                                    if (!advancement.isGranted(player)) {
                                                        GrantCriteriaResult result = manager.grantCriteria(player, advancement, convertedCriteria);
                                                        success = result == GrantCriteriaResult.CHANGED;
                                                    }
                                                } else {
                                                    GenericResult result = manager.revokeCriteria(player, advancement, convertedCriteria);
                                                    success = result == GenericResult.CHANGED;
                                                }

                                                String criteriaString = "" + convertedCriteria[0];
                                                if (convertedCriteria.length > 1) {
                                                    for (String criteria : Arrays.copyOfRange(convertedCriteria, 1, convertedCriteria.length - 1)) {
                                                        criteriaString += ", " + criteria;
                                                    }
                                                    criteriaString += " and " + convertedCriteria[convertedCriteria.length - 1];
                                                }

                                                if (success) {
                                                    if (fileAdvancementManager.equals(manager)) {
                                                        fileAdvancementManager.saveProgress(player, advancement);
                                                    }
                                                    Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "Successfully " + (grant ? "granted" : "revoked") + " Criteria " + criteriaString + " for '" + advancement.getName() + "' " + (grant ? "to" : "from") + " " + player.getName());
                                                } else {
                                                    Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "Criteria " + criteriaString + " for '" + advancement.getName() + "' " + (grant ? "is already granted to" : "is already not granted to") + " " + player.getName());
                                                }

                                            } else {
                                                boolean success = false;

                                                if (grant) {
                                                    if (!advancement.isGranted(player)) {
                                                        GenericResult result = manager.grantAdvancement(player, advancement);
                                                        success = result == GenericResult.CHANGED;
                                                    }
                                                } else {
                                                    GenericResult result = manager.revokeAdvancement(player, advancement);
                                                    success = result == GenericResult.CHANGED;
                                                }

                                                if (success) {
                                                    if (fileAdvancementManager.equals(manager)) {
                                                        fileAdvancementManager.saveProgress(player, advancement);
                                                    }
                                                    Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "Successfully " + (grant ? "granted" : "revoked") + " Advancement '" + advancement.getName() + "' " + (grant ? "to" : "from") + " " + player.getName());
                                                } else {
                                                    Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "Advancement '" + advancement.getName() + "' " + (grant ? "is already granted to" : "is already not granted to") + " " + player.getName());
                                                }
                                            }

                                        } else {
                                            Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "Advancement with Name '" + args[2] + "' does not exist in '" + args[1] + "'");
                                        }
                                    } else {
                                        Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "'" + args[1] + "' does not contain Player '" + args[0] + "'");
                                    }
                                }
                            } else {
                                Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "Manager with Name '" + args[1] + "' does not exist");
                            }
                        } else {
                            Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), args[0].startsWith("@") ? "No Player found for Selector " + args[0] + "" : "Can't find Player '" + args[0] + "'");
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), commandIncompatible);
                    }

                } else {
                    Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "Usage: " + cmd.getUsage());
                }
            } else {
                Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), noPermission);
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("setprogress")) {
            if (sender.hasPermission("crazyadvancements.command.*") || sender.hasPermission("crazyadvancements.command.grantrevoke")) {
                if (args.length >= 3) {
                    try {
                        List<Player> players = new ArrayList<>();
                        for (Entity entity : Bukkit.selectEntities(sender, args[0])) {
                            if (entity instanceof Player) {
                                players.add((Player) entity);
                            }
                        }

                        if (players.size() > 0) {
                            AdvancementManager manager = AdvancementManager.getAccessibleManager(new NameKey(args[1]));

                            if (manager != null) {
                                for (Player player : players) {
                                    if (manager.getPlayers().contains(player)) {
                                        Advancement advancement = manager.getAdvancement(new NameKey(args[2]));

                                        if (advancement != null) {
                                            if (args.length >= 4) {
                                                int number = Integer.parseInt(args[3]);
                                                ProgressChangeOperation operation = args.length >= 5 ? ProgressChangeOperation.parse(args[4]) : ProgressChangeOperation.SET;

                                                int currentProgress = advancement.getProgress(player).getCriteriaProgress();
                                                int progress = operation.apply(currentProgress, number);

                                                manager.setCriteriaProgress(player, advancement, progress);

                                                if (fileAdvancementManager.equals(manager)) {
                                                    fileAdvancementManager.saveProgress(player, advancement);
                                                }

                                                Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "Successfully updated Criteria Progress for Advancement '" + advancement.getName() + "' for Player " + player.getName());
                                            }

                                        } else {
                                            Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "Advancement with Name '" + args[2] + "' does not exist in '" + args[1] + "'");
                                        }

                                    } else {
                                        Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "'" + args[1] + "' does not contain Player '" + args[0] + "'");
                                    }
                                }
                            } else {
                                Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "Manager with Name '" + args[1] + "' does not exist");
                            }
                        } else {
                            Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), args[0].startsWith("@") ? "No Player found for Selector " + args[0] + "" : "Can't find Player '" + args[0] + "'");
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), commandIncompatible);
                    }

                } else {
                    Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "Usage: " + cmd.getUsage());
                }
            } else {
                Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), noPermission);
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("careload")) {
            if (sender.hasPermission("crazyadvancements.command.*") || sender.hasPermission("crazyadvancements.command.reload")) {
                if (args.length > 0) {
                    switch (args[0].toLowerCase()) {
                        case "all":
                            reload();
                            Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "Crazy Advancements API was reloaded");
                            break;
                        case "advancements":
                            reloadFileAdvancements();
                            Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "JSON Advancements have been reloaded");
                            break;
                        case "items":
                            loadCustomItems();
                            Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "Custom Items have been reloaded");
                            break;
                        default:
                            Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "Invalid Reload Category '" + args[0] + "'. Valid categories are all, advancements, items");
                            break;
                    }
                } else {
                    reload();
                    Adapt.messagePlayer(sender.getServer().getPlayer(sender.getName()), "Crazy Advancements API was reloaded");
                }
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        ArrayList<String> tab = new ArrayList<>();

        if (cmd.getName().equalsIgnoreCase("showtoast")) {
            if (sender.hasPermission("crazyadvancements.command.*") || sender.hasPermission("crazyadvancements.command.showtoast")) {
                if (args.length == 1) {
                    for (String selector : SELECTORS) {
                        if (selector.toLowerCase().startsWith(args[0].toLowerCase())) {
                            tab.add(selector);
                        }
                    }
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                            tab.add(player.getName());
                        }
                    }
                } else if (args.length == 2) {
                    for (Material mat : Material.values()) {
                        if (mat.isItem() && mat.name().toLowerCase().startsWith(args[1].toLowerCase())) {
                            tab.add(mat.name().toLowerCase());
                        }
                    }
                    for (CustomItem customItem : customItems) {
                        if (customItem.getName().toString().startsWith(args[1].toLowerCase())) {
                            tab.add(customItem.getName().toString());
                        }
                    }
                } else if (args.length == 3) {
                    for (AdvancementFrame frame : AdvancementFrame.values()) {
                        if (frame.name().toLowerCase().startsWith(args[2].toLowerCase())) {
                            tab.add(frame.name().toLowerCase());
                        }
                    }
                }
            }
        }

        if (cmd.getName().equalsIgnoreCase("grant") || cmd.getName().equalsIgnoreCase("revoke")) {
            if (sender.hasPermission("crazyadvancements.command.*") || sender.hasPermission("crazyadvancements.command.grantrevoke")) {
                if (args.length == 1) {
                    for (String selector : SELECTORS) {
                        if (selector.toLowerCase().startsWith(args[0].toLowerCase())) {
                            tab.add(selector);
                        }
                    }
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                            tab.add(player.getName());
                        }
                    }
                } else if (args.length == 2) {
                    for (AdvancementManager manager : AdvancementManager.getAccessibleManagers()) {
                        if (manager.getName().toString().startsWith(args[1].toLowerCase())) {
                            tab.add(manager.getName().toString());
                        }
                    }
                } else if (args.length == 3) {
                    AdvancementManager manager = AdvancementManager.getAccessibleManager(new NameKey(args[1]));
                    if (manager != null) {
                        for (Advancement advancement : manager.getAdvancements()) {
                            if (advancement.getName().toString().startsWith(args[2].toLowerCase()) || advancement.getName().getKey().startsWith(args[2].toLowerCase())) {
                                tab.add(advancement.getName().toString());
                            }
                        }
                    }
                } else if (args.length >= 4) {
                    AdvancementManager manager = AdvancementManager.getAccessibleManager(new NameKey(args[1]));
                    if (manager != null) {
                        Advancement advancement = manager.getAdvancement(new NameKey(args[2]));
                        if (advancement != null) {
                            for (String criterion : advancement.getCriteria().getActionNames()) {
                                if (criterion.toLowerCase().startsWith(args[args.length - 1].toLowerCase())) {
                                    tab.add(criterion);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (cmd.getName().equalsIgnoreCase("setprogress")) {
            if (sender.hasPermission("crazyadvancements.command.*") || sender.hasPermission("crazyadvancements.command.grantrevoke")) {
                if (args.length == 1) {
                    for (String selector : SELECTORS) {
                        if (selector.toLowerCase().startsWith(args[0].toLowerCase())) {
                            tab.add(selector);
                        }
                    }
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                            tab.add(player.getName());
                        }
                    }
                } else if (args.length == 2) {
                    for (AdvancementManager manager : AdvancementManager.getAccessibleManagers()) {
                        if (manager.getName().toString().startsWith(args[1].toLowerCase())) {
                            tab.add(manager.getName().toString());
                        }
                    }
                } else if (args.length == 3) {
                    AdvancementManager manager = AdvancementManager.getAccessibleManager(new NameKey(args[1]));
                    if (manager != null) {
                        for (Advancement advancement : manager.getAdvancements()) {
                            if ((advancement.getName().toString().startsWith(args[2].toLowerCase()) || advancement.getName().getKey().startsWith(args[2].toLowerCase())) && advancement.getCriteria().getType() == CriteriaType.NUMBER) {
                                tab.add(advancement.getName().toString());
                            }
                        }
                    }
                } else if (args.length == 4) {
                    AdvancementManager manager = AdvancementManager.getAccessibleManager(new NameKey(args[1]));
                    if (manager != null) {
                        Advancement advancement = manager.getAdvancement(new NameKey(args[2]));
                        if (advancement != null && advancement.getCriteria().getType() == CriteriaType.NUMBER) {
                            tab.add(args[3]);
                            tab.add("" + advancement.getCriteria().getRequiredNumber());
                        }
                    }
                } else if (args.length == 5) {
                    AdvancementManager manager = AdvancementManager.getAccessibleManager(new NameKey(args[1]));
                    if (manager != null) {
                        Advancement advancement = manager.getAdvancement(new NameKey(args[2]));
                        if (advancement != null && advancement.getCriteria().getType() == CriteriaType.NUMBER) {
                            for (ProgressChangeOperation operation : ProgressChangeOperation.values()) {
                                if (operation.name().toLowerCase().startsWith(args[4].toLowerCase())) {
                                    tab.add(operation.name().toLowerCase());
                                }
                            }
                        }
                    }
                }
            }
        }

        if (cmd.getName().equalsIgnoreCase("careload")) {
            if (sender.hasPermission("crazyadvancements.command.*") || sender.hasPermission("crazyadvancements.command.reload")) {
                if ("all".startsWith(args[0])) {
                    tab.add("all");
                }
                if ("advancements".startsWith(args[0])) {
                    tab.add("advancements");
                }
                if ("items".startsWith(args[0])) {
                    tab.add("items");
                }
            }
        }

        return tab;
    }

    private Material getMaterial(String input) {
        for (Material mat : Material.values()) {
            if (mat.name().equalsIgnoreCase(input)) {
                return mat;
            }
        }
        return Material.matchMaterial(input);
    }

    private CustomItem getCustomItem(String input) {
        NameKey inputName = new NameKey(input);
        for (CustomItem item : customItems) {
            if (item.getName().isSimilar(inputName)) {
                return item;
            }
        }
        return null;
    }

    private ItemStack getItemStack(String input, CommandSender... commandSender) {
        int colonIndex = input.indexOf(':');
        String materialName = colonIndex == -1 ? input : input.substring(0, colonIndex);
        String data = colonIndex == -1 ? "" : input.substring(colonIndex + 1);
        Material material = getMaterial(materialName);

        ItemStack stack;

        if (material == null || !material.isItem()) {
            CustomItem customItem = getCustomItem(input);
            if (customItem == null) {
                return null;
            } else {
                material = customItem.getType();
                stack = new ItemStack(material);
                ItemMeta meta = stack.getItemMeta();
                meta.setCustomModelData(customItem.getCustomModelData());
                stack.setItemMeta(meta);
            }
        } else {
            stack = new ItemStack(material);
        }

        switch (material) {
            case PLAYER_HEAD:
                if (!data.isEmpty()) {
                    if (commandSender.length > 0) {
                        for (String selector : SELECTORS) {
                            if (data.startsWith(selector)) {
                                List<Player> players = new ArrayList<>();
                                for (Entity entity : Bukkit.selectEntities(commandSender[0], data)) {
                                    if (entity instanceof Player) {
                                        players.add((Player) entity);
                                    }
                                }

                                if (players.size() > 0) {
                                    Player player = players.get(0);
                                    SkullMeta meta = (SkullMeta) stack.getItemMeta();
                                    meta.setOwningPlayer(player);
                                    stack.setItemMeta(meta);
                                }
                                return stack;
                            }
                        }
                    }
                    OfflinePlayer player = Bukkit.getOfflinePlayer(data);
                    SkullMeta meta = (SkullMeta) stack.getItemMeta();
                    meta.setOwningPlayer(player);
                    stack.setItemMeta(meta);
                }
            default:
                if (!data.isEmpty() && material.getMaxDurability() > 0) {
                    try {
                        short damage = Short.parseShort(data);
                        Damageable meta = (Damageable) stack.getItemMeta();
                        meta.setDamage(damage);
                        stack.setItemMeta(meta);
                    } catch (ClassCastException | NumberFormatException ignored) {
                        Adapt.verbose("Invalid damage value for item " + input);
                    }
                }
                return stack;
        }
    }

}