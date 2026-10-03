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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
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
    /** Synced so the client knows it is looking at the Minotaur of Kronos, not an ordinary one. */
    private static final TrackedData<Boolean> KRONOS = DataTracker.registerData(MinotaurEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final Identifier RAGE_SPEED = MazeCraft.id("minotaur_rage_speed");

    private static final int STUN_TICKS = 60;
    private static final float CHARGE_DAMAGE = 16.0f;
    private static final double CHARGE_SPEED = 0.75;

    private static final Identifier PHASE_SPEED = MazeCraft.id("minotaur_phase_speed");

    /** Health fractions at which the Minotaur of Kronos changes phase. */
    private static final float PHASE_2_AT = 2.0f / 3.0f, PHASE_3_AT = 1.0f / 3.0f;
    /** How often phase 3 puts the lights out, and for how long. */
    private static final int DARKNESS_EVERY = 100, DARKNESS_FOR = 140;
    /** Radius within which phase 3 blinds. */
    private static final double ARENA_REACH = 14.0;

    private int stunTicks;
    private int chargeCooldown = 60;
    private boolean enraged;
    /** 1, 2 or 3 — Kronos only; an ordinary Minotaur stays on 1 and uses {@link #enraged}. */
    private int phase = 1;
    /** Centre of the arena it guards (Kronos only); null for an ordinary Minotaur. */
    private BlockPos arena;

    /** How far from the centre of the arena the Minotaur of Kronos is allowed to stray. */
    private static final double LEASH = 11.0;
    /** Beyond this it is put back rather than walked back. */
    private static final double LEASH_HARD = 20.0;

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
        builder.add(KRONOS, false);
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

    public boolean isKronos() {
        return dataTracker.get(KRONOS);
    }

    public int getPhase() {
        return phase;
    }

    /**
     * The Minotaur of Kronos: the one the whole progression leads to, so it is not simply the
     * colossal Minotaur with more health. 400 HP, heavier blows, and three phases rather than
     * the single rage of its lesser kin.
     *
     * <p>Phase 1 is the fight as it has always been. At two thirds it calls the labyrinth
     * itself — the shifting walls of the whole vault go frantic and two guards of the tomb
     * join it. At one third it puts the lights out, in pulses, and gets faster again. The
     * phases only ever go forwards: healing it would not walk them back.</p>
     */
    public void setupForKronos(BlockPos arenaCentre) {
        this.arena = arenaCentre;
        dataTracker.set(KRONOS, true);
        this.experiencePoints = 600;
        setAttribute(EntityAttributes.GENERIC_MAX_HEALTH, 400.0);
        setAttribute(EntityAttributes.GENERIC_ATTACK_DAMAGE, 14.0);
        setAttribute(EntityAttributes.GENERIC_ARMOR, 12.0);
        setAttribute(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
        setAttribute(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
        setHealth(getMaxHealth());
        setCustomName(Text.translatable("mazecraft.kronos.minotaur.name")
                .formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
        setCustomNameVisible(true);
        setPersistent();
    }

    private void setAttribute(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> which,
                              double value) {
        EntityAttributeInstance instance = getAttributeInstance(which);
        if (instance != null) instance.setBaseValue(value);
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

        if (isKronos()) {
            updatePhase();
            keepToTheArena();
            return;
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

    /**
     * Phase changes and the standing effect of the current phase.
     *
     * <p>Driven by health alone and one-way: a phase is entered when health first drops past
     * its threshold and never left. Nothing is saved — the phase is recomputed from health on
     * the first tick after a reload, so a reload cannot land the fight in a state the blocks
     * and the boss disagree about. The only thing lost is the entry roar.</p>
     */
    private void updatePhase() {
        float fraction = getHealth() / getMaxHealth();
        int want = fraction > PHASE_2_AT ? 1 : fraction > PHASE_3_AT ? 2 : 3;
        // One phase at a time, never a jump. A blow big enough to cross both thresholds at
        // once used to skip phase two entirely — no guards of the tomb, no frenzy in the walls
        // — and anything that killed it outright skipped everything. Stepping through means a
        // phase can always be counted on to have happened.
        while (phase < want) {
            enterPhase(++phase);
        }
        if (phase == 3 && age % DARKNESS_EVERY == 0) putOutTheLights();
    }

    /**
     * Keeps the Minotaur of Kronos in its arena.
     *
     * <p>It has a wander goal like any mob, and with no target — nobody in range, or only
     * players in creative, whom vanilla rightly refuses to target — it simply walks out of the
     * open door and goes for a stroll in the labyrinth. Then the arena door has no boss behind
     * it and the fight never starts. This is not a creative-mode quirk: in survival the same
     * thing happens whenever it loses its target long enough.</p>
     *
     * <p>Inside the leash it is left completely alone, so the fight itself is never nudged.
     * Past it, it is walked back; past the hard limit — which means it is out in the corridors
     * and pathing home through a maze — it is simply put back, because a boss that spends five
     * minutes walking home is worse than one that appears.</p>
     */
    private void keepToTheArena() {
        if (arena == null) return;
        double dx = getX() - (arena.getX() + 0.5), dz = getZ() - (arena.getZ() + 0.5);
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= LEASH) return;

        if (distance > LEASH_HARD) {
            if (getWorld() instanceof ServerWorld world) {
                world.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 1.0, getZ(), 20, 0.4, 0.8, 0.4, 0.02);
            }
            requestTeleport(arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5);
            getNavigation().stop();
            if (getWorld() instanceof ServerWorld world) {
                world.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 1.0, getZ(), 20, 0.4, 0.8, 0.4, 0.02);
            }
            return;
        }
        if (age % 20 == 0) {
            getNavigation().startMovingTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, 1.0);
        }
    }

    private void enterPhase(int entered) {
        playSound(ModSounds.MINOTAUR_ROAR, 3.0f, 0.45f);
        if (!(getWorld() instanceof ServerWorld world)) return;
        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 2.8, getZ(), 24, 1.2, 0.6, 1.2, 0.0);

        if (entered == 2) {
            // It calls the labyrinth: the whole vault's walls go frantic, and two guards of
            // the tomb come up out of the floor.
            fr.mazecraft.progression.KronosWalls.setFrenzy(world, getBlockPos(), true);
            summonGuards(world, 2);
        } else if (entered == 3) {
            EntityAttributeInstance speed = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (speed != null && !speed.hasModifier(PHASE_SPEED)) {
                speed.addTemporaryModifier(new EntityAttributeModifier(
                        PHASE_SPEED, 0.35, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            putOutTheLights();
        }
    }

    /**
     * Phase 3: darkness, in pulses rather than continuously.
     *
     * <p>The lanterns of the arena are not actually removed. Taking blocks out of a protected
     * structure means putting them back on every way the fight can end — death, logout, the
     * chunk unloading mid-swing — and a single missed path leaves the arena dark for good. The
     * effect is on the player instead, so nothing has to be undone.</p>
     */
    private void putOutTheLights() {
        // The darkness itself is an effect on the player, and a creative player is rightly
        // immune to it — so without something in the world as well, the whole of phase three
        // is invisible to whoever is testing it. This is the part everybody can see.
        if (getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.5, getZ(),
                    60, 6.0, 1.5, 6.0, 0.01);
            world.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 1.0, getZ(),
                    25, 3.0, 1.0, 3.0, 0.02);
            world.playSound(null, getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_BEACON_DEACTIVATE,
                    net.minecraft.sound.SoundCategory.HOSTILE, 1.6f, 0.4f);
        }
        for (PlayerEntity player : getWorld().getPlayers()) {
            if (player.isSpectator() || player.isCreative()) continue;
            if (player.squaredDistanceTo(this) > ARENA_REACH * ARENA_REACH) continue;
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, DARKNESS_FOR, 0, false, false));
        }
    }

    /** Two guards of the tomb, on either side of it. */
    private void summonGuards(ServerWorld world, int count) {
        for (int i = 0; i < count; i++) {
            WitherSkeletonEntity guard = EntityType.WITHER_SKELETON.create(world);
            if (guard == null) continue;
            // Beside it, but only where there is room: refreshPositionAndAngles does not check
            // for walls, and a guard dropped into the rim of the arena suffocates on arrival.
            double angle = Math.PI * 2 * i / count + random.nextDouble();
            double gx = getX() + Math.cos(angle) * 3.0, gz = getZ() + Math.sin(angle) * 3.0;
            BlockPos spot = BlockPos.ofFloored(gx, getY(), gz);
            if (!world.getBlockState(spot).isAir() || !world.getBlockState(spot.up()).isAir()) {
                gx = getX();
                gz = getZ();
            }
            guard.refreshPositionAndAngles(gx, getY(), gz, random.nextFloat() * 360f, 0f);
            guard.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
            guard.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0f);
            guard.setPersistent();
            guard.setTarget(getTarget());
            world.spawnEntity(guard);
            world.spawnParticles(ParticleTypes.SOUL, guard.getX(), guard.getY() + 1.0, guard.getZ(),
                    20, 0.3, 0.6, 0.3, 0.02);
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

    /**
     * Asterion's death. Granted to everyone still in the arena, not only whoever landed the
     * last blow — the fight is long enough that handing the only reward to the last hit would
     * be a poor way to treat the three other people who fought it.
     *
     * <p>Granted from code rather than by an advancement predicate: the ordinary Bronze
     * Guardian and Asterion are the same entity type, told apart by a tracked field a
     * predicate cannot read.</p>
     */
    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (!isKronos() || getWorld().isClient) return;
        for (PlayerEntity player : getWorld().getPlayers()) {
            if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity served)) continue;
            if (served.isSpectator()) continue;
            if (served.squaredDistanceTo(this) > ARENA_REACH * ARENA_REACH) continue;
            fr.mazecraft.progression.MazeProgress.grant(served, "defeat_asterion");
        }
        if (getWorld() instanceof ServerWorld world) {
            fr.mazecraft.progression.KronosArena.onAsterionSlain(world, getBlockPos());
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (arena != null) {
            nbt.putIntArray("KronosArena", new int[]{arena.getX(), arena.getY(), arena.getZ()});
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        int[] a = nbt.getIntArray("KronosArena");
        if (a.length == 3) arena = new BlockPos(a[0], a[1], a[2]);
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
            // Kronos presses harder each phase; an ordinary Minotaur keeps its two speeds.
            chargeCooldown = isKronos() ? switch (phase) { case 3 -> 40; case 2 -> 60; default -> 90; }
                                        : (enraged ? 60 : 120);
        }
    }
}
