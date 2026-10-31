# Mon Manager Web

Application Android générique multi-client. Un seul moteur peut se connecter à plusieurs sites compatibles et se personnaliser à partir de la configuration publiée par chaque site.

Le nom public de l'application est **Mon Manager Web**. L'identité du site connecté (logo, couleurs, nom et modules) reste fournie dynamiquement par le site.

**Dendrila CMS** est le noyau serveur réutilisable que l’application peut piloter. Mon Manager Web reste le client de gestion ; les identifiants du protocole MMW v1 restent inchangés pendant cette migration de marque.

## Principe

1. L'utilisateur saisit l'adresse de son site ou utilise un QR/deep-link d'association.
2. L'application lit `https://site.example/.well-known/lepotager-site-manager.json`.
3. Le manifeste public annonce uniquement l'identité du site, la version du protocole, l'API et les méthodes d'authentification.
4. Après authentification, l'app reçoit une configuration privée versionnée : marque, modules, libellés, champs éditables, contrat média, actions autorisées et permissions.
5. Le moteur Android affiche uniquement des composants déjà embarqués dans l'application. **Aucun code exécutable n'est téléchargé.**
6. Les caches et la file de petites mutations sont locaux. Les secrets de session sont protégés par Android Keystore.
7. Le serveur reste source d'autorité et peut imposer ou non une validation avant publication selon la nature de la modification.

Les implémentations concrètes du protocole restent côté serveur et ne font pas partie du moteur Android générique. Les intégrations métier (réservation, boutique, ticketing, etc.) sont traduites vers les primitives du protocole : l'APK ne contient aucune condition `if client == ...` ni dépendance à Easy!Appointments, AbanteCart, WooCommerce ou un autre logiciel métier.

## Fonctions v1

- découverte automatique d'un site compatible ;
- connexion identifiant/mot de passe + TOTP ;
- association rapide par code/QR ;
- jeton révocable par appareil ;
- branding dynamique sans recompilation ;
- modules `dashboard`, `form`, `gallery`, `requests`, `records`, `calendar`, `settings`, `media_library` ;
- formulaires entièrement décrits par le serveur ;
- édition de pages Dendrila par fiches versionnées, brouillons, publication explicite et protection contre les écritures concurrentes ;
- création, modification et suppression d'éléments de galerie ;
- albums photographiques : affichage des images (y compris les anciens champs `image_url`), navigation par album, aperçu grand format sans recadrage et édition des métadonnées lorsqu’elle est autorisée ;
- listes métier génériques (`records`) pour commandes, clients, dossiers, tâches ou tout autre objet structuré, avec création/modification/suppression uniquement lorsque le serveur l'autorise ;
- agenda générique (`calendar`) pour rendez-vous, événements ou échéances ;
- actions sur objets déclarées par le serveur et exécutées via l'action standard `item_action` ;
- confirmation locale facultative pour les actions sensibles ;
- actions métier exclues de la file hors connexion par défaut, sauf autorisation explicite `allow_offline=true` ;
- sélection de médias via le sélecteur Android ;
- bibliothèque média générique avec métadonnées, rotation et retrait uniquement selon les capacités annoncées par le serveur ;
- contrat média serveur : formats, taille et métadonnées ;
- upload immédiat sécurisé, sans file média hors connexion ;
- cache Room + DataStore ;
- file offline idempotente pour les petites mutations autorisées ;
- reprise réseau via WorkManager ;
- affichage clair des demandes qui nécessitent réellement une validation.

## Modules métier génériques

L'application ne connaît pas les outils tiers utilisés par les clients. Le back-office traduit leurs données vers les primitives du protocole.

Exemples :

```text
Easy!Appointments ── connecteur serveur ──> calendar "Rendez-vous"
AbanteCart        ── connecteur serveur ──> records  "Commandes"
WooCommerce      ── connecteur serveur ──> records  "Commandes"
Cal.com          ── connecteur serveur ──> calendar "Rendez-vous"
outil de tickets ── connecteur serveur ──> records  "Tickets"
```

Ainsi, l'ajout d'un nouveau client ou le remplacement d'un outil métier ne nécessite pas de forker l'APK. Une nouvelle primitive Android n'est ajoutée que si plusieurs intégrations ont réellement besoin d'un nouveau type d'interface générique.

### Actions métier

Un module `records` ou `calendar` peut annoncer des actions connues seulement par un identifiant opaque pour l'app :

```json
{
  "id": "cancel",
  "label": "Annuler",
  "tone": "danger",
  "requires_confirmation": true,
  "confirmation_text": "Confirmer l'annulation ?",
  "allow_offline": false
}
```

Quand l'utilisateur confirme, l'app envoie :

```json
{
  "module_id": "appointments",
  "action": "item_action",
  "client_request_id": "uuid",
  "payload": {
    "item_id": "82",
    "action_id": "cancel"
  }
}
```

Le serveur valide l'utilisateur, le module, l'objet et l'action avant d'appeler son connecteur métier. L'app ne reçoit ni endpoint tiers ni secret de l'intégration.

## Identité et variantes client

Le dépôt ne doit pas être forké pour chaque client. L'APK peut recevoir au moment du build :

- un `applicationId` propre au client ;
- un nom d'installation, par défaut `Mon Manager Web` ;
- une icône et une icône ronde propres au client.

Les propriétés Gradle prévues sont :

```text
-PmanagerApplicationId=org.example.manager
-PmanagerAppName="Mon Manager Web"
-PmanagerLauncherIcon=@mipmap/ic_launcher_client
-PmanagerLauncherRoundIcon=@mipmap/ic_launcher_client_round
```

Les ressources d'icône client peuvent être ajoutées comme simples overlays Android sans toucher au moteur Kotlin. Le build standard utilise l'icône neutre de Mon Manager Web. Une variante dédiée peut donc reprendre le logo ou favicon d'un client tout en restant exactement sur le même code et le même protocole.

À l'intérieur de l'application, le logo et les couleurs affichés viennent toujours du site connecté : une mise à jour de l'identité du site ne nécessite donc pas de nouvelle version de l'APK. Seule l'icône visible dans le lanceur Android nécessite une reconstruction de l'APK si on souhaite qu'elle suive le nouveau logo/favicon.

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

## Connexion aux sites qui utilisent un code d’association

Certains sites, comme **Oscura / Vues Imprenables**, ne proposent pas de connexion par mot de passe dans l’application Mon Manager Web : ils exposent uniquement la méthode `pairing_code`.

1. Ouvrir le site sur sa véritable adresse HTTPS (pour le pilote Oscura : `https://vuesimprenables.lepotager.org`).
2. Dans le back-office du site, accéder à **Administration → Appli → Créer un code** après connexion avec son compte administrateur. Ce code à huit chiffres est personnel, valable dix minutes, à usage unique.
3. Dans Mon Manager Web **0.1.2 ou supérieur**, saisir ce code dès l’écran « Site détecté » : le formulaire d’association apparaît automatiquement lorsqu’aucune autre méthode n’est proposée.
4. Le jeton d’appareil obtenu après association est distinct du mot de passe du back-office et peut être révoqué depuis la rubrique Appli.

La version Android 0.1.1 avait un défaut d’interface : le formulaire était caché quand le site ne proposait que `pairing_code`. La détection réussissait, mais l’écran ne proposait plus que « Changer de site ». Le correctif fait l’objet de tests couvrant les sites à code seul, mot de passe seul et double méthode.

**APK bêta** : les compilations de test sont signées avec une clé debug susceptible de changer entre deux builds GitHub Actions. Android peut donc demander de désinstaller l’ancienne bêta avant d’installer la nouvelle. La désinstallation efface les associations locales ; il faut alors générer un nouveau code. Ne pas partager le code d’association.

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
- aucun secret Easy!Appointments/AbanteCart/autre outil tiers dans l'APK ;
- sauvegarde Android désactivée pour les données sensibles ;
- pas de WebView pour l'administration ;
- pas de téléchargement de DEX/JAR/JS exécutable ;
- modules UI limités à une liste de composants connus par l'app ;
- actions sensibles non rejouées hors connexion sans opt-in serveur explicite ;
- erreur HTTP/protocole jamais confondue avec une panne réseau ;
- médias contrôlés côté client puis obligatoirement revérifiés/réencodés côté serveur de référence.

## Protocole

- spécification : `docs/protocol-v1.md` ;
- checklist pour rendre un nouveau site compatible : `docs/implementer-checklist.md` ;
- schéma de découverte : `protocol/site-manifest.schema.json`.

Le protocole sépare :

- **discovery public** : identité + endpoints + auth ;
- **configuration authentifiée** : thème, navigation, modules, champs, droits, actions et médias ;
- **données métier** : contenus, collections, rendez-vous, commandes et demandes sous formes génériques ;
- **mutations** : demandes idempotentes, éventuellement soumises à validation ;
- **connecteurs serveur** : traduction entre primitives génériques et outils tiers, jamais exposée comme code à l'APK ;
- **médias** : multipart immédiat, jamais republié directement depuis le fichier reçu.

## Build

Le dépôt contient son propre workflow Android. Les builds lourds restent volontaires : lancement manuel, tag `v*` / `apk-*`, ou pull request modifiant le code Android ou la configuration de build. Les pushes ordinaires sur `main` ne génèrent pas d'APK.

La CI valide le Gradle Wrapper, prépare le SDK Android nécessaire, puis exécute `testDebugUnitTest`, `assembleDebug` et `lintDebug`. Un APK debug est conservé comme artefact pendant 7 jours lorsque le build est déclenché et réussit.

Le Gradle Wrapper officiel est commité dans le dépôt et validé avant chaque build.

## Licence

Mon Manager Web est distribué sous **GNU Affero General Public License v3.0 only (`AGPL-3.0-only`)**. Voir `LICENSE`.
