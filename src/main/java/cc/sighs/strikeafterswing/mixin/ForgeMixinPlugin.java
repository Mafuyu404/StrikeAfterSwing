package cc.sighs.strikeafterswing.mixin;

public final class ForgeMixinPlugin extends ConditionalMixinPlugin {
    @Override
    protected boolean shouldEnable() {
        return !isClassPresent("net.neoforged.fml.loading.FMLLoader")
                && isClassResourcePresent("net.minecraft.world.entity.Entity");
    }
}
