# Le Potager — Gestion de site

Application Android générique multi-client. Un seul binaire installé depuis une source de confiance peut se connecter à plusieurs sites compatibles et se personnaliser à partir de la configuration publiée par chaque site.

## Principe

1. L'utilisateur saisit l'adresse de son site ou scanne un QR d'association.
2. L'application lit `https://site.example/.well-known/lepotager-site-manager.json`.
3. Le manifeste public annonce uniquement l'identité du site, la version du protocole, l'API et les méthodes d'authentification.
4. Après authentification, l'app reçoit une configuration privée versionnée : marque, modules, libellés, champs éditables et permissions.
5. Le moteur Android affiche uniquement des composants déjà embarqués dans l'application. **Aucun code exécutable n'est téléchargé.**
6. Les brouillons et caches sont locaux. Les secrets de session sont protégés par Android Keystore.
7. Le serveur reste source d'autorité et peut imposer `review_before_publish`.

BMH Rénovation est le premier site pilote.

## Stack retenue

- Kotlin + Jetpack Compose Material 3 ;
- Material 3 Adaptive pour téléphone/tablette/grands écrans ;
- MaterialKolor pour générer un thème accessible à partir de la couleur de marque ;
- kotlinx.serialization pour le protocole JSON versionné ;
- OkHttp pour HTTPS ;
- Coil pour logos et médias avec cache/redimensionnement ;
- DataStore pour petites préférences ;
- Room 2.8.x pour cache structuré et file locale ;
- Android Keystore pour les jetons d'appareil ;
- WorkManager uniquement pour les synchronisations différées explicitement mises en file.

## Sécurité

- HTTPS obligatoire ;
- découverte via `/.well-known/` ;
- mot de passe jamais persisté ;
- TOTP demandé si le serveur l'exige ;
- jeton individuel par appareil, révocable ;
- jeton chiffré via une clé Android Keystore ;
- aucun secret GitHub/o2switch/API globale dans l'APK ;
- sauvegarde Android désactivée pour les données sensibles ;
- pas de WebView pour l'administration ;
- pas de téléchargement de DEX/JAR/JS exécutable ;
- modules UI limités à une liste de composants connus par l'app.

## Protocole

Voir `docs/protocol-v1.md` et `protocol/site-manifest.schema.json`.

Le protocole sépare :

- **discovery public** : identité + endpoints + auth ;
- **configuration authentifiée** : thème, navigation, modules, champs, droits ;
- **données métier** : contenu et réalisations ;
- **mutations** : demandes de modification, éventuellement soumises à validation.

## Build

Le projet utilise la fabrique privée `jasmin-abernathy/app-build-factory` comme les autres applications du Potager. Le Gradle Wrapper officiel est généré par CI lors du premier passage puis commité automatiquement.
