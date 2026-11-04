package fr.rangement;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Remet en ordre un coffre : piles fusionnees, puis outils, blocs, objets, nourriture (meme ordre que SurviePlus). */
final class Tri {

    private static final Comparator<ItemStack> ORDRE = Comparator
            .comparingInt(Tri::categorie)
            .thenComparing(it -> famille(it.getType()))
            .thenComparing(it -> it.getType().name())
            .thenComparing(it -> it.hasItemMeta() ? it.getItemMeta().getAsString() : "")
            .thenComparing(Comparator.comparingInt(ItemStack::getAmount).reversed());

    private Tri() {}

    static void trier(Inventory inv) {
        // 1. Fusionne les piles identiques incompletes
        List<ItemStack> objets = new ArrayList<>();
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.isEmpty()) continue;
            int reste = it.getAmount();
            for (ItemStack f : objets) {
                if (reste == 0) break;
                if (!f.isSimilar(it) || f.getAmount() >= f.getMaxStackSize()) continue;
                int ajout = Math.min(reste, f.getMaxStackSize() - f.getAmount());
                f.setAmount(f.getAmount() + ajout);
                reste -= ajout;
            }
            if (reste > 0) {
                ItemStack copie = it.clone();
                copie.setAmount(reste);
                objets.add(copie);
            }
        }
        // 2. Trie et remet en place
        objets.sort(ORDRE);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, i < objets.size() ? objets.get(i) : null);
    }

    /** 0 = outils, armes et armures ; 1 = blocs ; 2 = objets ; 3 = nourriture. */
    private static int categorie(ItemStack it) {
        Material m = it.getType();
        if (m.getMaxDurability() > 0) return 0;
        if (m.isEdible()) return 3;
        if (m.isBlock()) return 1;
        return 2;
    }

    /** Dernier mot du nom (OAK_LOG -> LOG, IRON_INGOT -> INGOT) pour regrouper les objets de meme sorte. */
    private static String famille(Material m) {
        String nom = m.name();
        int i = nom.lastIndexOf('_');
        return i < 0 ? nom : nom.substring(i + 1);
    }
}
