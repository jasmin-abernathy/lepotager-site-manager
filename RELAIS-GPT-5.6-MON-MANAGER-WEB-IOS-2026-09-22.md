# Relais GPT-5.6 — Mon Manager Web iOS
Date : 22 septembre 2026

## Mission
Continuer la finalisation iOS de jasmin-abernathy/lepotager-site-manager sans casser Android. Le portage KMP, le host SwiftUI, les photos, le QR et les liens d’association sont déjà intégrés à la PR. Ne pas recommencer le bootstrap.

L’utilisateur demande d’avancer sur GitHub, de rechercher avant de coder, de documenter les erreurs immédiatement et les enseignements techniques dans repo-factory en fin de lot. Pas d’APK supplémentaire sans demande. Ne pas publier TestFlight ni inventer une signature Apple.

## État vérifié à la reprise

- PR ouverte en brouillon : https://github.com/jasmin-abernathy/lepotager-site-manager/pull/3
- Branche PR : work/ios-kmp-bootstrap
- SHA validé : 4276d910450a290ce121f50ed80ee19e29e2ce7e
- main observé : 12e733212a2f3032f897a421a2660e99562419ad
- work/qr-association-native est également à 4276d910… : sa promotion est déjà faite.
- Branche documentaire de ce relais : work/ios-gpt56-handoff, créée depuis le SHA validé. Elle reçoit seulement ce document. Son HEAD documentaire ne doit pas être confondu avec un nouveau SHA applicatif validé.

| Contrôle du SHA 4276d910… | Résultat |
| --- | --- |
| Android : tests unitaires, assemble debug, lint | completed / success |
| iOS : compilation et linkage du framework, xcodebuild du host SwiftUI pour simulateur | completed / success |
| Tests Kotlin exécutés sur simulateur iOS | Pas encore configurés dans ce workflow |
| Essais physiques iPhone | Non effectués ici |
| Signature, IPA, TestFlight | Non effectués |

Runs :
- Android : https://github.com/jasmin-abernathy/lepotager-site-manager/actions/runs/35709732385
- iOS : https://github.com/jasmin-abernathy/lepotager-site-manager/actions/runs/35709732453

Le précédent lot QR cd696ca… est lui aussi vert (runs 35708405773 et 35708405768), mais utiliser le SHA 4276d910… pour reprendre.

## Avant toute écriture

1. Re-fetch PR, main et branches concernées ; comparer au SHA ci-dessus.
2. Lire AGENTS.md du dépôt et de repo-factory, COMPATIBILITY-AND-DEPRECATION-PLAYBOOK.md, KMP-IOS-PORTING-PLAYBOOK.md et les incidents errors/2026-09-22-* pertinents.
3. Vérifier les versions réellement déclarées. Socle observé : Kotlin 2.2.21, Compose Multiplatform 1.9.3, AGP 8.10.1, compileSdk 36, Gradle 8.11.1, JDK 17, Ktor 3.3.3, coroutines 1.9.0.
4. Lire entièrement un fichier avant de le réécrire. Vérifier le diff final.
5. Créer une nouvelle branche de travail depuis le HEAD réel de la PR ; ne pas partir d’une ancienne branche divergente.
6. Un seul lot cohérent puis une validation Android + iOS. Aucun push sur la PR pendant un run lourd.
7. Ne pas pousser un simple relais sur la PR pour provoquer une nouvelle CI. Les filtres pull_request portent sur le diff de la PR : une synchronisation documentaire peut encore relancer les workflows d’une PR contenant du code.

## Acquis à préserver

- commonMain : protocole, repository, state holder, UI Compose.
- Android conserve ses adaptateurs et le fonctionnement existant.
- iOS : Ktor Darwin ; HTTPS, même origine, redirections refusées, réponses bornées.
- Cache/file persistants ; jetons Keychain WhenUnlockedThisDeviceOnly.
- Host SwiftUI réel sous iosApp ; framework MonManagerShared.
- Photos : PHPickerViewController, copie temporaire, multipart.
- Scanner Android Google Code Scanner 16.1.0 sans permission CAMERA ; scanner iOS AVFoundation avec permission à la demande.
- Scanner proposé avant la saisie de l’URL.
- AuthModeTest : association directe pour un site pairing_code uniquement.
- CFBundleURLTypes + onOpenURL + handleIncomingPairingUrl + Channel.BUFFERED.
- Tous les liens passent par holder.pairFromLink ; aucun parser supplémentaire en Swift.
- Ne pas modifier /mobile-api/ uniquement pour iOS ; pas de WebView ni de fork client.

## Travaux retrouvés hors PR — ne pas fusionner aveuglément

Comparaison faite contre 4276d910… :

| Branche | SHA observé | Divergence | Usage |
| --- | --- | --- | --- |
| work/ios-online-queue-resume | 060f73f17d22b58c935c0c892777905932cb0894 | 2 commits propres, 6 commits PR manquants | Brouillon de reprise réseau à réimplémenter proprement |
| work/ios-ci-tests-v2 | 1083ca70f80976011b5f9d7afe703e8b712e59df | 1 commit propre, 7 commits PR manquants | Récupérer seulement l’intention d’ajouter iosSimulatorArm64Test |

La branche réseau réintroduit un ancien handleIncomingPairingLink et une inbox MutableStateFlow à la place du Channel actuel. Ne pas recopier IosManagerRuntime.kt en bloc. Son adaptateur tente aussi d’importer platform.Network.NWPathMonitor avec une API de forme Swift : ce code ne constitue pas une implémentation Kotlin/Native validée.

## Lot suivant recommandé pour GPT-5.6

### 1. Reprise fiable de la file hors ligne

Constat du SHA validé : IosQueueScheduler.schedule() ne fait rien ; flushQueue est appelé au démarrage avant holder.initialize(). Aucun moniteur réseau n’est intégré. La branche brouillon ne résout donc pas encore ce besoin dans la PR.

Décisions de conception :
- Garder la file et les décisions de rejeu dans le repository commun.
- Sérialiser les appels de vidage avant d’ajouter des déclencheurs réseau, démarrage et rafraîchissement. SiteRepository.flushQueue() n’a actuellement pas de verrou ; deux appels peuvent lire puis envoyer la même entrée en parallèle.
- Un Mutex commun à la même instance de repository constitue une base ; vérifier aussi tous les appelants et instances Android. Ce verrou ne remplace pas l’idempotence serveur par clientRequestId.
- Conserver clientRequestId lors du rejeu ; supprimer une entrée uniquement après succès.
- Propager CancellationException au lieu de la traiter comme une erreur métier ou réseau dans les chemins modifiés.
- Initialiser l’interface sans attendre que toute la file ait tenté des appels réseau. Sérialiser ensuite le démarrage, le retour réseau et le rafraîchissement.
- Un signal de connectivité est une occasion de tenter l’envoi, pas une preuve que le serveur répond.
- La reprise pendant que l’app est active et au retour au premier plan suffit pour ce lot. Ne pas promettre un équivalent WorkManager garanti en arrière-plan.

Choix d’interop : utiliser les fonctions C de Network.framework avec leurs bindings Kotlin vérifiés, ou un petit adaptateur Swift qui transmet uniquement le signal au contrôleur Kotlin. Ne pas importer directement une API purement Swift comme si elle était automatiquement exposée à Kotlin. Préférer une seule voie et garder toute logique de queue en Kotlin.

Critères de test : deux demandes de flush concurrentes ne doublonnent pas un envoi ; échec réseau conserve l’entrée ; succès la supprime ; annulation ne devient pas une erreur persistée ; changement de site/déconnexion ne publie pas sous une autre session. Vérifier les fixtures de token/config avant d’accuser les garde-fous métier.

### 2. Cycle de vie des photos temporaires

Constat de lecture :
- IosMediaUploader supprime dans un finally entourant lecture/envoi.
- Les rejets de module, taille, MIME ou session placés avant ce try ne passent pas par ce nettoyage.
- Le picker crée des fichiers manager-pick-… ; son implémentation ne nettoie pas elle-même un ancien choix remplacé ou abandonné.

Définir un propriétaire unique des fichiers temporaires et une fonction de nettoyage limitée aux fichiers créés par l’app. Couvrir refus avant envoi, remplacement, abandon, annulation, succès et échec. Ne jamais supprimer une référence arbitraire sur un simple test de préfixe ; vérifier l’appartenance au répertoire dédié après normalisation. Préserver une photo si le parcours propose explicitement de réessayer.

La vérification MIME actuelle de l’uploader repose sur l’extension : ne pas la présenter comme une inspection du contenu. Les contrôles serveur restent indispensables.

### 3. Exécuter réellement les tests iOS

Le workflow actuel compile et lie ; il n’exécute pas :shared:iosSimulatorArm64Test. Reprendre cet ajout sur la branche fraîche, vérifier le simulateur disponible sur macos-15 et les tâches de la version Kotlin utilisée. Conserver xcodebuild : les tests du module ne valident pas le host SwiftUI.

Faire une seule validation finale pour le lot cohérent. N’appeler « vert » que le SHA dont tous les runs sont completed/success.

### 4. Documentation et icône

docs/ios-kmp-migration.md est resté en partie au stade bootstrap : il annonce encore des fonctions iOS à venir alors qu’elles existent. Le remettre en cohérence dans le même lot, après les changements réels.

L’AppIcon iOS est absent de l’arbre inspecté ; TEAM_ID est vide dans iosApp/Configuration/Config.xcconfig.
Un kit utilisateur est désormais identifié dans le contexte : mon-manager-web-logo-kit.zip, Library ID libfile_81343958d1d08191ab0e500480df94be. Récupérer et inspecter ce kit avant toute intégration ; son contenu n’a pas été inspecté pendant ce relais. Ne pas inventer une nouvelle identité visuelle. Préparer les assets et leur référence Xcode à partir du kit retenu, puis vérifier le catalogue et le build.

## Essais à faire avec un iPhone / un accès Apple réel

- Installation signée ; caméra autorisée/refusée ; scan/annulation/réouverture.
- URL d’association à froid et à chaud, invalide/expirée, sans écraser silencieusement une session existante.
- Choix photo, changement de choix, fichier trop gros, format refusé, upload réel et reprise après erreur.
- Jeton Keychain après fermeture/réouverture ; déconnexion.
- Mode avion, retour réseau, premier plan, absence de double mutation.
- Petit écran, paysage, clavier, grandes polices et VoiceOver.
- Signature avec le vrai compte et TEAM_ID fournis/configurés par l’utilisateur ; TestFlight seulement après ce passage.

Ne pas annoncer ces scénarios comme exécutés depuis la seule CI. Ne pas demander ces accès avant d’avoir terminé les travaux réalisables sans eux.

## Ce qui a été fait pour préparer ce relais

- Vérification des branches, comparaisons et runs actuels.
- Lecture des règles repo-factory et du code concerné.
- Identification des branches divergentes et des défauts de cycle de vie/concurrence à traiter.
- Préflight officiel sur l’interop Kotlin/Apple et le Mutex coroutines 1.9.0.
- Erreurs de lecture des outils consignées dans repo-factory/errors/2026-09-22-relay-connector-response-parsing-and-ref-paths.md.
- Aucun nouveau code applicatif implémenté dans cette passe de reprise ; le présent document est un plan de finalisation, pas une preuve que ses correctifs sont déjà faits.
- PR toujours en brouillon ; main inchangé au contrôle.

## Sources techniques consultées

- Kotlin, interop Swift/Objective-C : https://kotlinlang.org/docs/native-objc-interop.html
- Apple, API C du moniteur : https://developer.apple.com/documentation/network/nw_path_monitor_create%28%29
- Apple, callback de chemin réseau : https://developer.apple.com/documentation/network/nw_path_monitor_set_update_handler%28_%3A_%3A%29
- Source officielle Mutex, tag exact 1.9.0 : https://github.com/Kotlin/kotlinx.coroutines/blob/1.9.0/kotlinx-coroutines-core/common/src/sync/Mutex.kt

Le lien Markdown de la page Apple NWPathMonitor n’a pas pu être lu par l’outil web ; ne pas transformer cette limitation de récupération en conclusion technique.

## Quand repasser le relais

Continuer de façon autonome pour les corrections bornées, tests et intégration du kit. Repasser à GPT-6 si un choix d’architecture non couvert reste nécessaire après lecture des logs et des sources officielles, avec le SHA, le symptôme, les essais déjà effectués et une question précise. Un compte Apple absent ou un essai physique manquant demande l’intervention de l’utilisateur, pas un changement de modèle.
