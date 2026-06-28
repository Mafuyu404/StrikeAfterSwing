package cc.sighs.strikeafterswing;

import java.lang.reflect.Method;
import java.util.Optional;

public final class FabricVersionLookup {
    private FabricVersionLookup() {
    }

    public static String minecraftVersion() {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            Method getModContainer = loaderClass.getMethod("getModContainer", String.class);
            Optional<?> minecraft = (Optional<?>) getModContainer.invoke(loader, "minecraft");
            if (!minecraft.isPresent()) {
                return "";
            }

            Object metadata = minecraft.get().getClass().getMethod("getMetadata").invoke(minecraft.get());
            Object version = metadata.getClass().getMethod("getVersion").invoke(metadata);
            return (String) version.getClass().getMethod("getFriendlyString").invoke(version);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return "";
        }
    }
}
