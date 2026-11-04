package fr.rangement;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Envoie le contenu d'un coffre de tri dans les coffres voisins. */
final class Distributeur {

    /** Un coffre de rangement trouve autour du coffre de tri. */
    record Cible(Inventory inv, Block bloc, Role role, Set<Material> contenu, Set<String> familles, double distance2) {}

    /** Reglages lus dans config.yml. */
    record Reglages(int rayon, int hauteur, boolean familles, boolean trier) {}

    /**
     * @param ranges       nombre d'objets deplaces
     * @param destinations coffres qui ont recu quelque chose (un bloc par coffre)
     * @param pleins       coffres qui auraient du recevoir un objet mais n'avaient plus de place
     * @param restesPleins objets restes dans le coffre de tri parce que leurs coffres sont pleins
     * @param sansCoffre   objets restes dans le coffre de tri faute de coffre
     */
    record Resultat(int ranges, Set<Block> destinations, Set<Block> pleins,
                    Map<Material, Integer> restesPleins, Map<Material, Integer> sansCoffre) {}

    private Distributeur() {}

    static Resultat ranger(Inventory source, List<Block> blocsSource, Reglages reglages) {
        return repartir(source, chercher(blocsSource, reglages.rayon(), reglages.hauteur()), reglages);
    }

    /** Repartit le contenu de la source dans les cibles (deja triees de la plus proche a la plus loin). */
    static Resultat repartir(Inventory source, List<Cible> cibles, Reglages reglages) {
        int ranges = 0;
        Set<Block> destinations = new LinkedHashSet<>();
        Set<Block> pleins = new LinkedHashSet<>();
        Map<Material, Integer> restesPleins = new LinkedHashMap<>();
        Map<Material, Integer> sansCoffre = new LinkedHashMap<>();
        Set<Inventory> aTrier = new LinkedHashSet<>();

        for (int slot = 0; slot < source.getSize(); slot++) {
            ItemStack it = source.getItem(slot);
            if (it == null || it.isEmpty()) continue;
            Material m = it.getType();
            String famille = reglages.familles() ? Familles.de(m) : null;
            ItemStack reste = it.clone();
            boolean candidat = false;

            // 1. Les coffres qui contiennent deja cet objet, du plus proche au plus loin
            for (Cible c : cibles) {
                if (reste == null) break;
                if (c.role() != Role.NORMAL || !c.contenu().contains(m)) continue;
                candidat = true;
                reste = ajouter(c, reste, destinations, pleins, aTrier);
            }
            // 2. Les coffres qui contiennent un objet de la meme famille (chene -> bouleau...)
            if (famille != null) {
                for (Cible c : cibles) {
                    if (reste == null) break;
                    if (c.role() != Role.NORMAL || c.contenu().contains(m) || !c.familles().contains(famille)) continue;
                    candidat = true;
                    reste = ajouter(c, reste, destinations, pleins, aTrier);
                }
            }
            // 3. Les coffres [Divers]
            for (Cible c : cibles) {
                if (reste == null) break;
                if (c.role() != Role.DIVERS) continue;
                candidat = true;
                reste = ajouter(c, reste, destinations, pleins, aTrier);
            }

            int restant = reste == null ? 0 : reste.getAmount();
            if (restant < it.getAmount()) {
                ranges += it.getAmount() - restant;
                source.setItem(slot, reste);
            }
            if (restant > 0) (candidat ? restesPleins : sansCoffre).merge(m, restant, Integer::sum);
        }

        if (reglages.trier()) for (Inventory inv : aTrier) Tri.trier(inv);
        return new Resultat(ranges, destinations, pleins, restesPleins, sansCoffre);
    }

    /** Ajoute la pile au coffre ; renvoie ce qui n'a pas tenu (null si tout est passe). */
    private static ItemStack ajouter(Cible c, ItemStack pile, Set<Block> destinations, Set<Block> pleins,
                                     Set<Inventory> aTrier) {
        int avant = pile.getAmount();
        Map<Integer, ItemStack> refus = c.inv().addItem(pile.clone());
        ItemStack reste = refus.isEmpty() ? null : refus.values().iterator().next();
        if (reste == null || reste.getAmount() < avant) {
            destinations.add(c.bloc());
            aTrier.add(c.inv());
        }
        if (reste != null) pleins.add(c.bloc());
        return reste;
    }

    /** Coffres et tonneaux des chunks charges autour (sauf les coffres de tri), du plus proche au plus loin. */
    static List<Cible> chercher(List<Block> blocsSource, int rayon, int hauteur) {
        Block origine = blocsSource.getFirst();
        World monde = origine.getWorld();
        int ox = origine.getX(), oy = origine.getY(), oz = origine.getZ();

        Set<Block> vus = new HashSet<>(blocsSource);
        List<Cible> cibles = new ArrayList<>();
        for (int cx = (ox - rayon) >> 4; cx <= (ox + rayon) >> 4; cx++) {
            for (int cz = (oz - rayon) >> 4; cz <= (oz + rayon) >> 4; cz++) {
                if (!monde.isChunkLoaded(cx, cz)) continue;
                for (BlockState st : monde.getChunkAt(cx, cz).getTileEntities(false)) {
                    if (!Conteneurs.gere(st)) continue;
                    Block b = st.getBlock();
                    if (vus.contains(b)) continue;
                    if (Math.abs(b.getX() - ox) > rayon || Math.abs(b.getZ() - oz) > rayon
                            || Math.abs(b.getY() - oy) > hauteur) continue;

                    Inventory inv = ((Container) st).getInventory();
                    List<Block> blocs = Conteneurs.blocs(inv);
                    if (blocs.isEmpty()) continue;
                    vus.addAll(blocs);
                    Role role = Conteneurs.role(blocs);
                    if (role == Role.TRI) continue;

                    cibles.add(cible(inv, b, role, b.getLocation().distanceSquared(origine.getLocation())));
                }
            }
        }
        cibles.sort(Comparator.comparingDouble(Cible::distance2));
        return cibles;
    }

    /** Cible a partir de ce que contient deja le coffre (objets et familles d'objets). */
    static Cible cible(Inventory inv, Block b, Role role, double distance2) {
        Set<Material> contenu = new HashSet<>();
        Set<String> familles = new HashSet<>();
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.isEmpty()) continue;
            if (contenu.add(it.getType())) {
                String f = Familles.de(it.getType());
                if (f != null) familles.add(f);
            }
        }
        return new Cible(inv, b, role, contenu, familles, distance2);
    }
}
