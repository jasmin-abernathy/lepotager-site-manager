# Rendre un deuxième site compatible — checklist v1

Cette checklist décrit le minimum nécessaire pour connecter un nouveau site à **Le Potager — Gestion** sans modifier ni recompiler l'application Android.

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
- champs éditables ;
- options de galerie ;
- contrat média éventuel.

Le site décide de la configuration. L'APK ne doit contenir aucune condition spécifique au client.

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
    "requests": {"items": []}
  }
}
```

`revision` change quand les données visibles par l'app changent.

## 5. Recevoir les mutations JSON

`POST v1/changes`

Chaque requête possède un `client_request_id` UUID. Le serveur doit enregistrer cette clé avec la réponse produite afin qu'un même envoi répété retourne la même réponse sans créer de doublon.

Actions actuellement utilisées :

- formulaire : `update_fields` ;
- galerie : `create_item`, `update_item`, `delete_item`.

Valider côté serveur : permissions, types, longueurs, choix autorisés et existence des objets ciblés.

## 6. Ajouter les médias si nécessaire

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

## 7. Prévoir la révocation

Le back-office doit permettre au propriétaire :

- de voir les appareils associés ;
- de révoquer un jeton ;
- de désactiver l'accès mobile ;
- idéalement de voir la dernière utilisation.

## 8. Tester avant mise en service

À vérifier :

- manifeste accessible en HTTPS ;
- auth valide et auth refusée ;
- jeton révoqué refusé ;
- `v1/config` et `v1/snapshot` lisibles ;
- mutation acceptée une seule fois même si renvoyée avec le même UUID ;
- erreur de validation non mise en file offline ;
- perte réseau : mutation JSON mise en file seulement si autorisée ;
- upload trop gros refusé ;
- faux MIME refusé côté serveur ;
- média réencodé/stagé hors webroot ;
- demande visible dans le back-office avant publication si `review_before_publish=true`.

## 9. Aucun changement Android requis

Si le site reste dans les primitives du protocole v1, aucune recompilation de l'APK n'est nécessaire. Une nouvelle fonction métier doit d'abord être modélisée comme primitive générique du protocole avant d'être ajoutée au client Android.
