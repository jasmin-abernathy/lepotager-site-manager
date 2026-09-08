# Rendre un deuxième site compatible — checklist v1

Cette checklist décrit le minimum nécessaire pour connecter un nouveau site à **Mon Manager Web** sans modifier ni recompiler l'application Android.

## 1. Publier le manifeste de découverte

Créer :

`/.well-known/lepotager-site-manager.json`

Il doit contenir au minimum :

```json
{
  "schema_version": 1,
  "site_id": "client-exemple",
  "display_name": "Client Exemple",
  "api_base_url": "https://client.example/mobile-api/",
  "auth_methods": ["pairing_code"],
  "protocol_min": 1,
  "protocol_max": 1
}
```

Règles : HTTPS, même origine en v1, aucun secret dans ce fichier.

## 2. Implémenter l'authentification

Au moins une méthode :

- `POST v1/auth/pair` ;
- ou `POST v1/auth/start` + `POST v1/auth/verify` pour mot de passe + TOTP.

Le résultat final doit être un **jeton distinct par appareil**, révocable côté serveur.

## 3. Exposer la configuration privée

`GET v1/config`

Décrire :

- identité visuelle ;
- politique `review_before_publish` ;
- politique `allow_offline_queue` ;
- modules visibles ;
- champs éditables ou affichables ;
- options de galerie ;
- contrat média éventuel ;
- actions métier éventuellement autorisées sur `records` et `calendar`.

Le site décide de la configuration. L'APK ne doit contenir aucune condition spécifique au client ni au logiciel tiers utilisé.

Modules génériques disponibles :

- `dashboard` ;
- `form` ;
- `gallery` ;
- `requests` ;
- `records` pour des objets métier structurés ;
- `calendar` pour des objets datés.

## 4. Exposer le snapshot

`GET v1/snapshot`

La clé `data` contient une entrée par `module.id`.

Exemple :

```json
{
  "schema_version": 1,
  "revision": 42,
  "data": {
    "home": {"title": "Bonjour"},
    "projects": {"items": []},
    "appointments": {
      "items": [
        {
          "id":"82",
          "title":"Rendez-vous",
          "status":"Confirmé",
          "start":"2026-09-08T11:00:00+02:00"
        }
      ]
    },
    "orders": {
      "items": [
        {"id":"1042","title":"Commande #1042","status":"Payé","amount":"95 €"}
      ]
    }
  }
}
```

`revision` change quand les données visibles par l'app changent.

Pour `records`, prévoir `id`, puis facultativement `title`, `subtitle`, `status`. Les autres champs visibles sont décrits par `module.fields`.

Pour `calendar`, ajouter `start` et éventuellement `end` en ISO 8601.

## 5. Recevoir les mutations JSON

`POST v1/changes`

Chaque requête possède un `client_request_id` UUID. Le serveur doit enregistrer cette clé avec la réponse produite afin qu'un même envoi répété retourne la même réponse sans créer de doublon.

Actions standard :

- formulaire : `update_fields` ;
- galerie : `create_item`, `update_item`, `delete_item` ;
- module métier : `item_action`.

Pour `item_action` :

```json
{
  "module_id":"appointments",
  "action":"item_action",
  "client_request_id":"uuid",
  "payload":{"item_id":"82","action_id":"cancel"}
}
```

Le back-office doit vérifier que l'action est encore autorisée pour l'utilisateur et l'état courant de l'objet. Ne jamais faire confiance au seul fait que l'action était visible dans une configuration mise en cache.

## 6. Brancher un logiciel métier via un connecteur serveur

Ne jamais ajouter un type `easyappointments`, `abantecart`, `woocommerce`, etc. dans l'APK.

Le connecteur appartient au back-office et doit :

1. lire l'outil source ;
2. normaliser ses données vers `records` ou `calendar` ;
3. traduire les `item_action` vers l'API/hook de l'outil ;
4. conserver les secrets côté serveur ;
5. considérer l'outil source comme autorité sur ses propres données ;
6. incrémenter la `revision` lorsque l'état visible change ;
7. traiter callbacks/webhooks et répétitions de manière idempotente.

Exemples :

```text
EasyAppointmentsConnector -> calendar
AbanteCartConnector        -> records
WooCommerceConnector       -> records
CalComConnector            -> calendar
```

Si une nouvelle intégration ne rentre pas dans les primitives existantes, chercher d'abord une primitive générique utile à plusieurs clients avant d'étendre l'APK.

## 7. Déclarer les actions métier avec prudence

Exemple :

```json
"actions": [
  {
    "id":"cancel",
    "label":"Annuler",
    "tone":"danger",
    "requires_confirmation":true,
    "confirmation_text":"Confirmer l'annulation ?",
    "allow_offline":false
  }
]
```

Règles :

- `action.id` est opaque pour l'app ;
- `tone` reste une valeur UI connue, jamais du CSS/code serveur ;
- les actions transactionnelles restent `allow_offline=false` ;
- `allow_offline=true` doit être réservé à une action réellement sûre et idempotente ;
- une confirmation Android ne remplace jamais les contrôles serveur.

## 8. Ajouter les médias si nécessaire

Dans le module :

```json
"media": {
  "upload_enabled": true,
  "max_bytes": 12582912,
  "accepted_mime_types": ["image/jpeg", "image/png", "image/webp"],
  "fields": []
}
```

Puis implémenter `POST v1/media` en multipart.

Ne jamais publier directement le fichier reçu. Vérifier le MIME réel, la taille, les dimensions et le décodage ; réencoder l'image dans un format maîtrisé avant staging/validation.

## 9. Prévoir la révocation

Le back-office doit permettre au propriétaire :

- de voir les appareils associés ;
- de révoquer un jeton ;
- de désactiver l'accès mobile ;
- idéalement de voir la dernière utilisation.

## 10. Tester avant mise en service

À vérifier :

- manifeste accessible en HTTPS ;
- auth valide et auth refusée ;
- jeton révoqué refusé ;
- `v1/config` et `v1/snapshot` lisibles ;
- mutation acceptée une seule fois même si renvoyée avec le même UUID ;
- `records` correctement rendu avec ses champs ;
- `calendar` trié à partir des dates ISO 8601 ;
- action non autorisée refusée même si un client tente de la fabriquer ;
- action transactionnelle hors ligne refusée et non mise en file ;
- petite action avec `allow_offline=true` rejouée une seule fois ;
- erreur de validation non mise en file offline ;
- upload trop gros refusé ;
- faux MIME refusé côté serveur ;
- média réencodé/stagé hors webroot ;
- demande visible dans le back-office avant publication si `review_before_publish=true`.

## 11. Aucun changement Android requis

Si le site reste dans les primitives du protocole v1, aucune recompilation de l'APK n'est nécessaire. Une nouvelle fonction métier doit d'abord être modélisée comme primitive générique du protocole avant d'être ajoutée au client Android.
