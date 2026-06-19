# MicCraft

Ce fichier fournit le contexte du projet **micraft** à Claude Code pour travailler efficacement dessus.

## Vue d'ensemble

**micraft** est un clone client/serveur de Minecraft, écrit en **Kotlin Multiplatform**. L'objectif est un jeu voxel multijoueur, avec génération procédurale de cartes et un monde persistant côté serveur.

## Architecture

- **Client/Serveur** : le serveur est autoritaire, le client envoie des inputs et reçoit l'état du monde.
- **Kotlin Multiplatform** : code partagé entre client et serveur (modèle du monde, protocole réseau, génération de chunks, maths/physique) ; code spécifique par cible (rendu côté client, I/O côté serveur).
- Modules attendus (à adapter selon l'avancement réel du repo) :
    - `shared` (ou `common`) : modèle de domaine, protocole, sérialisation des messages, génération du monde
    - `server` : boucle de jeu, persistance, gestion des connexions joueurs
    - `client` : rendu, input, UI, réseau côté client

> Avant toute modification, vérifier l'arborescence réelle (`./gradlew :projects` ou lecture de `settings.gradle.kts`) plutôt que de supposer cette structure.

## Règles du monde

- Le monde est composé de **blocs (voxels)**.
- **Génération procédurale** : les cartes sont générées aléatoirement (pas de carte statique préchargée).
- **Élévation** : axe vertical allant de **0** (sous-sol / bedrock) à **1024** (sommet du ciel). Toute logique de génération, de collision ou de rendu doit respecter ces bornes.

## Joueurs

- Chaque joueur est **nommé** et identifié de façon unique.
- Le **serveur** est la source de vérité pour :
    - la position (x, y, z) du joueur
    - son orientation (yaw/pitch, ou équivalent)
- Le client envoie des intentions de mouvement/regard ; le serveur valide et fait autorité sur l'état final (anti-triche, cohérence multijoueur).

### Dimensions du joueur (hitbox)

| État              | Hauteur | Largeur |
|-------------------|---------|---------|
| Debout            | 1,8 bloc | 0,6 bloc |
| Accroupi (sneak)  | ~1,5 bloc | 0,6 bloc |
| Nage / reptation  | 0,6 bloc | 0,6 bloc |

Ces valeurs doivent être utilisées pour :
- le calcul des collisions (AABB du joueur vs voxels)
- la caméra à la première personne (offset des yeux selon l'état)
- la détection de passage dans des espaces étroits (sneak vs debout)

## Conventions de code

- Préférer des **types immuables** (data class, value class) pour les positions, orientations et messages réseau.
- Centraliser les constantes de gameplay (hauteur du monde, dimensions joueur, vitesses) dans un objet partagé (`shared`) plutôt que de les dupliquer client/serveur.
- Toute logique de simulation (physique, collisions, génération de chunks) doit vivre dans le module partagé pour garantir que client (prédiction) et serveur (autorité) restent cohérents.

## Server and Client
- server is written in kotlin
- clients are written 
  - in kotlin for desktop part
  - in wasm embeded in a html/js application

## Commandes utiles

```bash
# Build complet
./gradlew build

# Lancer le serveur
./gradlew :server:run

# Lancer le client
./gradlew :client:run

# Tests
./gradlew test
```

> À ajuster selon les noms réels des modules/tâches Gradle une fois le projet structuré.

## Points d'attention pour Claude

- Ne pas supposer la présence d'un moteur de rendu particulier sans vérifier les dépendances du module client.
- Respecter la séparation **autorité serveur / prédiction client** : ne pas faire confiance à une position envoyée par le client sans validation côté serveur.
- Toujours raisonner en blocs (unité de base = 1 bloc) pour les distances, et vérifier les bornes d'élévation [0, 1024] lors de toute génération ou calcul de position verticale.

This is a Kotlin Multiplatform project targeting Web, Desktop (JVM), Server.

* [/app/shared](./app/shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./app/shared/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./app/shared/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./app/shared/src/jvmMain/kotlin)
      folder is the appropriate location.

* [/core](./core/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](./core/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/server](./server/src/main/kotlin) is for the Ktor server application.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Desktop app:
    - Hot reload: `./gradlew :app:desktopApp:hotRun --auto`
    - Standard run: `./gradlew :app:desktopApp:run`
- Server: `./gradlew :server:run`
- Web app:
    - Wasm target (faster, modern browsers): `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun`
    - JS target (slower, supports older browsers): `./gradlew :app:webApp:jsBrowserDevelopmentRun`

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Desktop tests: `./gradlew :app:shared:jvmTest`
- Server tests: `./gradlew :server:test`
- Web tests:
    - Wasm target: `./gradlew :app:shared:wasmJsTest`
    - JS target: `./gradlew :app:shared:jsTest`

### Rules
- Always prefer the Gradle wrapper command ./gradlew.
- Use rtk in front of verbose commands.
- Do not run unfiltered find, ls -R, git diff, or gradlew test if an rtk version exists.
- Read only the files that are necessary.
- If a result is too long, ask for a more targeted output.

### Useful commands
- rtk ./gradlew test
- rtk ./gradlew build
- rtk ./gradlew ktlintCheck
- rtk ./gradlew test --tests com.example.MyTest
- rtk git diff
- rtk git status