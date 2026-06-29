package cc.sighs.strikeafterswing.mixin.legacy;

import cc.sighs.strikeafterswing.mixin.ConditionalMixinPlugin;

public final class LegacyForgeMixinPlugin extends ConditionalMixinPlugin {
    @Override
    protected boolean shouldEnable() {
        return isClassResourcePresent("net.minecraft.entity.Entity")
                && !isClassResourcePresent("net.minecraft.world.entity.Entity")
                && !isClassPresent("net.neoforged.fml.loading.FMLLoader");
    }
}
