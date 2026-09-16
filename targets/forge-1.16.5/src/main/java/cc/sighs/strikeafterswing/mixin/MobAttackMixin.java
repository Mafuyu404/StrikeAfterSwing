package cc.sighs.strikeafterswing.mixin;

import cc.sighs.strikeafterswing.ForgeAttackHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class MobAttackMixin {
    @Inject(method = "attackEntityAsMob", at = @At("HEAD"), cancellable = true)
    private void strikeafterswing$delayMobAttack(Entity target, CallbackInfoReturnable<Boolean> cir) {
        MobEntity attacker = (MobEntity) (Object) this;
        int delayTicks = ((LivingEntityAccessor) attacker).strikeafterswing$getArmSwingAnimationEnd();
        if (ForgeAttackHandler.delayAttack(attacker, target, delayTicks)) {
            cir.setReturnValue(true);
        }
    }
}
