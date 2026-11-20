package jp.bingo.bingomap.mixin;

import jp.bingo.bingomap.BingoWorlds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;

/** Lets players light nether portals inside the bingo worlds. */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin {
    @Inject(method = "inPortalDimension", at = @At("HEAD"), cancellable = true, require = 0)
    private static void bingo$allowPortals(Level level, CallbackInfoReturnable<Boolean> cir) {
        if (BingoWorlds.isOurs(level.dimension())) {
            cir.setReturnValue(true);
        }
    }
}
