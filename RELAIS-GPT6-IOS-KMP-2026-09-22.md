# RELAIS GPT-6 — Mon Manager Web iPhone / Kotlin Multiplatform

**Date :** 2026-09-22
**Dépôt :** `jasmin-abernathy/lepotager-site-manager`
**PR :** #3
**Branche PR :** `work/ios-kmp-bootstrap`
**HEAD PR au moment du relais :** `cd696ca0901c62b5bd558723c280fd93ea9206b7`
**Branche staging à reprendre :** `work/qr-association-native`
**Dernier SHA de code staging (hors commits de relais/doc) :** `221875481f397459ddcf475e96dd1be7edbef833`
**Important :** re-fetch le HEAD réel de `work/qr-association-native` avant toute promotion ; le commit du présent relais se trouve forcément après ce SHA de code.
**Base `main` observée :** `12e733212a2f3032f897a421a2660e99562419ad`

## Objectif utilisateur

Faire de **Mon Manager Web** une vraie application iPhone sans casser Android, en conservant une seule logique métier et une seule UI Compose autant que possible. Pas de WebView, pas de fork par client, pas de modification de `/mobile-api/` uniquement pour iOS.

## Règles obligatoires avant toute écriture

1. Re-fetch le HEAD réel des branches avant toute mutation.
2. Lire `AGENTS.md` du dépôt.
3. Lire `jasmin-abernathy/repo-factory/AGENTS.md` et `COMPATIBILITY-AND-DEPRECATION-PLAYBOOK.md`.
4. Lire les erreurs `repo-factory/errors/2026-09-22-*` liées à ce chantier.
5. Rechercher la documentation officielle de la version exacte avant d’introduire une API Kotlin/Ktor/Apple/Android.
6. Ne jamais réécrire un fichier complet depuis une lecture partielle.
7. Ne pas pousser sur la PR pendant une CI lourde en cours : `concurrency.cancel-in-progress` annule les runs précédents.
8. Toute erreur réelle rencontrée doit être documentée immédiatement dans `repo-factory/errors/`.
9. Les enseignements techniques positifs évités grâce au préflight doivent être capitalisés dans `repo-factory` en fin de lot.

## État réellement VALIDÉ avant le lot QR

Le SHA `c4ac9ee9713f228807ae085076735c9b611ce6f0` a passé :

- Android : tests + assemble debug + lint ✅
- iOS : compilation Kotlin/Native simulator ✅
- linkage de `MonManagerShared.framework` ✅
- vrai `iosApp` SwiftUI compilé avec `xcodebuild` pour simulateur ✅

Fonctionnellement, ce SHA contient déjà :

- `shared/commonMain` : protocole, repository, state holder, UI Compose partagée ;
- client HTTP iOS Ktor/Darwin avec HTTPS obligatoire, redirects refusés, même origine et réponses bornées ;
- stockage iOS persistant : cache + queue dans UserDefaults ;
- jetons d’appareil dans le Keychain, `WhenUnlockedThisDeviceOnly` ;
- runtime iOS et `ComposeUIViewController` ;
- vrai host SwiftUI/Xcode sous `iosApp/` ;
- upload photo iOS natif via `PHPickerViewController` ;
- copie temporaire contrôlée + vérification taille/MIME + upload multipart ;
- fermeture propre du client Darwin ;
- Android toujours fonctionnel en parallèle.

## Lot QR actuellement sur la PR

`cd696ca0901c62b5bd558723c280fd93ea9206b7` — `Add native QR association scanning on Android and iOS`

Ce commit ajoute :

- `expect/actual PlatformQrScannerButton` ;
- Android : Google Code Scanner `16.1.0`, QR uniquement, auto-zoom, **sans permission CAMERA dans l’app** ;
- préchargement Google Play Services via `com.google.mlkit.vision.DEPENDENCIES=barcode_ui` ;
- iOS : AVFoundation `AVCaptureSession` + `AVCaptureMetadataOutput`, limité à `AVMetadataObjectTypeQRCode` ;
- demande caméra iOS au moment du scan ;
- `NSCameraUsageDescription` ;
- correction du cas où un site propose uniquement `pairing_code` : démarrage direct en mode association ;
- tests communs `AuthModeTest`.

### Validation de `cd696ca…` au moment du relais

- Android : **SUCCESS** ✅ — run `35708405773`
- iOS : framework compile/link **SUCCESS jusqu’à l’étape framework** ✅
- iOS : étape `Build iOS app for simulator` encore **IN_PROGRESS** dans le run `35708405768`

Ne pas considérer `cd696ca…` comme totalement vert tant que le job iOS n’est pas `completed/success`.

## Staging PRÊT mais PAS encore promu

Branche : `work/qr-association-native`
HEAD : `221875481f397459ddcf475e96dd1be7edbef833`

Elle est **4 commits devant la PR, 0 derrière**, et ne modifie que :

- `shared/src/commonMain/.../ui/Screens.kt` ;
- `shared/src/iosMain/.../IosManagerRuntime.kt` ;
- `iosApp/iosApp/ContentView.swift` ;
- `iosApp/iosApp/Info.plist`.

Contenu du staging :

1. Le scanner QR est proposé **dès l’écran d’accueil, avant la saisie manuelle de l’URL**, conformément au sweep UX de `repo-factory`.
2. iOS déclare `CFBundleURLTypes` pour `lepotager-manager`.
3. SwiftUI reçoit les URLs via `.onOpenURL`.
4. Une `Channel.BUFFERED` Kotlin tamponne les deep-links reçus au cold-start.
5. Après `holder.initialize()`, les URLs sont envoyées à `holder.pairFromLink(raw)` : **aucun second parser**, validation métier identique au scanner.

### Promotion prévue

Si `35708405768` finit en `success` :

1. re-fetch HEAD PR + staging ;
2. vérifier que staging reste descendant de PR ;
3. re-fetch le HEAD réel de `work/qr-association-native`, vérifier que `221875481f397459ddcf475e96dd1be7edbef833` est bien son ancêtre et que les commits suivants sont uniquement documentaires, puis fast-forward la PR vers ce HEAD réel ;
4. attendre Android + iOS sur CE nouveau SHA ;
5. ne rien pousser pendant ces runs.

Si `35708405768` échoue :

1. récupérer les logs seulement une fois le blob disponible ;
2. documenter l’erreur dans `repo-factory/errors/` avant correction ;
3. corriger sur staging puis reconstruire une chaîne propre depuis le vrai HEAD.

## Préflight/documentation déjà vérifiés

- Google Code Scanner 16.1.0 : `enableAutoZoom()` supporté ; `barcode_ui` est le meta-data officiel de préchargement ; pas de permission caméra à demander par l’app.
- Apple AVFoundation : `metadataObjectTypes` doit être un sous-ensemble de `availableMetadataObjectTypes` ; QR = `AVMetadataObjectTypeQRCode`.
- Apple : `NSCameraUsageDescription` obligatoire pour accès caméra.
- SwiftUI : `onOpenURL(perform:)` est le point de réception des custom URLs.
- Apple : `CFBundleURLTypes` déclare le schéma custom ; les paramètres doivent être validés avant action.

## Incidents déjà documentés dans repo-factory pendant ce chantier

- action GitHub `list_commits` supposée mais non exposée ;
- Compose Multiplatform 1.12 exigeant compileSdk 37 ;
- collision template literal JS / `${...}` Kotlin ;
- push concurrent annulant la CI via `cancel-in-progress` ;
- extensions Ktor `takeFrom` / `encodedPath` non importées ;
- `\\n` injecté littéralement dans un fichier Kotlin ;
- blob de logs GitHub Actions indisponible pendant job en cours ;
- endpoint `/actions/jobs/{id}` non accepté par `mcp__GitHub__fetch`.

## Ce qui restera après QR + deep-link

- test physique iPhone : caméra QR, ouverture custom URL, photo picker, upload réel, Keychain, reprise après kill ;
- AppIcon iOS : actuellement absent ;
- signature Apple / `TEAM_ID` : volontairement vide ;
- TestFlight/App Store : nécessite le compte Apple et la signature réelle ;
- éventuellement universal links plus tard ; le protocole v1 actuel utilise le schéma `lepotager-manager://pair?...`.

## Ne pas faire

- ne pas réintroduire de logique iOS séparée du repository/state holder commun ;
- ne pas ajouter `android.permission.CAMERA` juste pour Google Code Scanner ;
- ne pas bypasser `pairFromLink` avec un parser Swift ;
- ne pas monter AGP/Kotlin/Compose/compileSdk dans ce lot ;
- ne pas appeler “validé” un SHA dont un workflow est encore pending/in_progress ;
- ne pas fabriquer d’IPA/TestFlight sans signature Apple configurée.
