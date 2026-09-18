package cc.sighs.strikeafterswing.common;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public final class PendingAttackManager<M, E> {
    private final AttackBridge<M, E> bridge;
    private final List<PendingAttack<M, E>> pendingAttacks = new LinkedList<>();
    private boolean bypassing;
    private long tickCounter;

    public PendingAttackManager(AttackBridge<M, E> bridge) {
        this.bridge = bridge;
    }

    public boolean delayAttack(M attacker, E target, int delayTicks) {
        if (bypassing || !canQueue(attacker, target)) {
            return false;
        }

        if (hasPendingAttack(attacker)) {
            return true;
        }

        pendingAttacks.add(new PendingAttack<>(attacker, target, tickCounter + Math.max(1, delayTicks)));
        return true;
    }

    public void tick() {
        tickCounter++;
        Iterator<PendingAttack<M, E>> iterator = pendingAttacks.iterator();
        while (iterator.hasNext()) {
            PendingAttack<M, E> attack = iterator.next();
            if (tickCounter < attack.executeTick) {
                continue;
            }

            iterator.remove();
            execute(attack);
        }
    }

    private void execute(PendingAttack<M, E> attack) {
        if (!canQueue(attack.attacker, attack.target)) {
            return;
        }

        // 挥击期间目标可能已经跑出攻击范围：结算前用原版的近战范围判定重新确认一次，
        // 落空就丢弃这次命中（挥击动画照常播完，与原版打空一致）。
        if (!bridge.isTargetInReach(attack.attacker, attack.target)) {
            return;
        }

        bypassing = true;
        try {
            bridge.performAttack(attack.attacker, attack.target);
        } finally {
            bypassing = false;
        }
    }

    private boolean canQueue(M attacker, E target) {
        return bridge.isAttackerUsable(attacker) && bridge.isTargetUsable(target);
    }

    private boolean hasPendingAttack(M attacker) {
        for (PendingAttack<M, E> attack : pendingAttacks) {
            if (attack.attacker == attacker) {
                return true;
            }
        }
        return false;
    }

    private static final class PendingAttack<M, E> {
        private final M attacker;
        private final E target;
        private final long executeTick;

        private PendingAttack(M attacker, E target, long executeTick) {
            this.attacker = attacker;
            this.target = target;
            this.executeTick = executeTick;
        }
    }
}
