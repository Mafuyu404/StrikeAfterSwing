package cc.sighs.strikeafterswing.legacy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public final class LegacyPendingAttackManager {
    private static final List<PendingAttack> PENDING_ATTACKS = new LinkedList<>();
    private static boolean bypassing;

    private static final RuntimeMethod MOB_ATTACK_TARGET = RuntimeMethod.withArgs("func_70652_k", "net.minecraft.entity.Entity");
    private static final RuntimeMethod ENTITY_IS_ALIVE = RuntimeMethod.noArgs("func_70089_S");
    private static final RuntimeMethod SWING_DURATION = RuntimeMethod.noArgs("func_82166_i");
    private static final RuntimeField ENTITY_REMOVED = new RuntimeField("field_70128_L");
    private static long tickCounter;

    private LegacyPendingAttackManager() {
    }

    public static boolean delayMobAttack(Object attacker, Object target) {
        if (bypassing || !canQueue(attacker, target)) {
            return false;
        }

        if (hasPendingAttack(attacker)) {
            return true;
        }

        int delayTicks = Math.max(1, asInt(SWING_DURATION.invoke(attacker)));
        PENDING_ATTACKS.add(new PendingAttack(attacker, target, tickCounter + delayTicks));
        return true;
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

    private static void execute(PendingAttack attack) {
        if (!canQueue(attack.attacker, attack.target)) {
            return;
        }

        bypassing = true;
        try {
            MOB_ATTACK_TARGET.invoke(attack.attacker, attack.target);
        } finally {
            bypassing = false;
        }
    }

    private static boolean canQueue(Object attacker, Object target) {
        return isAlive(attacker) && !isRemoved(attacker) && !isRemoved(target);
    }

    private static boolean hasPendingAttack(Object attacker) {
        for (PendingAttack attack : PENDING_ATTACKS) {
            if (attack.attacker == attacker) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAlive(Object entity) {
        Object value = ENTITY_IS_ALIVE.invoke(entity);
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean isRemoved(Object entity) {
        Object value = ENTITY_REMOVED.get(entity);
        return value instanceof Boolean && (Boolean) value;
    }

    private static int asInt(Object value) {
        return value instanceof Integer ? (Integer) value : 1;
    }

    private static final class PendingAttack {
        private final Object attacker;
        private final Object target;
        private final long executeTick;

        private PendingAttack(Object attacker, Object target, long executeTick) {
            this.attacker = attacker;
            this.target = target;
            this.executeTick = executeTick;
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
