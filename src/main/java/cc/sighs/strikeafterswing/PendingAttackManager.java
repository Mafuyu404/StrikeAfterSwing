package cc.sighs.strikeafterswing;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import cc.sighs.strikeafterswing.mixin.EntityAccessor;
import cc.sighs.strikeafterswing.mixin.LivingEntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public final class PendingAttackManager {
    private static final List<PendingAttack> PENDING_ATTACKS = new LinkedList<>();
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);
    private static final RuntimeMethod DAMAGE_SOURCE_ENTITY = RuntimeMethod.noArgs(DamageSource.class, "getEntity", "m_7639_");
    private static final RuntimeMethod DAMAGE_SOURCE_DIRECT_ENTITY = RuntimeMethod.noArgs(DamageSource.class, "getDirectEntity", "m_7640_");
    private static final RuntimeMethod LEVEL_GAME_TIME = RuntimeMethod.noArgs(ServerLevel.class, "getGameTime", "m_46467_");
    private static final RuntimeMethod ENTITY_HURT = RuntimeMethod.withArgs(Entity.class, "hurt", "m_6469_",
            DamageSource.class, float.class);
    private static final RuntimeMethod LIVING_ENTITY_IS_ALIVE = RuntimeMethod.noArgs(LivingEntity.class, "isAlive", "m_6084_");
    private static final RuntimeMethod ENTITY_IS_ATTACKABLE = RuntimeMethod.noArgs(Entity.class, "isAttackable", "m_6097_");
    private static final RuntimeMethod ENTITY_BB_WIDTH = RuntimeMethod.noArgs(Entity.class, "getBbWidth", "m_20205_");
    private static final RuntimeMethod ENTITY_DISTANCE_TO_SQR = RuntimeMethod.withArgs(Entity.class, "distanceToSqr", "m_20280_",
            Entity.class);

    private PendingAttackManager() {
    }

    public static boolean isBypassing() {
        return BYPASS.get();
    }

    public static boolean delayIncomingMobAttack(Entity target, DamageSource source, float amount) {
        if (BYPASS.get() || !(((EntityAccessor) target).strikeafterswing$getLevel() instanceof ServerLevel)) {
            return false;
        }
        ServerLevel level = (ServerLevel) ((EntityAccessor) target).strikeafterswing$getLevel();

        Entity sourceEntity = (Entity) DAMAGE_SOURCE_ENTITY.invoke(source);
        Entity directEntity = (Entity) DAMAGE_SOURCE_DIRECT_ENTITY.invoke(source);
        if (!(sourceEntity instanceof Mob) || sourceEntity != directEntity) {
            return false;
        }
        Mob attacker = (Mob) sourceEntity;

        return queueAttack(attacker, target, source, amount, level);
    }

    private static boolean queueAttack(Mob attacker, Entity target, DamageSource source, float amount, ServerLevel level) {
        if (isRemoved(attacker) || !isAlive(attacker) || isRemoved(target) || !isAttackable(target)) {
            return false;
        }

        if (hasPendingAttack(attacker)) {
            return true;
        }

        int delayTicks = Math.max(1, ((LivingEntityAccessor) attacker).strikeafterswing$getCurrentSwingDuration());
        PENDING_ATTACKS.add(new PendingAttack(
                attacker,
                target,
                level,
                getGameTime(level) + delayTicks,
                source,
                amount
        ));
        return true;
    }

    public static void tickPendingAttacks() {
        if (PENDING_ATTACKS.isEmpty()) {
            return;
        }

        Iterator<PendingAttack> iterator = PENDING_ATTACKS.iterator();
        while (iterator.hasNext()) {
            PendingAttack attack = iterator.next();

            if (getGameTime(attack.level) < attack.executeGameTime) {
                continue;
            }

            iterator.remove();
            execute(attack);
        }
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
                hurt(target, attack.damageSource, attack.damageAmount);
            }
        } finally {
            BYPASS.set(false);
        }
    }

    private static boolean isStillValid(LivingEntity attacker, Entity target) {
        if (!isAlive(attacker) || isRemoved(attacker) || isRemoved(target) || !isAttackable(target)) {
            return false;
        }

        if (target instanceof LivingEntity && !isAlive((LivingEntity) target)) {
            return false;
        }

        return true;
    }

    private static boolean isRemoved(Entity entity) {
        return ((EntityAccessor) entity).strikeafterswing$getRemovalReason() != null;
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

    private static long getGameTime(ServerLevel level) {
        Object value = LEVEL_GAME_TIME.invoke(level);
        return value instanceof Long ? (Long) value : 0L;
    }

    private static boolean isAlive(LivingEntity entity) {
        Object value = LIVING_ENTITY_IS_ALIVE.invoke(entity);
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean isAttackable(Entity entity) {
        Object value = ENTITY_IS_ATTACKABLE.invoke(entity);
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean hurt(Entity target, DamageSource source, float amount) {
        Object value = ENTITY_HURT.invoke(target, source, amount);
        return value instanceof Boolean && (Boolean) value;
    }

    private static float getBbWidth(Entity entity) {
        Object value = ENTITY_BB_WIDTH.invoke(entity);
        return value instanceof Float ? (Float) value : 0.0F;
    }

    private static double distanceToSqr(Entity source, Entity target) {
        Object value = ENTITY_DISTANCE_TO_SQR.invoke(source, target);
        return value instanceof Double ? (Double) value : Double.MAX_VALUE;
    }

    private static final class PendingAttack {
        private final Mob attacker;
        private final Entity target;
        private final ServerLevel level;
        private final long executeGameTime;
        private final DamageSource damageSource;
        private final float damageAmount;

        private PendingAttack(Mob attacker, Entity target, ServerLevel level, long executeGameTime,
                              DamageSource damageSource, float damageAmount) {
            this.attacker = attacker;
            this.target = target;
            this.level = level;
            this.executeGameTime = executeGameTime;
            this.damageSource = damageSource;
            this.damageAmount = damageAmount;
        }
    }

    private static final class RuntimeMethod {
        private final Class<?> owner;
        private final String[] names;
        private final Class<?>[] parameterTypes;
        private Method method;

        private static RuntimeMethod noArgs(Class<?> owner, String firstName, String secondName) {
            return new RuntimeMethod(owner, new String[] { firstName, secondName }, new Class<?>[0]);
        }

        private static RuntimeMethod withArgs(Class<?> owner, String firstName, String secondName,
                                             Class<?>... parameterTypes) {
            return new RuntimeMethod(owner, new String[] { firstName, secondName }, parameterTypes);
        }

        private RuntimeMethod(Class<?> owner, String[] names, Class<?>[] parameterTypes) {
            this.owner = owner;
            this.names = names;
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

            for (String name : names) {
                try {
                    Method resolved = owner.getMethod(name, parameterTypes);
                    resolved.setAccessible(true);
                    method = resolved;
                    return resolved;
                } catch (NoSuchMethodException ignored) {
                }
            }
            return null;
        }
    }
}
