package cc.sighs.strikeafterswing.common;

public interface AttackBridge<M, E> {
    boolean isAttackerUsable(M attacker);

    boolean isTargetUsable(E target);

    void performAttack(M attacker, E target);
}
