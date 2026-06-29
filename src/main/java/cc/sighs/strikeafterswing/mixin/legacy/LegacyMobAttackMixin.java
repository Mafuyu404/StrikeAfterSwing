package cc.sighs.strikeafterswing.mixin.legacy;

import cc.sighs.strikeafterswing.legacy.LegacyPendingAttackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.minecraft.entity.MobEntity")
public abstract class LegacyMobAttackMixin {
    @Inject(method = "func_70652_k", at = @At("HEAD"), cancellable = true, remap = false)
    private void strikeafterswing$delayMobAttack(@Coerce Object target, CallbackInfoReturnable<Boolean> cir) {
        if (LegacyPendingAttackManager.delayMobAttack(this, target)) {
            cir.setReturnValue(true);
        }
    }
}
