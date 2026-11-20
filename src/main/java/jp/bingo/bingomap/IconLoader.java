package jp.bingo.bingomap;

/**
 * Loads a texture (path such as "item/diamond" or "block/stone", under assets/minecraft/textures/)
 * and returns it as a 16x16 ARGB array, or null when not found.
 * Implemented on the client side only (the host's client in an Essential / integrated-server game).
 */
@FunctionalInterface
public interface IconLoader {
    int[] load(String path);
}
