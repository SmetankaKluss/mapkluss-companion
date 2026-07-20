package art.mapkluss.companion;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class CompanionMaterialIcons {
    private static final ConcurrentMap<String, ItemStack> CACHE = new ConcurrentHashMap<>();

    private CompanionMaterialIcons() {
    }

    static ItemStack stackFor(BuildSessionMaterial material) {
        if (material == null) return ItemStack.EMPTY;
        return CACHE.computeIfAbsent(material.nbtName(), CompanionMaterialIcons::createStack);
    }

    private static ItemStack createStack(String rawName) {
        String normalized = normalize(rawName);
        if (normalized.isBlank() || "unknown".equals(normalized) || "air".equals(normalized)) {
            return Items.BARRIER.getDefaultInstance();
        }

        Identifier id = Identifier.tryParse(normalized.contains(":") ? normalized : "minecraft:" + normalized);
        if (id == null) return Items.BARRIER.getDefaultInstance();

        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.BARRIER);
        return item.getDefaultInstance();
    }

    static String normalize(String rawName) {
        if (rawName == null) return "";
        String value = rawName.trim().toLowerCase(Locale.ROOT);
        int stateIndex = value.indexOf('[');
        if (stateIndex >= 0) value = value.substring(0, stateIndex);
        int nbtIndex = value.indexOf('{');
        if (nbtIndex >= 0) value = value.substring(0, nbtIndex);
        if (value.startsWith("block.minecraft.")) {
            value = value.substring("block.minecraft.".length());
        }
        if (value.startsWith("minecraft:")) {
            return "minecraft:" + value.substring("minecraft:".length()).replaceAll("[^a-z0-9_./-]", "_");
        }
        return value.replaceAll("[^a-z0-9_./-]", "_");
    }
}
