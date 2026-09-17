package cc.sighs.strikeafterswing.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 按实体类型缩放攻击前摇：实体类型 id 到倍率的映射来自模组的 JSON 配置。
 *
 * <p>各 target 把攻击者的实体 id 与原版挥击时长传入，返回的时长同时驱动攻击动画与延迟队列；
 * 未知 id 使用 {@link #DEFAULT_MULTIPLIER}，所以配置缺失或为空时与原版时序完全一致。
 */
public final class WindupMultipliers {
    public static final double DEFAULT_MULTIPLIER = 1.0D;
    /** 防止写错的倍率把前摇变成一个永不结束的等待。 */
    public static final int MAX_SWING_DURATION_TICKS = 72000;

    /** 客户端渲染线程与服务端 tick 线程都会读它（单人游戏里是同一个 JVM），所以要 volatile。 */
    private static volatile Map<String, Double> multipliers = Collections.emptyMap();

    private WindupMultipliers() {
    }

    public static void replaceAll(Map<String, Double> loaded) {
        multipliers = loaded == null || loaded.isEmpty()
                ? Collections.<String, Double>emptyMap()
                : Collections.unmodifiableMap(new HashMap<String, Double>(loaded));
    }

    public static double multiplierFor(String entityId) {
        Double multiplier = entityId == null ? null : multipliers.get(entityId);
        return multiplier == null ? DEFAULT_MULTIPLIER : multiplier.doubleValue();
    }

    public static int scaleSwingDuration(String entityId, int baseTicks) {
        long scaled = Math.round(baseTicks * multiplierFor(entityId));
        if (scaled < 1L) {
            return 1;
        }
        return scaled > MAX_SWING_DURATION_TICKS ? MAX_SWING_DURATION_TICKS : (int) scaled;
    }
}
