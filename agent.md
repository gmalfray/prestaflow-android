# agent.md — PrestaFlow Android

## 1. Objectifs, périmètre et non-objectifs
- Objectif : proposer une application Android (minSdk 26, targetSdk dernière stable) permettant la gestion complète d’une boutique PrestaShop ≥ 1.7 via le module **Rebuild Connector** (REST JSON) et, à terme, la nouvelle API PrestaShop 9.
- Périmètre MVP : authentification par clé API → JWT, onglets Dashboard/Commandes/Clients/Produits/Paniers (lecture), modification suivi expédition, gestion prix/stock/statut produit, gestion des stocks (`stock_availables`), historique clients, classements top clients/ventes, notifications push FCM (paramétrables), mode hors ligne (cache + file d’attente), FR/EN.
- KPI : délai de traitement commandes, fiabilité notifications, rapidité MAJ stock/suivi, visibilité sur KPI (CA, commandes, top ventes).
- Extensions prévues : thèmes « Skins » (5 palettes + Material You), analytics avancés (taux conversion, panier moyen, TVA), compatibilité PrestaShop 9, multi-boutiques, relance paniers via module dédié.
- Hors périmètre actuel : automatisations marketing, relance paniers, multi-boutiques (reportées), édition d’images avancée (post-MVP).
- Rôle de l’assistant : garantir la conformité au cahier des charges, documenter dépendances Android ↔ Rebuild Connector, tracer questions en suspens (cf. §13 du cahier), maintenir la cohérence FR/EN (i18n).

## 2. Arborescence du dépôt
```
repo-root/
├─ agent.md                          # Présent document, source de vérité
├─ README.md                         # Pitch + setup rapide
├─ app/                              # Module Android principal
│  ├─ build.gradle.kts               # Config module (Compose, Hilt, etc.)
│  └─ src/
│      ├─ main/
│      │   ├─ java/com/rebuildit/prestaflow/
│      │   │   ├─ ui/                # Écrans Compose, navigation
│      │   │   ├─ data/              # Repositories, Retrofit, Room
│      │   │   ├─ domain/            # Use-cases, modèles métier
│      │   │   └─ di/                # Modules Hilt/Koin
│      │   ├─ res/                   # Thèmes, strings FR/EN, icônes
│      │   └─ AndroidManifest.xml
│      └─ test/ & androidTest/
├─ build.gradle.kts                  # Build global (plugins, versions)
├─ config/
│  └─ detekt/detekt.yml              # Configuration detekt
├─ env.sample                        # Variables locales d’exemple
├─ gradle/                           # Wrapper & catalogue de versions
├─ gradle.properties                 # Flags (compose, kotlin options)
├─ settings.gradle.kts
└─ docs/                             # Diagrammes, chartes UX (à créer)
```
> Conserver la cohérence avec l’arborescence décrite côté module (`rebuild-connector/agent.md`) pour les dossiers `docs/`, `scripts/`, etc.

## 3. Environnements & secrets
- **Boutiques ciblées** : `preprod`, `prod`. Auth obligatoire via HTTPS + certificat valide + HSTS.
- **Variables locales** : stocker `API_BASE_URL`, `SHOP_URL`, `API_KEY` dans `~/.gradle/gradle.properties` ou via Gradle `local.properties` (non commit). Utiliser un `env.sample`.
- **Google/Firebase** : `app/google-services.json` (non versionné), `FIREBASE_SERVICE_ACCOUNT` (CI pour FCM HTTP v1), Crashlytics activé.
- **Notifications** : enregistrement du token FCM via endpoint `POST /connector/push/register` si exposé ; sinon module gère mapping.
- **Sécurité Android** : stocker JWT + refresh (si implémenté) avec `EncryptedSharedPreferences`, device-bound key alias dans Android Keystore ; effacer en cas de logout ou rotation de clé.
- **CI/CD** : secrets `PLAY_STORE_JSON`, `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`, `FIREBASE_APP_ID`, `SENTRY_DSN?`, `CRASHLYTICS_TOKEN`.
- **Monitoring** : Crashlytics + traces réseau optionnelles (use HTTP logging interceptors conditionnés `BuildConfig.DEBUG`).

## 4. Endpoints REST Rebuild Connector (principal) & compatibilité
Base : `https://<boutique>/module/rebuildconnector/api`

| Méthode | Endpoint | Usage App | Notes |
|---------|----------|-----------|-------|
| POST | `/connector/login` | Auth via clé API → JWT court (scopes) | Réponse contient `token`, `expires_in`, `scopes`. Refresh via relogin (rotation). |
| GET | `/orders` | Liste paginée, filtres état/date | Paramètres : `limit`, `offset`, `filter[state]`, `sort=-date_add`, `search`. |
| GET | `/orders/{id}` | Détail complet | Inclut produits, client, adresses, tracking. |
| PATCH | `/orders/{id}/status` | Changement état (workflow) | Corps `{ "status": "shipped", "comment": "..." }`. |
| PATCH | `/orders/{id}/shipping` | Ajout/édition tracking + transporteur | Corps `{ "tracking_number": "...", "carrier_id": 5 }`. Scanner code-barres/code 2D via caméra. |
| GET | `/products` | Catalogue | Support `filter[active]=1`, `search`, `limit`, `offset`. |
| GET | `/products/{id}` | Détail produit | Prix, descriptions, déclinaisons, images. |
| PATCH | `/products/{id}` | MAJ prix, statut actif | Corps partiel, valider double-check. |
| PATCH | `/products/{id}/stock` | MAJ stock dispo | Corps `{ "quantity": 42, "warehouse_id": null }`. |
| GET | `/stock/availables` | Suivi multi-entrepôts | Optionnel, utile pour vues stock. |
| GET | `/customers` | Liste + stats commandes | Filtrage par segment, date dernière commande. |
| GET | `/customers/{id}` | Historique commandes | Exposer top produits client. |
| GET | `/orders/{id}/history` | Timeline états utilisateur | Pour écran activité. |
| GET | `/dashboard/metrics` | KPI CA, commandes, clients | `period=day|week|month|year`, `from`, `to`. |
| GET | `/reports?resource=bestsellers` | Classement top ventes | Graphs + top 5. |
| GET | `/reports?resource=bestcustomers` | Classement top clients | Param `limit`. |
| GET | `/baskets` | Paniers (lecture) | Paginé, filtrage `abandoned_since_days`. |
| GET | `/baskets/{id}` | Détail panier | Utilisé pour info service client. |

Fallback optionnel : feature flag pour utiliser l’ancien webservice (`mobassistantconnector?call_function=...`) via un adapter HTTP si activé côté boutique. L’app doit détecter la dispo via `/connector/info`.

### Exemples `curl`
```bash
# Authentification
curl -sS -X POST "https://shop.tld/module/rebuildconnector/api/connector/login" \
  -H "Content-Type: application/json" \
  -d '{ "api_key": "<KEY>", "shop_url": "https://shop.tld" }'

# MAJ numéro de suivi
curl -sS -X PATCH "https://shop.tld/module/rebuildconnector/api/orders/123/shipping" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{ "tracking_number": "1Z999", "carrier_id": 17 }'
```

## 5. DTO / Schémas principaux (Kotlin data classes)
- `OrderDto` : `id`, `reference`, `status`, `payment`, `currency`, `total_paid`, `date_add`, `customer_id`, `carrier`, `tracking_number`, `products: List<OrderItemDto>`, `history: List<OrderHistoryDto>`.
- `OrderItemDto` : `product_id`, `name`, `sku`, `quantity`, `price_unit`, `image_url`.
- `OrderHistoryDto` : `status`, `changed_by`, `changed_at`, `message`.
- `ShippingUpdateRequest` : `tracking_number`, `carrier_id?`.
- `ProductDto` : `id`, `name`, `price`, `active`, `stock`, `images`, `updated_at`, `declinations`.
- `StockUpdateRequest` : `quantity`, `warehouse_id?`, `reason`.
- `CustomerDto` : `id`, `firstname`, `lastname`, `email (masked if needed)`, `orders_count`, `total_spent`, `last_order_at`.
- `DashboardMetricsDto` : `turnover`, `orders_count`, `customers_count`, `products_count`, `chart: List<DataPoint>`, `period`.
- `RankingEntryDto` : `id`, `label`, `metric`, `position`.
- `BasketDto` : `id`, `last_update`, `total`, `is_converted`, `items: List<BasketItemDto>`.
- `AuthResponse` : `token`, `expires_in`, `scopes`, `refresh_before`.
- `OfflineSyncEntity` (Room) : `id`, `endpoint`, `method`, `payload`, `retries`, `lastAttempt`, `status`.

Mapper couche `domain` pour convertir en modèles UI (ex. `Order`, `OrderSummary`, `DashboardCard`).

## 6. Flux fonctionnels & techniques
- **Notification → détail commande** : réception FCM (`order.created` / `order.status.changed`) → deep link `prestaflow://orders/{id}` → récupération `GET /orders/{id}` → possibilité de changer état ou ajouter tracking → `PATCH /orders/{id}/status` ou `/shipping` → confirmation + options de notification vers client (si module active).
- **Scan suivi expédition** : écran action rapide → Intent caméra (ML Kit / ZXing) → parsing du code → préremplissage du champ tracking → validation → `PATCH /orders/{id}/shipping` → si max 5s sans réponse, indiquer statut pending + push local snooze.
- **Gestion produit** : recherche → sélection produit → édition prix/stock/actif → enregistrement local en pending → tentative `PATCH /products/{id}` ou `/stock` → si offline, en file d’attente → UI affiche badge "En attente de synchro".
- **Mode hors ligne** : bootstrap → charger cache Room (orders, products, metrics) → afficher timestamp de fraîcheur → file d’attente Replay sur reconnexion (WorkManager, backoff expo 5/30/120s). Conflit stock : si 409, recharger état distant et proposer merge.
- **Connexion initiale** : écran onboarding → saisie URL + clé API → test `POST /connector/login` (vérif HTTPS + cert) → stockage secure + initial sync (orders, products, metrics) → paramétrage notifications/skins.
- **Thèmes/Skins** : Material 3 dynamic color (Android 12+) ou palettes fixes (Royal Violet, Indigo Neon, Teal & Tangerine, Graphite Pro, Forest Green). Stocker préférences par compte + appareil. Appliquer tokens Compose (colorScheme, shapes 16-20dp, elevations).

## 7. Guides build, qualité & release
- **Gradle** :
  ```bash
  ./gradlew clean
  ./gradlew lintDebug detekt ktlintCheck
  ./gradlew testDebugUnitTest
  ./gradlew connectedDebugAndroidTest      # besoin d’émulateur
  ./gradlew assemblePreprodDebug           # flavor preprod
  ./gradlew bundleProdRelease              # aab signé
  ```
- **Flavors** : `preprod`, `prod` (distinguer `BASE_URL`, `FCM_TOPIC`, `LOGGING_ENABLED` via `BuildConfig`). Prévoir `prod` release + `internal` pour test.
- **CI/CD** : GitHub Actions / GitLab CI (à confirmer). Jobs : lint/tests → assemble → upload artefacts → distribution Firebase App Distribution / Play Internal. Intégrer step Crashlytics (upload mapping) et Sentry si retenu.
- **Qualité** : activer Compose metrics (`enableComposeCompilerReports=true`), `Detekt`, `ktlint`, `Gradle Doctor`. Prévoir tests unitaires (usecases, repos) + tests instrumentés (flows critiques). Ajouter tests snapshot Compose si pertinent.
- **Release** : semver codeName (ex. `1.0.0`) + `versionCode` monotone. Changelog automatisé (conventional commits). Publication initiale via APK direct, passer à Play Store (track interne) ensuite.

## 8. Gestion erreurs, retries & offline
- **HTTP 401/403** : rafraîchir token via nouvel appel login → si échec, forcer logout (wipe secrets). Log audit (Crashlytics breadcrumb).
- **HTTP 429** : appliquer retry after (header), informer utilisateur (toast/snackbar) + queue WorkManager.
- **HTTP 5xx / timeout** : WorkManager retry (backoff exponentiel), badge d’état sur cartes (ex. "Sync en échec"). Stocker stack simplifiée pour support.
- **Conflits stock (409)** : recharger stock via `GET /products/{id}` et proposer override ou annulation. Historiser tentative dans Room.
- **Perte connexion** : détection via `ConnectivityManager` → bascule mode hors ligne → UI affiche bannière + lecture cache. Les actions modifiantes sont `enqueued` et marquées `pending`.
- **Logs** : `Timber` en debug, logger custom en release (Crashlytics breadcrumbs) sans PII.

## 9. Sécurité & conformité
- HTTPS obligatoire (+ check certificate pinning optionnel via OkHttp). HSTS forcé côté boutique.
- JWT courts, scopes limités (Commandes, Produits, Stocks, Dashboard). Rotation + révocation : prévoir purge des tokens sur logout, invalidation automatique à expiration.
- Stockage local : `EncryptedSharedPrefs`, `BiometricPrompt` optionnel pour déverrouillage rapide. Ne pas stocker clés en clair ni logs contenant PII.
- RGPD : masquer emails/téléphones lorsque non nécessaires (affichage partiel). Prévoir purge des caches sur logout ou via menu "Effacer données".
- Permissions : accès caméra (scan tracking) → demander runtime + explication. Notifications → `POST_NOTIFICATIONS` (Android 13+). Aucun stockage externe.
- Observabilité : Crashlytics + traces (option). Prévoir switch debug pour réseau (chucker) non inclus en release.
- Audit : aligner avec `rebuild-connector` (logs HMAC, allowlist IP). L’app doit honorer `scopes` (masquer modules non autorisés).

## 10. Checklists
- **Pré-release** :
  - Vérifier versionName/versionCode, changelog FR/EN.
  - Lancer `lint`, `detekt`, `ktlint`, tests unitaires, tests instrumentés clés.
  - Tester scénarios critiques (auth, liste commandes, MAJ tracking, MAJ stock, offline/online, notifications deep link).
  - Vérifier Crashlytics initialisation, enregistrement token FCM, traduction FR/EN complètes.
  - QA sur appareils réels (minSdk 26, Android 13+, tablette).
- **Post-release** :
  - Monitorer Crashlytics, ANR, temps synchronisation (<5s pour MAJ stock/suivi).
  - Vérifier réception notifications prod, métriques adoption (KPI CA/commandes).
  - Collecter feedback via formulaire in-app ou lien.
- **Rollback** :
  - Conserver APK/AAB précédent (track interne).
  - Révoquer tokens compromettants via module (rotation clé API).
  - Purger queues offline si cohérence rompue (migration Room).

## Annexes & références
- Cahier des charges Android PrestaFlow (source de vérité) — cf. ticket principal.
- Module PrestaShop : voir `../rebuild-connector/agent.md` pour détails API, sécurité, CI.
- Questions ouvertes (§13 cahier) : confirmer mapping numéro de suivi (`order_carriers.tracking_number` vs `orders.shipping_number`), métriques dashboard additionnelles, contraintes MDM éventuelles.
- Bibliothèques pressenties : Retrofit + Kotlinx Serialization, OkHttp Logging (debug), Hilt, Room, WorkManager, Paging 3, DataStore (settings/skins), ML Kit Barcode, MPAndroidChart ou Compose Charts, Accompanist (perfs).
- Lignes directrices UI/UX : Material 3, bottom navigation (5 tabs), top app bar avec recherche/filtres, skeleton loading, pull-to-refresh, empty states explicites, accessibilité (TalkBack, contrastes AA, touch targets 48dp, haptique léger).
