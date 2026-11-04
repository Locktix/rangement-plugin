package fr.rangement;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;

public final class Rangement extends JavaPlugin {

    static final Component PREFIXE = Component.text("[Rangement] ", NamedTextColor.GOLD);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Ajoute dans config.yml les options des nouvelles versions
        getConfig().options().copyDefaults(true);
        saveConfig();

        getServer().getPluginManager().registerEvents(new Ecouteur(this), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, e ->
                e.registrar().register("rangement", "Aide du coffre de tri", List.of("tri"), new Commande()));
    }

    int rayon() {
        return Math.max(1, Math.min(64, getConfig().getInt("rayon", 16)));
    }

    int hauteur() {
        return Math.max(1, Math.min(64, getConfig().getInt("hauteur", 8)));
    }

    Distributeur.Reglages reglages() {
        return new Distributeur.Reglages(rayon(), hauteur(),
                getConfig().getBoolean("familles", true), getConfig().getBoolean("trier-destinations", true));
    }

    static void aide(CommandSender s) {
        s.sendMessage(PREFIXE.append(Component.text("Coffre de tri", NamedTextColor.YELLOW)));
        s.sendMessage(Component.text(" 1. Pose un coffre ou un tonneau, puis accroupi, accroche un panneau dessus avec ", NamedTextColor.GRAY)
                .append(Component.text("[Tri]", NamedTextColor.GOLD))
                .append(Component.text(" sur la 1re ligne (ou renomme le coffre \"Tri\" a l'enclume).", NamedTextColor.GRAY)));
        s.sendMessage(Component.text(" 2. Range un exemplaire de chaque objet dans les coffres autour : c'est ce qui leur donne leur contenu.", NamedTextColor.GRAY));
        s.sendMessage(Component.text(" 3. Vide ton inventaire dans le coffre de tri et ferme-le : tout part dans le bon coffre.", NamedTextColor.GRAY));
        s.sendMessage(Component.text(" Option : un coffre ", NamedTextColor.GRAY)
                .append(Component.text("[Divers]", NamedTextColor.AQUA))
                .append(Component.text(" recoit tout ce qui n'a pas de coffre.", NamedTextColor.GRAY)));
        s.sendMessage(Component.text(" Accroupi + clic droit sur le coffre de tri (ou son panneau) : montre la zone et les coffres pris en compte.", NamedTextColor.GRAY));
    }

    private final class Commande implements BasicCommand {
        @Override
        public void execute(CommandSourceStack src, String[] args) {
            CommandSender s = src.getSender();
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!s.hasPermission("rangement.admin")) {
                    s.sendMessage(PREFIXE.append(Component.text("Tu n'as pas la permission.", NamedTextColor.RED)));
                    return;
                }
                reloadConfig();
                s.sendMessage(PREFIXE.append(Component.text("Configuration rechargee.", NamedTextColor.GREEN)));
                return;
            }
            if (args.length > 0 && (args[0].equalsIgnoreCase("test") || args[0].equalsIgnoreCase("objets"))) {
                if (!s.hasPermission("rangement.admin")) {
                    s.sendMessage(PREFIXE.append(Component.text("Tu n'as pas la permission.", NamedTextColor.RED)));
                    return;
                }
                if (!(src.getExecutor() instanceof Player p)) {
                    s.sendMessage("Commande reservee aux joueurs.");
                    return;
                }
                if (args[0].equalsIgnoreCase("test")) {
                    if (!getConfig().getBoolean("commande-test", false)) {
                        s.sendMessage(PREFIXE.append(Component.text(
                                "/rangement test est desactivee (elle vide une zone de 13 x 7 blocs devant toi). "
                                        + "Pour l'utiliser : commande-test: true dans config.yml puis /rangement reload.",
                                NamedTextColor.RED)));
                        return;
                    }
                    Test.construire(p);
                }
                else Test.donner(p);
                return;
            }
            aide(s);
        }

        @Override
        public Collection<String> suggest(CommandSourceStack src, String[] args) {
            if (args.length <= 1 && src.getSender().hasPermission("rangement.admin"))
                return getConfig().getBoolean("commande-test", false)
                        ? List.of("reload", "test", "objets") : List.of("reload", "objets");
            return List.of();
        }
    }
}
