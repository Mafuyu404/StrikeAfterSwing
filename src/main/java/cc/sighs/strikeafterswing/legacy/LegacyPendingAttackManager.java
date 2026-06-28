package cc.sighs.strikeafterswing.legacy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public final class LegacyPendingAttackManager {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("StrikeAfterSwingLegacy");
    private static final List<PendingAttack> PENDING_ATTACKS = new LinkedList<>();
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    private static final RuntimeMethod DAMAGE_SOURCE_ENTITY = RuntimeMethod.noArgs("func_76346_g");
    private static final RuntimeMethod DAMAGE_SOURCE_DIRECT_ENTITY = RuntimeMethod.noArgs("func_76364_f");
    private static final RuntimeMethod WORLD_GAME_TIME = RuntimeMethod.noArgs("func_82737_E");
    private static final RuntimeMethod MOB_ATTACK_TARGET = RuntimeMethod.withArgs("func_70652_k", "net.minecraft.entity.Entity");
    private static final RuntimeMethod ENTITY_IS_ALIVE = RuntimeMethod.noArgs("func_70089_S");
    private static final RuntimeMethod ENTITY_IS_ATTACKABLE = RuntimeMethod.noArgs("func_70067_L");
    private static final RuntimeMethod ENTITY_BB_WIDTH = RuntimeMethod.noArgs("func_213311_cf");
    private static final RuntimeMethod ENTITY_DISTANCE_TO_SQR = RuntimeMethod.withArgs("func_70068_e", "net.minecraft.entity.Entity");
    private static final RuntimeMethod SWING_DURATION = RuntimeMethod.noArgs("func_82166_i");
    private static final RuntimeField ENTITY_LEVEL = new RuntimeField("field_70170_p");
    private static final RuntimeField ENTITY_REMOVED = new RuntimeField("field_70128_L");

    private LegacyPendingAttackManager() {
    }

    public static boolean delayIncomingMobAttack(Object target, Object source, float amount) {
        if (BYPASS.get()) {
            return false;
        }

        Object level = ENTITY_LEVEL.get(target);
        if (level == null || !"net.minecraft.world.server.ServerWorld".equals(level.getClass().getName())) {
            return false;
        }

        Object sourceEntity = DAMAGE_SOURCE_ENTITY.invoke(source);
        Object directEntity = DAMAGE_SOURCE_DIRECT_ENTITY.invoke(source);
        if (sourceEntity == null || (directEntity != null && sourceEntity != directEntity) || !isMob(sourceEntity)) {
            LOGGER.info("skip source={}, direct={}, sourceClass={}", sourceEntity, directEntity,
                    sourceEntity == null ? "null" : sourceEntity.getClass().getName());
            return false;
        }

        return queueAttack(sourceEntity, target, source, amount, level);
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

    private static boolean queueAttack(Object attacker, Object target, Object source, float amount, Object level) {
        if (isRemoved(attacker) || !isAlive(attacker) || isRemoved(target) || !isAttackable(target)) {
            LOGGER.info("queue invalid attackerRemoved={} attackerAlive={} targetRemoved={} targetAttackable={}",
                    isRemoved(attacker), isAlive(attacker), isRemoved(target), isAttackable(target));
            return false;
        }

        if (hasPendingAttack(attacker)) {
            LOGGER.info("already pending attacker={}", attacker);
            return true;
        }

        int delayTicks = Math.max(1, asInt(SWING_DURATION.invoke(attacker)));
        long executeGameTime = getGameTime(level) + delayTicks;
        PENDING_ATTACKS.add(new PendingAttack(attacker, target, level, executeGameTime, source, amount));
        LOGGER.info("queued attacker={} target={} now={} execute={} delay={} amount={}",
                attacker, target, getGameTime(level), executeGameTime, delayTicks, amount);
        return true;
    }

    private static void execute(PendingAttack attack) {
        if (!isStillValid(attack.attacker, attack.target)) {
            LOGGER.info("execute invalid attackerAlive={} attackerRemoved={} targetAlive={} targetRemoved={} targetAttackable={}",
                    isAlive(attack.attacker), isRemoved(attack.attacker), isAlive(attack.target),
                    isRemoved(attack.target), isAttackable(attack.target));
            return;
        }

        BYPASS.set(true);
        try {
            if (isMobAttackStillValid(attack.attacker, attack.target)) {
                Object result = MOB_ATTACK_TARGET.invoke(attack.attacker, attack.target);
                LOGGER.info("execute attack result={} attacker={} target={}", result, attack.attacker, attack.target);
            } else {
                LOGGER.info("execute out of range distance={} attackerWidth={} targetWidth={}",
                        distanceToSqr(attack.attacker, attack.target), getBbWidth(attack.attacker),
                        getBbWidth(attack.target));
            }
        } finally {
            BYPASS.set(false);
        }
    }

    private static boolean isStillValid(Object attacker, Object target) {
        return isAlive(attacker) && !isRemoved(attacker) && !isRemoved(target)
                && isAttackable(target) && isAlive(target);
    }

    private static boolean isMobAttackStillValid(Object attacker, Object target) {
        double reach = getBbWidth(attacker) * 2.0F;
        return distanceToSqr(attacker, target) < reach * reach + getBbWidth(target);
    }

    private static boolean hasPendingAttack(Object attacker) {
        for (PendingAttack attack : PENDING_ATTACKS) {
            if (attack.attacker == attacker) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMob(Object entity) {
        return isInstance("net.minecraft.entity.MobEntity", entity);
    }

    private static boolean isAlive(Object entity) {
        Object value = ENTITY_IS_ALIVE.invoke(entity);
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean isAttackable(Object entity) {
        Object value = ENTITY_IS_ATTACKABLE.invoke(entity);
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean isRemoved(Object entity) {
        Object value = ENTITY_REMOVED.get(entity);
        return value instanceof Boolean && (Boolean) value;
    }

    private static long getGameTime(Object level) {
        Object value = WORLD_GAME_TIME.invoke(level);
        return value instanceof Long ? (Long) value : 0L;
    }

    private static float getBbWidth(Object entity) {
        Object value = ENTITY_BB_WIDTH.invoke(entity);
        return value instanceof Float ? (Float) value : 0.0F;
    }

    private static double distanceToSqr(Object source, Object target) {
        Object value = ENTITY_DISTANCE_TO_SQR.invoke(source, target);
        return value instanceof Double ? (Double) value : Double.MAX_VALUE;
    }

    private static int asInt(Object value) {
        return value instanceof Integer ? (Integer) value : 1;
    }

    private static boolean isInstance(String className, Object instance) {
        try {
            return Class.forName(className, false, LegacyPendingAttackManager.class.getClassLoader()).isInstance(instance);
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static final class PendingAttack {
        private final Object attacker;
        private final Object target;
        private final Object level;
        private final long executeGameTime;
        private final Object damageSource;
        private final float damageAmount;

        private PendingAttack(Object attacker, Object target, Object level, long executeGameTime,
                              Object damageSource, float damageAmount) {
            this.attacker = attacker;
            this.target = target;
            this.level = level;
            this.executeGameTime = executeGameTime;
            this.damageSource = damageSource;
            this.damageAmount = damageAmount;
        }
    }

    private static final class RuntimeMethod {
        private final String name;
        private final Object[] parameterTypes;
        private Method method;

        private static RuntimeMethod noArgs(String name) {
            return new RuntimeMethod(name);
        }

        private static RuntimeMethod withArgs(String name, Object... parameterTypes) {
            return new RuntimeMethod(name, parameterTypes);
        }

        private RuntimeMethod(String name, Object... parameterTypes) {
            this.name = name;
            this.parameterTypes = parameterTypes;
        }

        private Object invoke(Object instance, Object... args) {
            Method resolved = resolve(instance);
            if (resolved == null) {
                return null;
            }

            try {
                return resolved.invoke(instance, args);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private Method resolve(Object instance) {
            if (method != null) {
                return method;
            }

            Class<?>[] resolvedParameters = resolveParameterTypes();
            if (resolvedParameters == null) {
                return null;
            }

            try {
                Method resolved = findMethod(instance.getClass(), resolvedParameters);
                resolved.setAccessible(true);
                method = resolved;
                return resolved;
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }

        private Method findMethod(Class<?> type, Class<?>[] resolvedParameters) throws NoSuchMethodException {
            Class<?> current = type;
            while (current != null) {
                try {
                    return current.getDeclaredMethod(name, resolvedParameters);
                } catch (NoSuchMethodException ignored) {
                    current = current.getSuperclass();
                }
            }
            throw new NoSuchMethodException(name);
        }

        private Class<?>[] resolveParameterTypes() {
            Class<?>[] resolved = new Class<?>[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                Object parameterType = parameterTypes[i];
                if (parameterType instanceof Class<?>) {
                    resolved[i] = (Class<?>) parameterType;
                    continue;
                }

                try {
                    resolved[i] = Class.forName((String) parameterType, false,
                            LegacyPendingAttackManager.class.getClassLoader());
                } catch (ClassNotFoundException ignored) {
                    return null;
                }
            }
            return resolved;
        }
    }

    private static final class RuntimeField {
        private final String name;
        private Field field;

        private RuntimeField(String name) {
            this.name = name;
        }

        private Object get(Object instance) {
            Field resolved = resolve(instance);
            if (resolved == null) {
                return null;
            }

            try {
                return resolved.get(instance);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private Field resolve(Object instance) {
            if (field != null) {
                return field;
            }

            Class<?> type = instance.getClass();
            while (type != null) {
                try {
                    Field resolved = type.getDeclaredField(name);
                    resolved.setAccessible(true);
                    field = resolved;
                    return resolved;
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
            return null;
        }
    }
}
