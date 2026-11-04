package fr.rangement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** /rangement test : construit un coin de rangement devant le joueur et lui donne des objets au hasard. */
final class Test {

    /** Un coffre d'exemple : sa position (devant, a droite), son objet modele, son nom sur le panneau. */
    private record Exemple(int devant, int droite, Material objet, String nom, boolean grand, boolean tonneau, boolean plein) {}

    private static final List<Exemple> EXEMPLES = List.of(
            new Exemple(4, -5, Material.COBBLESTONE, "Pierre", true, false, false),
            new Exemple(4, -2, Material.DIRT, "Terre", false, false, false),
            new Exemple(4, 0, Material.OAK_LOG, "Bois", false, false, false),
            new Exemple(4, 2, Material.IRON_INGOT, "Fer", false, false, false),
            new Exemple(4, 4, Material.COAL, "Charbon", false, false, false),
            new Exemple(6, -2, Material.BREAD, "Pain", false, true, false),
            new Exemple(6, 0, Material.SAND, "Sable (plein)", false, false, true));

    /** Objets de la meme famille qu'un coffre (bois, sable) : ils doivent y aller quand meme. */
    private static final List<Material> FAMILLE = List.of(
            Material.BIRCH_LOG, Material.SPRUCE_LOG, Material.CHERRY_LOG, Material.RED_SAND);

    /** Objets qui n'ont pas de coffre : ils iront dans le coffre [Divers]. */
    private static final List<Material> ORPHELINS = List.of(
            Material.ROTTEN_FLESH, Material.BONE, Material.STRING, Material.GUNPOWDER, Material.FLINT,
            Material.WHEAT_SEEDS, Material.ARROW, Material.DIAMOND_PICKAXE);

    private Test() {}

    static void construire(Player p) {
        BlockFace avant = p.getFacing();
        BlockFace droite = horaire(avant);
        BlockFace versJoueur = avant.getOppositeFace();
        Block pied = p.getLocation().getBlock();

        // Fait de la place : sol en pierre lisse, air au-dessus
        for (int d = 1; d <= 7; d++) {
            for (int l = -6; l <= 6; l++) {
                Block b = decaler(pied, avant, d, droite, l);
                b.getRelative(BlockFace.DOWN).setType(Material.SMOOTH_STONE, false);
                for (int h = 0; h < 4; h++) b.getRelative(BlockFace.UP, h).setType(Material.AIR, false);
            }
        }

        // Coffre de tri, juste devant
        Block tri = decaler(pied, avant, 2, droite, 0);
        poserCoffre(tri, versJoueur, Chest.Type.SINGLE);
        poserPanneau(tri, versJoueur, Component.text("[Tri]", NamedTextColor.GOLD, TextDecoration.BOLD), "Mets tout ici");

        // Coffres de rangement avec un objet modele chacun
        for (Exemple ex : EXEMPLES) {
            Block b = decaler(pied, avant, ex.devant(), droite, ex.droite());
            if (ex.tonneau()) {
                b.setType(Material.BARREL, false);
                if (b.getBlockData() instanceof Directional dir) {
                    dir.setFacing(BlockFace.UP);
                    b.setBlockData(dir, false);
                }
            } else if (ex.grand()) {
                // La moitie LEFT a son autre moitie dans le sens horaire de son orientation
                poserCoffre(b, versJoueur, Chest.Type.LEFT);
                poserCoffre(b.getRelative(horaire(versJoueur)), versJoueur, Chest.Type.RIGHT);
            } else {
                poserCoffre(b, versJoueur, Chest.Type.SINGLE);
            }
            if (b.getState(false) instanceof Container c) {
                Inventory inv = c.getInventory();
                if (ex.plein()) for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, new ItemStack(ex.objet(), 64));
                else inv.addItem(new ItemStack(ex.objet()));
            }
            poserPanneau(b, versJoueur, Component.text(ex.nom(), NamedTextColor.DARK_BLUE), "");
        }

        // Coffre divers
        Block divers = decaler(pied, avant, 6, droite, 3);
        poserCoffre(divers, versJoueur, Chest.Type.SINGLE);
        poserPanneau(divers, versJoueur, Component.text("[Divers]", NamedTextColor.AQUA, TextDecoration.BOLD), "Le reste");

        donner(p);
        p.sendMessage(Rangement.PREFIXE.append(Component.text("Coin de test construit devant toi.", NamedTextColor.GREEN)));
        p.sendMessage(Component.text(" Vide ton inventaire dans le coffre [Tri] et ferme-le. ", NamedTextColor.GRAY)
                .append(Component.text("/rangement objets", NamedTextColor.YELLOW))
                .append(Component.text(" pour en ravoir. Accroupi + clic droit sur le coffre [Tri] : voir la zone. Le coffre de sable est plein expres. Casse le panneau [Divers] pour voir les messages.", NamedTextColor.GRAY)));
    }

    /** Donne une quinzaine de piles au hasard, dont certaines sans coffre. */
    static void donner(Player p) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int donnees = 0, perdues = 0;
        for (int i = 0; i < 15; i++) {
            int tirage = r.nextInt(4);
            Material m = tirage == 0 ? ORPHELINS.get(r.nextInt(ORPHELINS.size()))
                    : tirage == 1 ? FAMILLE.get(r.nextInt(FAMILLE.size()))
                    : EXEMPLES.get(r.nextInt(EXEMPLES.size())).objet();
            ItemStack it = new ItemStack(m, 1 + r.nextInt(m.getMaxStackSize()));
            if (p.getInventory().addItem(it).isEmpty()) donnees++;
            else perdues++;
        }
        p.sendMessage(Rangement.PREFIXE.append(Component.text(donnees + " piles d'objets donnees"
                + (perdues > 0 ? " (" + perdues + " n'ont pas tenu, inventaire plein)" : "") + ".", NamedTextColor.GREEN)));
    }

    private static void poserCoffre(Block b, BlockFace facing, Chest.Type type) {
        b.setType(Material.CHEST, false);
        Chest data = (Chest) b.getBlockData();
        data.setFacing(facing);
        data.setType(type);
        b.setBlockData(data, false);
    }

    /** Panneau pose sur le coffre, tourne vers le joueur, cire pour qu'un clic n'ouvre pas l'edition. */
    private static void poserPanneau(Block coffre, BlockFace versJoueur, Component ligne1, String ligne2) {
        Block s = coffre.getRelative(BlockFace.UP);
        s.setType(Material.OAK_SIGN, false);
        if (s.getBlockData() instanceof Rotatable rot) {
            rot.setRotation(versJoueur);
            s.setBlockData(rot, false);
        }
        if (s.getState() instanceof Sign panneau) {
            panneau.getSide(Side.FRONT).line(0, ligne1);
            panneau.getSide(Side.FRONT).line(1, Component.text(ligne2));
            panneau.setWaxed(true);
            panneau.update(true, false);
        }
    }

    private static Block decaler(Block b, BlockFace avant, int d, BlockFace droite, int l) {
        return b.getRelative(avant, d).getRelative(droite, l);
    }

    private static BlockFace horaire(BlockFace f) {
        return switch (f) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }
}
