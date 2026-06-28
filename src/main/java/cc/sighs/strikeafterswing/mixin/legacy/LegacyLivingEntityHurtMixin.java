package cc.sighs.strikeafterswing.mixin.legacy;

import cc.sighs.strikeafterswing.legacy.LegacyPendingAttackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.minecraft.entity.LivingEntity")
public abstract class LegacyLivingEntityHurtMixin {
    @Inject(method = "func_70097_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void strikeafterswing$delayIncomingMobAttack(@Coerce Object source, float amount,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (LegacyPendingAttackManager.delayIncomingMobAttack(this, source, amount)) {
            cir.setReturnValue(true);
        }
    }
}
