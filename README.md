# Le Potager — Gestion de site

Application Android générique multi-client. Un seul binaire installé depuis une source de confiance peut se connecter à plusieurs sites compatibles et se personnaliser à partir de la configuration publiée par chaque site.

## Principe

1. L'utilisateur saisit l'adresse de son site ou utilise un QR/deep-link d'association.
2. L'application lit `https://site.example/.well-known/lepotager-site-manager.json`.
3. Le manifeste public annonce uniquement l'identité du site, la version du protocole, l'API et les méthodes d'authentification.
4. Après authentification, l'app reçoit une configuration privée versionnée : marque, modules, libellés, champs éditables, contrat média et permissions.
5. Le moteur Android affiche uniquement des composants déjà embarqués dans l'application. **Aucun code exécutable n'est téléchargé.**
6. Les caches et la file de petites mutations sont locaux. Les secrets de session sont protégés par Android Keystore.
7. Le serveur reste source d'autorité et peut imposer `review_before_publish`.

BMH Rénovation est l'implémentation de référence du protocole v1.

## Fonctions v1

- découverte automatique d'un site compatible ;
- connexion identifiant/mot de passe + TOTP ;
- association rapide par code/QR ;
- jeton révocable par appareil ;
- branding dynamique sans recompilation ;
- modules `dashboard`, `form`, `gallery`, `requests` ;
- formulaires entièrement décrits par le serveur ;
- création, modification et suppression d'éléments de galerie ;
- sélection de médias via le sélecteur Android ;
- contrat média serveur : formats, taille et métadonnées ;
- upload immédiat sécurisé, sans file média hors connexion ;
- cache Room + DataStore ;
- file offline idempotente pour les petites mutations autorisées ;
- reprise réseau via WorkManager ;
- affichage clair des demandes en attente de validation.

## Stack retenue

- Kotlin + Jetpack Compose Material 3 ;
- Material 3 Adaptive pour téléphone/tablette/grands écrans ;
- MaterialKolor pour générer un thème accessible à partir de la couleur de marque ;
- kotlinx.serialization pour le protocole JSON versionné ;
- OkHttp pour HTTPS et multipart ;
- Coil pour logos et médias ;
- DataStore pour petites préférences ;
- Room 2.8.x pour cache structuré et file locale ;
- Android Keystore pour les jetons d'appareil ;
- WorkManager uniquement pour les mutations JSON explicitement mises en file.

## Sécurité

- HTTPS obligatoire ;
- découverte via `/.well-known/` ;
- API sur la même origine en protocole v1 ;
- redirects refusés pendant les échanges sécurisés ;
- mot de passe jamais persisté ;
- TOTP demandé si le serveur l'exige ;
- jeton individuel par appareil, révocable ;
- jeton chiffré via une clé Android Keystore ;
- aucun secret GitHub/o2switch/API globale dans l'APK ;
- sauvegarde Android désactivée pour les données sensibles ;
- pas de WebView pour l'administration ;
- pas de téléchargement de DEX/JAR/JS exécutable ;
- modules UI limités à une liste de composants connus par l'app ;
- erreur HTTP/protocole jamais confondue avec une panne réseau ;
- médias contrôlés côté client puis obligatoirement revérifiés/réencodés côté serveur de référence.

## Protocole

- spécification : `docs/protocol-v1.md` ;
- checklist pour rendre un nouveau site compatible : `docs/implementer-checklist.md` ;
- schéma de découverte : `protocol/site-manifest.schema.json`.

Le protocole sépare :

- **discovery public** : identité + endpoints + auth ;
- **configuration authentifiée** : thème, navigation, modules, champs, droits et médias ;
- **données métier** : contenus, collections et demandes ;
- **mutations** : demandes idempotentes, éventuellement soumises à validation ;
- **médias** : multipart immédiat, jamais republié directement depuis le fichier reçu.

## Build

Le projet utilise le workflow réutilisable privé `jasmin-abernathy/app-build-factory`. La CI exécute `testDebugUnitTest`, `assembleDebug` et `lintDebug`, puis conserve l'APK debug comme artefact GitHub Actions lorsque tout est vert.

Le Gradle Wrapper officiel est commité dans le dépôt et validé par la fabrique avant chaque build.
