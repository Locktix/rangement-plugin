package fr.rangement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Montre en particules la zone d'un coffre de tri et les coffres qu'elle contient. */
final class Zone {

    private static final Particle.DustOptions BORD = new Particle.DustOptions(Color.fromRGB(255, 170, 0), 1.2f);
    private static final Particle.DustOptions TRI = new Particle.DustOptions(Color.fromRGB(255, 200, 0), 1.6f);
    private static final Particle.DustOptions NORMAL = new Particle.DustOptions(Color.fromRGB(60, 220, 60), 1.4f);
    private static final Particle.DustOptions DIVERS = new Particle.DustOptions(Color.fromRGB(60, 200, 255), 1.4f);

    /** Duree d'affichage : 30 fois toutes les 10 ticks = 15 secondes. */
    private static final int REPETITIONS = 30;

    private final Rangement plugin;
    private final Map<UUID, BukkitTask> actives = new HashMap<>();

    Zone(Rangement plugin) {
        this.plugin = plugin;
    }

    void montrer(Player p, List<Block> blocsTri) {
        BukkitTask ancienne = actives.remove(p.getUniqueId());
        if (ancienne != null) ancienne.cancel();

        int r = plugin.rayon(), h = plugin.hauteur();
        Block o = blocsTri.getFirst();
        World monde = o.getWorld();
        double x0 = o.getX() - r, x1 = o.getX() + r + 1;
        double y0 = o.getY() - h, y1 = o.getY() + h + 1;
        double z0 = o.getZ() - r, z1 = o.getZ() + r + 1;

        List<Distributeur.Cible> cibles = Distributeur.chercher(blocsTri, r, h);
        long divers = cibles.stream().filter(c -> c.role() == Role.DIVERS).count();
        long normaux = cibles.size() - divers;
        p.sendMessage(Rangement.PREFIXE.append(Component.text(
                "Zone de tri : " + (2 * r + 1) + " x " + (2 * h + 1) + " x " + (2 * r + 1) + " blocs. ", NamedTextColor.YELLOW))
                .append(Component.text(normaux + " coffre" + (normaux > 1 ? "s" : "") + " de rangement", NamedTextColor.GREEN))
                .append(Component.text(", ", NamedTextColor.GRAY))
                .append(Component.text(divers + " divers", NamedTextColor.AQUA))
                .append(Component.text(".", NamedTextColor.GRAY)));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);

        int[] tours = {0};
        BukkitTask tache = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline() || !p.getWorld().equals(monde) || tours[0]++ >= REPETITIONS) {
                BukkitTask t = actives.remove(p.getUniqueId());
                if (t != null) t.cancel();
                return;
            }
            // Les 12 aretes de la boite
            for (double y : new double[]{y0, y1}) {
                ligne(p, monde, x0, y, z0, x1, y, z0);
                ligne(p, monde, x0, y, z1, x1, y, z1);
                ligne(p, monde, x0, y, z0, x0, y, z1);
                ligne(p, monde, x1, y, z0, x1, y, z1);
            }
            for (double x : new double[]{x0, x1})
                for (double z : new double[]{z0, z1}) ligne(p, monde, x, y0, z, x, y1, z);

            // Les coffres
            for (Block b : blocsTri) marquer(p, b, TRI);
            for (Distributeur.Cible c : cibles) marquer(p, c.bloc(), c.role() == Role.DIVERS ? DIVERS : NORMAL);
        }, 0L, 10L);
        actives.put(p.getUniqueId(), tache);
    }

    private static void ligne(Player p, World w, double xa, double ya, double za, double xb, double yb, double zb) {
        double dx = xb - xa, dy = yb - ya, dz = zb - za;
        int pas = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz));
        for (int i = 0; i <= pas; i++) {
            double t = (double) i / pas;
            p.spawnParticle(Particle.DUST, new Location(w, xa + dx * t, ya + dy * t, za + dz * t),
                    1, 0, 0, 0, 0, BORD, true);
        }
    }

    private static void marquer(Player p, Block b, Particle.DustOptions couleur) {
        p.spawnParticle(Particle.DUST, b.getLocation().add(0.5, 1.15, 0.5), 5, 0.25, 0.08, 0.25, 0, couleur, true);
    }
}
