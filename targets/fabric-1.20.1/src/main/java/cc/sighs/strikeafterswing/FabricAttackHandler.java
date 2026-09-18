package cc.sighs.strikeafterswing;

import cc.sighs.strikeafterswing.common.AttackBridge;
import cc.sighs.strikeafterswing.common.PendingAttackManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

public final class FabricAttackHandler {
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
                public boolean isTargetInReach(Mob attacker, Entity target) {
                    // 与 MeleeAttackGoal#getAttackReachSqr 相同的近战范围：命中被延迟到挥击结束后才结算，
                    // 必须重新确认目标没有跑出范围。
                    double reach = attacker.getBbWidth() * 2.0D * attacker.getBbWidth() * 2.0D
                            + target.getBbWidth();
                    return attacker.distanceToSqr(target) <= reach;
                }

                @Override
                public void performAttack(Mob attacker, Entity target) {
                    attacker.doHurtTarget(target);
                }
            });

    private FabricAttackHandler() {
    }

    public static boolean delayAttack(Mob attacker, Entity target, int delayTicks) {
        return PENDING_ATTACKS.delayAttack(attacker, target, delayTicks);
    }

    public static void tick() {
        PENDING_ATTACKS.tick();
    }
}
