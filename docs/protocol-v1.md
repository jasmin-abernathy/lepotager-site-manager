# Le Potager Site Manager Protocol — v1

## Objectif

Permettre à une application Android unique de découvrir un site compatible, d'authentifier un utilisateur de ce site et de construire une interface de gestion adaptée **sans télécharger de code**.

Le protocole décrit des primitives génériques. Les intégrations avec Easy!Appointments, AbanteCart, WooCommerce, Cal.com ou tout autre outil restent côté serveur et ne deviennent jamais des types client spécifiques.

## 1. Découverte publique

L'app normalise l'adresse saisie vers une origine HTTPS puis lit :

`GET /.well-known/lepotager-site-manager.json`

Le document doit respecter `protocol/site-manifest.schema.json`.

Règles client v1 :

- HTTPS uniquement ;
- taille max du manifeste : 64 KiB ;
- `schema_version == 1` ;
- `api_base_url` doit être HTTPS ;
- en v1, `api_base_url` reste sur la même origine que le site ;
- les redirects HTTP sont refusés pendant les échanges de protocole ;
- aucune clé secrète, aucun token et aucune donnée personnelle dans ce manifeste.

## 2. Authentification

Deux méthodes standard sont prévues.

### `password_totp`

`POST {api}/v1/auth/start`

```json
{"username":"marie","password":"...","device_name":"Pixel 9"}
```

Réponse avec TOTP :

```json
{
  "status":"mfa_required",
  "challenge_id":"opaque-random-id",
  "methods":["totp"],
  "expires_in":300
}
```

Puis :

`POST {api}/v1/auth/verify`

```json
{"challenge_id":"...","method":"totp","code":"123456"}
```

Réponse :

```json
{
  "status":"authenticated",
  "device_token":"jeton aléatoire par appareil",
  "expires_at":null
}
```

Le mot de passe n'est jamais persisté dans l'app. Le challenge doit être à usage unique, court et limité en tentatives.

### `pairing_code`

Le back-office web authentifié génère un code/QR à usage unique. L'app envoie ce code à :

`POST {api}/v1/auth/pair`

Le résultat est le même jeton d'appareil révocable.

Un QR peut utiliser un deep-link de la forme :

`lepotager-manager://pair?site=https%3A%2F%2Fclient.fr&code=12345678`

Le QR ne doit jamais contenir de mot de passe ni de jeton permanent.

## 3. Configuration privée

`GET {api}/v1/config`

Headers :

- `X-Lepotager-Protocol: 1`
- `X-Lepotager-Device-Token: <token>`

Exemple :

```json
{
  "schema_version":1,
  "config_version":18,
  "site":{"id":"client-exemple","display_name":"Client Exemple"},
  "branding":{"primary":"#A94E37","logo_url":"https://.../logo.webp"},
  "policy":{"review_before_publish":true,"allow_offline_queue":true},
  "modules":[
    {"id":"home","kind":"form","title":"Accueil","fields":[...]},
    {
      "id":"appointments",
      "kind":"calendar",
      "title":"Rendez-vous",
      "fields":[
        {"id":"service","type":"text","label":"Prestation"},
        {"id":"customer","type":"text","label":"Client"}
      ],
      "actions":[
        {
          "id":"cancel",
          "label":"Annuler",
          "tone":"danger",
          "requires_confirmation":true,
          "confirmation_text":"Confirmer l'annulation ?",
          "allow_offline":false
        }
      ]
    },
    {
      "id":"orders",
      "kind":"records",
      "title":"Commandes",
      "fields":[
        {"id":"amount","type":"text","label":"Montant"},
        {"id":"payment_method","type":"text","label":"Paiement"}
      ]
    },
    {"id":"requests","kind":"requests","title":"Demandes"}
  ]
}
```

### Modules v1 embarqués

- `dashboard` : résumé ;
- `form` : formulaire piloté par une liste de champs sûrs ;
- `gallery` : collection structurée, création/modification/suppression selon `options` ;
- `requests` : suivi des demandes ;
- `records` : collection métier générique, par exemple commandes, clients, tickets, dossiers, tâches ;
- `calendar` : collection datée générique, par exemple rendez-vous, événements ou échéances ;
- `settings` : réservé à de futurs paramètres non sensibles explicitement autorisés.

Un `kind` inconnu est ignoré et affiché comme non pris en charge. Le serveur ne peut pas demander l'exécution d'un composant arbitraire.

### Types de champs v1

- `text`
- `multiline`
- `boolean`
- `single_choice`
- `number`
- `url`
- `email`

Les contraintes (`required`, `max_length`, `min`, `max`, `choices`) sont validées côté client pour l'UX **et obligatoirement à nouveau côté serveur**.

### Actions déclarées par le serveur

Les modules métier peuvent annoncer une liste `actions`. Chaque action comporte :

- `id` : identifiant opaque, interprété uniquement par le serveur ;
- `label` : texte affiché à l'utilisateur ;
- `tone` : `primary`, `secondary` ou `danger` pour un rendu appartenant à l'app ;
- `requires_confirmation` : demande une confirmation locale ;
- `confirmation_text` : texte facultatif de confirmation ;
- `allow_offline` : `false` par défaut.

Le serveur ne fournit jamais d'URL arbitraire, de script, de classe ou de payload exécutable à l'app. Le client connaît seulement l'identifiant de l'action et envoie l'action standard `item_action`.

## 4. Données et mutations

`GET {api}/v1/snapshot` renvoie les données des modules autorisés.

La clé `data` contient une entrée par `module.id`.

### `form`

Objet clé/valeur :

```json
"home": {"title":"Bonjour","intro":"..."}
```

### `gallery`, `requests`, `records`

Tableau direct ou objet contenant `items` :

```json
"orders": {
  "items": [
    {
      "id":"1042",
      "title":"Commande #1042",
      "subtitle":"Guidance classique",
      "status":"Payé",
      "amount":"95 €",
      "payment_method":"Stripe"
    }
  ]
}
```

Pour `records`, les clés réservées d'affichage sont :

- `id` obligatoire pour toute action ;
- `title` ;
- `subtitle` ;
- `status`.

Les autres valeurs affichées sont décrites par `module.fields`.

### `calendar`

Même forme que `records`, avec en plus :

- `start` : date ou date-heure ISO 8601 ;
- `end` : facultatif, date ou date-heure ISO 8601.

Exemple :

```json
"appointments": {
  "items": [
    {
      "id":"82",
      "title":"Guidance classique",
      "subtitle":"Camille D.",
      "status":"Confirmé",
      "start":"2026-09-08T11:00:00+02:00",
      "end":"2026-09-08T12:00:00+02:00",
      "service":"Guidance classique",
      "customer":"Camille D."
    }
  ]
}
```

`revision` change quand les données visibles par l'app changent.

### Mutations standard

`POST {api}/v1/changes`

Requête générale :

```json
{
  "module_id":"home",
  "action":"update_fields",
  "client_request_id":"123e4567-e89b-12d3-a456-426614174000",
  "payload":{"home_title":"..."}
}
```

Actions standard actuellement prises en charge :

- `update_fields` pour un module `form` ;
- `create_item`, `update_item`, `delete_item` pour un module `gallery` ;
- `item_action` pour une action déclarée sur `records` ou `calendar`.

`item_action` utilise :

```json
{
  "module_id":"appointments",
  "action":"item_action",
  "client_request_id":"uuid",
  "payload":{
    "item_id":"82",
    "action_id":"cancel"
  }
}
```

Le serveur doit vérifier que :

1. le module existe pour l'utilisateur courant ;
2. l'objet appartient réellement à ce module ;
3. `action_id` figure dans les actions autorisées pour cet utilisateur ;
4. l'action reste valide dans l'état actuel de l'objet ;
5. le connecteur métier applique ses propres contrôles avant toute mutation externe.

Le serveur répond soit `applied`, soit `pending_review` selon sa politique et les droits.

Les répétitions du même `client_request_id` pour le même utilisateur doivent être **idempotentes** : le serveur renvoie la réponse déjà produite au lieu de créer une deuxième mutation.

## 5. Connecteurs métier côté serveur

Le protocole ne définit aucun connecteur tiers dans l'APK. Un back-office peut utiliser des adaptateurs internes, par exemple :

```text
EasyAppointmentsConnector -> calendar
AbanteCartConnector        -> records
WooCommerceConnector       -> records
CalComConnector            -> calendar
```

Responsabilités d'un connecteur :

- lire les données depuis l'outil source ;
- normaliser vers `snapshot` ;
- traduire un `item_action` vers une action métier autorisée ;
- ne jamais exposer ses secrets à l'app ;
- conserver l'outil source comme autorité sur son domaine ;
- propager les changements vers une nouvelle `revision` ;
- traiter les callbacks/webhooks de manière idempotente.

Le back-office peut agréger plusieurs connecteurs sans que l'application sache quels logiciels sont utilisés.

## 6. Médias

Un module peut annoncer un objet `media`. Le client n'affiche le sélecteur que si `upload_enabled == true`.

Le site décrit :

- `max_bytes` ;
- `accepted_mime_types` ;
- les champs de métadonnées à afficher avant l'envoi.

Exemple BMH : `kind = normal|before|after`, `alt`, `caption`.

L'envoi utilise :

`POST {api}/v1/media`

Type : `multipart/form-data`.

Champs :

- `module_id` ;
- `item_id` ;
- `client_request_id` ;
- `metadata` : objet JSON sérialisé ;
- `media` : fichier binaire.

Le client vérifie MIME et taille avant transfert, mais **le serveur reste l'autorité** : il doit vérifier l'erreur d'upload, la taille réelle, le MIME réel, les dimensions et le contenu décodable. Pour les images, l'implémentation de référence BMH décode puis réencode en WebP dans un stockage privé avant de créer une demande de publication.

Les médias ne sont pas mis dans la file hors connexion en v1. Le choix de fichier est relancé lorsque le réseau est disponible, afin de ne pas conserver silencieusement une URI Android fragile ou une copie volumineuse.

`client_request_id` s'applique également aux uploads média : une répétition ne doit pas créer deux demandes ni deux publications.

## 7. Offline-first

- dernière configuration + snapshot en Room ;
- petites préférences en DataStore ;
- jetons d'appareil protégés par Android Keystore ;
- une mutation JSON explicitement envoyée hors ligne peut être mise en file si `allow_offline_queue` l'autorise ;
- une action `item_action` n'est **pas** mise en file par défaut ; elle ne peut l'être que si son propre `allow_offline=true` ;
- les opérations transactionnelles (annulation, remboursement, changement de statut de paiement, etc.) devraient normalement rester `allow_offline=false` ;
- WorkManager ne republie que des mutations déjà consenties par l'utilisateur ;
- jamais de publication automatique d'un brouillon simplement parce que le réseau revient ;
- jamais de mise en file automatique des médias en v1.

Une erreur HTTP/protocole/validation n'est pas une panne réseau et ne doit pas être placée dans la file offline.

## 8. Versionnement

Chaque document comporte `schema_version`. Les nouveaux champs sont facultatifs par défaut. L'ajout de `records`, `calendar` et `actions` reste compatible avec le schéma v1 : les anciens clients ignorent les `kind` inconnus sans exécuter de code. Une rupture nécessite une nouvelle version majeure du protocole.

Le client annonce sa version via :

`X-Lepotager-Protocol: 1`

## 9. Confidentialité et sécurité

Le protocole ne nécessite aucun service central du Potager pour fonctionner : l'application parle directement au domaine du client. Un registre central pourra plus tard être ajouté **en option** pour faciliter la découverte, jamais comme condition technique.

Principes v1 :

- pas de WebView d'administration ;
- pas de DEX/JAR/JS téléchargé pour étendre l'interface ;
- pas de secrets globaux embarqués dans l'APK ;
- pas de secrets des logiciels tiers dans l'APK ;
- permissions minimales ;
- sélection de fichiers via le sélecteur système Android ;
- jeton révocable par appareil ;
- validation serveur systématique des droits, champs, médias et actions ;
- action transactionnelle jamais rejouée hors connexion sans autorisation explicite ;
- l'outil métier source reste source d'autorité pour ses données.
