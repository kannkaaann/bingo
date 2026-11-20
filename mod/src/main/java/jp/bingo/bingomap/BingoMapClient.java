package jp.bingo.bingomap;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Optional;
import javax.imageio.ImageIO;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Client entrypoint: gives the (shared) server code access to the real item textures. */
public class BingoMapClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BingoMapMod.ICON_LOADER = BingoMapClient::load;
    }

    private static int[] load(String path) {
        try {
            ResourceManager rm = Minecraft.getInstance().getResourceManager();
            Optional<Resource> res = rm.getResource(Identifier.parse("minecraft:textures/" + path + ".png"));
            if (res.isEmpty()) {
                return null;
            }
            BufferedImage img;
            try (InputStream in = res.get().open()) {
                img = ImageIO.read(in);
            }
            if (img == null) {
                return null;
            }
            int w = img.getWidth();
            int[] out = new int[16 * 16];
            // animated textures are tall strips: use the first square frame only
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int sx = Math.min(w - 1, x * w / 16 + Math.max(0, w / 32));
                    int sy = Math.min(img.getHeight() - 1, y * w / 16 + Math.max(0, w / 32));
                    out[y * 16 + x] = img.getRGB(sx, sy);
                }
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }
}
