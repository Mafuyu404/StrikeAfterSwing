package cc.sighs.strikeafterswing.fabric26.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface Fabric26LivingEntityAccessor {
    @Invoker("getCurrentSwingDuration")
    int strikeafterswing$getCurrentSwingDuration();
}
