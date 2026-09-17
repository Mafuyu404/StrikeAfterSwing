package cc.sighs.strikeafterswing;

import cc.sighs.strikeafterswing.common.WindupMultipliers;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 读取 {@code config/strikeafterswing/windup.json}：实体类型 id 到攻击前摇倍率的扁平映射，
 * 文件缺失或某个实体没有条目都表示 1.0（原版时长）。
 *
 * <p>文件改动（mtime 或大小变化）会在最多两秒后的下一次查询时自动重载，不需要重启；文件被删除
 * 则退回原版时长。客户端与服务端都会各自读取自己实例的那份配置。
 */
public final class WindupConfig {
    private static final Logger LOGGER = LogManager.getLogger("strikeafterswing");
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("strikeafterswing/windup.json");
    private static final String DEFAULT_JSON = "{\n  \"minecraft:zombie\": 1.0\n}\n";
    /** 两次文件检查之间的最小间隔：足够快，又不至于每 tick 都去 stat 文件。 */
    private static final long CHECK_INTERVAL_NANOS = 2_000_000_000L;

    /** 下一次允许检查文件的时间；查询路径只读这一个 volatile 字段。 */
    private static volatile long nextCheck;
    private static boolean seeded;
    private static boolean missing;
    private static FileTime loadedTime;
    private static long loadedSize = -1L;

    private WindupConfig() {
    }

    public static int scaleSwingDuration(LivingEntity entity, int baseTicks) {
        if (System.nanoTime() >= nextCheck) {
            refresh();
        }
        // 只把 id 的字符串形式传入 common（26.1.2 把 ResourceLocation 改名为 Identifier）。
        Object id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return WindupMultipliers.scaleSwingDuration(id == null ? null : id.toString(), baseTicks);
    }

    private static synchronized void refresh() {
        long now = System.nanoTime();
        if (now < nextCheck) {
            return;
        }
        nextCheck = now + CHECK_INTERVAL_NANOS;

        try {
            if (!Files.exists(FILE)) {
                if (!seeded) {
                    // 首次运行：写一份默认文件，然后按正常流程读取它。
                    Files.createDirectories(FILE.getParent());
                    Files.write(FILE, DEFAULT_JSON.getBytes(StandardCharsets.UTF_8));
                    seeded = true;
                    LOGGER.info("Wrote default attack windup config to {}", FILE);
                } else if (!missing) {
                    // 文件被删除：退回原版时长，并且不重新生成，否则用户删不掉它。
                    missing = true;
                    loadedTime = null;
                    loadedSize = -1L;
                    WindupMultipliers.replaceAll(null);
                    LOGGER.info("Attack windup config {} is gone; using vanilla timing", FILE);
                    return;
                } else {
                    return;
                }
            } else {
                missing = false;
            }

            FileTime time = Files.getLastModifiedTime(FILE);
            long size = Files.size(FILE);
            if (time.equals(loadedTime) && size == loadedSize) {
                return;
            }

            // 先记下文件状态再解析：同一份坏文件只警告一次，不会每两秒重新解析刷日志。
            loadedTime = time;
            loadedSize = size;
            try {
                WindupMultipliers.replaceAll(parse(new String(Files.readAllBytes(FILE), StandardCharsets.UTF_8)));
                LOGGER.info("Loaded attack windup config from {}", FILE);
            } catch (IOException | RuntimeException e) {
                // 读坏一次不该把已经生效的倍率丢掉，也不该让游戏崩掉。
                LOGGER.warn("Could not read attack windup config {}; keeping the previous multipliers: {}", FILE, e.toString());
            }
        } catch (IOException e) {
            LOGGER.warn("Could not check attack windup config {}: {}", FILE, e.toString());
        }
    }

    private static Map<String, Double> parse(String json) {
        Map<String, Double> multipliers = new HashMap<String, Double>();
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            LOGGER.warn("Attack windup config {} must be a JSON object of \"entity id\": multiplier", FILE);
            return multipliers;
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                LOGGER.warn("Ignoring attack windup multiplier for {}: not a number", entry.getKey());
                continue;
            }
            double multiplier = value.getAsDouble();
            // 写成取反比较，这样 NaN 也会被拒绝。
            if (!(multiplier >= 0.0D) || Double.isInfinite(multiplier)) {
                LOGGER.warn("Ignoring attack windup multiplier for {}: {}", entry.getKey(), multiplier);
                continue;
            }
            multipliers.put(entry.getKey(), multiplier);
        }
        return multipliers;
    }
}
