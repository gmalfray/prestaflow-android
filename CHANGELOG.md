# Changelog PrestaFlow Android

Les versions suivent [Semantic Versioning](https://semver.org/) : `MAJEUR.MINEUR.CORRECTIF`.

---

## [0.45.0] - 2026-08-12

### Ajouts
- Notification quand une cliente écrit sur un fil SAV. Importance haute, son dédié de deux notes
  ascendantes, volontairement à l'opposé du motif descendant et répété de l'alerte paiement, et
  distinct de la caisse enregistreuse qui signifierait une vente. Activée par défaut. Un tap ouvre
  le fil concerné. Son généré par `tools/sounds/make_sav_message_alert.py`, versionné pour rester
  reproductible.
- Notification quand un avis entre en file de modération. Importance normale, son système,
  désactivée par défaut pour rester cohérente avec le réglage de la boutique. Un tap ouvre la file
  de modération, faute d'écran de détail par avis.

Le module émettait déjà ces deux événements et le hub les routait, mais l'app ne connaissait ni les
catégories ni les canaux : elle ne s'abonnait qu'à cinq sujets, et rien ne pouvait arriver.

Le son et l'importance d'un canal Android sont figés à sa création. En changer plus tard demandera
un nouveau canal sous un autre identifiant.

### Interne
- Les catégories de notification peuvent avoir un défaut d'activation propre, là où elles étaient
  toutes activées de la même façon.

---

## [0.44.1] - 2026-08-12

### Corrections
- Dans la liste SAV, le point rouge marquait toutes les lignes. Elle s'appuyait sur `unread`, le
  drapeau « non lu » de PrestaShop, qui n'est posé que lorsqu'un employé ouvre le fil dans la vue
  back-office : une boutique qui traite son SAV par mail ne le pose jamais. En production, 449 fils
  sur 481 le portent, fils clos et déjà répondus compris. Les lignes utilisent maintenant
  `to_process`, la même notion que le compteur de l'onglet, donc le nombre annoncé correspond aux
  lignes marquées.

---

## [0.44.0] - 2026-08-12

### Ajouts
- SAV et Avis dans l'onglet Clients. Les fils de service après-vente et la modération des avis
  se traitent depuis l'app : liste, détail, réponse, changement de statut, publication et mise
  à la corbeille avec motif. Les deux sections n'apparaissent que si la boutique en est capable
  et si le jeton porte la portée correspondante : une fonction absente ou interdite ne laisse
  aucune trace dans l'interface, pas même une entrée désactivée.
- Pastille sur l'onglet Commandes, pour les commandes arrivées depuis le dernier passage sur la
  liste, mémorisée par boutique. Consulter la liste suffit à l'effacer, sans avoir à ouvrir
  chaque commande. Ouvrir une commande depuis une notification l'efface aussi, mais pour elle
  seule : les autres restent au compteur.
- Réapprovisionnement par journal de session. Les scans alimentent un journal que rien n'écrit
  avant la validation définitive : chaque ligne s'annule quand on veut, et deux scans du même
  produit s'additionnent. Le journal survit à la fermeture de l'écran et à l'arrêt de l'app.
  L'écriture finale part en incréments, ce qui préserve les ventes survenues pendant la session.
  Demande le connecteur 1.21.0.

### Corrections
- Le compteur SAV annonçait 88 alors que rien n'attendait de réponse. Il comptait les fils
  « non lus » au sens de PrestaShop, drapeau jamais posé quand le SAV est traité par mail. Il
  compte désormais ce qui attend vraiment une réponse : fil non clos, dernier message venant de
  la cliente, activité récente. Sur les mêmes données, 2 au lieu de 88.
- Le réapprovisionnement perdait un carton sur deux quand on rescannait le même produit avant
  la fin du délai d'écriture.
- Les listes ne se rechargeaient qu'à la création de leur écran, lequel survit aux changements
  d'onglet : il fallait tirer pour rafraîchir après chaque aller-retour. Commandes, Clients,
  Produits, Paniers, SAV et Avis se rechargent maintenant au retour, au plus une fois par minute.
- La pastille d'un sous-onglet recouvrait son propre libellé.
- Le tableau de bord portait un bouton de rafraîchissement faisant doublon avec le tirer-pour-
  rafraîchir.
- La fenêtre de scan des codes-barres gagne 20 % de hauteur.

### Divers
- Documentation et textes de l'app repassés au skill humanizer, pour retirer le style LLM.

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
