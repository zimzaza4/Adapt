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

package com.volmit.adapt.content.skill;

import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.crafting.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.advancements.advancement.AdvancementDisplay;
import com.volmit.adapt.util.advancements.advancement.AdvancementVisibility;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class SkillCrafting extends SimpleSkill<SkillCrafting.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillCrafting() {
        super("crafting", Localizer.dLocalize("skill", "crafting", "icon"));
        registerConfiguration(Config.class);
        setColor(C.YELLOW);
        setDescription(Localizer.dLocalize("skill", "crafting", "description"));
        setDisplayName(Localizer.dLocalize("skill", "crafting", "name"));
        setInterval(3789);
        setIcon(Material.CRAFTING_TABLE);
        registerAdaptation(new CraftingDeconstruction());
        registerAdaptation(new CraftingXP());
        registerAdaptation(new CraftingLeather());
        registerAdaptation(new CraftingSkulls());
        registerAdaptation(new CraftingBackpacks());
        registerAdaptation(new CraftingStations());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.CRAFTING_TABLE).key("challenge_craft_1k")
                .title(Localizer.dLocalize("advancement", "challenge_craft_1k", "title"))
                .description(Localizer.dLocalize("advancement", "challenge_craft_1k", "description"))
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                        .icon(Material.CRAFTING_TABLE)
                        .key("challenge_craft_5k")
                        .title(Localizer.dLocalize("advancement", "challenge_craft_5k", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_craft_5k", "description"))
                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.CRAFTING_TABLE)
                                .key("challenge_craft_50k")
                                .title(Localizer.dLocalize("advancement", "challenge_craft_50k", "title"))
                                .description(Localizer.dLocalize("advancement", "challenge_craft_50k", "description"))
                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                        .icon(Material.CRAFTING_TABLE)
                                        .key("challenge_craft_500k")
                                        .title(Localizer.dLocalize("advancement", "challenge_craft_500k", "title"))
                                        .description(Localizer.dLocalize("advancement", "challenge_craft_500k", "description"))
                                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                                .icon(Material.CRAFTING_TABLE)
                                                .key("challenge_craft_5m")
                                                .title(Localizer.dLocalize("advancement", "challenge_craft_5m", "title"))
                                                .description(Localizer.dLocalize("advancement", "challenge_craft_5m", "description"))
                                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_block_1k").goal(1000).stat("blocked.hits").reward(getConfig().challengeCraft1kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_block_5k").goal(5000).stat("blocked.hits").reward(getConfig().challengeCraft1kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_block_50k").goal(50000).stat("blocked.hits").reward(getConfig().challengeCraft1kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_block_500k").goal(500000).stat("blocked.hits").reward(getConfig().challengeCraft1kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_block_5m").goal(5000000).stat("blocked.hits").reward(getConfig().challengeCraft1kReward).build());

        cooldowns = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(CraftItemEvent e) {
        if (!this.isEnabled() || e.isCancelled()) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR))) {
            return;
        }

        if (cooldowns.containsKey(p)) {
            if (cooldowns.get(p) + getConfig().cooldownDelay > System.currentTimeMillis()) {
                return;
            } else {
                cooldowns.remove(p);
            }
        }
        cooldowns.put(p, System.currentTimeMillis());

        if (e.getInventory().getResult() != null && !e.isCancelled() && e.getInventory().getResult().getAmount() > 0) {
            if (e.getInventory().getResult() != null && e.getCursor() != null && e.getCursor().getAmount() < 64) {
                if (p.getInventory().addItem(e.getCurrentItem()).isEmpty()) {
                    p.getInventory().removeItem(e.getCurrentItem());
                    ItemStack test = e.getRecipe().getResult().clone();
                    int recipeAmount = e.getInventory().getResult().getAmount();
                    switch (e.getClick()) {
                        case NUMBER_KEY:
                            if (e.getWhoClicked().getInventory().getItem(e.getHotbarButton()) != null) {
                                recipeAmount = 0;
                            }
                            break;

                        case DROP:
                        case CONTROL_DROP:
                            ItemStack cursor = e.getCursor();
                            if (!(cursor == null || cursor.getType().isAir())) {
                                recipeAmount = 0;
                            }
                            break;

                        case SHIFT_RIGHT:
                        case SHIFT_LEFT:
                            if (recipeAmount == 0) {
                                break;
                            }

                            int maxCraftable = getMaxCraftAmount(e.getInventory());
                            int capacity = fits(test, e.getView().getBottomInventory());
                            if (capacity < maxCraftable) {
                                maxCraftable = ((capacity + recipeAmount - 1) / recipeAmount) * recipeAmount;
                            }

                            recipeAmount = maxCraftable;
                            break;
                        default:
                    }

                    if (recipeAmount > 0 && !e.isCancelled()) {
                        double v = recipeAmount * getValue(test) * getConfig().craftingValueXPMultiplier;


                        getPlayer((Player) e.getWhoClicked()).getData().addStat("crafted.items", recipeAmount);
                        getPlayer((Player) e.getWhoClicked()).getData().addStat("crafted.value", v);


                        xp((Player) e.getWhoClicked(), v + getConfig().baseCraftingXP);
                    }
                }
            }
        }
    }

    private int fits(ItemStack stack, Inventory inv) {
        ItemStack[] contents = inv.getContents();
        int result = 0;

        for (ItemStack is : contents)
            if (is == null)
                result += stack.getMaxStackSize();
            else if (is.isSimilar(stack))
                result += Math.max(stack.getMaxStackSize() - is.getAmount(), 0);

        return result;
    }

    private int getMaxCraftAmount(CraftingInventory inv) {
        if (inv.getResult() == null) {
            return 0;
        }

        int resultCount = inv.getResult().getAmount();
        int materialCount = Integer.MAX_VALUE;

        for (ItemStack is : inv.getMatrix()) {
            if (is != null && is.getAmount() < materialCount) {
                materialCount = is.getAmount();
            }
        }

        return resultCount * materialCount;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(FurnaceSmeltEvent e) {
        if (!this.isEnabled()) {
            return;
        }
        if (e.isCancelled()) {
            return;
        }
        if (AdaptConfig.get().blacklistedWorlds.contains(e.getBlock().getWorld().getName())) {
            return;
        }

        xp(e.getBlock().getLocation(), getConfig().furnaceBaseXP + (getValue(e.getResult()) * getConfig().furnaceValueXPMultiplier), getConfig().furnaceXPRadius, getConfig().furnaceXPDuration);
    }

    @Override
    public void onTick() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            checkStatTrackers(getPlayer(i));
            if (AdaptConfig.get().blacklistedWorlds.contains(i.getWorld().getName())) {
                return;
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        double furnaceBaseXP = 24;
        double furnaceValueXPMultiplier = 4;
        int furnaceXPRadius = 32;
        long cooldownDelay = 10000;
        long furnaceXPDuration = 10000;
        double craftingValueXPMultiplier = 1;
        double baseCraftingXP = 0.25;
        double challengeCraft1kReward = 1200;
    }
}
