package fr.rangement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tests avec un faux serveur (MockBukkit) : panneaux, familles, tri d'un coffre, repartition. */
class RangementTest {

    private ServerMock serveur;
    private WorldMock monde;

    @BeforeEach
    void demarrer() {
        serveur = MockBukkit.mock();
        monde = serveur.addSimpleWorld("monde");
    }

    @AfterEach
    void arreter() {
        MockBukkit.unmock();
    }

    // ------------------------------------------------------------ panneaux

    @Test
    void depuisTexte_reconnaitLesPanneaux() {
        assertEquals(Role.TRI, Conteneurs.depuisTexte(Component.text("[Tri]")));
        assertEquals(Role.TRI, Conteneurs.depuisTexte(Component.text("  tri ")));
        assertEquals(Role.TRI, Conteneurs.depuisTexte(Component.text("[ Rangement ]", NamedTextColor.GOLD)));
        assertEquals(Role.DIVERS, Conteneurs.depuisTexte(Component.text("[DIVERS]")));
        assertEquals(Role.NORMAL, Conteneurs.depuisTexte(Component.text("Triangle")));
        assertEquals(Role.NORMAL, Conteneurs.depuisTexte(Component.empty()));
    }

    // ------------------------------------------------------------ familles

    @Test
    void familles_regroupentLesVariantes() {
        assertNotNull(Familles.de(Material.OAK_LOG));
        assertEquals(Familles.de(Material.OAK_LOG), Familles.de(Material.BIRCH_LOG));
        assertEquals(Familles.de(Material.RED_WOOL), Familles.de(Material.BLUE_WOOL));
        assertNotEquals(Familles.de(Material.OAK_LOG), Familles.de(Material.OAK_PLANKS));
        // Verre normal et verre teinte vont ensemble (regle sur le nom)
        assertEquals(Familles.de(Material.RED_STAINED_GLASS), Familles.de(Material.GLASS));
        assertEquals(Familles.de(Material.MUSIC_DISC_CAT), Familles.de(Material.MUSIC_DISC_13));
        assertNull(Familles.de(Material.DIAMOND));
    }

    // ------------------------------------------------------------ tri d'un coffre
    // (Tri.trier compare les objets avec ItemMeta.getAsString(), que MockBukkit ne simule pas :
    // on teste separement la fusion des piles et l'ordre des categories)

    @Test
    void fusionner_regroupeLesPilesIncompletes() {
        ItemStack[] contenu = {new ItemStack(Material.STONE, 10), null, new ItemStack(Material.DIAMOND, 3),
                new ItemStack(Material.STONE, 60), new ItemStack(Material.STONE, 64)};

        List<ItemStack> objets = Tri.fusionner(contenu);

        // 10 + 60 + 64 = 134 pierres -> 64 + 64 + 6
        assertEquals(List.of(new ItemStack(Material.STONE, 64), new ItemStack(Material.DIAMOND, 3),
                new ItemStack(Material.STONE, 64), new ItemStack(Material.STONE, 6)), objets);
        // Le contenu d'origine n'est pas touche
        assertEquals(10, contenu[0].getAmount());
    }

    @Test
    void fusionner_neMelangePasDesObjetsDifferents() {
        ItemStack nomme = new ItemStack(Material.STONE, 10);
        nomme.editMeta(m -> m.displayName(Component.text("Pierre speciale")));
        ItemStack[] contenu = {nomme, new ItemStack(Material.STONE, 10)};

        List<ItemStack> objets = Tri.fusionner(contenu);

        assertEquals(2, objets.size());
        assertEquals(20, objets.stream().mapToInt(ItemStack::getAmount).sum());
    }

    @Test
    void categorie_outilsBlocsObjetsNourriture() {
        assertEquals(0, Tri.categorie(new ItemStack(Material.DIAMOND_PICKAXE)));
        assertEquals(0, Tri.categorie(new ItemStack(Material.IRON_CHESTPLATE)));
        assertEquals(1, Tri.categorie(new ItemStack(Material.STONE)));
        assertEquals(2, Tri.categorie(new ItemStack(Material.DIAMOND)));
        assertEquals(3, Tri.categorie(new ItemStack(Material.BREAD)));
    }

    // ------------------------------------------------------------ rangement depuis un coffre de tri
    // (la recherche des coffres autour passe par Chunk.getTileEntities, que MockBukkit ne simule pas :
    // on donne les coffres cibles directement a Distributeur.repartir)

    private static final Distributeur.Reglages REGLAGES = new Distributeur.Reglages(16, 8, true, false);

    private int prochainX;

    private Inventory coffre(ItemStack... contenu) {
        Inventory inv = serveur.createInventory(null, 27);
        inv.addItem(contenu);
        return inv;
    }

    /** Cible a une distance croissante (dans l'ordre de creation). */
    private Distributeur.Cible cible(Inventory inv, Role role) {
        int x = ++prochainX;
        return Distributeur.cible(inv, monde.getBlockAt(x, 64, 0), role, x * x);
    }

    private static int total(Inventory inv, Material m) {
        return Arrays.stream(inv.getContents()).filter(it -> it != null && it.getType() == m)
                .mapToInt(ItemStack::getAmount).sum();
    }

    @Test
    void repartir_envoieChaqueObjetDansLeCoffreQuiEnContientDeja() {
        Inventory tri = coffre(new ItemStack(Material.COBBLESTONE, 30), new ItemStack(Material.WHEAT, 12),
                new ItemStack(Material.DIAMOND, 2));
        Inventory pierres = coffre(new ItemStack(Material.COBBLESTONE, 1));
        Inventory ble = coffre(new ItemStack(Material.WHEAT, 1));

        Distributeur.Resultat r = Distributeur.repartir(tri,
                List.of(cible(pierres, Role.NORMAL), cible(ble, Role.NORMAL)), REGLAGES);

        assertEquals(42, r.ranges());
        assertEquals(31, total(pierres, Material.COBBLESTONE));
        assertEquals(13, total(ble, Material.WHEAT));
        // Le diamant n'a pas de coffre : il reste dans le coffre de tri et c'est signale
        assertEquals(2, total(tri, Material.DIAMOND));
        assertEquals(0, total(tri, Material.COBBLESTONE));
        assertEquals(Map.of(Material.DIAMOND, 2), r.sansCoffre());
        assertTrue(r.restesPleins().isEmpty());
        assertEquals(2, r.destinations().size());
    }

    @Test
    void repartir_memeObjetAvantFamilleAvantDivers() {
        Inventory tri = coffre(new ItemStack(Material.BIRCH_LOG, 16), new ItemStack(Material.OAK_LOG, 8),
                new ItemStack(Material.DIAMOND, 2));
        Inventory divers = coffre();
        Inventory chene = coffre(new ItemStack(Material.OAK_LOG, 1));
        Inventory bouleau = coffre(new ItemStack(Material.BIRCH_LOG, 1));

        // Le coffre divers est le plus proche, mais il ne passe qu'en dernier
        Distributeur.repartir(tri, List.of(cible(divers, Role.DIVERS), cible(chene, Role.NORMAL),
                cible(bouleau, Role.NORMAL)), REGLAGES);

        assertEquals(17, total(bouleau, Material.BIRCH_LOG));
        assertEquals(9, total(chene, Material.OAK_LOG));
        assertEquals(2, total(divers, Material.DIAMOND));
        assertEquals(0, total(divers, Material.BIRCH_LOG) + total(divers, Material.OAK_LOG));
        assertTrue(tri.isEmpty());
    }

    @Test
    void repartir_familleQuandAucunCoffreNaLObjetExact() {
        Inventory tri = coffre(new ItemStack(Material.SPRUCE_LOG, 20));
        Inventory bois = coffre(new ItemStack(Material.OAK_LOG, 1));

        Distributeur.repartir(tri, List.of(cible(bois, Role.NORMAL)), REGLAGES);
        assertEquals(20, total(bois, Material.SPRUCE_LOG));

        // Familles desactivees dans config.yml : le bois reste dans le coffre de tri
        Inventory tri2 = coffre(new ItemStack(Material.SPRUCE_LOG, 20));
        Inventory bois2 = coffre(new ItemStack(Material.OAK_LOG, 1));
        Distributeur.Resultat r = Distributeur.repartir(tri2, List.of(cible(bois2, Role.NORMAL)),
                new Distributeur.Reglages(16, 8, false, false));
        assertEquals(20, total(tri2, Material.SPRUCE_LOG));
        assertEquals(Map.of(Material.SPRUCE_LOG, 20), r.sansCoffre());
    }

    @Test
    void repartir_debordeDansLeCoffreSuivantPuisSignaleLesPleins() {
        ItemStack[] presquePlein = new ItemStack[27];
        for (int i = 0; i < 26; i++) presquePlein[i] = new ItemStack(Material.COBBLESTONE, 64);
        presquePlein[26] = new ItemStack(Material.COBBLESTONE, 54); // 10 places
        Inventory tri = coffre(new ItemStack(Material.COBBLESTONE, 64), new ItemStack(Material.COBBLESTONE, 30));
        Inventory proche = serveur.createInventory(null, 27);
        proche.setContents(presquePlein);
        Inventory loin = coffre(new ItemStack(Material.COBBLESTONE, 1));
        Distributeur.Cible cibleProche = cible(proche, Role.NORMAL);

        Distributeur.Resultat r = Distributeur.repartir(tri, List.of(cibleProche, cible(loin, Role.NORMAL)), REGLAGES);

        // Rien ne se perd : 94 objets au depart, 10 dans le coffre proche, 84 dans le suivant
        assertEquals(94, r.ranges());
        assertEquals(27 * 64, total(proche, Material.COBBLESTONE));
        assertEquals(85, total(loin, Material.COBBLESTONE));
        assertTrue(tri.isEmpty());
        assertTrue(r.pleins().contains(cibleProche.bloc()));
    }

    @Test
    void repartir_toutPleinRienNeSePerd() {
        ItemStack[] plein = new ItemStack[27];
        for (int i = 0; i < 27; i++) plein[i] = new ItemStack(Material.COBBLESTONE, 64);
        Inventory tri = coffre(new ItemStack(Material.COBBLESTONE, 20));
        Inventory cible = serveur.createInventory(null, 27);
        cible.setContents(plein);

        Distributeur.Resultat r = Distributeur.repartir(tri, List.of(cible(cible, Role.NORMAL)), REGLAGES);

        assertEquals(0, r.ranges());
        assertEquals(20, total(tri, Material.COBBLESTONE));
        assertEquals(Map.of(Material.COBBLESTONE, 20), r.restesPleins());
        assertTrue(r.sansCoffre().isEmpty());
        assertTrue(r.destinations().isEmpty());
    }
}
