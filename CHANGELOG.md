# Changelog PrestaFlow Android

Les versions suivent [Semantic Versioning](https://semver.org/) : `MAJEUR.MINEUR.CORRECTIF`.

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
