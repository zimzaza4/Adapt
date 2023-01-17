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

package com.volmit.adapt.content.adaptation.rift;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.content.item.BoundEyeOfEnder;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;


public class RiftGate extends SimpleAdaptation<RiftGate.Config> {
    public RiftGate() {
        super("rift-gate");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("rift", "gate", "description"));
        setDisplayName(Localizer.dLocalize("rift", "gate", "name"));
        setIcon(Material.END_PORTAL_FRAME);
        setBaseCost(0);
        setCostFactor(0);
        setMaxLevel(1);
        setInitialCost(30);
        setInterval(1322);
        registerRecipe(AdaptRecipe.shapeless()
                .key("rift-recall-gate")
                .ingredient(Material.ENDER_PEARL)
                .ingredient(Material.AMETHYST_SHARD)
                .ingredient(Material.EMERALD)
                .result(BoundEyeOfEnder.io.withData(new BoundEyeOfEnder.Data(null)))
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.YELLOW + Localizer.dLocalize("rift", "gate", "lore1"));
        v.addLore(C.RED + Localizer.dLocalize("rift", "gate", "lore2"));
        v.addLore(C.ITALIC + Localizer.dLocalize("rift", "gate", "lore3") + C.UNDERLINE + C.RED + Localizer.dLocalize("rift", "gate", "lore4"));
    }


    @EventHandler
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        Location location;

        ItemStack offhand = p.getInventory().getItemInOffHand();
        if (e.getHand() != null && e.getHand().equals(EquipmentSlot.OFF_HAND) && BoundEyeOfEnder.isBindableItem(offhand)) {
            e.setCancelled(true);
            return;
        }

        if (BoundEyeOfEnder.isBindableItem(hand) && hasAdaptation(p)) {
            e.setCancelled(true);
            if (!hasAdaptation(p)) {
                return;
            }
            if (e.getClickedBlock() == null) {
                location = p.getLocation();
            } else {
                location = new Location(e.getClickedBlock().getLocation().getWorld(), e.getClickedBlock().getLocation().getX() + 0.5, e.getClickedBlock().getLocation().getY() + 1, e.getClickedBlock().getLocation().getZ() + 0.5);
            }
            switch (e.getAction()) {
                case LEFT_CLICK_BLOCK -> {
                    if (p.isSneaking()) {
                        linkEye(p, location);
                    }
                }
                case LEFT_CLICK_AIR -> {
                    if (p.isSneaking() && isBound(hand)) {
                        unlinkEye(p);
                    } else if (p.isSneaking() && !isBound(hand)) {
                        linkEye(p, location);
                    }
                }
                case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> // use
                        openEye(p);
            }
        }
    }


    private void openEye(Player p) {
        Location l = BoundEyeOfEnder.getLocation(p.getInventory().getItemInMainHand());
        ItemStack hand = p.getInventory().getItemInMainHand();

        if (getConfig().consumeOnUse) {
            xp(p, 75);
            decrementItemstack(hand, p);
        }
        if (p.getCooldown(Material.ENDER_EYE) > 0) {
            p.playSound(p.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1, 1);
            return;
        }
        p.setCooldown(Material.ENDER_EYE, 150);

        if (getPlayer(p).getData().getSkillLines().get("rift").getAdaptations().get("rift-resist") != null
                && getPlayer(p).getData().getSkillLines().get("rift").getAdaptations().get("rift-resist").getLevel() > 0) {
            RiftResist.riftResistStackAdd(p, 150, 3);
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 10, true, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 85, 0, true, false, false));
        p.playSound(l, Sound.BLOCK_LODESTONE_PLACE, 1f, 0.1f);
        p.playSound(l, Sound.BLOCK_BELL_RESONATE, 1f, 0.1f);
        J.a(() -> {
            double d = 2;
            double pcd = 1000;
            double y = 0.1;
            while (pcd > 0) {

                for (int i = 0; i < 16; i++) {
                    if (getConfig().showParticles) {

                        p.getWorld().spawnParticle(Particle.ASH, p.getLocation().clone()
                                .add(Vector.getRandom().subtract(Vector.getRandom()).setY(y).normalize().multiply(d)), 1, 0, 0, 0, 0);
                    }
                }
                pcd = pcd - 20;
                d = d - 0.04;
                y = y * 1.07;
                J.sleep(80);
            }
            vfxLevelUp(p);
            p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 5.35f, 0.1f);
            J.s(() -> p.teleport(l, PlayerTeleportEvent.TeleportCause.PLUGIN));
        });
    }

    private boolean isBound(ItemStack stack) {
        return stack.getType().equals(Material.ENDER_EYE) && BoundEyeOfEnder.getLocation(stack) != null;
    }


    private void unlinkEye(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        decrementItemstack(hand, p);
        ItemStack eye = new ItemStack(Material.ENDER_EYE);
        p.getInventory().addItem(eye).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
    }

    private void linkEye(Player p, Location location) {
        if (getConfig().showParticles) {

            vfxSingleCuboidOutline(location.getBlock(), location.add(0, 1, 0).getBlock(), Particle.REVERSE_PORTAL);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 0.50f, 0.22f);
        ItemStack hand = p.getInventory().getItemInMainHand();

        if (hand.getAmount() == 1) {
            BoundEyeOfEnder.setData(hand, location);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            ItemStack eye = BoundEyeOfEnder.withData(location);
            p.getInventory().addItem(eye).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        }
    }


    @Override
    public void onTick() {
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        boolean consumeOnUse = true;
        boolean showParticles = true;
    }
}