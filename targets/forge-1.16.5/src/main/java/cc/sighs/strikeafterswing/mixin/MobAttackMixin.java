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
    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void strikeafterswing$delayMobAttack(Entity target, CallbackInfoReturnable<Boolean> cir) {
        MobEntity attacker = (MobEntity) (Object) this;
        int delayTicks = ((LivingEntityAccessor) attacker).strikeafterswing$getCurrentSwingDuration();
        if (ForgeAttackHandler.delayAttack(attacker, target, delayTicks)) {
            // 不能谎报命中：原版覆写类（Husk/Zombie/CaveSpider 等）以 doHurtTarget 的返回值
            // 门控附加效果，伪造 true 会让饥饿/中毒在延迟命中之前就生效，并随延迟命中再挂一次。
            cir.setReturnValue(false);
        }
    }
}
