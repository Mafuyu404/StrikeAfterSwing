package cc.sighs.strikeafterswing;

import cc.sighs.strikeafterswing.common.AttackBridge;
import cc.sighs.strikeafterswing.common.PendingAttackManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

public final class ForgeAttackHandler {
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
                    attacker.doHurtTarget(target);
                }
            });

    private ForgeAttackHandler() {
    }

    public static boolean delayAttack(Mob attacker, Entity target, int delayTicks) {
        return PENDING_ATTACKS.delayAttack(attacker, target, delayTicks);
    }

    public static void tick() {
        PENDING_ATTACKS.tick();
    }
}
