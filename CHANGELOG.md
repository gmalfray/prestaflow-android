# Changelog PrestaFlow Android

Les versions suivent [Semantic Versioning](https://semver.org/) : `MAJEUR.MINEUR.CORRECTIF`.

---

## [0.43.0] - 2026-08-09

### Ajouts
- Alerte push quand le paiement tombe en panne. Nouvelle catégorie de notification
  (`shop.payment.error`), activée par défaut, avec son canal Android dédié (`payment_error`,
  importance haute, vibration en double pulsation) et son propre son : deux notes
  descendantes, volontairement à l'opposé de la caisse enregistreuse des ventes, qui
  signifierait l'inverse du message. Son généré par `tools/sounds/make_payment_alert.py`,
  versionné pour rester reproductible.
  L'événement vient de la surveillance côté serveur (`watch-payments.sh` du dépôt
  `pensebonheur`), pas du connecteur : le module ne voit pas les échecs du prestataire de
  paiement, seul son journal les connaît.
  Né de la panne du 07→09/08 sur pensebonheur.fr, où le tunnel de paiement est resté bloqué
  40 h sans que rien ne remonte.

---

## [0.42.0] - 2026-07-06

### Ajouts
- Alertes de stock faible en push. Nouvelle catégorie de notification `Stock faible`
  (`stock.low`), activée par défaut, avec son canal Android (`stock_low`, importance par
  défaut, sans son de caisse) et son entrée dans l'écran des préférences de notifications.
  Le titre et le corps du push (déjà localisés) viennent du connecteur ; l'app route
  l'affichage et le tap vers le canal et le deep link correspondants.
- Deep link fiche produit : `prestaflow://products/{productId}` est désormais reconnu
  (NavGraph + manifeste), au même titre que `prestaflow://orders/{orderId}`. Un tap sur une
  notification de stock faible ouvre directement la fiche du produit concerné.

---

## [0.40.0] - 2026-07-04

### Ajouts
- Internationalisation (i18n). L'app s'affiche désormais dans la langue du téléphone,
  détectée automatiquement par Android (aucun sélecteur de langue en app). Traductions
  complètes ajoutées pour l'anglais, l'espagnol, l'allemand, l'italien, le portugais et le
  néerlandais (`values-en/`, `values-es/`, `values-de/`, `values-it/`, `values-pt/`,
  `values-nl/`), en plus du français qui reste la langue par défaut (`values/`). Le nom
  « PrestaFlow » n'est jamais traduit.

### Corrections
- Devise forcée en euros quelle que soit la langue de l'appareil. Les montants du
  dashboard, des listes produits et clients utilisaient `NumberFormat.getCurrencyInstance()`
  sans devise explicite : sur un téléphone réglé sur une locale hors zone euro (ex. anglais
  US), ils s'affichaient en dollars au lieu d'euros. La devise est désormais figée en EUR
  (nouveau helper `euroCurrencyFormatter()`), en gardant le formatage (séparateurs, position
  du symbole) de la locale de l'appareil.

### Interne
- Un test `LocalizationParityTest` vérifie, pour les 6 langues cibles, que toutes les clés du
  défaut FR sont traduites (0 `MissingTranslation`) et que les placeholders (`%1$s`, `%1$d`…)
  sont préservés à l'identique.

---

## [0.38.0] - 2026-07-04

### Ajouts
- Lecture d'étiquette (OCR) en secours du réappro stock. Quand un code-barres scanné est
  introuvable, l'app lit désormais le texte de l'étiquette (elle réutilise la frame caméra
  ayant servi au décodage, ML Kit Text Recognition on-device, modèle bundled, donc hors-ligne
  et sans aucun appel cloud) pour en extraire une référence probable, cherche un produit
  correspondant, et pré-remplit l'écran d'association existant avec les candidats trouvés.
  Tentative best-effort avec timeout dur (~1,3 s) : au-delà, ou si rien n'est lisible ou
  trouvé, l'app retombe silencieusement sur l'association manuelle habituelle, sans latence
  perçue.

---

## [0.3.1] - 2026-06-26

### Corrections
- Filtrage par période : "Aujourd'hui" restait vide et le comptage KPI ne collait pas à la
  liste. `date_to` envoyé sans suffixe horaire (`"2024-01-15"`) était interprété par MySQL
  comme `"2024-01-15 00:00:00"`, ce qui excluait toutes les commandes passées après minuit.
  Résultat : 0 commandes pour "Aujourd'hui", et un écart entre le KPI Dashboard (qui applique
  `23:59:59` côté serveur) et la liste filtrée. Correction : `date_to` est désormais envoyé au
  format `"Y-m-d 23:59:59"`.
- Loader infini sur liste filtrée vide. Quand un filtre de période ne retournait aucune
  commande, `isLoading` était remis à `true` dans le bloc `onSuccess` (`current.orders.isEmpty()`)
  après que Room avait déjà émis `[]` et positionné `isLoading = false`. L'écran restait bloqué
  sur le spinner indéfiniment. Correction : `isLoading = false` inconditionnellement après succès
  ou échec réseau.

---

## [0.3.0] - 2026-06-26

### Ajouts
- Notifications push vers le détail commande : taper une notification "nouvelle commande" (ou
  changement de statut, ou expédition) ouvre directement l'écran de détail de la commande
  concernée. L'app au premier plan passe par le deep link URI `prestaflow://orders/{id}` via
  `ContentIntent`, l'app en arrière-plan par les extras FCM du système. Le bouton Retour ramène
  au Dashboard (back stack propre).

### Corrections
- Retour au Dashboard impossible : après avoir navigué vers les Commandes via une carte KPI du
  Dashboard (ex. "Commandes du jour"), l'item Dashboard de la barre de navigation ne répondait
  plus. Corrigé avec `popBackStack(route = "dashboard")` au lieu de `navigate` avec
  `launchSingleTop`, qui se comportait comme un no-op puisque la destination de départ était déjà
  au sommet du back stack.

---

## [0.2.1] - 2026-06-19

### Corrections
- Barre d'actions de sélection en masse : remplacement des libellés textuels par des icônes pour éviter
  le débordement sur petits écrans.
- Navigation commandes : correction du matching de route paramétré (filtres période) et de l'état vide
  affiché lors d'un filtre sans résultat.

---

## [0.2.0] - 2026-06-14

### Ajouts
- Dashboard : cartes KPI Commandes et Clients cliquables (navigation directe filtrée) ; courbe comparatif
  période précédente.
- Commandes : sélecteur de statut sur le détail, mise à jour en lot, dialogue de statuts scrollable.
- Impression du bordereau de transport (endpoint `/api/orders/{id}/shipping-label`).
- Nouvelle icône adaptative Terracotta Flux (icône monochrome notification).

---

## [0.1.21] - 2026-05-xx

### Corrections
- Ré-enregistrement automatique du token FCM au démarrage (relai hub push `push.rebuild-it.fr`).
