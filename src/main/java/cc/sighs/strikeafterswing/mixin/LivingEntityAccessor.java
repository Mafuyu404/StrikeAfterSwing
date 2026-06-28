package cc.sighs.strikeafterswing.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Invoker("getCurrentSwingDuration")
    int strikeafterswing$getCurrentSwingDuration();
}
