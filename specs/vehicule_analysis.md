# Rails et véhicules — analyse

## Contexte

Ajout de blocs `rail` orientables/statefull, formant des `segment`s (ligne ouverte) ou `loop`s (boucle fermée), et d'un item `vehicule` posable qui avance dessus à VITESSE constante, changeant de direction en bout de segment, ou tournant indéfiniment sur une loop.

## État actuel du code (rappel)

- **BlockDefinition** (`core/.../game/world/BlockDefinition.kt`) : props statiques par `BlockType` — `hardness`, `solid`, `isCubic`, `brickSize`, `rotatable`, `modelElement`. Chargé depuis `resources/blocks/<NAME>/<NAME>.yaml` (+ override `data/resources/blocks/<name>/<name>.yaml`).
- **BlockState** (`core/.../game/world/BlockState.kt`) : **1 byte par bloc** — bits 0-1 = rotation cardinale (0..3), bits 2-7 = index couleur palette (0-63). Pas de state arbitraire multi-valeurs aujourd'hui.
- **Rotation existante** : 4 orientations à 90° seulement, gérée à la pose (`LocalPlayerController.kt`, `BlockPlacer.kt`), rendue via `SceneMesher.kt` qui indexe les meshes en `(ord*4 + rotation)*6` — donc jusqu'à 4 variantes de mesh par forme. Exemple non-cubique orienté existant : `LEGO_SLOPE`, `LEGO_CORNER`, `LEGO_STEP_*`.
- **Aucun bloc stateful** (type porte ouverte/fermée) n'existe dans le repo — le byte `BlockState` est déjà saturé par rotation+couleur.
- **ItemDefinition** (`resources/config/items.yaml`) : `placesBlock: BlockType?` — un item pose un bloc, point. Aucun précédent d'item qui spawn une entité mobile.
- **NPC** (`server/.../game/npc/`) : pipeline serveur autoritaire mature — `NpcBehavior.tick(instance, world, ctx)`, `NpcPhysics`, `AabbCollider`, `NpcTickPipeline`, `NpcSpawner`. `RandomMovableNpcBehavior` fait du wander avec machine à états (Pausing/Moving/Decel). Bon squelette réutilisable pour un mouvement contraint sur trajectoire, mais rien d'existant pour suivre un rail.

## 1. Blocs rail

### 1.1 Types de bloc à créer

| BlockType | Géométrie | Rotatable | Remarque |
|---|---|---|---|
| `RAIL_STRAIGHT` | droit, horizontal | oui (4×90°) | segment de base |
| `RAIL_CURVE_90` | courbe 90° horizontale | oui (4×90°) | |
| `RAIL_CURVE_45` | courbe 45° horizontale | oui (4×90°) | |
| `RAIL_CURVE_60` | courbe 60° horizontale | oui (4×90°) | |
| `RAIL_SLOPE_45` | droit incliné 45° | oui (4×90°) | pente — géométrie non-cubique façon `LEGO_SLOPE`, sens montée/descente porté par l'angle du modèle, pas par un bit d'état |
| `RAIL_SLOPE_22` | droit incliné 22.5° | oui (4×90°) | idem, sens montée/descente porté par l'angle du modèle |
| `RAIL_Y_SPLIT` | Y : 1 entrée, 2 sorties (45°/45°) | oui (4×90°) | **stateful** : `state1` = sortie branche 1 active, `state2` = sortie branche 2 active |

### 1.2 Impact rotation/angles

`BlockState` code la rotation sur 2 bits (4 valeurs) — **suffisant tel quel**, aucune extension nécessaire. Tous les rails (y compris les courbes 45°/60°/22.5°) restent sur le modèle de rotation existant à 4×90° : l'angle fin (45° vs 60° vs 22.5°) est porté par le `BlockType` lui-même (donc par le modèle 3D/`modelElement`), pas par le state. `RAIL_CURVE_45` et `RAIL_CURVE_60` sont deux types de bloc distincts, chacun rotatable sur les 4 orientations cardinales standard — comme `LEGO_SLOPE`/`LEGO_CORNER` déjà. Idem pour les pentes : le sens montée/descente est une propriété du modèle (le `modelElement` incliné pointe déjà dans un sens), pas un bit de state supplémentaire.

### 1.3 Bloc stateful (`RAIL_Y_SPLIT`)

Premier bloc du jeu à avoir un état fonctionnel au-delà de rotation+couleur. Le byte `BlockState` actuel (2 bits rotation + 6 bits couleur) n'a plus de bit libre pour porter `state1`/`state2`.

Options :
- **Sacrifier des bits de couleur** sur ce type de bloc seulement (ex: 1 bit couleur en moins, ou palette réduite à 32 pour les rails) — pas d'impact réseau, mais incohérent bloc à bloc.
- **Étendre `BlockState` à 2 bytes** — propre et extensible (utile pour de futurs blocs stateful : portes, leviers, etc.), mais impacte : format chunk persistant (`WorldPersistence.kt`), protocole réseau (wire format bloc), `SceneMesher.kt` (lecture du state), `BlockBreaker`/`BlockPlacer`. Migration de sauvegarde à prévoir (chunks existants en 1 byte).

Recommandation : **étendre `BlockState` à 2 bytes** — la dette d'un hack "1 bit volé" se paiera au premier bloc stateful suivant (portes, leviers, etc. sont des extensions naturelles). Prévoir une commande admin ou une migration automatique au chargement de chunk pour les mondes existants (`WorldPersistence.kt`).

Sémantique `RAIL_Y_SPLIT` : le state contrôle quelle branche de sortie est active pour le passage d'un véhicule — bascule via interaction joueur (clic droit) ou commande, comme un aiguillage. `state1`/`state2` = un seul bit suffit en réalité (2 valeurs) ; nommage `state1`/`state2` de la demande = 2 valeurs mutuellement exclusives d'un même champ, pas 2 bits indépendants.

### 1.4 Segment vs loop — notion serveur, pas bloc

`segment` et `loop` ne sont **pas** des propriétés de bloc individuel mais une propriété **dérivée** de la topologie du réseau de rails, calculée côté serveur :

- Un `segment` = chaîne de blocs rail connectés dont les deux extrémités ne se reconnectent pas entre elles (bouts "ouverts", ou terminaison sur un bloc non-rail).
- Une `loop` = chaîne de blocs rail connectés qui se referme sur elle-même (le voisin "sortie" du dernier bloc est le premier bloc).

Détection : parcours de graphe (BFS/DFS) à partir de chaque bloc rail modifié (pose/casse), en suivant les connexions "entrée/sortie" déduites de la rotation + du type de bloc (ex: `RAIL_STRAIGHT` a 2 connexions opposées selon sa rotation ; `RAIL_Y_SPLIT` a 1 entrée + 2 sorties possibles selon son state). Résultat mis en cache par région/chunk et invalidé sur `BlockBreaker`/`BlockPlacer` touchant un bloc rail — nouveau composant serveur, ex. `RailNetworkRegistry` (miroir de `SceneRegistry`/`BlockRegistry` en pattern).

## 2. Item véhicule

### 2.1 Placement

Aucun précédent d'item spawnant une entité (`ItemDefinition.placesBlock` ne pose qu'un bloc). Approche retenue : **entité pure** — nouveau champ `ItemDefinition.spawnsEntity: EntityType?` (à côté de `placesBlock`), posé sur un bloc rail valide seulement (validation serveur : la position ciblée doit être un `RAIL_*`). Le véhicule est une entité serveur-autoritaire distincte du monde voxel, comme un NPC — cohérent avec le pipeline NPC existant.

Placement via slash command dédiée : `/vehicule:add <vehiculeName>` — récupère le bloc visé par le joueur (raycast, comme la pose de bloc), valide que c'est un `RAIL_*`, spawn l'entité véhicule dessus. Le sens de parcours initial du véhicule est déterminé par l'angle entre l'orientation du joueur et celle du rail visé (le joueur regardant "avec" ou "contre" le sens du rail détermine la direction de départ).

### 2.2 Comportement serveur (`VehicleBehavior`)

Nouveau `VehicleBehavior : NpcBehavior`-like (ou interface sœur si le véhicule n'a pas besoin de toutes les features NPC — santé, IA de combat, etc.) :

- État : position sur le réseau de rails = (bloc rail courant, progression 0..1 le long du bloc, direction de parcours).
- `tick()` : avance de `VITESSE * deltaTime` le long du rail courant.
  - Sur `segment` : en bout de segment (dernier bloc, pas de connexion sortante dans le sens courant), inverse la direction de parcours (fait demi-tour) — cf. exigence "change de direction".
  - Sur `loop` : à la fin d'un bloc, passe systématiquement au suivant dans la boucle, sans jamais s'arrêter — avance "indéfiniment".
  - Sur `RAIL_Y_SPLIT` : la sortie prise dépend du `state` courant du bloc (aiguillage) au moment du passage.
- Réutilise `AabbCollider`/`NpcPhysics` pour la hauteur Y (pentes `RAIL_SLOPE_*`), mais le déplacement XZ est **contraint au rail** (pas de physique libre comme un NPC qui wander) — donc pas de collision latérale à calculer, juste suivre la courbe géométrique du bloc courant (interpolation le long de la forme du modelElement : droite, arc 45°/60°/90°, pente).
- Position/orientation répliquées au client via un message réseau dédié (nouveau `ServerMessage.VehicleUpdate` ou extension du pattern `PlayerUpdate`), à fréquence tick serveur, avec interpolation client comme pour les autres entités.

### 2.3 Détection de segment/loop pour le comportement

`VehicleBehavior` interroge le `RailNetworkRegistry` (§1.4) à chaque changement de bloc rail pour savoir si son bloc courant appartient à un `segment` (→ gérer les demi-tours en bout) ou une `loop` (→ jamais de demi-tour). Pas besoin que le véhicule recalcule la topologie lui-même.

## 3. Rendu client

- Rails non-stateful (`RAIL_STRAIGHT`, `RAIL_CURVE_*`, `RAIL_SLOPE_*`) : mesh statique par `(BlockType, rotation)`, pattern identique à `LEGO_SLOPE`/`LEGO_CORNER` dans `SceneMesher.kt` — pas de changement d'architecture, juste ajout de modèles + réutilisation de l'indexage `(ord*4 + rotation)*6` existant, sans extension.
- `RAIL_Y_SPLIT` : mesh dépend en plus du `state` (aiguillage visuel gauche/droite) — `SceneMesher` doit lire le 2ᵉ byte de state (§1.3) pour choisir la variante.
- Véhicule : entité rendue comme un NPC/joueur (modèle bbmodel ou primitive), interpolée en continu entre updates serveur — pas de mesh de chunk, rendu séparé du voxel world (comme `AdminScenePreview`/entités actuelles).

## 4. Résumé des impacts / risques

| Zone | Impact | Risque |
|---|---|---|
| `BlockState` (1→2 bytes) | Format chunk persistant + protocole réseau + migration mondes existants | Moyen-élevé — touche tout le pipeline bloc |
| `RailNetworkRegistry` (nouveau) | Détection segment/loop, invalidation sur pose/casse | Moyen — nouveau composant, logique de graphe |
| `VehicleBehavior` + entité véhicule (nouveau) | Nouveau protocole réseau (`VehicleUpdate`), tick serveur, interpolation client | Élevé — nouveau type d'entité de bout en bout |
| `ItemDefinition.spawnsEntity` (nouveau) | Item → entité au lieu d'item → bloc | Faible-moyen — extension localisée |
| Modèles 3D rails (bbmodel/gltf) | Assets à produire pour chaque forme × rotation | Hors code, mais volume de travail non négligeable (7 types × jusqu'à 8 rotations) |

## 5. Décisions v1

1. **VITESSE** : par-item véhicule — champ dans `ItemDefinition` (ou définition dédiée type `VehicleDefinition`), pas de constante globale. Permet plusieurs types de véhicules à vitesses différentes sans refonte.
2. **Joueur à bord** : non — v1 purement automatique/décoratif. Le joueur pose le véhicule, pose/oriente les rails, bascule les aiguillages, mais ne monte pas dessus. Pas de contrôle caméra/input à gérer. Extensible plus tard (hors scope v1).
3. **Collision véhicule-véhicule** : ignorée en v1 — les véhicules se traversent, pas de détection de proximité entre véhicules à chaque tick.
4. **`RAIL_Y_SPLIT`** : bascule manuelle uniquement — le `state` ne change que sur interaction joueur (clic droit) ou commande slash, comme un levier. Pas d'alternance automatique en v1.
