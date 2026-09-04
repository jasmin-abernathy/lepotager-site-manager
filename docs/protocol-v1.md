# Le Potager Site Manager Protocol — v1

## Objectif

Permettre à une application Android unique de découvrir un site compatible, d'authentifier un utilisateur de ce site et de construire une interface de gestion adaptée **sans télécharger de code**.

## 1. Découverte publique

L'app normalise l'adresse saisie vers une origine HTTPS puis lit :

`GET /.well-known/lepotager-site-manager.json`

Le document doit respecter `protocol/site-manifest.schema.json`.

Règles client v1 :

- HTTPS uniquement hors build de développement ;
- taille max du manifeste : 64 KiB ;
- `schema_version == 1` ;
- `api_base_url` doit être HTTPS ;
- les redirects HTTP vers un autre hôte sont refusés pendant la découverte ;
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
  "device_token":"64+ caractères aléatoires",
  "expires_at":null
}
```

Le mot de passe n'est jamais persisté dans l'app. Le challenge doit être à usage unique, court et limité en tentatives.

### `pairing_code`

Le back-office web authentifié génère un code/QR à usage unique. L'app envoie ce code à :

`POST {api}/v1/auth/pair`

Le résultat est le même jeton d'appareil révocable.

## 3. Configuration privée

`GET {api}/v1/config`

Header : `X-Lepotager-Device-Token: <token>`

Exemple :

```json
{
  "schema_version":1,
  "config_version":17,
  "site":{"id":"bmh-renovation","display_name":"BMH Rénovation"},
  "branding":{"primary":"#E2741F","logo_url":"https://.../logo.webp"},
  "policy":{"review_before_publish":true},
  "modules":[
    {"id":"home","kind":"form","title":"Accueil","fields":[...]},
    {"id":"projects","kind":"gallery","title":"Réalisations"},
    {"id":"requests","kind":"requests","title":"Demandes"}
  ]
}
```

### Modules v1 embarqués

- `dashboard` : résumé ;
- `form` : formulaire piloté par une liste de champs sûrs ;
- `gallery` : réalisations/photos/avant-après ;
- `requests` : suivi des demandes ;
- `settings` : paramètres non sensibles explicitement autorisés.

Un `kind` inconnu est ignoré et affiché comme non pris en charge. Le serveur ne peut pas demander l'exécution d'un composant arbitraire.

### Types de champs v1

- `text`
- `multiline`
- `boolean`
- `single_choice`
- `number`
- `url`
- `email`

Les contraintes (required, max_length, min/max, choices) sont validées côté client pour l'UX **et obligatoirement à nouveau côté serveur**.

## 4. Données et mutations

`GET {api}/v1/snapshot` renvoie les données des modules autorisés.

`POST {api}/v1/changes` reçoit une mutation structurée :

```json
{
  "module_id":"home",
  "action":"update_fields",
  "client_request_id":"uuid",
  "payload":{"home_title":"..."}
}
```

Le serveur répond soit `applied`, soit `pending_review` selon sa politique et les droits.

Les répétitions du même `client_request_id` doivent être idempotentes.

## 5. Offline-first

- dernière configuration + snapshot en Room ;
- petites préférences en DataStore ;
- brouillons persistés localement ;
- une mutation explicitement envoyée hors ligne peut être mise en file ;
- WorkManager ne republie que des mutations déjà consenties par l'utilisateur ;
- jamais de publication automatique d'un brouillon simplement parce que le réseau revient.

## 6. Versionnement

Chaque document comporte `schema_version`. Les nouveaux champs sont facultatifs par défaut. Une rupture nécessite une nouvelle version majeure du protocole.

Le client annonce sa plage supportée via `X-Lepotager-Protocol: 1`.

## 7. Confidentialité

Le protocole ne nécessite aucun service central du Potager pour fonctionner : l'application parle directement au domaine du client. Un registre central pourra plus tard être ajouté **en option** pour faciliter la découverte, jamais comme condition technique.
