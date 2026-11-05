# Rangement

Plugin [Paper](https://papermc.io/) : un coffre de tri qui range tout seul son contenu dans les coffres voisins.

## Utilisation

1. Pose un coffre ou un tonneau et mets un panneau **`[Tri]`** dessus (ou renomme-le « Tri » à l'enclume).
2. Mets tes objets dedans et ferme-le : chaque objet part dans le coffre le plus proche qui contient
   déjà ce même objet.
3. Facultatif : un coffre avec un panneau **`[Divers]`** (ou renommé « Divers ») reçoit ce qui n'a pas
   trouvé de place.

Autres comportements (tous réglables dans `config.yml`) :

- **Familles** : si aucun coffre n'a l'objet exact, il va avec un objet de la même famille
  (chêne → bouleau, laine rouge → laine bleue, minerais, fleurs, poissons…).
- Les coffres qui reçoivent des objets sont remis en ordre.
- Les objets amenés par un **entonnoir** dans le coffre de tri sont aussi rangés (pratique pour les fermes).
- Petites particules vertes sur les coffres qui ont reçu des objets.
- Rayon de recherche : 16 blocs autour, 8 blocs en hauteur (par défaut).

## Commandes

| Commande | Description | Permission |
|---|---|---|
| `/rangement reload` | Recharge la configuration | `rangement.admin` |
| `/rangement objets` | Donne des objets au hasard pour tester le tri | `rangement.admin` |
| `/rangement test` | Construit un coin de test devant soi | `rangement.admin` |

> ⚠️ `/rangement test` vide une zone de 13 × 7 blocs. Elle est désactivée par défaut
> (`commande-test: false`) : à ne pas activer sur un vrai serveur.

## Prérequis

- Serveur Paper 26.x (`api-version: 26.3`)
- Java 25

## Compiler

Le projet se compile avec les jars de Paper du serveur : le dossier du plugin doit être placé dans le
dossier du serveur (à côté de `libraries/`). Les jars de test (JUnit, MockBukkit) listés dans
`lib/test/dependances.txt` sont téléchargés automatiquement depuis Maven Central au premier build.

```powershell
powershell -File build.ps1              # compile, lance les tests, crée build\Rangement-1.0.0.jar
powershell -File build.ps1 -Installer   # ... et copie le jar dans plugins\
powershell -File build.ps1 -SansTests   # sans les tests
```
