package cc.sighs.strikeafterswing.fabric;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public final class FabricPendingAttackManager {
    private static final List<PendingAttack> PENDING_ATTACKS = new LinkedList<>();
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);
    private static final RuntimeMethod LEVEL_GAME_TIME = RuntimeMethod.noArgs(ServerLevel.class,
            "getGameTime", "method_8510", "method_8412");
    private static final RuntimeMethod ENTITY_IS_REMOVED = RuntimeMethod.noArgs(Entity.class,
            "isRemoved", "method_31481");
    private static final RuntimeMethod LIVING_ENTITY_SWING_DURATION = RuntimeMethod.noArgs(LivingEntity.class,
            "getCurrentSwingDuration", "method_6054");
    private static final RuntimeField ENTITY_LEVEL = new RuntimeField(Entity.class, "level", "field_6002");
    private static final RuntimeField ENTITY_REMOVED = new RuntimeField(Entity.class, "removed", "field_5960");

    private FabricPendingAttackManager() {
    }

    public static boolean delayIncomingMobAttack(Entity target, DamageSource source, float amount) {
        Object targetLevel = ENTITY_LEVEL.get(target);
        if (BYPASS.get() || !(targetLevel instanceof ServerLevel)) {
            return false;
        }
        ServerLevel level = (ServerLevel) targetLevel;

        Entity sourceEntity = source.getEntity();
        Entity directEntity = source.getDirectEntity();
        if (!(sourceEntity instanceof Mob) || sourceEntity != directEntity) {
            return false;
        }

        return queueAttack((Mob) sourceEntity, target, source, amount, level);
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

    private static boolean queueAttack(Mob attacker, Entity target, DamageSource source, float amount, ServerLevel level) {
        if (isRemoved(attacker) || !isAlive(attacker) || isRemoved(target) || !isAttackable(target)) {
            return false;
        }

        if (hasPendingAttack(attacker)) {
            return true;
        }

        int delayTicks = Math.max(1, getCurrentSwingDuration(attacker));
        PENDING_ATTACKS.add(new PendingAttack(attacker, target, level, getGameTime(level) + delayTicks, source, amount));
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

        return !(target instanceof LivingEntity) || isAlive((LivingEntity) target);
    }

    private static boolean isRemoved(Entity entity) {
        Object methodValue = ENTITY_IS_REMOVED.invoke(entity);
        if (methodValue instanceof Boolean) {
            return (Boolean) methodValue;
        }

        Object fieldValue = ENTITY_REMOVED.get(entity);
        return fieldValue instanceof Boolean && (Boolean) fieldValue;
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

        double reach = attacker.getBbWidth() * 2.0F;
        return attacker.distanceToSqr(target) < reach * reach + target.getBbWidth();
    }

    private static boolean isAlive(LivingEntity entity) {
        return entity.isAlive();
    }

    private static boolean isAttackable(Entity entity) {
        return entity.isAttackable();
    }

    private static boolean hurt(Entity target, DamageSource source, float amount) {
        return target.hurt(source, amount);
    }

    private static long getGameTime(ServerLevel level) {
        Object value = LEVEL_GAME_TIME.invoke(level);
        return value instanceof Long ? (Long) value : 0L;
    }

    private static int getCurrentSwingDuration(LivingEntity entity) {
        Object value = LIVING_ENTITY_SWING_DURATION.invoke(entity);
        return value instanceof Integer ? (Integer) value : 6;
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
        private Method method;

        private static RuntimeMethod noArgs(Class<?> owner, String... names) {
            return new RuntimeMethod(owner, names);
        }

        private RuntimeMethod(Class<?> owner, String[] names) {
            this.owner = owner;
            this.names = names;
        }

        private Object invoke(Object instance) {
            Method resolved = resolve();
            if (resolved == null) {
                return null;
            }

            try {
                return resolved.invoke(instance);
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
                    Method resolved = owner.getMethod(name);
                    resolved.setAccessible(true);
                    method = resolved;
                    return resolved;
                } catch (NoSuchMethodException ignored) {
                }
            }
            return null;
        }
    }

    private static final class RuntimeField {
        private final Class<?> owner;
        private final String[] names;
        private Field field;

        private RuntimeField(Class<?> owner, String... names) {
            this.owner = owner;
            this.names = names;
        }

        private Object get(Object instance) {
            Field resolved = resolve();
            if (resolved == null) {
                return null;
            }

            try {
                return resolved.get(instance);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private Field resolve() {
            if (field != null) {
                return field;
            }

            for (String name : names) {
                try {
                    Field resolved = owner.getDeclaredField(name);
                    resolved.setAccessible(true);
                    field = resolved;
                    return resolved;
                } catch (NoSuchFieldException ignored) {
                }
            }
            return null;
        }
    }
}
