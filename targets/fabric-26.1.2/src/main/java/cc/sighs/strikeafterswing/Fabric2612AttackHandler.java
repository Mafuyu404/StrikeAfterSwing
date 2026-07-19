package cc.sighs.strikeafterswing;

import cc.sighs.strikeafterswing.common.AttackBridge;
import cc.sighs.strikeafterswing.common.PendingAttackManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

public final class Fabric2612AttackHandler {
    private static final PendingAttackManager<Mob, Entity> PENDING_ATTACKS = new PendingAttackManager<>(
            new AttackBridge<Mob, Entity>() {
                @Override
                public boolean isAttackerUsable(Mob attacker) {
                    return attacker.isAlive() && !attacker.isRemoved();
                }

                @Override
                public boolean isTargetUsable(Entity target) {
                    return !target.isRemoved();
                }

                @Override
                public void performAttack(Mob attacker, Entity target) {
                    if (attacker.level() instanceof ServerLevel serverLevel) {
                        attacker.doHurtTarget(serverLevel, target);
                    }
                }
            });

    private Fabric2612AttackHandler() {
    }

    public static boolean delayAttack(Mob attacker, Entity target, int delayTicks) {
        return PENDING_ATTACKS.delayAttack(attacker, target, delayTicks);
    }

    public static void tick() {
        PENDING_ATTACKS.tick();
    }
}
