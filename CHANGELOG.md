# Changelog PrestaFlow Android

Les versions suivent [Semantic Versioning](https://semver.org/) : `MAJEUR.MINEUR.CORRECTIF`.

---

## [0.42.0] — 2026-07-06

### Ajouts
- **Alertes stock faible en push** : nouvelle catégorie de notification `Stock faible`
  (`stock.low`), activée par défaut, avec son propre canal Android (`stock_low`, importance
  par défaut, sans son caisse) et sa propre entrée dans l'écran des préférences de
  notifications. Le titre et le corps du push (déjà localisés) proviennent du connecteur ;
  l'app se contente de router l'affichage et le tap sur le canal/deep-link adéquat.
- **Deep link fiche produit** : `prestaflow://products/{productId}` est désormais reconnu
  (NavGraph + manifeste), au même titre que `prestaflow://orders/{orderId}`. Un tap sur une
  notification de stock faible ouvre directement la fiche du produit concerné.

---

## [0.40.0] — 2026-07-04

### Ajouts
- **Internationalisation (i18n)** : l'app s'affiche désormais dans la langue du téléphone,
  détectée automatiquement par Android (aucun sélecteur de langue en app). Traductions
  complètes ajoutées pour l'anglais, l'espagnol, l'allemand, l'italien, le portugais et le
  néerlandais (`values-en/`, `values-es/`, `values-de/`, `values-it/`, `values-pt/`,
  `values-nl/`), en plus du français qui reste la langue par défaut (`values/`). Le nom
  « PrestaFlow » n'est jamais traduit.

### Corrections
- **Devise forcée en euros indépendamment de la langue de l'appareil** : les montants du
  dashboard, des listes produits et clients utilisaient `NumberFormat.getCurrencyInstance()`
  sans devise explicite — sur un téléphone réglé sur une locale hors zone euro (ex. anglais
  US), les montants s'affichaient en dollars au lieu d'euros. La devise est désormais figée en
  EUR (nouveau helper `euroCurrencyFormatter()`), tout en conservant le formatage (séparateurs,
  position du symbole) de la locale de l'appareil.

### Interne
- Un test `LocalizationParityTest` vérifie désormais, pour les 6 langues cibles, que toutes les
  clés du défaut FR sont traduites (0 `MissingTranslation`) et que les placeholders (`%1$s`,
  `%1$d`…) sont préservés à l'identique.

---

## [0.38.0] — 2026-07-04

### Ajouts
- **Lecture d'étiquette (OCR) en secours du réappro stock** : quand un code-barres scanné est
  introuvable, l'app tente désormais de lire le texte de l'étiquette (réutilise la frame caméra
  ayant servi au décodage, ML Kit Text Recognition on-device, modèle bundled — hors-ligne, aucun
  appel cloud) pour en extraire une référence probable, cherche un produit correspondant, et
  propose les candidats trouvés en pré-remplissant l'écran d'association existant. Tentative
  best-effort avec timeout dur (~1,3 s) : au delà, ou si rien n'est lisible/trouvé, retombe
  silencieusement sur l'association manuelle habituelle — jamais de latence ajoutée perçue.

---

## [0.3.1] — 2026-06-26

### Corrections
- **Filtrage par période : "Aujourd'hui" vide et écart de comptage KPI↔liste** : `date_to` envoyé
  sans suffixe horaire (`"2024-01-15"`) était interprété par MySQL comme `"2024-01-15 00:00:00"`,
  excluant toutes les commandes passées après minuit. Résultat : 0 commandes pour "Aujourd'hui",
  et un écart entre le KPI Dashboard (qui applique `23:59:59` côté serveur) et la liste filtrée.
  Correction : `date_to` est désormais envoyé au format `"Y-m-d 23:59:59"`.
- **Loader infini sur liste filtrée vide** : quand un filtre de période ne retourne aucune
  commande, `isLoading` était remis à `true` dans le bloc `onSuccess` (`current.orders.isEmpty()`)
  après que Room avait déjà émis `[]` et positionné `isLoading = false`. L'écran restait bloqué
  sur le spinner indéfiniment. Correction : `isLoading = false` inconditionnellement après succès
  ou échec réseau.

---

## [0.3.0] — 2026-06-26

### Ajouts
- **Notifications push → détail commande** : taper une notification "nouvelle commande" (ou changement de
  statut / expédition) ouvre directement l'écran de détail de la commande concernée. Gère l'app au premier
  plan (deep link URI `prestaflow://orders/{id}` via `ContentIntent`) et en arrière-plan (extras FCM du
  système). Le bouton Retour ramène au Dashboard (back stack propre).

### Corrections
- **Retour au Dashboard impossible** : après avoir navigué vers les Commandes via une carte KPI du Dashboard
  (ex. "Commandes du jour"), l'item Dashboard de la barre de navigation ne répondait plus. Corrigé en
  utilisant `popBackStack(route = "dashboard")` au lieu de `navigate` avec `launchSingleTop` (qui se
  comportait comme un no-op en tant que destination de départ déjà au sommet du back stack).

---

## [0.2.1] — 2026-06-19

### Corrections
- Barre d'actions de sélection en masse : remplacement des libellés textuels par des icônes pour éviter
  le débordement sur petits écrans.
- Navigation commandes : correction du matching de route paramétré (filtres période) et de l'état vide
  affiché lors d'un filtre sans résultat.

---

## [0.2.0] — 2026-06-14

### Ajouts
- Dashboard : cartes KPI Commandes et Clients cliquables (navigation directe filtrée) ; courbe comparatif
  période précédente.
- Commandes : sélecteur de statut sur le détail, mise à jour en lot, dialogue de statuts scrollable.
- Impression du bordereau de transport (endpoint `/api/orders/{id}/shipping-label`).
- Nouvelle icône adaptative Terracotta Flux (icône monochrome notification).

---

## [0.1.21] — 2026-05-xx

### Corrections
- Ré-enregistrement automatique du token FCM au démarrage (relai hub push `push.rebuild-it.fr`).
