package cc.sighs.strikeafterswing.mixin;

import cc.sighs.strikeafterswing.PendingAttackManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobAttackMixin {
    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void strikeafterswing$delayMobAttack(Entity target, CallbackInfoReturnable<Boolean> cir) {
        Mob attacker = (Mob) (Object) this;
        int delayTicks = ((LivingEntityAccessor) attacker).strikeafterswing$getCurrentSwingDuration();
        if (PendingAttackManager.delayMobAttack(attacker, target, delayTicks)) {
            cir.setReturnValue(true);
        }
    }
}
