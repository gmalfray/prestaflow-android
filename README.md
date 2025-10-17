# PrestaFlow Android

App Android (Kotlin, Jetpack Compose, Material Design 3, CI/CD, Crashlytics) pour la gestion de boutiques PrestaShop via le module **Rebuild Connector**.

## 🚀 Objectif

Gérer facilement sa boutique PrestaShop depuis un smartphone :
- Suivi des commandes, produits, stocks et clients
- Tableau de bord (CA, ventes, clients, graphiques)
- Notifications push en temps réel (nouvelles commandes)
- Ajout du numéro de suivi colis via scan caméra
- Mode hors ligne (lecture et synchro différée)
- Connexion sécurisée via URL HTTPS + clé API dédiée (stockage chiffré)
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
- `./gradlew testPreprodDebugUnitTest` pour exécuter les tests unitaires du flavour préproduction.
- `./gradlew testPreprodDebugUnitTest` pour exécuter les tests unitaires du flavour préproduction.

## 🧠 Roadmap

- ✅ MVP : Commandes, Produits, Clients, Dashboard, Notifs
- 🚧 v1 : Statistiques avancées, filtres, multi-boutiques
- 🕓 v1.1 : Édition images produits, relance paniers

## 🧑‍💻 Développement

```bash
git clone git@github.com:gmalfray/prestaflow-android.git
cd prestaflow-android
./gradlew assembleDebug
```

## 🪪 Licence

Apache License 2.0 – © Rebuild IT, 2025.
