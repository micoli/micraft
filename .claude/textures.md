# comment une texture est appliquée sur un bloc (orientation, positionnement) ?

Une texture de bloc Minecraft n’est pas “posée” comme une image libre : elle est **mappée** sur les faces du modèle 3D du bloc, face par face, selon les coordonnées UV du modèle. En pratique, le bloc vanilla est souvent un cube de 1×1×1, et chaque face reçoit une portion de la texture définie par le modèle JSON ou par les règles du moteur.[^1][^2]

## Orientation sur un bloc

L’orientation dépend d’abord du **modèle** du bloc, pas seulement de l’image PNG. Pour un bloc simple, les six faces du cube utilisent la même logique de projection, mais certaines faces peuvent être tournées de 0, 90, 180 ou 270 degrés via les UV du modèle, ce qui explique pourquoi une texture peut apparaître “à l’endroit” sur une face et “pivotée” sur une autre.[^1][^2]

Dans les packs récents, le placement exact est donc déterminé par :

- la géométrie du modèle,
- les coordonnées UV,
- la rotation UV éventuelle,
- et la face ciblée dans le fichier modèle.[^2][^1]


## Positionnement

Le PNG lui-même ne contient pas de notion d’orientation spatiale ; c’est le moteur qui découpe l’image et l’associe aux faces du bloc. Si un bloc a un modèle non cubique, la texture suit les faces du modèle, donc une face inclinée ou étirée peut afficher la texture déformée ou décalée selon la projection appliquée.[^1][^3]

Pour les blocs orientables, comme ceux qui ont un axe ou une direction, la texture peut aussi changer selon l’état du bloc : le même modèle peut être tourné par l’état de bloc, et le moteur réapplique alors la géométrie et les UV dans cette orientation.[^2][^1]

## Cas des blocs spéciaux

Certains blocs utilisent des règles particulières, par exemple les textures connectées, les variantes de biomes, ou les animations de texture. Dans ces cas, le résultat visuel ne dépend plus seulement de l’image PNG, mais aussi de fichiers de configuration additionnels comme les `.properties` ou des mécanismes spécifiques au pack/mod.[^1][^4]

En bref : la **texture** est une image, mais son orientation finale sur le bloc est fixée par le **modèle 3D + les UV + l’état du bloc**.[^2][^1]

## Exemple mental

Imagine une étiquette imprimée sur un cube en carton : le dessin n’a pas “d’orientation” tout seul. C’est la façon dont tu colles l’étiquette sur chaque face qui décide si elle est à l’endroit, tournée, ou inversée ; Minecraft fait la même chose avec les UV du modèle.[^1][^2]
