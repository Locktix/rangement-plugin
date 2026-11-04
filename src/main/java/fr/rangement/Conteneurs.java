package fr.rangement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Nameable;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.Locale;

/** Reconnaitre les coffres et tonneaux, et lire leur role (panneau ou nom donne a l'enclume). */
final class Conteneurs {

    private static final BlockFace[] COTES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private Conteneurs() {}

    /** Coffre (simple, piege, en cuivre) ou tonneau. */
    static boolean gere(BlockState st) {
        return st instanceof Chest || st instanceof Barrel;
    }

    /** Les blocs d'un inventaire de coffre ou de tonneau (2 pour un grand coffre), ou une liste vide. */
    static List<Block> blocs(Inventory inv) {
        InventoryHolder h = inv.getHolder(false);
        if (h instanceof DoubleChest dc
                && dc.getLeftSide(false) instanceof BlockState g
                && dc.getRightSide(false) instanceof BlockState d) {
            return List.of(g.getBlock(), d.getBlock());
        }
        if (h instanceof BlockState st && gere(st)) return List.of(st.getBlock());
        return List.of();
    }

    /** Role d'un coffre a partir de ses blocs : [Tri] l'emporte sur [Divers]. */
    static Role role(List<Block> blocs) {
        Role r = Role.NORMAL;
        for (Block b : blocs) {
            Role rb = roleBloc(b);
            if (rb == Role.TRI) return Role.TRI;
            if (rb == Role.DIVERS) r = Role.DIVERS;
        }
        return r;
    }

    private static Role roleBloc(Block b) {
        // Nom donne a l'enclume
        if (b.getState(false) instanceof Nameable n && n.customName() != null) {
            Role r = depuisTexte(n.customName());
            if (r != Role.NORMAL) return r;
        }
        // Panneau accroche sur un cote
        for (BlockFace f : COTES) {
            Block s = b.getRelative(f);
            if (s.getBlockData() instanceof WallSign ws && ws.getFacing() == f) {
                Role r = lirePanneau(s);
                if (r != Role.NORMAL) return r;
            }
        }
        // Panneau pose dessus
        Block haut = b.getRelative(BlockFace.UP);
        if (haut.getBlockData() instanceof org.bukkit.block.data.type.Sign) return lirePanneau(haut);
        return Role.NORMAL;
    }

    private static Role lirePanneau(Block s) {
        if (!(s.getState(false) instanceof Sign panneau)) return Role.NORMAL;
        for (Side cote : Side.values()) {
            Role r = depuisTexte(panneau.getSide(cote).line(0));
            if (r != Role.NORMAL) return r;
        }
        return Role.NORMAL;
    }

    /** "[Tri]", "tri", "[Rangement]" -> TRI ; "[Divers]" -> DIVERS. */
    static Role depuisTexte(Component c) {
        String t = PlainTextComponentSerializer.plainText().serialize(c).trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("[") && t.endsWith("]")) t = t.substring(1, t.length() - 1).trim();
        return switch (t) {
            case "tri", "rangement" -> Role.TRI;
            case "divers" -> Role.DIVERS;
            default -> Role.NORMAL;
        };
    }

    /** Bloc auquel un panneau est accroche (mural) ou sur lequel il est pose. */
    static Block support(Block panneau) {
        if (panneau.getBlockData() instanceof WallSign ws) return panneau.getRelative(ws.getFacing().getOppositeFace());
        return panneau.getRelative(BlockFace.DOWN);
    }
}
