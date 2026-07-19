package cc.sighs.strikeafterswing.mixin;

import java.util.function.BooleanSupplier;

import cc.sighs.strikeafterswing.LegacyAttackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraft.server.MinecraftServer")
public abstract class LegacyMinecraftServerTickMixin {
    @Inject(method = "func_71190_q", at = @At("TAIL"), remap = false)
    private void strikeafterswing$tickPendingAttacks(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        LegacyAttackHandler.tick();
    }

    @Inject(method = "func_71217_p", at = @At("TAIL"), remap = false)
    private void strikeafterswing$tickPendingAttacksOuter(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        LegacyAttackHandler.tick();
    }
}
