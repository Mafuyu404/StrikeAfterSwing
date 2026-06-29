package cc.sighs.strikeafterswing.fabric;

import cc.sighs.strikeafterswing.FabricVersionLookup;
import cc.sighs.strikeafterswing.mixin.ConditionalMixinPlugin;

public final class FabricIntermediaryMixinPlugin extends ConditionalMixinPlugin {
    @Override
    protected boolean shouldEnable() {
        return !FabricVersionLookup.minecraftVersion().startsWith("26.");
    }
}
