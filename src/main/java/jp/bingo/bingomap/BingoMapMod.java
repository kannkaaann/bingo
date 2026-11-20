package jp.bingo.bingomap;

import java.util.HashMap;
import java.util.Map;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Commands (called by the data pack):
 *   /bingomap render <name> <origCorner> <blankCorner>   draw the card onto the map called <name>
 *   /bingomap give <players> <name>                      give the player that map
 *   /bingomap wipe <players>                             clear the players' ender chests
 *   /bingomap dim open|close <prep|play>                 create / delete the same-seed temporary worlds
 * Corner = block position of the top-left (west / top) item frame of a 5x5 wall facing south.
 */
public class BingoMapMod implements ModInitializer {
    public static volatile IconLoader ICON_LOADER = null;
    private static final Map<String, MapId> MAPS = new HashMap<>();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("bingomap")
                .then(Commands.literal("render")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("orig", BlockPosArgument.blockPos())
                            .then(Commands.argument("blank", BlockPosArgument.blockPos())
                                .executes(BingoMapMod::render)))))
                .then(Commands.literal("give")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(BingoMapMod::give))))
                .then(Commands.literal("dim")
                    .then(Commands.literal("open")
                        .then(Commands.argument("kind", StringArgumentType.word())
                            .executes(ctx -> dim(ctx, true))))
                    .then(Commands.literal("close")
                        .then(Commands.argument("kind", StringArgumentType.word())
                            .executes(ctx -> dim(ctx, false)))))
                .then(Commands.literal("wipe")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes(BingoMapMod::wipe)))));
    }

    private static int dim(CommandContext<CommandSourceStack> ctx, boolean open) {
        String kind = StringArgumentType.getString(ctx, "kind");
        if (!kind.equals("prep") && !kind.equals("play")) {
            ctx.getSource().sendFailure(Component.literal("kind must be prep or play"));
            return 0;
        }
        if (open) {
            BingoWorlds.open(ctx.getSource().getServer(), kind);
        } else {
            BingoWorlds.close(ctx.getSource().getServer(), kind);
        }
        return 1;
    }

    private static MapItemSavedData dataFor(ServerLevel level, String name) {
        MapId id = MAPS.get(name);
        MapItemSavedData data = id == null ? null : level.getMapData(id);
        if (data == null) {
            ItemStack stack = MapItem.create(level, 0, 0, (byte) 0, false, false);
            id = stack.get(DataComponents.MAP_ID);
            MAPS.put(name, id);
            data = level.getMapData(id);
        }
        return data;
    }

    private static int render(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        BlockPos orig = BlockPosArgument.getBlockPos(ctx, "orig");
        BlockPos blank = BlockPosArgument.getBlockPos(ctx, "blank");
        ServerLevel level = ctx.getSource().getLevel();
        MapItemSavedData data = dataFor(ctx.getSource().getServer().overworld(), name);
        CardRenderer.render(level, data, orig, blank);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        ServerLevel level = ctx.getSource().getServer().overworld();
        dataFor(level, name); // make sure it exists
        MapId id = MAPS.get(name);
        int n = 0;
        for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
            ItemStack stack = new ItemStack(Items.FILLED_MAP);
            stack.set(DataComponents.MAP_ID, id);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("ビンゴカード").withStyle(ChatFormatting.GOLD));
            if (!p.getInventory().add(stack)) {
                p.drop(stack, false);
            }
            n++;
        }
        return n;
    }

    private static int wipe(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        int n = 0;
        for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
            p.getEnderChestInventory().clearContent();
            n++;
        }
        return n;
    }
}
