package cc.sighs.strikeafterswing.mixin;

import java.util.function.BooleanSupplier;

import cc.sighs.strikeafterswing.Fabric2612AttackHandler;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerTickMixin {
    @Inject(method = "tickServer", at = @At("TAIL"), require = 0)
    private void strikeafterswing$tickPendingAttacks(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        Fabric2612AttackHandler.tick();
    }
}
