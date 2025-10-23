# agent.md — PrestaFlow Android

## 1. Objectifs, périmètre et non-objectifs
- Objectif : proposer une application Android (minSdk 26, targetSdk 35) permettant la gestion complète d’une boutique PrestaShop ≥ 1.7 via le module **Rebuild Connector** (REST JSON) et, à terme, la nouvelle API PrestaShop 9.
- Périmètre MVP : authentification par clé API → JWT, onglets Dashboard/Commandes/Clients/Produits/Paniers (lecture), modification suivi expédition, gestion prix/stock/statut produit, gestion des stocks (`stock_availables`), historique clients, classements top clients/ventes, notifications push FCM (paramétrables), mode hors ligne (cache + file d’attente), FR/EN.
- KPI : délai de traitement commandes, fiabilité notifications, rapidité MAJ stock/suivi, visibilité sur KPI (CA, commandes, top ventes).
- Extensions prévues : thèmes « Skins » (5 palettes + Material You), analytics avancés (taux conversion, panier moyen, TVA), compatibilité PrestaShop 9, multi-boutiques, relance paniers via module dédié.
- Hors périmètre actuel : automatisations marketing, relance paniers, multi-boutiques (reportées), édition d’images avancée (post-MVP).
- État courant (avril 2025) : l’onglet **Tableau de bord** consomme l’API 1.1.2 et affiche les KPI locaux; les onglets Commandes / Produits / Clients / Paniers sont encore des placeholders Compose (écrans à livrer) et aucun écran “Paramètres” n’existe pour l’instant.
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
│      │   │   ├─ core/              # App, sécurité, helpers communs (UiText, NetworkErrorMapper)
│      │   │   ├─ data/              # Remote + Room (DAOs, mappers, repositories)
│      │   │   ├─ domain/            # Logique métier (auth, tokens, validations)
│      │   │   ├─ navigation/        # Graph Compose
│      │   │   └─ ui/                # Écrans (auth, shells) et thèmes
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
- **Module PrestaShot** : page de configuration (BO > Modules > PrestaShot) permettant de saisir la clé API et de générer un QR code encodant `{"version":1,"shopUrl":...,"apiKey":...}`. Le QR code est consommé par l’application mobile via le schéma `prestaflow://setup?data=<base64(JSON)>`; prévoir rotation manuelle de la clé en cas de révocation.
- **QR pairing côté Android** : l’écran de connexion propose une action “Scanner un QR code” (ZXing Embedded `com.journeyapps:zxing-android-embedded:4.3.0`). Le payload est décodé, normalisé (HTTPS obligatoire) puis pré-remplit URL et clé API. En cas d’annulation ou d’échec, un message localisé est affiché.
- **CI/CD** : secrets `PLAY_STORE_JSON`, `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`, `FIREBASE_APP_ID`, `SENTRY_DSN?`, `CRASHLYTICS_TOKEN`.
- **Monitoring** : Crashlytics + traces réseau optionnelles (use HTTP logging interceptors conditionnés `BuildConfig.DEBUG`).

## 4. Endpoints REST Rebuild Connector (principal) & compatibilité
Base API : `https://<boutique>/module/rebuildconnector/api` (module ≥ **1.1.2**). Les réponses sont plates : pas de clé `data`, mais des objets ou tableaux nommés (`orders`, `products`, `customers`, …).

| Méthode | Endpoint | Usage App | Notes |
|---------|----------|-----------|-------|
| POST | `/connector/login` | Auth via clé API → JWT court | Réponse `{ "token": "...", "access_token": "...", "expires_in": 3600, "scopes": [] }`. |
| GET | `/orders` | Liste paginée des commandes | Query `limit`, `offset`, `status`, `search`, `date_from`, `date_to`. Réponse `{ "orders": [ { "id", "reference", "status", "total_paid", "currency", "date_upd", "customer": { "firstname", "lastname" } } ] }`. |
| GET | `/orders/{id}` | Détail commande | Réponse `{ "order": { ... } }` (structure historique complète inchangée). |
| PATCH | `/orders/{id}/status` | Changer l’état | Corps `{ "status": "<name|id>" }`. |
| PATCH | `/orders/{id}/shipping` | Mettre à jour tracking | Corps `{ "tracking_number": "...", "carrier_id": 3? }`. |
| GET | `/products` | Catalogue produits | Réponse `{ "products": [ { "id", "name", "reference", "price", "active", "stock": { "quantity", "warehouse_id", "updated_at" }, "images": [{ "id", "url" }], "updated_at" } ] }`. |
| GET | `/products/{id}` | Détail produit | Même schéma via `{ "product": { ... } }`. |
| PATCH | `/products/{id}` | MAJ prix/statut | Champs pris en charge : `price_tax_excl`, `active`. |
| PATCH | `/products/{id}/stock` | MAJ stock disponible | Corps `{ "quantity": 42, "warehouse_id": null }`. |
| GET | `/customers` | Liste clients + stats commandes | Réponse `{ "customers": [ { "id", "firstname", "lastname", "email", "orders_count", "total_spent", "last_order_at" } ], "pagination": {...} }`. |
| GET | `/customers/{id}` | Détail client | Réponse `{ "customer": { ... , "orders": [...] } }`. |
| GET | `/dashboard/metrics?period=month` | KPI Dashboard | Réponse `{ "turnover", "orders_count", "customers_count", "products_count", "currency", "chart": [{ "label", "turnover", "orders", "customers" }] }`. |
| GET | `/reports?resource=bestsellers` | Top ventes | Réponse `{ "products": [ { "product_id", "quantity", "total_tax_incl" … } ] }`. |
| GET | `/reports?resource=bestcustomers` | Top clients | Réponse `{ "customers": [ { "id", "firstname", "lastname", "total_spent", "last_order_at" } ] }`. |
| GET | `/customers/top` | Alias used par l’app pour le widget “Meilleurs clients” | Réponse identique à `resource=bestcustomers`. |
| GET | `/baskets` | (future) liste paniers | Réponse `{ "baskets": [...] }` – endpoints disponibles côté module mais UI encore non implémentée. |

Fallback legacy (`mobassistantconnector`) conservé comme plan B, mais désactivé par défaut. L’app détecte la compatibilité en appelant `/connector/login`; si 404 ou 410, afficher un message orientant vers la mise à jour du module.

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
- `OrderListDto` : `orders: List<OrderDto>`.
- `OrderDto` : `id`, `reference`, `status`, `total_paid`, `currency`, `dateUpdated`, `customer: OrderCustomerDto`.
- `OrderCustomerDto` : `firstName`, `lastName`.
- `OrderDetailDto` (via `/orders/{id}`) : même structure que legacy (produits, history, shipping). Mapper à enrichir lors de l’implémentation de l’écran détail.
- `ProductListResponseDto` : `products: List<ProductDto>`, `pagination`.
- `ProductDto` : `id`, `name`, `reference`, `price`, `active`, `stock: StockDto`, `images: List<ImageDto>`, `updatedAt`.
- `StockDto` : `quantity`, `warehouseId`, `updatedAt`.
- `CustomerListResponseDto` : `customers`, `pagination`.
- `CustomerDto` : `id`, `firstName`, `lastName`, `email`, `ordersCount`, `totalSpent`, `lastOrderAt`.
- `DashboardMetricsDto` : `turnover`, `ordersCount`, `customersCount`, `productsCount`, `chart: List<ChartPointDto>`, `period`.
- `ChartPointDto` : `label`, `orders`, `customers`, `turnover`.
- `Reports` : `Best sellers` réutilise `ProductsService::getBestSellers` → DTO maison (mapper à ajouter côté app), `Best customers` → `CustomerDto`.
- `AuthResponseDto` : `token`, `expiresIn`, `scopes` (l’app lit `access_token` aliasé côté module).
- `StockUpdateRequestDto` : `quantity`, `warehouseId`, `reason?`.
- `DeviceRegistrationRequestDto` : `token`, `topics`, `deviceId`, `platform`.

Mapper couche `domain` pour convertir en modèles UI (ex. `Order`, `OrderSummary`, `DashboardCard`).

## 6. Flux fonctionnels & techniques
- **Notification → détail commande** : réception FCM (`order.created` / `order.status.changed`) → deep link `prestaflow://orders/{id}` → récupération `GET /orders/{id}` → possibilité de changer état ou ajouter tracking → `PATCH /orders/{id}/status` ou `/shipping` → confirmation + options de notification vers client (si module active).
- **Scan suivi expédition** : écran action rapide → Intent caméra (ML Kit / ZXing) → parsing du code → préremplissage du champ tracking → validation → `PATCH /orders/{id}/shipping` → si max 5s sans réponse, indiquer statut pending + push local snooze.
- **Gestion produit** : recherche → sélection produit → édition prix/stock/actif → enregistrement local en pending → tentative `PATCH /products/{id}` ou `/stock` → si offline, en file d’attente → UI affiche badge "En attente de synchro".
- **Mode hors ligne** : bootstrap → charger cache Room (orders, products, metrics) → afficher timestamp de fraîcheur → file d’attente Replay sur reconnexion (WorkManager, backoff expo 5/30/120s). Conflit stock : si 409, recharger état distant et proposer merge.
- **Connexion initiale** : écran onboarding → saisie URL + clé API → test `POST /connector/login` (vérif HTTPS + cert) → stockage secure (`TokenManager` + EncryptedSharedPreferences) + initial sync (orders, products, metrics) → paramétrage notifications/skins.
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
- Mapper d’erreurs réseau : `NetworkErrorMapper` traduit les exceptions (IO/HTTP) en `UiText` localisés pour l’UI (snackbar / dialogues).
- File d’attente offline : `pending_sync` (Room) + Worker pour rejouer les opérations (implémentation à compléter dans Phase 4).
- Mapper d’erreurs réseau : `NetworkErrorMapper` traduit les exceptions (IO/HTTP) en `UiText` localisés pour l’UI (snackbar / dialogues).
- **Logs** : `Timber` en debug, logger custom en release (Crashlytics breadcrumbs) sans PII.

## 9. Sécurité & conformité
- HTTPS obligatoire (+ check certificate pinning optionnel via OkHttp). HSTS forcé côté boutique.
- JWT courts, scopes limités (Commandes, Produits, Stocks, Dashboard). Rotation + révocation : prévoir purge des tokens sur logout, invalidation automatique à expiration.
- Stockage local : `EncryptedSharedPrefs`, `BiometricPrompt` optionnel pour déverrouillage rapide. Ne pas stocker clés en clair ni logs contenant PII.
- Stockage local : `EncryptedSharedPrefs` via `TokenManager` + `TokenStorage` (InMemory + disque). Purger dès logout ou expiration.
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

## 11. Dépannage build Kotlin
- Erreur `e: Could not load module <Error module>` : signal d’un jar Kotlin incompatible ou corrompu sur le classpath (souvent metadata générée par un compilateur plus récent).
- Vérifications :
  1. Aligner versions Kotlin/Compose dans `gradle/libs.versions.toml` et `gradle.properties` (ex : Kotlin 1.9.23 + Compose compiler 1.5.13).
  2. Supprimer les caches ciblés `~/.gradle/caches/modules-2/files-2.1/<groupe>/<artifact>/<version>` pour les libs retrouvées dans les logs Kotlin (`/tmp/kotlin-daemon.*.log`), puis relancer `./gradlew clean assembleDebug`.
  3. Lancer `./gradlew app:dependencyInsight --configuration <variant>CompileClasspath --dependency <lib>` pour isoler l’artifact fautif, rétrograder ou mettre à jour la dépendance.
  4. Si besoin, désactiver temporairement des libs expérimentales (alpha/bêta) et réintroduire une à une pour identifier la source.
- Conserver les logs de daemon (`/tmp/kotlin-daemon.*.log`) pour toute remontée à JetBrains si l’erreur persiste après nettoyage.

## Annexes & références
- Cahier des charges Android PrestaFlow (source de vérité) — cf. ticket principal.
- Module PrestaShop : voir `../rebuild-connector/agent.md` pour détails API, sécurité, CI.
- Questions ouvertes (§13 cahier) : confirmer mapping numéro de suivi (`order_carriers.tracking_number` vs `orders.shipping_number`), métriques dashboard additionnelles, contraintes MDM éventuelles.
- Bibliothèques pressenties : Retrofit + Kotlinx Serialization, OkHttp Logging (debug), Hilt, Room, WorkManager, Paging 3, DataStore (settings/skins), ML Kit Barcode, MPAndroidChart ou Compose Charts, Accompanist (perfs).
- Lignes directrices UI/UX : Material 3, bottom navigation (5 tabs), top app bar avec recherche/filtres, skeleton loading, pull-to-refresh, empty states explicites, accessibilité (TalkBack, contrastes AA, touch targets 48dp, haptique léger).
