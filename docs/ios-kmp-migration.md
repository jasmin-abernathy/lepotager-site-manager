# Migration iOS — Mon Manager Web

## Objectif et invariants

Mon Manager Web utilise désormais Kotlin Multiplatform pour partager le protocole, le repository, le state holder et l’interface Compose entre Android et iOS. Le serveur reste inchangé : découverte `/.well-known/lepotager-site-manager.json`, authentification, configuration, snapshot, mutations et médias utilisent le même protocole.

Invariants à préserver : HTTPS et même origine, redirections refusées, aucun mot de passe persistant, jeton par appareil en stockage sécurisé, aucune WebView d’administration, aucune logique client spécifique dans le binaire générique, et Android doit continuer à compiler/tester avec chaque lot KMP.

## Toolchain validée pour ce portage

- Kotlin `2.2.21`
- Compose Multiplatform `1.9.3`
- AGP `8.10.1`
- Gradle `8.11.1`
- JDK 17
- `compileSdk = 36`
- Ktor `3.3.3` côté iOS
- coroutines `1.9.0`

Cette migration ne doit pas être mélangée à une montée majeure de la toolchain.

## Architecture actuelle

```text
shared/commonMain  -> protocole + repository + state holder + UI Compose
shared/androidMain -> adaptateurs Android (scanner, picker, formatage)
shared/iosMain     -> Ktor Darwin, stores iOS/Keychain, picker, QR, upload
iosApp             -> host SwiftUI/Xcode + signaux de cycle de vie
```

Le framework iOS statique s’appelle `MonManagerShared`. `iosApp` l’intègre avec `:shared:embedAndSignAppleFrameworkForXcode` et le host SwiftUI encapsule `ComposeUIViewController`.

## Réseau et file hors ligne

`SiteRepository` reste l’unique propriétaire des décisions de mise en file et de rejeu. Les entrées conservent leur `clientRequestId` lors du rejeu et ne sont supprimées qu’après succès.

Les appels à `flushQueue()` sont sérialisés par un `Mutex` dans la même instance de repository afin d’empêcher deux déclencheurs de lire puis d’envoyer la même entrée en parallèle. `CancellationException` est propagée au lieu d’être transformée en erreur réseau, tentative persistée ou erreur UI.

Android conserve WorkManager comme scheduler. iOS ne prétend pas émuler WorkManager en arrière-plan : un moniteur `Network.framework` basé sur les bindings C signale le retour de connectivité, et SwiftUI transmet le retour de la scène au premier plan. Ces signaux sont seulement des occasions de retenter la file ; ils ne prouvent pas que le serveur répond.

L’initialisation de l’interface ne dépend pas du vidage complet de la file : le state holder est restauré avant la boucle de reprise.

## Photos iOS

Le choix d’image utilise `PHPickerViewController`, sans permission générale Photos. Les fichiers copiés par l’app sont placés dans un répertoire temporaire dédié `mon-manager-web-media` et leur appartenance est vérifiée après normalisation du chemin.

Le cycle de vie est explicite :
- remplacer une sélection libère l’ancien fichier géré par l’app ;
- quitter l’écran avec une sélection non envoyée la libère ;
- annuler le picker conserve une sélection existante ;
- au déclenchement d’un upload, la propriété du fichier passe à l’uploader ;
- succès, échec, refus de taille/MIME/module/session et annulation de l’upload passent tous par le nettoyage de l’uploader.

La détection MIME côté client reste fondée sur l’extension du fichier copié ; elle n’est pas présentée comme une inspection du contenu. Le serveur doit continuer à revalider le fichier.

## QR et liens d’association

- Android : Google Code Scanner `16.1.0`, sans permission CAMERA dans l’application.
- iOS : AVFoundation, permission demandée au moment du scan.
- Le scanner est proposé avant la saisie manuelle.
- Les liens `lepotager-manager://pair?...` sont reçus par SwiftUI et transmis bruts au même `holder.pairFromLink` que le QR.
- Le tampon de démarrage reste dans le canal d’événements Kotlin ; aucun second parser n’est ajouté en Swift.

## Icône

Le kit utilisateur reste la source de vérité : composition modulaire abstraite, vert principal `#183E2D`, vert secondaire `#78947B`, accent `#CB603E` et fond iOS `#FCFBF7`.

Le dépôt garde un `AppIcon.appiconset` iOS en source 1024×1024 et un petit générateur Swift qui reproduit la géométrie et la palette du SVG du kit avant la compilation des ressources. L’image générée est opaque et le jeu principal est référencé par `ASSETCATALOG_COMPILER_APPICON_NAME=AppIcon`.

`TEAM_ID` reste volontairement vide : la CI simulateur compile avec la signature désactivée et aucune identité Apple n’est inventée.

## Validation automatisée

Le workflow iOS sur `macos-15` doit exécuter, sur le même SHA :

1. `:shared:iosSimulatorArm64Test` ;
2. `:shared:compileKotlinIosSimulatorArm64` ;
3. `:shared:linkDebugFrameworkIosSimulatorArm64` ;
4. vérification de `MonManagerShared.framework` ;
5. génération et contrôle de l’icône 1024×1024 opaque ;
6. `xcodebuild` du host SwiftUI pour simulateur avec `CODE_SIGNING_ALLOWED=NO`.

Le workflow Android reste la non-régression obligatoire du même lot : tests unitaires, assemble debug et lint.

## Tests physiques encore nécessaires

La CI simulateur ne remplace pas les essais sur iPhone : caméra autorisée/refusée, QR et annulation, liens à froid/à chaud, picker et upload réel, Keychain après fermeture/réouverture, mode avion puis retour réseau, orientation/clavier, grandes polices et VoiceOver.

Signature Apple, IPA et TestFlight restent hors périmètre tant que le vrai compte/`TEAM_ID` n’a pas été configuré.
