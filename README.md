# PrestaFlow Android

App Android (Kotlin, Jetpack Compose, Material Design 3, CI/CD, Crashlytics) pour la gestion de boutiques PrestaShop via le module **Rebuild Connector**.

## 🚀 Objectif

Gérer facilement sa boutique PrestaShop depuis un smartphone :
- Suivi des commandes, produits, stocks et clients (avec filtres : statut, stock, recherche)
- Tableau de bord (CA, ventes, clients, graphiques)
- **Multi-boutiques** : plusieurs boutiques connectées, bascule rapide
- **Notifications push par catégorie**, réglables **par appareil** (ventes / statuts / expéditions)
- **Impression des factures** (PDF officiel, montage 2 par page A4 paysage) et bordereaux d’expédition
- Ajout du numéro de suivi colis via scan caméra
- Onboarding guidé si le module n’est pas encore installé sur la boutique
- Mode hors ligne (lecture et synchro différée)
- Connexion sécurisée via QR / URL HTTPS + clé API dédiée (stockage chiffré, re-login transparent)
- Cache local Room pour commandes/produits avec file d’attente des synchros

## 🧩 Stack technique

| Couche | Technologies |
|--------|---------------|
| UI | Jetpack Compose, Material 3 |
| Données | Room, Retrofit2/OkHttp3, KotlinX Serialization |
| Auth | JWT + HTTPS (module Rebuild Connector), EncryptedSharedPreferences |
| Notif | Firebase Cloud Messaging (HTTP v1) |
| CI/CD | GitHub Actions (build, tests, artefacts) |
| Qualité | Detekt, Lint, Unit/UI tests, Crashlytics |

## 📁 Structure

```
app/
 ├─ src/main/java/com/rebuildit/prestaflow/
 │   ├─ ui/         # Écrans et thèmes
 │   ├─ data/       # Repositories, API, cache Room
 │   └─ domain/     # Use cases / business logic
 ├─ res/            # Layouts, couleurs, icônes
 └─ build.gradle
```

## ⚙️ Configuration

1. Générer le wrapper Gradle (une fois) :
   ```bash
   gradle wrapper --gradle-version 8.7
   ```
   > À défaut, installez Gradle localement puis exécutez `./gradlew tasks` pour vérifier la configuration.
2. Copier `google-services.json` dans `app/` (non versionné).
3. Créer un fichier de configuration locale (ex. `~/.gradle/gradle.properties`) à partir de `env.sample` :
   ```properties
   REBUILDCONNECTOR_API_BASE_URL=https://preprod.example.com/module/rebuildconnector/api/
   REBUILDCONNECTOR_SHOP_URL=https://preprod.example.com/
   REBUILDCONNECTOR_API_KEY=...
   FCM_PROJECT_ID=...
   ```
4. Lancer un premier build :
   ```bash
   ./gradlew assemblePreprodDebug
   ```

### Variantes & saveurs

- `preprodDebug` : environnement préproduction, logs & outils actifs.
- `prodRelease` : build production (minify à activer une fois les règles ProGuard stabilisées).
- `ENVIRONMENT_NAME` et `API_BASE_URL` sont exposés dans `BuildConfig` par flavor.

### Qualité & automatisation

- `./gradlew ktlintCheck ktlintFormat` pour le formatage Kotlin.
- `./gradlew detekt` pour l’analyse statique (`config/detekt/detekt.yml`).
- `./gradlew lintDebug` et `./gradlew testDebugUnitTest` pour la qualité Android.
- `./gradlew testProdDebugUnitTest` pour exécuter les tests unitaires (flavour prod).

## 🧠 Roadmap

- ✅ MVP : Commandes, Produits, Clients, Dashboard, Notifs
- ✅ v1 : filtres, multi-boutiques, notifications par catégorie, impression factures, onboarding
- 🕓 Suite : statistiques avancées, édition images produits, relance paniers

## 🧑‍💻 Développement

```bash
git clone git@github.com:gmalfray/prestaflow-android.git
cd prestaflow-android
./gradlew assembleDebug
```

## 🪪 Licence

**GNU General Public License v3.0 (GPLv3)** — voir [`LICENSE`](LICENSE).  
© 2026 Rebuild IT.

GPLv3 est une licence *copyleft* : toute version modifiée **distribuée** doit l’être sous GPLv3 avec son code
source. Le module serveur **Rebuild Connector** est distribué séparément sous **OSL-3.0** ; l’app et le module
communiquent uniquement par API REST (aucune liaison de code).
