package cc.sighs.strikeafterswing;

import cc.sighs.strikeafterswing.common.AttackBridge;
import cc.sighs.strikeafterswing.common.PendingAttackManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;

public final class ForgeAttackHandler {
    private static final PendingAttackManager<MobEntity, Entity> PENDING_ATTACKS = new PendingAttackManager<>(
            new AttackBridge<MobEntity, Entity>() {
                @Override
                public boolean isAttackerUsable(MobEntity attacker) {
                    return attacker.isAlive() && !attacker.removed;
                }

                @Override
                public boolean isTargetUsable(Entity target) {
                    return !target.removed;
                }

                @Override
                public void performAttack(MobEntity attacker, Entity target) {
                    attacker.attackEntityAsMob(target);
                }
            });

    private ForgeAttackHandler() {
    }

    public static boolean delayAttack(MobEntity attacker, Entity target, int delayTicks) {
        return PENDING_ATTACKS.delayAttack(attacker, target, delayTicks);
    }

    public static void tick() {
        PENDING_ATTACKS.tick();
    }
}
