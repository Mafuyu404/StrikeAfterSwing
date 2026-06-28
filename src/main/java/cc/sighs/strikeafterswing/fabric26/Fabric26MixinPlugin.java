package cc.sighs.strikeafterswing.fabric26;

import java.util.List;
import java.util.Set;

import cc.sighs.strikeafterswing.FabricVersionLookup;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class Fabric26MixinPlugin implements IMixinConfigPlugin {
    private static final boolean OFFICIAL_ENVIRONMENT = minecraftVersion().startsWith("26.");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return OFFICIAL_ENVIRONMENT;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static String minecraftVersion() {
        return FabricVersionLookup.minecraftVersion();
    }
}
