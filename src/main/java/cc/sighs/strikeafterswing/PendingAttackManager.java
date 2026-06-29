package cc.sighs.strikeafterswing;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

public final class PendingAttackManager {
    private static final List<PendingAttack> PENDING_ATTACKS = new LinkedList<>();
    private static boolean bypassing;
    private static long tickCounter;

    private PendingAttackManager() {
    }

    public static boolean delayMobAttack(Mob attacker, Entity target, int delayTicks) {
        if (bypassing || !canQueue(attacker, target)) {
            return false;
        }

        if (hasPendingAttack(attacker)) {
            return true;
        }

        PENDING_ATTACKS.add(new PendingAttack(attacker, target, tickCounter + Math.max(1, delayTicks)));
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
        Mob attacker = attack.attacker;
        Entity target = attack.target;
        if (!canQueue(attacker, target)) {
            return;
        }

        bypassing = true;
        try {
            attacker.doHurtTarget(target);
        } finally {
            bypassing = false;
        }
    }

    private static boolean canQueue(Mob attacker, Entity target) {
        return attacker.isAlive() && !attacker.isRemoved() && !target.isRemoved();
    }

    private static boolean hasPendingAttack(Mob attacker) {
        for (PendingAttack attack : PENDING_ATTACKS) {
            if (attack.attacker == attacker) {
                return true;
            }
        }
        return false;
    }

    private static final class PendingAttack {
        private final Mob attacker;
        private final Entity target;
        private final long executeTick;

        private PendingAttack(Mob attacker, Entity target, long executeTick) {
            this.attacker = attacker;
            this.target = target;
            this.executeTick = executeTick;
        }
    }
}
