package fr.rangement;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Familles d'objets : un coffre qui contient du chene accepte aussi le bouleau, une laine rouge accepte
 * toutes les laines, etc. Basee sur les tags du jeu, plus quelques regles sur le nom.
 */
final class Familles {

    /** Du plus precis au plus large : le premier tag qui contient l'objet donne sa famille. */
    private static final List<Tag<Material>> TAGS = List.of(
            Tag.ITEMS_LOGS, Tag.ITEMS_PLANKS, Tag.ITEMS_LEAVES, Tag.ITEMS_SAPLINGS,
            Tag.ITEMS_WOOL, Tag.ITEMS_WOOL_CARPETS, Tag.ITEMS_WOOL_SLABS, Tag.ITEMS_WOOL_STAIRS,
            Tag.ITEMS_GLAZED_TERRACOTTA, Tag.ITEMS_TERRACOTTA,
            Tag.ITEMS_CONCRETE_POWDERS, Tag.ITEMS_CONCRETE_SLABS, Tag.ITEMS_CONCRETE_STAIRS, Tag.ITEMS_CONCRETE,
            Tag.ITEMS_CANDLES, Tag.ITEMS_BEDS, Tag.ITEMS_BANNERS, Tag.ITEMS_DYES,
            Tag.ITEMS_SMALL_FLOWERS, Tag.ITEMS_FLOWERS, Tag.ITEMS_MUSHROOMS,
            Tag.ITEMS_ORES, Tag.ITEMS_SAND, Tag.ITEMS_STONE_BRICKS,
            Tag.ITEMS_FISHES, Tag.ITEMS_MEAT, Tag.ITEMS_EGGS, Tag.ITEMS_ARROWS,
            Tag.ITEMS_CHEST_BOATS, Tag.ITEMS_BOATS, Tag.ITEMS_HANGING_SIGNS, Tag.ITEMS_SIGNS,
            Tag.ITEMS_DOORS, Tag.ITEMS_TRAPDOORS, Tag.ITEMS_FENCE_GATES, Tag.ITEMS_FENCES,
            Tag.ITEMS_WOODEN_PRESSURE_PLATES, Tag.ITEMS_BUTTONS, Tag.ITEMS_RAILS,
            Tag.ITEMS_WOODEN_STAIRS, Tag.ITEMS_WOODEN_SLABS, Tag.ITEMS_STAIRS, Tag.ITEMS_SLABS, Tag.ITEMS_WALLS,
            Tag.ITEMS_SKULLS, Tag.ITEMS_SHULKER_BOXES, Tag.ITEMS_DECORATED_POT_SHERDS,
            Tag.ITEMS_SWORDS, Tag.ITEMS_PICKAXES, Tag.ITEMS_AXES, Tag.ITEMS_SHOVELS, Tag.ITEMS_HOES);

    /** Familles reconnues par la fin (ou le debut) du nom, pour ce que les tags ne couvrent pas. */
    private static final List<String> SUFFIXES = List.of(
            "_STAINED_GLASS_PANE", "_STAINED_GLASS", "_SPAWN_EGG", "_SMITHING_TEMPLATE", "_SEEDS");
    private static final List<String> PREFIXES = List.of("MUSIC_DISC_");

    private static final Map<Material, String> CACHE = new HashMap<>();

    private Familles() {}

    /** Nom de la famille de l'objet, ou null s'il n'en a pas. */
    static String de(Material m) {
        String f = CACHE.computeIfAbsent(m, Familles::calculer);
        return f.isEmpty() ? null : f;
    }

    private static String calculer(Material m) {
        for (Tag<Material> t : TAGS) if (t.isTagged(m)) return t.getKey().asString();
        String nom = m.name();
        for (String s : SUFFIXES) if (nom.endsWith(s)) return "*" + s;
        for (String p : PREFIXES) if (nom.startsWith(p)) return p + "*";
        if (m == Material.GLASS) return "*_STAINED_GLASS";
        if (m == Material.GLASS_PANE) return "*_STAINED_GLASS_PANE";
        return "";
    }
}
