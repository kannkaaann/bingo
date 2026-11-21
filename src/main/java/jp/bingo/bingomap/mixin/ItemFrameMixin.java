package jp.bingo.bingomap.mixin;

import jp.bingo.bingomap.BingoWorlds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.decoration.ItemFrame;

/**
 * Item frames inside the bingo worlds never pop off because of the periodic "still attached?" check.
 * (In 1.21.11 frames created for the bingo walls were being dropped by that check.)
 */
@Mixin(ItemFrame.class)
public abstract class ItemFrameMixin {
    @Inject(method = "survives", at = @At("HEAD"), cancellable = true, require = 0)
    private void bingo$keepAttached(CallbackInfoReturnable<Boolean> cir) {
        if (BingoWorlds.DIAG) return;
        ItemFrame self = (ItemFrame) (Object) this;
        if (BingoWorlds.isOurs(self.level().dimension())) {
            cir.setReturnValue(true);
        }
    }
}
