package cc.sighs.strikeafterswing.mixin;

import cc.sighs.strikeafterswing.PendingAttackManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void strikeafterswing$delayIncomingMobAttack(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (PendingAttackManager.delayIncomingMobAttack((LivingEntity) (Object) this, source, amount)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true, require = 0)
    private void strikeafterswing$delayIncomingMobAttack(ServerLevel level, DamageSource source, float amount,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (PendingAttackManager.delayIncomingMobAttack((LivingEntity) (Object) this, source, amount)) {
            cir.setReturnValue(true);
        }
    }
}
