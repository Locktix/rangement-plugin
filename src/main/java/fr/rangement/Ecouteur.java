package fr.rangement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Ecouteur implements Listener {

    /** Nombre d'objets restants detailles dans un message. */
    private static final int RESTES_AFFICHES = 5;
    private static final Particle.DustOptions ROUGE = new Particle.DustOptions(Color.fromRGB(230, 40, 40), 1.4f);

    private final Rangement plugin;
    private final Zone zone;
    /** Coffres de tri remplis par entonnoir, en attente de rangement. */
    private final Set<Block> enAttente = new HashSet<>();

    Ecouteur(Rangement plugin) {
        this.plugin = plugin;
        this.zone = new Zone(plugin);
    }

    // ------------------------------------------------------------ fermeture du coffre de tri

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFermeture(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory inv = e.getInventory();
        List<Block> blocs = Conteneurs.blocs(inv);
        if (blocs.isEmpty() || Conteneurs.role(blocs) != Role.TRI) return;

        // Au tick suivant, une fois la fermeture terminee (objet tenu au curseur rendu, etc.)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            List<Block> encore = Conteneurs.blocs(inv);
            if (encore.isEmpty() || inv.isEmpty()) return;
            annoncer(p, Distributeur.ranger(inv, encore, plugin.reglages()));
        });
    }

    private void annoncer(Player p, Distributeur.Resultat r) {
        boolean particules = plugin.getConfig().getBoolean("particules", true);
        if (r.ranges() > 0) {
            int n = r.destinations().size();
            Component barre = Component.text("✔ " + r.ranges() + " objet" + pluriel(r.ranges())
                    + " range" + pluriel(r.ranges()) + " dans " + n + " coffre" + pluriel(n), NamedTextColor.GREEN);
            if (!r.pleins().isEmpty()) barre = barre.append(Component.text(
                    "  ⚠ " + r.pleins().size() + " coffre" + pluriel(r.pleins().size()) + " plein" + pluriel(r.pleins().size()),
                    NamedTextColor.RED));
            p.sendActionBar(barre);
            p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.8f, 1f);
            if (particules) {
                for (Block b : r.destinations())
                    if (!r.pleins().contains(b))
                        p.spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 1.1, 0.5), 8, 0.3, 0.2, 0.3);
            }
        }
        if (particules) {
            for (Block b : r.pleins())
                p.spawnParticle(Particle.DUST, b.getLocation().add(0.5, 1.15, 0.5), 12, 0.3, 0.15, 0.3, 0, ROUGE, true);
        }

        if (!r.restesPleins().isEmpty()) {
            p.sendMessage(liste(Component.text("Coffres pleins pour : ", NamedTextColor.RED), r.restesPleins()));
            p.sendMessage(Component.text("  Ajoute un coffre a cote avec un de ces objets dedans, ou vide les coffres en rouge.", NamedTextColor.GRAY));
        }
        if (!r.sansCoffre().isEmpty()) {
            p.sendMessage(liste(Component.text("Pas de coffre pour : ", NamedTextColor.YELLOW), r.sansCoffre()));
            if (r.ranges() == 0) {
                p.sendMessage(Component.text("  Mets un exemplaire de ces objets dans un coffre a cote, ou pose un coffre ", NamedTextColor.GRAY)
                        .append(Component.text("[Divers]", NamedTextColor.AQUA))
                        .append(Component.text(". (/rangement pour l'aide)", NamedTextColor.GRAY)));
            }
        }
    }

    private static Component liste(Component debut, Map<Material, Integer> objets) {
        TextComponent.Builder msg = Component.text().append(Rangement.PREFIXE).append(debut);
        int i = 0;
        for (Map.Entry<Material, Integer> e : objets.entrySet()) {
            if (i == RESTES_AFFICHES) {
                msg.append(Component.text(" et " + (objets.size() - i) + " autre(s)", NamedTextColor.GRAY));
                break;
            }
            if (i > 0) msg.append(Component.text(", ", NamedTextColor.GRAY));
            String cle = e.getKey().getItemTranslationKey();
            msg.append(cle != null ? Component.translatable(cle, NamedTextColor.WHITE)
                    : Component.text(e.getKey().name().toLowerCase(), NamedTextColor.WHITE));
            msg.append(Component.text(" x" + e.getValue(), NamedTextColor.GRAY));
            i++;
        }
        return msg.build();
    }

    private static String pluriel(int n) {
        return n > 1 ? "s" : "";
    }

    // ------------------------------------------------------------ entonnoirs

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntonnoir(InventoryMoveItemEvent e) {
        if (!plugin.getConfig().getBoolean("entonnoirs", true)) return;
        Inventory dest = e.getDestination();
        List<Block> blocs = Conteneurs.blocs(dest);
        if (blocs.isEmpty() || enAttente.contains(blocs.getFirst())) return;
        if (Conteneurs.role(blocs) != Role.TRI) return;

        // Regroupe les objets qui arrivent pendant une seconde, puis range tout d'un coup
        Block cle = blocs.getFirst();
        enAttente.add(cle);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            enAttente.remove(cle);
            List<Block> encore = Conteneurs.blocs(dest);
            if (encore.isEmpty() || dest.isEmpty() || Conteneurs.role(encore) != Role.TRI) return;
            Distributeur.ranger(dest, encore, plugin.reglages());
        }, 20L);
    }

    // ------------------------------------------------------------ accroupi + clic droit : voir la zone

    @EventHandler(priority = EventPriority.HIGH)
    public void onClic(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!p.isSneaking() || e.getClickedBlock() == null) return;

        Block coffre = e.getClickedBlock();
        if (coffre.getState(false) instanceof Sign) coffre = Conteneurs.support(coffre);
        if (!(coffre.getState(false) instanceof Container c) || !Conteneurs.gere(c)) return;
        List<Block> blocs = Conteneurs.blocs(c.getInventory());
        if (blocs.isEmpty() || Conteneurs.role(blocs) != Role.TRI) return;

        e.setCancelled(true);
        zone.montrer(p, blocs);
    }

    // ------------------------------------------------------------ panneaux

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPanneau(SignChangeEvent e) {
        Component ligne = e.line(0);
        if (ligne == null) return;
        Role role = Conteneurs.depuisTexte(ligne);
        if (role == Role.NORMAL) return;
        Block support = Conteneurs.support(e.getBlock());
        if (!Conteneurs.gere(support.getState(false))) return;

        Player p = e.getPlayer();
        if (role == Role.TRI) {
            e.line(0, Component.text("[Tri]", NamedTextColor.GOLD, TextDecoration.BOLD));
            p.sendMessage(Rangement.PREFIXE.append(Component.text(
                    "Coffre de tri cree ! Ce que tu y mets part dans les coffres a " + plugin.rayon()
                            + " blocs autour qui contiennent deja le meme objet. Accroupi + clic droit dessus pour voir la zone.",
                    NamedTextColor.GREEN)));
        } else {
            e.line(0, Component.text("[Divers]", NamedTextColor.AQUA, TextDecoration.BOLD));
            p.sendMessage(Rangement.PREFIXE.append(Component.text(
                    "Coffre divers cree : il recevra ce qui n'a pas de coffre.", NamedTextColor.GREEN)));
        }
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
    }
}
