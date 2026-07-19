package cc.sighs.strikeafterswing;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import cc.sighs.strikeafterswing.common.AttackBridge;
import cc.sighs.strikeafterswing.common.PendingAttackManager;

public final class LegacyAttackHandler {
    private static final RuntimeMethod MOB_ATTACK_TARGET = new RuntimeMethod("func_70652_k", "net.minecraft.entity.Entity");
    private static final RuntimeMethod ENTITY_IS_ALIVE = new RuntimeMethod("func_70089_S");
    private static final RuntimeMethod SWING_DURATION = new RuntimeMethod("func_82166_i");
    private static final RuntimeField ENTITY_REMOVED = new RuntimeField("field_70128_L");
    private static final PendingAttackManager<Object, Object> PENDING_ATTACKS = new PendingAttackManager<Object, Object>(
            new AttackBridge<Object, Object>() {
                @Override
                public boolean isAttackerUsable(Object attacker) {
                    return asBoolean(ENTITY_IS_ALIVE.invoke(attacker)) && !asBoolean(ENTITY_REMOVED.get(attacker));
                }

                @Override
                public boolean isTargetUsable(Object target) {
                    return !asBoolean(ENTITY_REMOVED.get(target));
                }

                @Override
                public void performAttack(Object attacker, Object target) {
                    MOB_ATTACK_TARGET.invoke(attacker, target);
                }
            });

    private LegacyAttackHandler() {
    }

    public static boolean delayAttack(Object attacker, Object target) {
        return PENDING_ATTACKS.delayAttack(attacker, target, Math.max(1, asInt(SWING_DURATION.invoke(attacker))));
    }

    public static void tick() {
        PENDING_ATTACKS.tick();
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static int asInt(Object value) {
        return value instanceof Integer ? ((Integer) value).intValue() : 1;
    }

    private static final class RuntimeMethod {
        private final String name;
        private final String parameterTypeName;
        private Method method;

        private RuntimeMethod(String name) {
            this(name, null);
        }

        private RuntimeMethod(String name, String parameterTypeName) {
            this.name = name;
            this.parameterTypeName = parameterTypeName;
        }

        private Object invoke(Object instance, Object... arguments) {
            Method resolved = resolve(instance);
            if (resolved == null) {
                return null;
            }
            try {
                return resolved.invoke(instance, arguments);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private Method resolve(Object instance) {
            if (method != null) {
                return method;
            }
            Class<?> parameterType = null;
            if (parameterTypeName != null) {
                try {
                    parameterType = Class.forName(parameterTypeName, false, LegacyAttackHandler.class.getClassLoader());
                } catch (ClassNotFoundException ignored) {
                    return null;
                }
            }
            Class<?> type = instance.getClass();
            while (type != null) {
                try {
                    Method resolved = parameterType == null ? type.getDeclaredMethod(name) : type.getDeclaredMethod(name, parameterType);
                    resolved.setAccessible(true);
                    method = resolved;
                    return resolved;
                } catch (NoSuchMethodException ignored) {
                    type = type.getSuperclass();
                }
            }
            return null;
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
