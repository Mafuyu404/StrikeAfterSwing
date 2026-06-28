package cc.sighs.strikeafterswing.fabric26;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import cc.sighs.strikeafterswing.fabric26.mixin.Fabric26EntityAccessor;
import cc.sighs.strikeafterswing.fabric26.mixin.Fabric26LivingEntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public final class Fabric26PendingAttackManager {
    private static final List<PendingAttack> PENDING_ATTACKS = new LinkedList<>();
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);
    private static final RuntimeMethod DAMAGE_SOURCE_ENTITY = RuntimeMethod.noArgs(DamageSource.class, "getEntity");
    private static final RuntimeMethod DAMAGE_SOURCE_DIRECT_ENTITY = RuntimeMethod.noArgs(DamageSource.class, "getDirectEntity");
    private static final RuntimeMethod ENTITY_IS_REMOVED = RuntimeMethod.noArgs(Entity.class, "isRemoved");
    private static final RuntimeMethod ENTITY_IS_ATTACKABLE = RuntimeMethod.noArgs(Entity.class, "isAttackable");
    private static final RuntimeMethod ENTITY_BB_WIDTH = RuntimeMethod.noArgs(Entity.class, "getBbWidth");
    private static final RuntimeMethod ENTITY_DISTANCE_TO_SQR = RuntimeMethod.withArgs(Entity.class, "distanceToSqr", Entity.class);
    private static final RuntimeMethod LIVING_ENTITY_IS_ALIVE = RuntimeMethod.noArgs(LivingEntity.class, "isAlive");
    private static final RuntimeMethod LIVING_ENTITY_HURT_SERVER = RuntimeMethod.withArgs(LivingEntity.class, "hurtServer",
            ServerLevel.class, DamageSource.class, float.class);
    private static final RuntimeMethod ENTITY_HURT_OR_SIMULATE = RuntimeMethod.withArgs(Entity.class, "hurtOrSimulate",
            DamageSource.class, float.class);
    private static long tickCounter;

    private Fabric26PendingAttackManager() {
    }

    public static boolean delayIncomingMobAttack(Entity target, DamageSource source, float amount) {
        if (BYPASS.get() || !(((Fabric26EntityAccessor) target).strikeafterswing$getLevel() instanceof ServerLevel)) {
            return false;
        }
        ServerLevel level = (ServerLevel) ((Fabric26EntityAccessor) target).strikeafterswing$getLevel();

        Entity sourceEntity = (Entity) DAMAGE_SOURCE_ENTITY.invoke(source);
        Entity directEntity = (Entity) DAMAGE_SOURCE_DIRECT_ENTITY.invoke(source);
        if (!(sourceEntity instanceof Mob) || sourceEntity != directEntity) {
            return false;
        }

        return queueAttack((Mob) sourceEntity, target, source, amount, level);
    }

    public static void tickPendingAttacks() {
        tickCounter++;
        if (PENDING_ATTACKS.isEmpty()) {
            return;
        }

        Iterator<PendingAttack> iterator = PENDING_ATTACKS.iterator();
        while (iterator.hasNext()) {
            PendingAttack attack = iterator.next();
            if (tickCounter < attack.executeTick) {
                continue;
            }

            iterator.remove();
            execute(attack);
        }
    }

    private static boolean queueAttack(Mob attacker, Entity target, DamageSource source, float amount, ServerLevel level) {
        if (isRemoved(attacker) || !isAlive(attacker) || isRemoved(target) || !isAttackable(target)) {
            return false;
        }

        if (hasPendingAttack(attacker)) {
            return true;
        }

        int delayTicks = Math.max(1, ((Fabric26LivingEntityAccessor) attacker).strikeafterswing$getCurrentSwingDuration());
        PENDING_ATTACKS.add(new PendingAttack(attacker, target, level, tickCounter + delayTicks, source, amount));
        return true;
    }

    private static void execute(PendingAttack attack) {
        Mob attacker = attack.attacker;
        Entity target = attack.target;
        if (!isStillValid(attacker, target)) {
            return;
        }

        BYPASS.set(true);
        try {
            if (isMobAttackStillValid(attacker, target)) {
                hurt(target, attack.level, attack.damageSource, attack.damageAmount);
            }
        } finally {
            BYPASS.set(false);
        }
    }

    private static boolean isStillValid(LivingEntity attacker, Entity target) {
        if (!isAlive(attacker) || isRemoved(attacker) || isRemoved(target) || !isAttackable(target)) {
            return false;
        }

        return !(target instanceof LivingEntity) || isAlive((LivingEntity) target);
    }

    private static boolean hasPendingAttack(Mob attacker) {
        for (PendingAttack attack : PENDING_ATTACKS) {
            if (attack.attacker == attacker) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMobAttackStillValid(Mob attacker, Entity target) {
        if (!(target instanceof LivingEntity)) {
            return false;
        }

        double reach = getBbWidth(attacker) * 2.0F;
        return distanceToSqr(attacker, target) < reach * reach + getBbWidth(target);
    }

    private static boolean hurt(Entity target, ServerLevel level, DamageSource source, float amount) {
        if (target instanceof LivingEntity) {
            Object result = LIVING_ENTITY_HURT_SERVER.invoke(target, level, source, amount);
            return result instanceof Boolean && (Boolean) result;
        }

        Object result = ENTITY_HURT_OR_SIMULATE.invoke(target, source, amount);
        return result instanceof Boolean && (Boolean) result;
    }

    private static boolean isRemoved(Entity entity) {
        Object result = ENTITY_IS_REMOVED.invoke(entity);
        return result instanceof Boolean && (Boolean) result;
    }

    private static boolean isAttackable(Entity entity) {
        Object result = ENTITY_IS_ATTACKABLE.invoke(entity);
        return result instanceof Boolean && (Boolean) result;
    }

    private static boolean isAlive(LivingEntity entity) {
        Object result = LIVING_ENTITY_IS_ALIVE.invoke(entity);
        return result instanceof Boolean && (Boolean) result;
    }

    private static float getBbWidth(Entity entity) {
        Object result = ENTITY_BB_WIDTH.invoke(entity);
        return result instanceof Float ? (Float) result : 0.0F;
    }

    private static double distanceToSqr(Entity source, Entity target) {
        Object result = ENTITY_DISTANCE_TO_SQR.invoke(source, target);
        return result instanceof Double ? (Double) result : Double.MAX_VALUE;
    }

    private static final class PendingAttack {
        private final Mob attacker;
        private final Entity target;
        private final ServerLevel level;
        private final long executeTick;
        private final DamageSource damageSource;
        private final float damageAmount;

        private PendingAttack(Mob attacker, Entity target, ServerLevel level, long executeTick,
                              DamageSource damageSource, float damageAmount) {
            this.attacker = attacker;
            this.target = target;
            this.level = level;
            this.executeTick = executeTick;
            this.damageSource = damageSource;
            this.damageAmount = damageAmount;
        }
    }

    private static final class RuntimeMethod {
        private final Class<?> owner;
        private final String name;
        private final Class<?>[] parameterTypes;
        private Method method;

        private static RuntimeMethod noArgs(Class<?> owner, String name) {
            return new RuntimeMethod(owner, name, new Class<?>[0]);
        }

        private static RuntimeMethod withArgs(Class<?> owner, String name, Class<?>... parameterTypes) {
            return new RuntimeMethod(owner, name, parameterTypes);
        }

        private RuntimeMethod(Class<?> owner, String name, Class<?>[] parameterTypes) {
            this.owner = owner;
            this.name = name;
            this.parameterTypes = parameterTypes;
        }

        private Object invoke(Object instance, Object... args) {
            Method resolved = resolve();
            if (resolved == null) {
                return null;
            }

            try {
                return resolved.invoke(instance, args);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private Method resolve() {
            if (method != null) {
                return method;
            }

            try {
                Method resolved = owner.getMethod(name, parameterTypes);
                resolved.setAccessible(true);
                method = resolved;
                return resolved;
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
    }
}
