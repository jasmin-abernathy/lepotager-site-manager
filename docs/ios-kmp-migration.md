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
| `network/SiteApiClient.kt` | logique partageable, transport JVM | extraire validation/protocole puis choisir un transport multiplateforme ou une interface de transport |
| `repository/SiteRepository.kt` | fortement Android | séparer orchestration pure des services fichiers, images, stockage, queue et identifiants |
| Room / DataStore | Android | définir des interfaces de cache/préférences et une implémentation iOS |
| `TokenVault` / Android Keystore | Android | contrat commun + Keychain côté iOS |
| WorkManager | Android | conserver le contrat de queue, adapter l’exécution aux contraintes iOS |
| sélection média / `Uri` / EXIF Android | Android | abstraction média + implémentation iOS PhotoKit/ImageIO |
| Compose UI | grande partie partageable | migrer écran par écran après extraction du ViewModel et des launchers Android |
| QR / Google code scanner | Android | interface de scan + implémentation iOS native |
| `MainActivity` / `AndroidViewModel` | Android | garder l’entrée Android et créer une entrée iOS dédiée |

## Ordre recommandé

1. **Protocole commun** — fait dans le lot 1.
2. Extraire les règles pures de validation d’URL, version de protocole et sérialisation hors d’OkHttp.
3. Introduire des interfaces communes pour transport HTTP, coffre à jetons, préférences/cache, horloge/UUID et queue.
4. Garder les implémentations Android actuelles derrière ces interfaces sans changer le comportement.
5. Ajouter les implémentations iOS (Keychain, stockage, sélection média, réseau).
6. Extraire le state holder du `AndroidViewModel` pour qu’il soit consommable par Compose Multiplatform.
7. Migrer les écrans Compose réutilisables ; garder les pickers/scanners comme points `expect/actual` ou wrappers injectés.
8. Créer `iosApp` dans Xcode et intégrer le framework KMP localement.
9. Ajouter une CI macOS **opt-in** seulement quand la cible iOS est réellement compilable, pour ne pas multiplier les minutes GitHub Actions.

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
