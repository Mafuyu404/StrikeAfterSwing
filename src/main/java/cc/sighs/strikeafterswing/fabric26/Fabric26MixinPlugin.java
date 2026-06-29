package cc.sighs.strikeafterswing.fabric26;

import cc.sighs.strikeafterswing.FabricVersionLookup;
import cc.sighs.strikeafterswing.mixin.ConditionalMixinPlugin;

public final class Fabric26MixinPlugin extends ConditionalMixinPlugin {
    @Override
    protected boolean shouldEnable() {
        return FabricVersionLookup.minecraftVersion().startsWith("26.");
    }
}
