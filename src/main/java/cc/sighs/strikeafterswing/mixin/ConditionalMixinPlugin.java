package cc.sighs.strikeafterswing.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public abstract class ConditionalMixinPlugin implements IMixinConfigPlugin {
    private boolean enabled;

    @Override
    public final void onLoad(String mixinPackage) {
        enabled = shouldEnable();
    }

    @Override
    public final String getRefMapperConfig() {
        return null;
    }

    @Override
    public final boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return enabled;
    }

    @Override
    public final void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public final List<String> getMixins() {
        return null;
    }

    @Override
    public final void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                               IMixinInfo mixinInfo) {
    }

    @Override
    public final void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                                IMixinInfo mixinInfo) {
    }

    protected abstract boolean shouldEnable();

    protected final boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    protected final boolean isClassResourcePresent(String className) {
        String path = className.replace('.', '/') + ".class";
        return getClass().getClassLoader().getResource(path) != null;
    }
}
