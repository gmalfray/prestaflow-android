# PrestaFlow Android

App Android (Kotlin, Jetpack Compose, Material Design 3, CI/CD, Crashlytics) pour la gestion de boutiques PrestaShop via le module **Rebuild Connector**.

## 🚀 Objectif

Gérer facilement sa boutique PrestaShop depuis un smartphone :
- Suivi des commandes, produits, stocks et clients
- Tableau de bord (CA, ventes, clients, graphiques)
- Notifications push en temps réel (nouvelles commandes)
- Ajout du numéro de suivi colis via scan caméra
- Mode hors ligne (lecture et synchro différée)

## 🧩 Stack technique

| Couche | Technologies |
|--------|---------------|
| UI | Jetpack Compose, Material 3 |
| Données | Room, Retrofit2/OkHttp3, KotlinX Serialization |
| Auth | JWT + HTTPS (module Rebuild Connector) |
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

1. Copier `google-services.json` dans `/app`
2. Créer un fichier `.env` local :
   ```bash
   API_BASE_URL=https://votreboutique.fr/module/rebuildconnector/api/
   FCM_PROJECT_ID=...
   ```
3. Lancer le build :
   ```bash
   ./gradlew assembleDebug
   ```

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
