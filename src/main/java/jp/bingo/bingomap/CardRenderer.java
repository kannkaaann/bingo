package jp.bingo.bingomap;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;

/** Draws a 5x5 bingo card (item icons, enchant pips, check marks) into a 128x128 map. */
final class CardRenderer {
    private static final int CELL = 24;
    private static final int MARGIN = 4;

    private static int[] palRgb;
    private static int[] palId;

    private static final int C_GRID = 0x303030;
    private static final int C_CELL = 0xC8C8C8;
    private static final int C_DONE = 0x78C878;
    private static final int C_CHECK = 0x006400;
    private static final int C_ENCH = 0xA020F0;

    private CardRenderer() {}

    // ---------- palette ----------
    private static synchronized void initPalette() {
        if (palRgb != null) return;
        List<int[]> list = new ArrayList<>();
        for (int i = 1; i < 64; i++) {
            MapColor mc;
            try {
                mc = MapColor.byId(i);
            } catch (Throwable t) {
                break;
            }
            if (mc == null || mc == MapColor.NONE) continue;
            for (int b = 0; b < 4; b++) {
                int mod = switch (b) { case 0 -> 180; case 1 -> 220; case 2 -> 255; default -> 135; };
                int r = ((mc.col >> 16) & 255) * mod / 255;
                int g = ((mc.col >> 8) & 255) * mod / 255;
                int bl = (mc.col & 255) * mod / 255;
                list.add(new int[] {(r << 16) | (g << 8) | bl, i * 4 + b});
            }
        }
        palRgb = new int[list.size()];
        palId = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            palRgb[i] = list.get(i)[0];
            palId[i] = list.get(i)[1];
        }
    }

    private static byte nearest(int rgb) {
        initPalette();
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int best = Integer.MAX_VALUE, bestId = 0;
        for (int i = 0; i < palRgb.length; i++) {
            int pr = (palRgb[i] >> 16) & 255, pg = (palRgb[i] >> 8) & 255, pb = palRgb[i] & 255;
            int d = (r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb);
            if (d < best) {
                best = d;
                bestId = palId[i];
            }
        }
        return (byte) bestId;
    }

    // ---------- drawing ----------
    static void render(ServerLevel level, MapItemSavedData data, BlockPos orig, BlockPos blank) {
        byte[] px = new byte[128 * 128];
        java.util.Arrays.fill(px, nearest(C_GRID));
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                ItemStack want = frameItem(level, orig.offset(c, -r, 0));
                boolean done = !frameItem(level, blank.offset(c, -r, 0)).isEmpty();
                drawCell(px, c, r, want, done);
            }
        }
        // setColor() marks the changed pixels dirty so holders of the map get updated
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                data.setColor(x, y, px[x + y * 128]);
            }
        }
        data.setDirty();
    }

    private static ItemStack frameItem(ServerLevel level, BlockPos pos) {
        List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(pos));
        for (ItemFrame f : frames) {
            ItemStack s = f.getItem();
            if (!s.isEmpty()) return s;
        }
        return ItemStack.EMPTY;
    }

    private static void put(byte[] px, int x, int y, byte col) {
        if (x >= 0 && y >= 0 && x < 128 && y < 128) px[x + y * 128] = col;
    }

    private static void rect(byte[] px, int x0, int y0, int w, int h, byte col) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) put(px, x0 + x, y0 + y, col);
    }

    private static void drawCell(byte[] px, int c, int r, ItemStack stack, boolean done) {
        int x0 = MARGIN + c * CELL;
        int y0 = MARGIN + r * CELL;
        rect(px, x0 + 1, y0 + 1, CELL - 2, CELL - 2, nearest(done ? C_DONE : C_CELL));
        if (stack.isEmpty()) return;

        int[] icon = IconResolver.resolve(stack);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int argb = icon[y * 16 + x];
                if (((argb >>> 24) & 255) < 128) continue;
                put(px, x0 + 4 + x, y0 + 4 + y, nearest(argb & 0xFFFFFF));
            }
        }
        if (isEnchanted(stack)) {
            rect(px, x0 + 18, y0 + 2, 3, 3, nearest(C_ENCH));
        }
        if (done) {
            int[][] pts = {{0, 4}, {1, 5}, {2, 6}, {3, 5}, {4, 4}, {5, 3}, {6, 2}, {7, 1}};
            byte cc = nearest(C_CHECK);
            for (int[] p : pts) {
                put(px, x0 + 14 + p[0], y0 + 14 + p[1], cc);
                put(px, x0 + 14 + p[0], y0 + 13 + p[1], cc);
            }
        }
    }

    static boolean isEnchanted(ItemStack stack) {
        if (stack.isEnchanted()) return true;
        ItemEnchantments se = stack.get(DataComponents.STORED_ENCHANTMENTS);
        return se != null && !se.isEmpty();
    }

    // ---------- icons ----------
    static final class IconResolver {
        static int[] resolve(ItemStack stack) {
            String full = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            String name = full.substring(full.indexOf(':') + 1);
            IconLoader loader = BingoMapMod.ICON_LOADER;
            if (loader != null) {
                int[] base = null;
                for (String p : candidates(name)) {
                    base = loader.load(p);
                    if (base != null) break;
                }
                if (base != null) {
                    base = base.clone();
                    PotionContents pc = stack.get(DataComponents.POTION_CONTENTS);
                    if (pc != null) {
                        String overlay = switch (name) {
                            case "potion", "splash_potion", "lingering_potion" -> "item/potion_overlay";
                            case "tipped_arrow" -> "item/tipped_arrow_head";
                            default -> null;
                        };
                        if (overlay != null) {
                            int[] ov = loader.load(overlay);
                            if (ov != null) applyTinted(base, ov, pc.getColor() & 0xFFFFFF);
                        }
                    }
                    return base;
                }
            }
            return fallback(name);
        }

        private static void applyTinted(int[] base, int[] overlay, int rgb) {
            int tr = (rgb >> 16) & 255, tg = (rgb >> 8) & 255, tb = rgb & 255;
            for (int i = 0; i < base.length; i++) {
                int a = (overlay[i] >>> 24) & 255;
                if (a < 128) continue;
                int r = ((overlay[i] >> 16) & 255) * tr / 255;
                int g = ((overlay[i] >> 8) & 255) * tg / 255;
                int b = (overlay[i] & 255) * tb / 255;
                base[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }

        private static List<String> candidates(String name) {
            Set<String> s = new LinkedHashSet<>();
            s.add("item/" + name);
            s.add("block/" + name);
            s.add("block/" + name + "_front");
            s.add("block/" + name + "_top");
            s.add("block/" + name + "_side");
            String[] suffixes = {"_stairs", "_slab", "_fence_gate", "_fence", "_wall", "_button", "_pressure_plate", "_sign", "_hanging_sign"};
            for (String suf : suffixes) {
                if (name.endsWith(suf)) {
                    String b = name.substring(0, name.length() - suf.length());
                    s.add("block/" + b);
                    s.add("block/" + b + "s");
                    s.add("block/" + b + "_planks");
                    s.add("block/" + b + "_block");
                    s.add("block/" + b + "_top");
                }
            }
            if (name.equals("tipped_arrow")) s.add("item/tipped_arrow_base");
            return new ArrayList<>(s);
        }

        private static int[] fallback(String name) {
            int h = name.hashCode();
            int r = 60 + ((h >> 16) & 127), g = 60 + ((h >> 8) & 127), b = 60 + (h & 127);
            int[] out = new int[256];
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    boolean edge = x == 0 || y == 0 || x == 15 || y == 15;
                    int rr = edge ? r / 2 : r, gg = edge ? g / 2 : g, bb = edge ? b / 2 : b;
                    out[y * 16 + x] = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
                }
            }
            return out;
        }
    }
}
