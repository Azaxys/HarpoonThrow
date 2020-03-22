package com.github.stevendesroches.spells;

import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.spells.InstantSpell;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.MagicConfig;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class HarpoonThrow extends InstantSpell {


    float speed;
    boolean vertSpeedUsed;
    float vertSpeed;
    float hitRadius;
    boolean projectileHasGravity;
    int specialEffectInterval = 0;


    public HarpoonThrow(MagicConfig config, String spellName) {
        super(config, spellName);

        this.speed = getConfigFloat("speed", 1);
        this.vertSpeedUsed = configKeyExists("vert-speed");
        this.hitRadius = getConfigFloat("hit-radius", 1F);
        this.projectileHasGravity = getConfigBoolean("gravity", true);
        this.vertSpeed = getConfigFloat("vert-speed", 0);
        this.specialEffectInterval = getConfigInt("special-effect-interval", 0);
    }


    @Override
    public PostCastAction castSpell(Player player, SpellCastState state, float power, String[] args) {
        if (state == SpellCastState.NORMAL) {
            new ThrowRunnable(player, power);
        }
        return PostCastAction.HANDLE_NORMALLY;
    }

    class ThrowRunnable implements Runnable {

        Player caster;
        float power;
        Vector vel;
        Snowball stand;
        Boolean hit = false;
        List<Entity> targetList = new ArrayList<>();

        int taskId;
        int count = 0;

        public ThrowRunnable(Player caster, float power) {
            this.caster = caster;
            this.power = power;

            if (vertSpeedUsed) {
                this.vel = caster.getEyeLocation().getDirection().setY(0).multiply(speed).setY(vertSpeed);
            } else {
                this.vel = caster.getEyeLocation().getDirection().multiply(speed);
            }

            Location spawnLoc = caster.getEyeLocation().clone();
            this.stand = caster.getWorld().spawn(spawnLoc, Snowball.class);
            this.stand.setGravity(false);

            this.taskId = MagicSpells.scheduleRepeatingTask(this, 1, 1);
        }

        @Override
        public void run() {
            count++;
            if (count == 25) {
                stop();
            }
            if (hit) {
                Vector currentVel = this.stand.getLocation().toVector();
                Vector casterVel = caster.getLocation().toVector();

                casterVel.setY(casterVel.getY());
                this.vel = casterVel.subtract(currentVel).normalize();
                Vector entityVel = this.vel;
                entityVel.multiply(0.90);
                for (Entity e : targetList) {
                    e.setGravity(false);
                    e.setVelocity(entityVel);
                }
            }
            Vector standVel = this.vel;
            standVel.multiply(1.15);
            this.stand.teleport(this.stand.getLocation().add(standVel));

            playSpellEffects(EffectPosition.PROJECTILE, this.stand);
            playSpellEffects(this.caster, this.stand);
            //playTrackingLinePatterns(EffectPosition.DYNAMIC_CASTER_PROJECTILE_LINE, caster.getLocation(), this.stand.getLocation(), caster, this.stand);

            for (Entity e : this.stand.getNearbyEntities(hitRadius, hitRadius, hitRadius)) {
                if (hit && e.getUniqueId() == this.caster.getUniqueId()) {
                    stop();
                }
                if (e instanceof LivingEntity && validTargetList.canTarget(this.caster, e)) {
                    SpellTargetEvent event = new SpellTargetEvent(HarpoonThrow.this, this.caster, (LivingEntity) e, this.power);
                    EventUtil.call(event);
                    if (!event.isCancelled()) {
                        if (!hit) {
                            count = 0;
                            ((LivingEntity) e).damage(0);
                            targetList.add(e);
                        }
                        this.hit = true;
                        return;
                    }
                }
            }
            if ((stand.getLocation().getBlock().getRelative(BlockFace.DOWN).getType() != Material.AIR && stand.getLocation().getBlock().getRelative(BlockFace.DOWN).getType() != Material.GRASS && stand.getLocation().getBlock().getRelative(BlockFace.DOWN).getType() != Material.LONG_GRASS) || stand.isOnGround()) {
                this.hit = true;
                count = 0;
            }

        }

        void stop() {
            this.stand.remove();

            for (Entity e : targetList) {
                e.setGravity(true);
                e.setFallDistance(0);
            }

            this.targetList.clear();
            MagicSpells.cancelTask(this.taskId);
        }
    }

}