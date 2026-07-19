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
