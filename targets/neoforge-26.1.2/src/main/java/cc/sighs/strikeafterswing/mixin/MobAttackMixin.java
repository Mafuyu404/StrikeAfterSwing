package cc.sighs.strikeafterswing.mixin;

import cc.sighs.strikeafterswing.NeoForgeAttackHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobAttackMixin {
    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void strikeafterswing$delayMobAttack(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
        Mob attacker = (Mob) (Object) this;
        int delayTicks = ((LivingEntityAccessor) attacker).strikeafterswing$getCurrentSwingDuration();
        if (NeoForgeAttackHandler.delayAttack(attacker, target, delayTicks)) {
            // 不能谎报命中：原版覆写类（Husk/Zombie/CaveSpider 等）以 doHurtTarget 的返回值
            // 门控附加效果，伪造 true 会让饥饿/中毒在延迟命中之前就生效，并随延迟命中再挂一次。
            cir.setReturnValue(false);
        }
    }
}
