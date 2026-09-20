package fr.mazecraft.entity;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.structure.MazeSize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * The Minotaur — boss of large and colossal mazes.
 * <ul>
 *   <li>Heavy melee blows with strong knockback.</li>
 *   <li><b>Charge</b>: roars (1 s wind-up), then rushes in a straight line and can't turn.
 *       Hitting the player deals big damage; hitting a wall <b>stuns</b> it for 3 s
 *       (takes +50% damage while stunned).</li>
 *   <li><b>Rage</b> under 50% health: faster, charges and strikes more often.</li>
 * </ul>
 * State is synced to the client for the animations.
 */
public class MinotaurEntity extends HostileEntity {

    public static final int STATE_IDLE = 0, STATE_WINDUP = 1, STATE_CHARGING = 2, STATE_STUNNED = 3;

    private static final TrackedData<Integer> STATE = DataTracker.registerData(MinotaurEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final Identifier RAGE_SPEED = MazeCraft.id("minotaur_rage_speed");

    private static final int STUN_TICKS = 60;
    private static final float CHARGE_DAMAGE = 16.0f;
    private static final double CHARGE_SPEED = 0.75;

    private int stunTicks;
    private int chargeCooldown = 60;
    private boolean enraged;

    public MinotaurEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 100;
    }

    public static DefaultAttributeContainer.Builder createMinotaurAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 150.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.0)
                .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 1.5)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.9)
                .add(EntityAttributes.GENERIC_ARMOR, 8.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0);
    }

    /** Health (and XP) by maze size: large 150 HP / 100 XP, colossal 250 HP / 200 XP. */
    public void setupForMaze(MazeSize size) {
        this.experiencePoints = size == MazeSize.COLOSSAL ? 200 : 100;
        EntityAttributeInstance health = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(size == MazeSize.COLOSSAL ? 250.0 : 150.0);
            setHealth(getMaxHealth());
        }
        setPersistent();
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(STATE, STATE_IDLE);
    }

    public int getState() {
        return dataTracker.get(STATE);
    }

    void setState(int state) {
        dataTracker.set(STATE, state);
    }

    public boolean isStunned() {
        return stunTicks > 0;
    }

    public boolean isEnraged() {
        return enraged;
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new StunnedGoal());
        goalSelector.add(1, new SwimGoal(this));
        goalSelector.add(2, new ChargeGoal());
        goalSelector.add(3, new MinotaurMeleeGoal());
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 16.0f));
        goalSelector.add(7, new LookAroundGoal(this));
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (chargeCooldown > 0) chargeCooldown--;

        if (stunTicks > 0) {
            stunTicks--;
            if (getWorld() instanceof ServerWorld world && age % 4 == 0) {
                world.spawnParticles(ParticleTypes.CRIT, getX(), getY() + 3.0, getZ(), 4, 0.4, 0.1, 0.4, 0.05);
            }
            if (stunTicks == 0) setState(STATE_IDLE);
        }

        if (!enraged && getHealth() < getMaxHealth() / 2) {
            enraged = true;
            EntityAttributeInstance speed = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (speed != null && !speed.hasModifier(RAGE_SPEED)) {
                speed.addTemporaryModifier(new EntityAttributeModifier(RAGE_SPEED, 0.3, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            playSound(ModSounds.MINOTAUR_ROAR, 2.5f, 0.6f);
            if (getWorld() instanceof ServerWorld world) {
                world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 2.8, getZ(), 8, 0.6, 0.3, 0.6, 0.0);
            }
        }
    }

    private void stun() {
        stunTicks = STUN_TICKS;
        setState(STATE_STUNNED);
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        playSound(ModSounds.MINOTAUR_STUNNED, 1.5f, 1.0f);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return super.damage(source, isStunned() ? amount * 1.5f : amount);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.MINOTAUR_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.MINOTAUR_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MINOTAUR_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state) {
        playSound(ModSounds.MINOTAUR_STEP, 0.8f, 1.0f);
    }

    @Override
    public float getSoundPitch() {
        return 0.7f + random.nextFloat() * 0.1f;
    }

    // =====================================================================
    // Goals
    // =====================================================================

    /** While stunned: holds every control, so no other goal (move, look, attack) can run. */
    private class StunnedGoal extends Goal {
        StunnedGoal() {
            setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
        }

        @Override
        public boolean canStart() {
            return isStunned();
        }

        @Override
        public void tick() {
            getNavigation().stop();
        }
    }

    /** Melee with a cooldown that shortens in rage. */
    private class MinotaurMeleeGoal extends MeleeAttackGoal {
        MinotaurMeleeGoal() {
            super(MinotaurEntity.this, 1.0, true);
        }

        @Override
        protected int getMaxCooldown() {
            return enraged ? 14 : 24;
        }
    }

    /** Wind-up then straight-line charge. */
    private class ChargeGoal extends Goal {
        private static final int WINDUP = 20;
        private static final int MAX_RUN = 40;

        private int ticks;
        private boolean running;
        private Vec3d direction = Vec3d.ZERO;

        ChargeGoal() {
            setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
        }

        @Override
        public boolean canStart() {
            LivingEntity target = getTarget();
            if (isStunned() || chargeCooldown > 0 || target == null || !target.isAlive() || !isOnGround()) return false;
            double d2 = squaredDistanceTo(target);
            return d2 > 6 * 6 && d2 < 24 * 24 && canSee(target) && Math.abs(target.getY() - getY()) < 2.0;
        }

        @Override
        public boolean shouldContinue() {
            if (isStunned()) return false;
            if (running) return ticks > 0;
            LivingEntity target = getTarget();
            return ticks > 0 && target != null && target.isAlive();
        }

        @Override
        public void start() {
            running = false;
            ticks = WINDUP;
            setState(STATE_WINDUP);
            getNavigation().stop();
            playSound(ModSounds.MINOTAUR_ROAR, 2.0f, 0.8f);
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (!running) {
                if (target != null) {
                    getLookControl().lookAt(target, 30f, 30f);
                    faceToward(target.getPos());
                }
                if (--ticks <= 0 && target != null) {
                    Vec3d d = target.getPos().subtract(getPos());
                    direction = new Vec3d(d.x, 0, d.z).normalize();
                    running = true;
                    ticks = MAX_RUN;
                    setState(STATE_CHARGING);
                }
                return;
            }

            ticks--;
            double speed = enraged ? CHARGE_SPEED * 1.2 : CHARGE_SPEED;
            setVelocity(direction.x * speed, getVelocity().y, direction.z * speed);
            velocityModified = true;
            float yaw = (float) (MathHelper.atan2(direction.z, direction.x) * (180f / Math.PI)) - 90f;
            setYaw(yaw);
            setBodyYaw(yaw);
            setHeadYaw(yaw);

            if (target != null && getBoundingBox().expand(0.4).intersects(target.getBoundingBox())) {
                target.damage(getDamageSources().mobAttack(MinotaurEntity.this), CHARGE_DAMAGE);
                target.takeKnockback(2.5, -direction.x, -direction.z);
                target.velocityModified = true;
                playSound(ModSounds.MINOTAUR_CHARGE_HIT, 2.0f, 1.0f);
                ticks = 0;
                return;
            }
            if (horizontalCollision && age > 5) {
                stun();
                ticks = 0;
            }
        }

        private void faceToward(Vec3d pos) {
            double dx = pos.x - getX(), dz = pos.z - getZ();
            float yaw = (float) (MathHelper.atan2(dz, dx) * (180f / Math.PI)) - 90f;
            setYaw(yaw);
            setBodyYaw(yaw);
        }

        @Override
        public void stop() {
            running = false;
            if (!isStunned()) setState(STATE_IDLE);
            chargeCooldown = enraged ? 60 : 120;
        }
    }
}
