package cc.sighs.strikeafterswing.fabric.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface FabricLivingEntityAccessor {
    @Invoker("getCurrentSwingDuration")
    int strikeafterswing$getCurrentSwingDuration();
}
