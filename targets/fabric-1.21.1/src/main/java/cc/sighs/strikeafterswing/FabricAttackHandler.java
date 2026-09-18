package cc.sighs.strikeafterswing;

import cc.sighs.strikeafterswing.common.AttackBridge;
import cc.sighs.strikeafterswing.common.PendingAttackManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
                    // 用原版自己的近战范围判定（1.21+ 是攻击盒相交，26.1.2 还会算上 ATTACK_RANGE 组件）。
                    if (!(target instanceof LivingEntity)) {
                        return true;
                    }
                    return attacker.isWithinMeleeAttackRange((LivingEntity) target);
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
