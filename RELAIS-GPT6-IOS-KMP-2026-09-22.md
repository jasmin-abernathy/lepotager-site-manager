# RELAIS GPT-6 — Mon Manager Web vers iOS / Kotlin Multiplatform

**Date :** 2026-09-22  
**Dépôt :** `jasmin-abernathy/lepotager-site-manager`  
**Branche de travail :** `work/ios-kmp-bootstrap`  
**Branche de base :** `main`  
**Main au démarrage :** `12e733212a2f3032f897a421a2660e99562419ad`

## Demande utilisateur

Transformer progressivement **Mon Manager Web**, actuellement Android, en application également disponible sur iPhone, sans casser Android et sans dupliquer la logique métier. Continuer directement le travail GitHub.

## Règles obligatoires avant de continuer

1. Re-fetch le HEAD réel de la branche avant toute écriture.
2. Lire `AGENTS.md` dans ce dépôt.
3. Lire dans `jasmin-abernathy/repo-factory` :
   - `AGENTS.md` ;
   - `COMPATIBILITY-AND-DEPRECATION-PLAYBOOK.md` ;
   - `errors/2026-09-20-resilience-vault-kotlin-github-ci.md`.
4. Ne jamais reconstruire un fichier complet depuis une lecture partielle.
5. Grouper les changements en peu de commits et ne lancer qu’une validation lourde par lot logique.
6. Ne pas monter AGP/Kotlin/Gradle au passage sans lot dédié et validation de compatibilité.

## État avant le lot

- Android pur, AGP 8.10.1, Kotlin 2.2.21, Gradle 8.11.1, JDK 17.
- `compileSdk/targetSdk 36`, `minSdk 26`.
- protocole générique, aucun fork client nécessaire.
- Compose Material 3, OkHttp 4.12, kotlinx.serialization, Room, DataStore, WorkManager, Android Keystore.
- CI Android volontairement lourde seulement sur `workflow_dispatch`, tags ou PR pertinente.

## Travail effectué dans ce lot

- création de `:shared`, module Kotlin Multiplatform Android + iOS ;
- déplacement de `org.lepotager.sitemanager.model.Protocol.kt` vers `shared/src/commonMain/...` sans changer son package ni son contenu ;
- dépendance `app -> project(":shared")` ;
- ajout des plugins root nécessaires à KMP et à la library Android ;
- `settings.gradle.kts` inclut désormais `:shared` ;
- ajout de `docs/ios-kmp-migration.md` avec l’audit et l’ordre de migration ;
- aucune modification de l’UI, du stockage, des permissions, de la sécurité ou du protocole serveur.

## Pourquoi cette stratégie

Le protocole et les DTO sont du Kotlin pur + kotlinx.serialization : c’est le premier bloc réellement partageable et à faible risque. Le reste contient encore des dépendances Android fortes (`Context`, `Uri`, Room, DataStore, WorkManager, Keystore, Activity Result, Google scanner). Il faut les découpler progressivement au lieu de tout réécrire en une fois.

Avec **Kotlin 2.2.21 + AGP 8.10.1**, conserver pour ce lot le montage KMP historique `com.android.library` + `androidTarget`. Ne pas basculer opportunément vers AGP 9/10 ou le nouveau plugin Android-KMP : ce sera une migration de toolchain séparée.

## Incident CI du bootstrap

Le premier run Android du SHA `e2e8a7d5d793b245c0eb54e2b927cadcdffeb6aa` a échoué dans `:app:compileDebugKotlin` après le déplacement de `UiField` dans `:shared`.

**Cause :** Kotlin ne peut plus smart-caster directement les propriétés publiques nullable (`maxLength`, `min`, `max`) d’un type déclaré dans un autre module.

**Correction :** copier d’abord chaque propriété nullable dans une variable locale stable avant le test de nullité et la comparaison. Le comportement métier reste identique.

À retenir pour les prochains déplacements de modèles vers `commonMain` : une extraction inter-modules peut révéler des smart casts qui compilaient seulement parce que le modèle et l’appelant étaient dans le même module.
## État après poussée GPT-5.6

GPT-5.6 a poursuivi le chantier au-delà de ce relais : `SiteApi`, la validation de protocole puis **`SiteRepository` lui-même** ont été portés dans `shared/commonMain`. Room, DataStore, Keystore, WorkManager, UUID/horloge et médias sont isolés derrière des adaptateurs Android. Des tests communs couvrent la règle critique de mise en file hors connexion.

**Aucune tâche n’est actuellement identifiée comme “GPT-6 uniquement”.** Le prochain vrai mur externe reste la compilation/exécution iOS sur macOS + Xcode. Tant que ce mur n’est pas atteint, continuer avec GPT-5.6.

## Avancement Compose Multiplatform

GPT-5.6 a également déplacé l’interface principale vers Compose Multiplatform : écrans, modules métier, bibliothèque média, thème et racine UI sont dans `shared/commonMain`. Android garde seulement ses actuals de plateforme et son entrypoint. Le framework iOS `MonManagerShared` est déclaré.

Le picker média iOS reste **explicitement non fonctionnel** tant qu’il n’a pas été branché à UIKit/Photos et compilé sous Xcode. Ne pas présenter ce point comme terminé.

## Prochain lot recommandé

### 1. Brancher les services iOS natifs puis valider sous Xcode

Le state holder commun est désormais extrait : `AppStage`, `AppUiState`, discovery/auth/TOTP/pairing/refresh/submit/disconnect sont dans `shared`. Android ne garde qu’un wrapper lifecycle, son parser de deep-link et l’upload média.

### 2. UI seulement après le state holder

`MainViewModel` dépend de `Application`, `Uri`, `Build` et Android lifecycle. Extraire un state holder commun avant de déplacer les Composables. `Screens.kt` contient aussi le picker Android, et `BrandTheme.kt` utilise `android.graphics.Color`.

## Points iOS prévus

- Keychain pour les jetons ;
- PhotoKit / document picker + ImageIO/CoreGraphics pour les médias ;
- scanner QR natif ;
- contraintes iOS spécifiques pour la reprise en arrière-plan ;
- `iosApp` Xcode comme point d’entrée séparé consommant le framework KMP.

## Validation attendue

Sur le SHA final de ce lot, vérifier **le même SHA** avec le workflow Android existant :

```text
testDebugUnitTest
assembleDebug
lintDebug
```

Ne pas annoncer la cible iOS compilée tant qu’un build macOS/Xcode réel n’a pas été exécuté. La présence de `iosArm64/iosSimulatorArm64` dans Gradle ne suffit pas.

## À ne pas faire

- ne pas réintroduire un `Protocol.kt` parallèle côté Android ;
- ne pas créer une app iOS WebView ;
- ne pas changer `/mobile-api/` juste pour iOS ;
- ne pas créer des conditions par client ;
- ne pas mélanger migration KMP + refonte UI + upgrade toolchain dans un seul commit ;
- ne pas pousser un correctif pendant une CI encore en cours sauf défaut réel.

## Fichier de référence

Lire `docs/ios-kmp-migration.md` avant le prochain lot.
