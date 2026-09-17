package cc.sighs.strikeafterswing.mixin;

import cc.sighs.strikeafterswing.WindupConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SwingDurationMixin {
    @Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
    private void strikeafterswing$scaleSwingDuration(CallbackInfoReturnable<Integer> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof Mob)) {
            // 玩家（以及其他非生物实体）的挥击保持原版：本模组只延后生物攻击。
            return;
        }
        // 原版用这个返回值驱动攻击动画（updateSwingTime）和挥击节奏，所以倍率只在这里应用一次：
        // 动画与延迟命中因此始终同步。不要在 MobAttackMixin 里再乘第二次。
        cir.setReturnValue(WindupConfig.scaleSwingDuration(entity, cir.getReturnValueI()));
    }
}
