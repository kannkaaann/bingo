package jp.bingo.bingomap.mixin;

import jp.bingo.bingomap.BingoWorlds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;

/**
 * Makes nether portals inside the bingo worlds lead to the partner bingo world
 * (prep_overworld <-> prep_nether, play_overworld <-> play_nether) instead of the real Nether/Overworld.
 */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {
    private static final ThreadLocal<ServerLevel> bingo$SOURCE = new ThreadLocal<>();

    @Inject(method = "getPortalDestination", at = @At("HEAD"), require = 0)
    private void bingo$captureSource(ServerLevel level, Entity entity, BlockPos pos, CallbackInfoReturnable<TeleportTransition> cir) {
        bingo$SOURCE.set(level);
    }

    @Inject(method = "getPortalDestination", at = @At("RETURN"), require = 0)
    private void bingo$clearSource(ServerLevel level, Entity entity, BlockPos pos, CallbackInfoReturnable<TeleportTransition> cir) {
        bingo$SOURCE.remove();
    }

    @Redirect(
        method = "getPortalDestination",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"
        ),
        require = 0
    )
    private ServerLevel bingo$remapDestination(MinecraftServer server, ResourceKey<Level> vanillaTarget) {
        ServerLevel source = bingo$SOURCE.get();
        if (source != null) {
            ResourceKey<Level> partner = BingoWorlds.partner(source.dimension());
            if (partner != null) {
                return server.getLevel(partner);
            }
        }
        return server.getLevel(vanillaTarget);
    }
}
