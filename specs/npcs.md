# Plan — Nouvelles entités `resources/entities/`

## Contexte

`resources/entities/` couvre aujourd'hui la faune de ferme, quelques prédateurs (wolf, tiger, lion, shark, crocodile), des variantes humanoïdes (`*_man`) et deux mobs "boss-like" (`little_stone_dragon`, `pterodactyl`). Il manque des tiers bas (passifs faciles, tier 1), des hostiles de forêt/désert/montagne (tier 2-4), des mini-boss de zone haute (tier 5), et de la variété aquatique/undead. Le système NPC (comportement, aggro, attaques/sorts, taming, pack, reproduction) est **déjà complet côté code** — ce plan est presque entièrement du contenu (yaml + bbmodel via `/add-npc-model` + quelques nouveaux fichiers d'attaque), plus une petite extension de code pour exposer `NpcTier` (actuellement défini dans `NpcDefinition.kt` mais absent de `NpcYamlEntry`/yaml).

Objectif : combler les tiers 1→5 (mapping skill-level → zone/npc-level du CLAUDE.md : 1→1-5, 2→6-10, 3→11-15, 4→16-20, 5→21-25+) sur tous les biomes (`snow_peaks, desert, dry_plains, plains, forest, pine_forest`), avec une répartition passif/hostile/tameable cohérente, et des attaques/status-effects variés en réutilisant le pipeline `attacks:`/`spells:` existant (`resources/config/skills/attacks/*.yaml`, `StatusEffect` sealed class).

## 0. Prérequis code : exposer `NpcTier` + schema JSON de validation

- `NpcTier` (`COMMON`, `ELITE`, `BOSS`) existe dans `NpcDefinition.kt` mais n'est pas lisible depuis yaml.
- Ajouter `tier: NpcTier = NpcTier.COMMON` à `NpcYamlEntry` (server, package `game/npc`), le propager vers `NpcDefinition` dans le mapper yaml→definition (même fichier/zone que les autres champs comme `aggroMode`).
- Server-side change → nécessite un test dans `server/src/test/` (règle CLAUDE.md) : vérifier qu'un yaml avec `tier: BOSS` charge bien un `NpcDefinition.tier == BOSS`, et qu'un yaml sans le champ retombe sur `COMMON` (défaut rétrocompatible).
- Après le changement : `make dev-restart-server` avant de spawn les nouvelles entités.

**Schema JSON + validation (nouveau — actuellement les NPC yaml invalides sont silencieusement droppés avec juste un warn log, aucun schema n'existe pour ce type de config) :**

- Suivre `/update-schema` (workflow déjà documenté dans CLAUDE.md §"Schema maintenance") pour ajouter `data/config/schemas/npc.schema.json` couvrant l'intégralité de `NpcYamlEntry` : `behavior` (enum `NpcBehaviorRegistry.keys()`), `aggroMode` (enum `AggroMode`), `characterClass` (enum `CharacterClass`), `tier` (enum `NpcTier`, nouveau), `movementMode` (enum `MovementMode`, array), `animal.diet` (enum `NpcDiet`), `attacks[].attackId`/`spells[]` (string, référence croisée vérifiée à l'exécution plutôt que dans le schema statique), plus les bornes numériques déjà en place (`hp>0`, `minLevel<=maxLevel`, `tameBaseChance` dans `[0,1]`, etc.).
- Générer le schema à partir des data classes Kotlin si un générateur existe déjà pour les autres types de config (vérifier le pattern utilisé pour `blocks/`, `biomes.yaml`, `items.yaml` — CLAUDE.md indique une table de correspondance dans `/update-schema`) ; sinon l'écrire à la main en suivant le style des schemas existants dans `data/config/schemas/`.
- **Mécanique de validation** : brancher le chargement dans `NpcRegistryLoader` (ou le point d'entrée générique de validation introduit par le refactor récent `refactor(config): centralize config paths and schema validation`, commit `6da57ba8`) pour que chaque `resources/entities/<name>/<name>.yaml` (et son override `data/resources/entities/<name>/<name>.yaml`) soit validé contre `npc.schema.json` **avant** parsing Kotlin. Un échec de validation doit produire une erreur de chargement explicite (nom du fichier + chemin JSON Pointer de l'erreur) au lieu du warn silencieux actuel — remplace le comportement "drop silencieux" par un fail-fast loggé, cohérent avec les autres types de config déjà validés par schema.
- Ajouter un test `server/src/test/.../NpcSchemaValidationTest.kt` : un yaml valide passe, un yaml avec `behavior: not_a_real_behavior` ou `tameBaseChance: 2.5` est rejeté avec message clair.
- Toutes les 48 entités du roster ci-dessous doivent passer cette validation — c'est donc un prérequis à traiter **avant** le batch de contenu, pour attraper les erreurs de yaml tôt plutôt qu'à la fin.

## 1. Roster des entités (48 : 38 common/elite + 10 boss)

Légende attaque : **[R]** = attaque/spell existant réutilisé tel quel, **[N]** = nouveau yaml à créer sous `resources/config/skills/attacks/` (ou `.../spells/` pour les capacités spéciales de boss).

### Batch 1 — 23 entités (tiers 1-5, cf. détail complet plus bas)

fox, rabbit, owl, boar, jackal, spider, vulture, hermit_man, scorpion, bear, skeleton, zombie, bandit_man, ice_wolf, snow_owl, ghost, eel, octopus, jellyfish, turtle, yeti, sand_worm, golem.

### Tier 1 (niveau 1-5) — passifs, remplissage plaines/forêt

| entity | biome(s) | morpho | behavior | tameable | attaques | tier(NpcTier) |
|---|---|---|---|---|---|---|
| `fox` | forest, pine_forest, plains | biped_animal (queue touffue) | `animal`, aggroMode PASSIVE, diet OMNIVORE, fuit joueur, `preyTypes:[duck, cat_baby]` | non | aucune (fuite pure) | COMMON |
| `rabbit` | plains, dry_plains, forest | quadruped petit | `animal`, PASSIVE, HERBIVORE, `fleeRadius` élevé, reproduction rapide | non | aucune | COMMON |
| `owl` | forest, pine_forest | biped_animal ailé | `animal`, PASSIVE, nocturne (spawn cosmétique, pas de mécanique jour/nuit dédiée — reste passif tout le temps), CARNIVORE, `preyTypes:[rabbit]` | non | `owl_dive` **[N]** (physique faible, uniquement contre proie, pas contre joueur) | COMMON |

**Descriptions modèle (input `/add-npc-model`) :**

- **`fox`** — quadruped/biped_animal, petit gabarit (~0.5×0.4 bloc, plus bas que `wolf`), silhouette fine et allongée : museau pointu, grandes oreilles triangulaires dressées, queue touffue longue (≈ moitié de la longueur du corps) tenue à l'horizontale. Fourrure roux-orangé sur le dos/flancs/tête, ventre et bout de la queue blancs, pattes basses noires ("chaussettes"). Yeux ambrés. Pattern : dégradé roux→blanc sur le ventre avec bruit de mottling léger pour texture de poil, pas de rayures.
- **`rabbit`** — quadruped tout petit (~0.35×0.3 bloc), corps compact arrondi, grandes oreilles longues dressées à l'arrière du crâne, pattes arrière visiblement plus grosses que les pattes avant, queue en petit pompon. Fourrure gris-brun sur le dos, ventre/pompon blancs. Pas de motif complexe, juste un léger bruit de grain pour éviter l'aspect plat. Yeux noirs simples.
- **`owl`** — biped_animal ailé, gabarit moyen (~0.6×0.7 bloc de haut), silhouette trapue verticale (pas de cou visible, tête large intégrée au corps), grands yeux frontaux ronds (jaunes sur fond de disque facial clair), bec crochu court, ailes repliées le long du corps au repos, serres visibles. Plumage brun tacheté (pattern moucheté/tacheté, pas rayé) sur le dos et les ailes, disque facial et poitrine plus clairs (crème/gris pâle).

### Tier 2 (niveau 6-10) — hostiles légers / tameables forêt-plaine

| entity | biome(s) | morpho | behavior | tameable | attaques | tier |
|---|---|---|---|---|---|---|
| `boar` | forest, plains, dry_plains | quadruped robuste, défenses | `animal`, PASSIVE_COOPERATIVE (charge si provoqué), OMNIVORE | oui (0.15) | `boar_charge` **[N]** (physical, knockback via `statusEffect: Stunned` court) | COMMON |
| `jackal` | desert, dry_plains | quadruped fin | `animal` + `pack` (réutilise `PackConfig` façon wolf), AGGRESSIVE, CARNIVORE | non | `poison_bite` **[R]** | COMMON |
| `spider` | forest, pine_forest (dense) | other (8 pattes, custom bbmodel) | `animal`, AGGRESSIVE, CARNIVORE, petit `aggroRange` (embuscade) | non | `spider_web_bite` **[N]** (poison léger + `statusEffect: Paralyzed` bref = "toile") | COMMON |
| `vulture` | desert, dry_plains | biped_animal ailé | `animal`, PASSIVE sauf mob mort à proximité (réutilise diet/preyTypes sur charognes si supporté, sinon PASSIVE simple) | non | `vulture_peck` **[N]** (physical faible) | COMMON |
| `hermit_man` | forest, pine_forest, snow_peaks | humanoid | `interactionable` (dialogue/quête, pas de combat) | n/a | aucune | COMMON |

**Descriptions modèle :**

- **`boar`** — quadruped robuste, gabarit proche de `pig` mais plus massif et bas sur pattes (~0.9×0.7 bloc), dos voûté avec crête de poils raides le long de l'échine, deux petites défenses blanches visibles de chaque côté du groin, tête large et courte. Peau/poil brun-gris foncé, crête plus sombre presque noire, pas de motif complexe — juste grain + léger gradient d'AO sous le ventre.
- **`jackal`** — quadruped fin, plus petit et plus élancé que `wolf` (~0.45×0.55 bloc), oreilles larges et pointues, museau fin, queue touffue basse. Pelage sable/beige avec pattern de mottling léger, pattes et bout de museau plus foncés (brun), pas de rayures — silhouette "chien du désert" agile.
- **`spider`** — morphologie `other` custom : corps en deux segments (céphalothorax + abdomen bulbeux), huit pattes fines articulées disposées symétriquement (4 par côté), pas de tête distincte — cluster de petits yeux peints sur le céphalothorax. Gabarit compact (~0.7×0.4 bloc, large mais bas). Couleur noir-brun avec motif marbré subtil sur l'abdomen, pattes plus claires aux articulations.
- **`vulture`** — biped_animal ailé, gabarit proche `eagle` mais silhouette plus voûtée/décharnée : cou long et fin peu emplumé (couleur peau nue grisâtre), tête petite sans plumes, grandes ailes larges repliées en "cape" au repos. Plumage principal brun-noir terne, cou et tête gris-rosé nu, bec crochu clair.
- **`hermit_man`** — humanoid gabarit standard (proche `pig_man`/`crocodile_man` en proportions humaines), silhouette voûtée (posture légèrement penchée en avant), vêtements simples en toile brun-beige rapiécée (patchwork visible via variations de teinte sur le torse), longue barbe grise peinte sur le bas du visage, capuche ou chapeau conique simple. Pas d'armure, palette terreuse neutre.

### Tier 3 (niveau 11-15) — hostiles moyens, premiers undead/bandits

| entity | biome(s) | morpho | behavior | tameable | attaques | tier |
|---|---|---|---|---|---|---|
| `scorpion` | desert | quadruped bas + queue custom | `animal`, AGGRESSIVE, CARNIVORE | non | `scorpion_sting` **[N]** (poison, `statusEffect: Poisoned`, DoT) | COMMON |
| `bear` | forest, pine_forest, snow_peaks | quadruped massif | `animal`, PASSIVE_COOPERATIVE (charge si provoqué/protège petits), OMNIVORE | non (trop fort) | `bear_maul` **[N]** (physical fort + `Stunned` court) | ELITE |
| `skeleton` | desert, snow_peaks, dry_plains (ruines) | humanoid (bbmodel os, texture sans "chair") | `random_movable`, AGGRESSIVE, garde distance via `rangeOverride` élevé sur son attaque | non | `skeleton_arrow` **[N]** (physical, ranged via `rangeOverride`) | COMMON |
| `zombie` | plains, dry_plains, forest (nocturne cosmétique) | humanoid (peau grisâtre) | `random_movable`, AGGRESSIVE, lent (wanderSpeed bas) | non | `zombie_bite` **[N]** (physical + `statusEffect: Withering`) | COMMON |
| `bandit_man` | dry_plains, desert (routes) | humanoid armé | `random_movable`, AGGRESSIVE, embuscade (`aggroRange` faible, `deaggroTimeSec` court) | non | `slash` **[R]** | COMMON |

**Descriptions modèle :**

- **`scorpion`** — quadruped bas custom : corps segmenté aplati, quatre paires de pattes courtes sur les côtés, deux pinces avant proéminentes tenues en avant, longue queue segmentée recourbée par-dessus le dos terminée par un dard pointu (élément articulé distinct). Gabarit ras du sol (~0.6×0.25 bloc). Carapace brun-rouge foncé avec reflets plus clairs sur les segments de la queue, pinces d'une teinte légèrement plus sombre.
- **`bear` (ELITE)** — quadruped massif, nettement plus grand/large que `wolf` et `boar` (~1.1×1.0 bloc), tête large avec petites oreilles rondes, museau court, corps trapu avec pattes épaisses, griffes visibles aux pattes avant. Fourrure brun foncé uniforme sur le corps, museau et bord des oreilles légèrement plus clairs, aucun motif complexe — juste variations de grain pour simuler l'épaisseur du poil.
- **`skeleton`** — humanoid gabarit standard mais silhouette amaigrie/anguleuse : membres visiblement plus fins que les autres humanoïdes (cage thoracique et articulations suggérées par le texturing plutôt que par la géométrie), crâne texturé avec orbites noires creuses, pas de vêtements (juste des lambeaux de tissu sombre optionnels autour de la taille). Teinte os blanc-gris cassé avec ombrage sombre dans les creux (orbites, articulations) pour lisibilité.
- **`zombie`** — humanoid gabarit standard, posture légèrement voûtée/raide, vêtements en lambeaux (teinte terreuse délavée avec déchirures suggérées par variations de couleur), peau gris-vert terne avec taches plus sombres (nécrose) sur le visage et les mains. Regard vide (yeux peints sans pupille visible ou d'une teinte pâle uniforme).
- **`bandit_man`** — humanoid gabarit standard, silhouette proche `pig_man` en proportions, capuche/bandana sur le bas du visage, vêtements sombres (cuir/tissu brun-noir) avec une pièce d'armure légère asymétrique (épaulière ou brassard sur un seul bras) pour suggérer l'équipement improvisé. Palette sombre neutre (bruns/gris), pas de couleurs vives.

### Tier 4 (niveau 16-20) — hostiles forts, montagne/glace

| entity | biome(s) | morpho | behavior | tameable | attaques | tier |
|---|---|---|---|---|---|---|
| `ice_wolf` | snow_peaks | quadruped (reskin loup, fourrure blanche/cristaux) | `animal` + `pack` (`extendPackType` incluant `wolf`? non — pack propre), AGGRESSIVE, CARNIVORE | oui (0.2) | `wolf_bite` **[R]**, `ice_shard_bite` **[N]** (`statusEffect: Frozen`, ralentit cible) | ELITE |
| `snow_owl` | snow_peaks | biped_animal ailé (variante `owl`, palette blanche) | `animal`, PASSIVE, CARNIVORE | non | `owl_dive` **[R]** (réutilise celui de `owl`) | COMMON |
| `ghost` | snow_peaks, forest (ruines/nuit) | other (silhouette translucide — texture avec alpha réduit) | `random_movable`, AGGRESSIVE, `movementMode:[FLYING]` (pas de collision spéciale à coder — juste visuel/traversée de vide déjà permise par flying) | non | `ghost_curse` **[N]** (`statusEffect: Cursed`, dégâts faibles mais debuff long) | ELITE |
| `eel` | rivières/eau (spawnBiomes aquatiques existants) | other (serpentiforme aquatique) | `animal`, AGGRESSIVE, `movementMode:[SWIMMING]`, CARNIVORE | non | `eel_shock` **[N]** (`statusEffect: Paralyzed`, courte) | COMMON |
| `octopus` | aquatique profond | other (tentacules) | `animal`, PASSIVE_COOPERATIVE, OMNIVORE | non | `octopus_ink` **[N]** (`statusEffect: Cursed` léger = "aveuglement", pas de dégâts) | COMMON |
| `jellyfish` | aquatique | other (dôme + tentacules, animation flottante) | `animal`, PASSIVE (contact = dégâts, pas de poursuite active) | non | `jellyfish_sting` **[N]** (`statusEffect: Paralyzed`) | COMMON |
| `turtle` | aquatique + plage (desert/plains côtier) | quadruped à carapace | `animal`, PASSIVE, HERBIVORE, très tanky (`hp` élevé) | oui (0.3, facile) | aucune (défense passive uniquement) | COMMON |

**Descriptions modèle :**

- **`ice_wolf`** — reskin proportions `wolf` à l'identique (quadruped, même gabarit), mais fourrure blanc-bleuté avec pattern de mottling façon givre (touches gris-bleu sur le dos), yeux bleu pâle lumineux, petites pointes de glace/cristaux optionnelles sur l'échine (géométrie additionnelle simple, quelques cuboids fins) pour différencier silhouette du wolf de base.
- **`snow_owl`** — reskin proportions `owl` à l'identique, plumage presque entièrement blanc avec motif tacheté noir clairsemé (au lieu du brun tacheté de l'owl de base), yeux jaunes conservés pour contraste.
- **`ghost`** — humanoid gabarit standard mais silhouette simplifiée/flottante : pas de jambes distinctes (bas du corps en forme de voile/traînée évasée, un seul cuboid effilé remplaçant les deux jambes), bras fins semi-transparents. Texture bleu-blanc translucide (canal alpha réduit uniformément sur toute la texture, pas seulement les bords) avec un léger effet de bruit "brume" plutôt qu'un pattern net. Yeux vides (points sombres simples ou lueur faible).
- **`eel`** — morphologie `other` serpentiforme : corps long et fin composé de plusieurs segments cylindriques articulés (façon chaîne de cuboids décroissants), pas de pattes, petite nageoire dorsale continue sur toute la longueur, tête légèrement aplatie avec petits yeux. Peau lisse gris-vert foncé avec ventre plus clair, pas de motif complexe (surface lisse = priorité sur le gradient plutôt que le mottling).
- **`octopus`** — morphologie `other` : dôme/manteau bulbeux central, huit tentacules fins et longs retombant en dessous (cuboids effilés multi-segments), grands yeux proéminents sur le manteau. Peau rouge-brun avec pattern de taches irrégulières (camouflage), texture légèrement translucide sur les tentacules.
- **`jellyfish`** — morphologie `other` très simple : dôme hémisphérique translucide (alpha réduit, teinte rose/bleu pâle) surmontant un rideau de tentacules fins pendants (nombreux petits cuboids filiformes). Pas d'yeux, pas de tête — silhouette purement flottante, léger effet de bioluminescence via une teinte plus claire au centre du dôme.
- **`turtle`** — quadruped trapu, pattes courtes et épaisses en forme de nageoires, carapace haute en dôme couvrant tout le dos (motif hexagonal/plaques peint sur la carapace), tête petite rétractable, gabarit large et bas (~0.8×0.5 bloc). Carapace vert olive à motif de plaques plus sombres, peau des pattes/tête vert-gris clair.

### Tier 5 (niveau 21-25+) — mini-boss de zone

| entity | biome(s) | morpho | behavior | tameable | attaques | tier |
|---|---|---|---|---|---|---|
| `yeti` | snow_peaks (haute altitude) | humanoid massif | `random_movable`, AGGRESSIVE, `minLevel:18 maxLevel:25` | non | `yeti_slam` **[N]** (physical fort, `aoeRadius` via mécanique spell si dispo sinon simple melee fort + `Stunned`) | BOSS |
| `sand_worm` | desert (profond) | other (custom, segmenté) | `random_movable` (ou `static` avec grand `aggroRange` façon embuscade — à valider avec `NpcBehaviorRegistry`), AGGRESSIVE, `minLevel:18 maxLevel:25` | non | `sand_worm_burrow_slam` **[N]** (physical fort + `Stunned`, gros cooldown) | BOSS |
| `golem` | snow_peaks / dry_plains (ruines) | humanoid bloc de pierre | `static` ou `random_movable` très lent (garde un lieu), AGGRESSIVE, `minLevel:20 maxLevel:25` | non | `golem_stone_smash` **[N]** (physical très fort + `Stunned` long, `cooldownMs` élevé) | BOSS |

**Descriptions modèle :**

- **`yeti`** — humanoid massif, gabarit nettement plus grand que les humanoïdes standards (≈1.3× la hauteur d'un `pig_man`, torse et bras très larges), silhouette simiesque (bras longs, épaules hautes). Fourrure blanche épaisse sur tout le corps (motif de mèches via bruit de grain marqué + gradient d'AO sous les bras/torse), visage/mains gris-bleu foncé nus, petits yeux sombres enfoncés, éventuellement deux petites défenses/crocs visibles.
- **`sand_worm`** — morphologie `other` custom, segmenté façon `eel` mais en beaucoup plus massif et court à l'écran (n'émerge que partiellement du sable) : large "tête" conique en anneaux concentriques façon iris de bouche (segments cylindriques emboîtés de diamètre décroissant vers l'intérieur), pas de membres, pas d'yeux. Peau sable/ocre avec anneaux plus sombres marquant chaque segment, intérieur de la "bouche" plus sombre/rougeâtre.
- **`golem` (BOSS)** — humanoid trapu et anguleux, silhouette clairement "bloc de pierre" : proportions cubiques marquées (torse un large cuboid unique, membres épais et rectangulaires, pas de courbes), fissures/veines lumineuses peintes (teinte orange/bleu selon variante biome) courant sur le torse et les bras pour suggérer un noyau magique interne. Texture roche grise avec bruit de grain fort façon granite, mousse verte optionnelle sur les épaules pour la variante forêt/ruines.

### Batch 2 — 15 entités additionnelles COMMON/ELITE (tiers 1-4, comble biomes/niches restantes)

| entity | biome(s) | morpho | behavior | tameable | attaques | tier |
|---|---|---|---|---|---|---|
| `hawk` | plains, dry_plains | biped_animal ailé | `animal`, PASSIVE (diurne, opposé à `owl`), CARNIVORE, `preyTypes:[rabbit]` | non | `hawk_dive` **[N]** | COMMON |
| `mole` | plains, forest | quadruped minuscule | `animal`, PASSIVE, HERBIVORE, `wanderRadius` faible (reste sous terre/proche du terrier) | non | aucune | COMMON |
| `raccoon` | forest, plains | biped_animal petit | `animal`, PASSIVE_COOPERATIVE, OMNIVORE, opportuniste | oui (0.2) | aucune | COMMON |
| `hedgehog` | plains, forest | quadruped minuscule | `animal`, PASSIVE, HERBIVORE, contact = léger dégât de recul au lieu de fuite | non | `hedgehog_spikes` **[N]** (physical très faible, riposte au contact) | COMMON |
| `hyena` | desert, dry_plains | quadruped | `animal` + `pack`, AGGRESSIVE, CARNIVORE, charognard opportuniste en meute | non | `poison_bite` **[R]** | ELITE |
| `stone_lizard` | desert, dry_plains | quadruped reptile | `animal`, PASSIVE (camouflage, fuite rapide courte distance), OMNIVORE | non | aucune | COMMON |
| `giant_ant` | forest, desert | other (insecte) | `animal`, AGGRESSIVE si nid dérangé sinon PASSIVE, OMNIVORE | non | `giant_ant_bite` **[N]** | COMMON |
| `crab` | plage (desert/plains côtier), aquatique peu profond | other (crustacé) | `animal`, PASSIVE_COOPERATIVE, OMNIVORE | non | `crab_pinch` **[N]** (physical + `statusEffect: Stunned` très bref) | COMMON |
| `moose` | pine_forest, snow_peaks | quadruped très grand | `animal`, PASSIVE_COOPERATIVE (charge si provoqué), HERBIVORE | non | `boar_charge` **[R]** | ELITE |
| `wildcat` (lynx) | forest, pine_forest, snow_peaks | quadruped félin | `animal`, AGGRESSIVE (embuscade, petit `aggroRange`), CARNIVORE | oui (0.2) | `wildcat_pounce` **[N]** | ELITE |
| `harpy` | snow_peaks, dry_plains (falaises) | humanoid ailé | `random_movable`, AGGRESSIVE, `movementMode:[FLYING]` | non | `harpy_shriek` **[N]** (`statusEffect: Stunned` bref, cible unique) | ELITE |
| `cave_bat` | forest, pine_forest, snow_peaks (grottes/nuit) | other (petit, ailé) | `animal` + `pack` (petit essaim, `maxSize` réduit), AGGRESSIVE si dérangé, CARNIVORE, `movementMode:[FLYING]` | non | `cave_bat_bite` **[N]** (physical faible, dégâts en essaim) | COMMON |
| `desert_raider_man` | desert, dry_plains | humanoid armé (variante mieux équipée du bandit) | `random_movable` + `pack` léger (petit groupe de raid), AGGRESSIVE | non | `slash` **[R]**, `desert_raider_javelin` **[N]** (ranged via `rangeOverride`) | ELITE |
| `frost_troll` | snow_peaks | humanoid massif | `random_movable`, AGGRESSIVE | non | `bear_maul` **[R]**, `frost_troll_slam` **[N]** (`statusEffect: Frozen`) | ELITE |
| `giant_toad` | forest, pine_forest (zones humides) | quadruped bas | `animal`, PASSIVE_COOPERATIVE, CARNIVORE, langue longue (bonus `rangeOverride` léger sur l'attaque) | non | `giant_toad_tongue` **[N]** (physical, `rangeOverride` moyen, tire la cible vers soi si supporté sinon simple dégât à distance) | COMMON |

**Descriptions modèle :**

- **`hawk`** — reskin/variation proportions `owl` mais silhouette plus effilée et aérodynamique (corps fuselé, ailes plus pointues en vol), tête plus petite sans disque facial marqué, bec crochu fin. Plumage brun-roux avec poitrine striée (bandes fines horizontales au lieu du mouchetis de l'owl), yeux jaune vif perçants.
- **`mole`** — quadruped minuscule (~0.3×0.2 bloc), corps cylindrique trapu, pattes avant démesurément larges en forme de pelles (griffes épaisses), museau pointu rose, pas d'oreilles visibles, très petits yeux quasi invisibles. Fourrure gris-noir veloutée uniforme, aucun motif — surface la plus lisse du roster pour suggérer le pelage court et dense.
- **`raccoon`** — biped_animal petit (~0.45×0.4 bloc), silhouette trapue avec queue annelée épaisse (alternance clair/sombre — seul roster avec pattern d'anneaux net sur la queue), masque facial noir distinctif autour des yeux sur fond de tête grise, oreilles rondes courtes. Corps gris-brun, pattes plus foncées.
- **`hedgehog`** — quadruped minuscule (~0.3×0.25 bloc), moitié inférieure du corps lisse (ventre/museau brun clair), moitié supérieure hérissée de piquants courts (suggérés par une géométrie en petits cuboids radiaux courts sur le dos plutôt qu'une texture plate), petit museau pointu noir, minuscules pattes à peine visibles sous le corps.
- **`hyena` (ELITE)** — quadruped proche gabarit `jackal` mais plus massif à l'avant (épaules hautes, arrière-train plus bas — silhouette caractéristique "penchée vers l'avant"), grosse tête large avec mâchoire puissante. Pelage tacheté beige-brun (pattern de taches irrégulières, contrairement au mottling uni du jackal), crinière courte plus sombre sur la nuque.
- **`stone_lizard`** — quadruped bas et allongé (~0.5×0.2 bloc), corps couvert d'écailles suggérées par un pattern géométrique répétitif subtil, longue queue effilée traînante, crête de petites épines le long du dos. Couleur gris-brun mouchetée proche de la teinte du sable/roche environnante (camouflage), yeux à pupille fine.
- **`giant_ant`** — morphologie `other`, corps en trois segments nets (tête, thorax, abdomen bulbeux séparés par des "tailles" fines), six pattes fines, grandes mandibules frontales proéminentes, antennes fines sur la tête. Carapace noir-brun luisante (léger effet spéculaire via gradient clair sur le dessus), pas de motif.
- **`crab`** — morphologie `other` large et bas (~0.5×0.2 bloc), carapace ovale plate couvrant le corps, deux grosses pinces frontales asymétriques (une plus grosse que l'autre), pattes fines sur les côtés disposées en éventail. Carapace orange-rouge, pinces d'une teinte plus claire aux extrémités.
- **`moose` (ELITE)** — quadruped très grand (le plus haut gabarit hors boss, ~1.2×1.3 bloc au garrot), silhouette massive avec grand panache de bois large et plat (géométrie en éventail de cuboids fins partant de la tête), long museau tombant. Pelage brun foncé uniforme, bois d'une teinte os clair contrastante.
- **`wildcat` (ELITE)** — quadruped félin élancé (~0.5×0.4 bloc), silhouette basse et musclée, oreilles triangulaires avec petites touffes de poil au sommet (pinceaux caractéristiques du lynx), queue courte. Pelage beige-gris tacheté (motif de taches sombres irrégulières, distinct du mottling du wolf/ice_wolf), yeux verts perçants.
- **`harpy` (ELITE)** — humanoid ailé : torse et tête humanoïdes standards, bras remplacés/prolongés par de grandes ailes de plumes, jambes griffues d'oiseau. Plumage sombre (gris-brun) sur les ailes, peau normale sur le visage/torse, cheveux/plumage de tête ébouriffé. Silhouette nettement plus anguleuse/agressive que les humanoïdes pacifiques.
- **`cave_bat`** — morphologie `other` petite (~0.35×0.25 bloc), corps compact, grandes ailes membraneuses (surface plane triangulaire plutôt que plumes), oreilles disproportionnées, museau plat. Peau/membrane brun-noir uniforme, légère translucidité sur les ailes (alpha réduit comme `ghost` mais plus faible).
- **`desert_raider_man` (ELITE)** — humanoid gabarit standard, équipement visiblement plus complet que `bandit_man` : turban/keffieh couvrant tête et bas du visage (teinte sable), plastron de cuir clouté, avant-bras protégés par des bandages/protections. Palette sable-brun avec touches de cuir foncé, silhouette plus "organisée militairement" que le bandit isolé.
- **`frost_troll` (ELITE)** — humanoid massif (gabarit proche `yeti` mais peau nue plutôt que fourrure), peau bleu-gris grumeleuse (texture avec gros bruit de grain façon peau épaisse/verruqueuse), longues défenses inférieures dépassant de la mâchoire, dos voûté, très longs bras. Pas de vêtements, quelques touffes de poil clairsemées sur les épaules seulement.
- **`giant_toad`** — quadruped bas et large (~0.7×0.35 bloc), corps bulbeux, grande bouche large, yeux protubérants sur le dessus de la tête, pattes arrière repliées puissantes (suggère le bond). Peau verte tachetée de brun (motif de verrues via bruit de mottling marqué), gorge/ventre plus clair jaune-pâle.

### Batch 3 — 10 boss additionnels (BOSS, capacités spéciales via `spells:` en plus des `attacks:` mêlée)

Les boss combinent une attaque de mêlée classique (`attacks:`) et une capacité spéciale (`spells:`, nouveau yaml sous `resources/config/skills/spells/` suivant le format `SpellDefinition` — `aoeRadius`, `cooldownMs`, `manaCost`/`rageCost`, `statusEffect`) pour un moment de combat distinct par boss.

| entity | biome(s) | morpho | behavior | attaque mêlée | capacité spéciale | tier |
|---|---|---|---|---|---|---|
| `frost_wyrm` | snow_peaks (sommets) | other (dragon serpentiforme, ailes réduites) | `random_movable`, `movementMode:[FLYING]`, `minLevel:22 maxLevel:25+` | `slash` **[R]** | `frost_wyrm_breath` **[N]** spell (cône, `aoeRadius`, `statusEffect: Frozen`) | BOSS |
| `ancient_treant` | forest, pine_forest | humanoid arbre géant | `static` (garde une clairière), `minLevel:20 maxLevel:25` | `bear_maul` **[R]** (branches lourdes) | `treant_root_grasp` **[N]** spell (`aoeRadius`, `statusEffect: Stunned`) + `treant_regrowth` **[N]** spell (self-heal, `statusEffect: HpRegenBoost`) | BOSS |
| `pharaoh_wraith` | desert (ruines/tombeaux) | other (silhouette drapée flottante, façon `ghost` mais ornée) | `random_movable`, `movementMode:[FLYING]`, AGGRESSIVE | `ghost_curse` **[R]** | `pharaoh_curse_of_sands` **[N]** spell (`aoeRadius`, `statusEffect: Cursed`, longue durée) | BOSS |
| `kraken_spawn` | aquatique profond | other (tête + tentacules géantes) | `animal`, `movementMode:[SWIMMING]`, AGGRESSIVE | `octopus_ink` **[R]** (mêlée tentacule) | `kraken_tentacle_slam` **[N]** spell (`aoeRadius` large, physical fort) | BOSS |
| `alpha_direwolf` | forest, pine_forest | quadruped (loup géant, reskin `wolf`/`ice_wolf` à plus grande échelle) | `animal` + `pack` (chef de meute, `extendPackType:[wolf, ice_wolf]`), AGGRESSIVE | `wolf_bite` **[R]** | `direwolf_howl_rally` **[N]** spell (buff zone alliés proches, `statusEffect: HpBoost`, pas de dégâts) | BOSS |
| `magma_golem` | desert (profond/volcanique) | humanoid bloc de pierre (variante `golem`, fissures orange lumineuses) | `static`/`random_movable` lent, AGGRESSIVE | `golem_stone_smash` **[R]** | `magma_golem_eruption` **[N]** spell (`aoeRadius`, `statusEffect: Burning`) | BOSS |
| `bog_hydra` | forest, pine_forest (zones humides) | other (corps quadrupède + 3 têtes de serpent sur cous longs) | `animal`, AGGRESSIVE | `scorpion_sting` **[N]** (une des têtes, poison) | `hydra_regrowth` **[N]** spell (self-heal, `statusEffect: HpRegenBoost`, se déclenche sous seuil hp) | BOSS |
| `storm_roc` | snow_peaks (falaises), dry_plains | biped_animal ailé (rapace géant, reskin `hawk`/`vulture` à grande échelle) | `animal`, `movementMode:[FLYING]`, AGGRESSIVE | `hawk_dive` **[R]** | `storm_roc_gust` **[N]** spell (`aoeRadius`, `statusEffect: Stunned`, knockback si supporté) | BOSS |
| `plague_ogre` | dry_plains, desert (ruines) | humanoid massif difforme | `random_movable`, AGGRESSIVE | `bear_maul` **[R]** | `plague_ogre_burst` **[N]** spell (`aoeRadius`, `statusEffect: Poisoned` + `Withering` combinés) | BOSS |
| `crystal_golem` | snow_peaks (grottes de glace) | humanoid bloc de cristal (variante `golem`, cristaux bleus translucides à la place des fissures) | `static`/`random_movable` lent, AGGRESSIVE | `golem_stone_smash` **[R]** | `crystal_shard_barrage` **[N]** spell (ranged, `aoeRadius` moyen, `statusEffect: Frozen`) | BOSS |

**Descriptions modèle :**

- **`frost_wyrm`** — morphologie `other`, corps serpentiforme long façon `eel` mais en beaucoup plus massif et orné : segments cylindriques décroissants couverts d'écailles givrées (pattern cristallin bleu-blanc), petites ailes membraneuses réduites près de la tête (vol suggéré plus que réaliste), tête allongée avec mâchoire proéminente et crocs visibles, cornes recourbées. Teinte bleu glacier avec veines blanches lumineuses.
- **`ancient_treant`** — humanoid géant fait d'écorce et de bois : torse = large tronc noueux (surface texturée en bruit de grain fort façon écorce), bras = branches épaisses se terminant en "mains" de brindilles, tête = amas de branchages avec deux points lumineux verts en guise d'yeux, quelques feuilles/mousse peintes sur les épaules et le sommet du crâne. Teinte brun-gris écorce, mousse verte en accents.
- **`pharaoh_wraith`** — other/humanoid flottant façon `ghost` mais orné : silhouette drapée dans un linceul en lambeaux (bas du corps effilé comme le ghost), tête recouverte d'un masque funéraire doré stylisé (géométrie simple, texture dorée), bijoux/bandelettes peints sur le torse. Teinte violet-doré translucide au lieu du bleu-blanc du ghost basique, alpha réduit similaire.
- **`kraken_spawn`** — other massif : grande tête bulbeuse avec un œil unique proéminent central, couronne de longues tentacules épaisses tout autour (plus grosses et plus nombreuses que `octopus`), ventouses suggérées par un pattern de petits cercles sur la face interne des tentacules. Peau vert-noir profond avec reflets bioluminescents (touches turquoise) sur la tête.
- **`alpha_direwolf`** — reskin `wolf`/`ice_wolf` à échelle nettement plus grande (≈1.4× le gabarit du wolf standard) et silhouette plus imposante (poitrine plus large, crinière épaisse autour du cou), cicatrices peintes sur le museau/flanc pour marquer le statut de chef. Pelage gris-noir foncé avec crinière plus claire, yeux rouge-orangé (distinct du jaune du wolf normal).
- **`magma_golem`** — variante directe de `golem` : mêmes proportions cubiques/anguleuses, mais roche plus sombre presque noire (basalte) avec fissures largement plus nombreuses et lumineuses (orange-rouge vif au lieu du bleu discret du golem de base), petites particules de braise peintes en surface près des fissures.
- **`bog_hydra`** — other : corps quadrupède trapu bas sur pattes (façon `turtle` sans carapace), surmonté de trois longs cous fins terminés chacun par une petite tête de serpent distincte (trois éléments de tête identiques répartis symétriquement). Peau vert-brun marécageuse tachetée, têtes légèrement plus sombres.
- **`storm_roc`** — reskin `hawk`/`vulture` à échelle boss (≈1.5× le gabarit d'un rapace normal, ailes très larges), plumage gris-bleu orageux avec pointes d'ailes plus sombres presque noires, yeux blancs électriques lumineux, petites touches de "foudre" peintes (lignes fines plus claires) sur le plumage des ailes.
- **`plague_ogre`** — humanoid massif difforme : proportions asymétriques volontaires (un bras plus gros que l'autre, dos bossu), peau verdâtre malade couverte de pustules/taches sombres irrégulières, ventre distendu, vêtements en lambeaux sales (teinte brun-gris terne). Silhouette clairement "corrompue" par contraste avec `frost_troll` qui reste propre/bleu.
- **`crystal_golem`** — variante directe de `golem` : mêmes proportions, mais matériau roche gris-blanc parsemé de véritables excroissances cristallines (petits cuboids pointus émergeant des épaules/avant-bras/tête, géométrie additionnelle plutôt que juste texture), fissures remplacées par des veines cristal bleu translucide lumineuses.

Total roster final : **48 entités** (fox, rabbit, owl, boar, jackal, spider, vulture, hermit_man, scorpion, bear, skeleton, zombie, bandit_man, ice_wolf, snow_owl, ghost, eel, octopus, jellyfish, turtle, yeti, sand_worm, golem, hawk, mole, raccoon, hedgehog, hyena, stone_lizard, giant_ant, crab, moose, wildcat, harpy, cave_bat, desert_raider_man, frost_troll, giant_toad, frost_wyrm, ancient_treant, pharaoh_wraith, kraken_spawn, alpha_direwolf, magma_golem, bog_hydra, storm_roc, plague_ogre, crystal_golem).

## 2. Nouveaux fichiers d'attaque et de sorts à créer

**Attaques** sous `resources/config/skills/attacks/` (suivre le format de `wolf_pounce.yaml` / `poison_bite.yaml` : `damageType`, `power` par niveau, `weaponDice`, `cooldownMs`, `rangeOverride` optionnel, `statusEffect`+`durationSec` optionnel) :

- Batch 1 : `owl_dive, boar_charge, spider_web_bite, vulture_peck, scorpion_sting, bear_maul, skeleton_arrow, zombie_bite, ice_shard_bite, ghost_curse, eel_shock, octopus_ink, jellyfish_sting, yeti_slam, sand_worm_burrow_slam, golem_stone_smash` (16 fichiers).
- Batch 2 : `hawk_dive, hedgehog_spikes, giant_ant_bite, crab_pinch, wildcat_pounce, harpy_shriek, cave_bat_bite, desert_raider_javelin, frost_troll_slam, giant_toad_tongue` (10 fichiers).
- Réutilisés tels quels sur plusieurs entités : `poison_bite`, `wolf_bite`, `slash`, `bear_maul` (aussi sur `frost_troll`, `ancient_treant`, `plague_ogre`), `boar_charge` (aussi sur `moose`), `ghost_curse` (aussi sur `pharaoh_wraith`), `octopus_ink` (aussi sur `kraken_spawn`), `golem_stone_smash` (aussi sur `magma_golem`, `crystal_golem`), `hawk_dive` (aussi sur `storm_roc`), `scorpion_sting` (aussi sur `bog_hydra`).

**Sorts/capacités spéciales de boss** sous `resources/config/skills/spells/` (format `SpellDefinition` : `aoeRadius`, `cooldownMs`, `manaCost`/`rageCost`, `statusEffect`+`durationSec`) — 8 fichiers, un par mécanique distincte (2 boss — `alpha_direwolf`, `bog_hydra` — sont couverts par des sorts propres non partagés, les autres capacités spéciales sont chacune propres à un boss) :

`frost_wyrm_breath, treant_root_grasp, treant_regrowth, pharaoh_curse_of_sands, kraken_tentacle_slam, direwolf_howl_rally, magma_golem_eruption, hydra_regrowth, storm_roc_gust, plague_ogre_burst, crystal_shard_barrage` (11 fichiers).

## 3. Exécution (par entité, via skill `add-npc-model`)

Pour chaque entité :
1. `/add-npc-model` avec la description détaillée du tableau/paragraphe ci-dessus (morpho, biome, rôle, taille, traits visuels, palette).
2. Écrire les nouveaux yaml d'attaque/sort associés (section 2) s'ils n'existent pas déjà (partagés entre entités quand listé, ex. `bear_maul` réutilisé par `frost_troll`/`ancient_treant`/`plague_ogre`).
3. Renseigner `tier:` dans le yaml de l'entité une fois le prérequis §0 (code + schema) mergé — le yaml doit passer la validation schema avant d'être committé.
4. `make dev-restart-server`, vérifier logs `NpcRegistryLoader` (aucune erreur de validation schema, aucun warn de champ dropé), spawn en jeu.
5. Batch groupés par biome pour limiter les allers-retours (ex. lot désert : `scorpion, vulture, jackal, hyena, stone_lizard, giant_ant, desert_raider_man, sand_worm, pharaoh_wraith, magma_golem, skeleton, bandit_man`).

Ordre suggéré :
1. Prérequis §0 : `NpcTier` + schema JSON + mécanique de validation + tests.
2. Tier 1 batch 1+2 (fox/rabbit/owl/hawk/mole/raccoon/hedgehog — valide vite le pipeline passif, aucune attaque à écrire pour la moitié).
3. Fichiers d'attaque tier 2-3 (batch 1+2 confondus).
4. Entités COMMON/ELITE tier 2-4 par biome (boar/jackal/spider/vulture/hermit_man → hyena/stone_lizard/giant_ant/crab → scorpion/bear/skeleton/zombie/bandit_man/desert_raider_man/wildcat/frost_troll/harpy/giant_toad → ice_wolf/snow_owl/ghost/eel/octopus/jellyfish/turtle/moose/cave_bat).
5. Sorts de boss (§2, 11 fichiers), puis les 3 boss batch 1 (yeti/sand_worm/golem — référence, déjà simples), puis les 10 boss batch 3 (variantes/reskins d'abord — `magma_golem`, `crystal_golem`, `alpha_direwolf`, `storm_roc` réutilisent des morphologies existantes — puis les boss custom `frost_wyrm`, `ancient_treant`, `pharaoh_wraith`, `kraken_spawn`, `bog_hydra`, `plague_ogre` en dernier, plus lourds en modélisation).

## 4. Vérification

- `make dc CMD="./gradlew :server:test"` après le changement `NpcTier` + schema (inclut `NpcSchemaValidationTest`).
- Par entité : spawn en jeu, vérifier hp/behavior/aggro via observation directe, vérifier que l'attaque/le sort déclenche le bon `statusEffect`/`aoeRadius` (log combat ou observation UI).
- Pour les boss : vérifier spécifiquement que la capacité spéciale (`spells:`) se déclenche indépendamment de l'attaque de mêlée (cooldown propre) et que l'AOE touche bien plusieurs cibles si plusieurs joueurs/pets à portée.
- `make quick-code-standard` avant commit sur les fichiers Kotlin touchés (§0).
- `/update-docs` si `data/config/*.yaml` par défaut ou constantes changent (pas attendu ici hors §0, contenu yaml pur sinon).
