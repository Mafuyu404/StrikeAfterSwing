package cc.sighs.strikeafterswing.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("level")
    Level strikeafterswing$getLevel();

    @Accessor("removalReason")
    Entity.RemovalReason strikeafterswing$getRemovalReason();
}
