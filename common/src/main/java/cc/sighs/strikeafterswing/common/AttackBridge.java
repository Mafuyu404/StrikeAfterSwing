package cc.sighs.strikeafterswing.common;

public interface AttackBridge<M, E> {
    boolean isAttackerUsable(M attacker);

    boolean isTargetUsable(E target);

    /**
     * 延迟结束、真正结算这次命中之前，攻击者是否还够得到目标。
     *
     * <p>原版是"能不能打到"和"造成伤害"发生在同一刻：AI 先确认目标在近战范围内，随后立刻调用
     * {@code doHurtTarget}。本模组把后半步推到了挥击之后，所以必须在这里用同一套范围判定重新确认
     * 一次，否则目标在挥击期间跑开也会挨打。
     */
    boolean isTargetInReach(M attacker, E target);

    void performAttack(M attacker, E target);
}
