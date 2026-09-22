# Migration iOS — socle Kotlin Multiplatform

## Objectif

Faire évoluer **Mon Manager Web** vers Android + iOS sans dupliquer le protocole ni créer une seconde logique métier. La version Android reste l’application de référence pendant la migration.

Le protocole serveur ne change pas : découverte `/.well-known/lepotager-site-manager.json`, authentification, configuration privée, snapshot, mutations et médias restent les mêmes.

## Lot 1 — bootstrap KMP

Ce lot introduit un module `:shared` Kotlin Multiplatform ciblant Android et iOS.

- `Protocol.kt` quitte le module Android et devient du code `commonMain`.
- l’app Android dépend désormais de `:shared` et continue d’utiliser exactement les mêmes packages et modèles ;
- aucun écran, stockage, permission ou comportement Android n’est modifié ;
- aucune montée de version AGP/Kotlin/Gradle n’est faite dans ce lot.

La toolchain reste volontairement AGP 8.10.1 + Kotlin 2.2.21 + Gradle 8.11.1. Avec cette baseline AGP 8.x, le module KMP utilise encore `com.android.library` + `androidTarget`; la migration vers le plugin Android-KMP dédié doit être traitée séparément avec une montée AGP/Kotlin validée, pas mélangée à l’extraction fonctionnelle.

## Audit de portabilité

| Zone actuelle | État iOS | Direction |
| --- | --- | --- |
| `model/Protocol.kt` | partageable | **Déplacé dans `shared/commonMain` dans ce lot** |
| `network/SiteApiClient.kt` | transport Android | **`SiteApi`, JSON et validation protocolaire sont désormais partagés ; OkHttp reste l’implémentation Android** |
| `repository/SiteRepository.kt` | partageable | **déplacé dans `shared/commonMain` ; stockage, horloge, UUID, queue et classification réseau sont injectés** |
| Room / DataStore | Android | contrats communs créés ; Room/DataStore sont maintenant des adaptateurs Android derrière `SiteCache`, `PendingChangeStore` et `ActiveSiteStore` |
| `TokenVault` / Android Keystore | Android | `TokenStore` commun créé ; Android Keystore reste l’implémentation Android, Keychain sera l’implémentation iOS |
| WorkManager | Android | `QueueScheduler` commun créé ; WorkManager est isolé dans `AndroidQueueScheduler` |
| sélection média / `Uri` / EXIF Android | Android | abstraction média + implémentation iOS PhotoKit/ImageIO |
| Compose UI | grande partie partageable | migrer écran par écran après extraction du ViewModel et des launchers Android |
| QR / Google code scanner | Android | interface de scan + implémentation iOS native |
| `MainActivity` / `AndroidViewModel` | Android | garder l’entrée Android et créer une entrée iOS dédiée |

## Ordre recommandé

1. **Protocole commun** — fait.
2. **Contrat réseau, JSON et validation de protocole communs** — fait ; le parsing URL reste confié au moteur de chaque plateforme.
3. **Repository, stockage abstrait, coffre, horloge/UUID et scheduler abstraits** — fait ; les implémentations Android existantes sont conservées derrière ces contrats.
4. **Préparation média Android extraite du repository** — fait.
5. Ajouter les implémentations iOS (Keychain, stockage, sélection média, réseau).
6. Extraire le state holder du `AndroidViewModel` pour qu’il soit consommable par Compose Multiplatform.
7. Migrer les écrans Compose réutilisables ; garder les pickers/scanners comme points `expect/actual` ou wrappers injectés.
8. Créer `iosApp` dans Xcode et intégrer le framework KMP localement.
9. Ajouter une CI macOS **opt-in** seulement quand la cible iOS est réellement compilable, pour ne pas multiplier les minutes GitHub Actions.

## Lots 2–3 — cœur réellement partageable

Le cœur commun expose désormais `SiteApi`, `SiteJson`, `SiteProtocolValidator`, `SiteRepository` et les contrats de stockage/sécurité/plateforme. `SiteRepository` ne contient plus aucun import Android/JVM. La mise en file hors connexion est testée dans `commonTest` : seule une panne classée comme réseau peut être mise en file, et un refus protocolaire ou une action `allowOffline=false` reste non rejouable.

Android fournit les adaptateurs concrets : Room, DataStore, Android Keystore, WorkManager, UUID/horloge et lecture/réencodage des médias. L’upload média est désormais dans `AndroidMediaUploader`, hors du repository commun.

## Invariants à ne pas casser

- pas de code client spécifique dans l’app ;
- pas de WebView d’administration ;
- HTTPS obligatoire et redirections sécurisées refusées ;
- API v1 sur la même origine ;
- mot de passe non persisté ;
- jeton par appareil protégé par le stockage sécurisé de la plateforme ;
- actions métier sensibles non rejouées hors ligne sans opt-in serveur ;
- médias contrôlés côté client et revérifiés côté serveur ;
- Android doit continuer à compiler et à passer ses tests à chaque étape de migration.

## Validation du lot

Le workflow Android existant doit être exécuté une seule fois sur le SHA final du lot : `testDebugUnitTest`, `assembleDebug`, `lintDebug`. La compilation iOS nécessite macOS/Xcode et sera ajoutée lorsque `iosApp` existera.
