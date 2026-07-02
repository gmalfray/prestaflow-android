# PrestaFlow — Roadmap / backlog fonctionnel

> Mis à jour : 2026-07-02. Priorisation décidée par Greg.
>
> **Principe directeur (1er temps) : d'abord ce qui est UTILE À PENSEBONHEUR** (la boutique réellement
> exploitée : kits crochet, laine, patrons), pas la parité avec les apps concurrentes. Les priorités
> P1→P4 sont retenues pour leur utilité quotidienne à la boutique — **P1 (produits) + P4 (combinaisons
> coloris/tailles) = le cœur catalogue pensebonheur** ; P3 (bons de réduction) sert les promos récurrentes
> (ex. −20 %) ; P2 (SAV) le support client. Le backlog (rôles multi-employés, RMA, multi-langue…) relève
> surtout de la **distribution à d'autres boutiques**, à traiter plus tard.
>
> Analyse d'écart source : apps concurrentes PrestaShop (FME Admin App, WebKul, Knowband, PrestaShop Mobile Admin).

## ✅ Déjà couvert (v0.26.0)
Dashboard (KPI + graphe double-axe + périodes + plage custom + plein écran + tendances/sparklines) ·
Commandes (liste paginée, filtre statut = chips sélection unique + menu multi-statuts, recherche, tri,
détail, changement de statut simple/bulk/swipe, n° de suivi, **étiquette Colissimo + impression
facture/bordereau thermique TSPL**) · Produits (liste + recherche + filtre stock, édition prix/stock/actif) ·
Clients (liste, détail + historique, top clients) · Paniers abandonnés · Multi-boutiques · **Push** commandes ·
Auth clé API→JWT + appairage QR.

## 🎯 Priorités (prochains chantiers, dans l'ordre)

### P1 — Fiche produit complète (création + édition)
- Éditer **tous les champs** : nom, description, prix, référence, stock, actif, **catégories**, **images
  (upload depuis appareil photo/galerie + suppression)**, backorders.
- **Création** d'un produit from scratch.
- **Gestion des catégories** (ajout/édition) — à terme.
- Volet **connecteur** : `POST /products`, `PATCH /products/{id}` étendu, upload images (multipart),
  CRUD catégories. Volet **app** : écran formulaire, picker images, sélecteur de catégories.
- Branche cible : `feat/product-edit`.

### P2 — Service client / messagerie SAV
- Répondre aux **messages clients / fils de discussion** (customer threads) liés aux commandes, depuis l'app.
- Le gap le plus cité par les apps concurrentes ; forte valeur marchand.
- Connecteur : endpoints threads/messages (liste, détail, réponse) ; app : écran conversations.

### P3 — Bons de réduction / cart rules
- Créer un **code promo** et l'appliquer à un client / une commande.
- Connecteur : CRUD cart rules ; app : formulaire de création + application.

### P4 — Combinaisons / déclinaisons produit
- Gérer les **combinaisons** (attributs tailles/coloris) : lister, créer/éditer, prix/stock par déclinaison.
- Particulièrement pertinent pour pensebonheur (laine/crochet : coloris, tailles).
- Naturellement lié à **P1** (fiche produit) : peut être livré dans la foulée du chantier Produits.
- Connecteur : endpoints combinaisons (attributs/valeurs, `product_attribute`) ; app : section déclinaisons dans la fiche produit.

## 🟠 Backlog (non priorisé — à ordonnancer plus tard)
- **Retours (RMA) + avoirs / remboursements** : approuver un retour, générer un avoir / rembourser depuis une commande.
- **Multi-langue de l'app** (sélecteur de langue in-app via per-app locales + langues additionnelles au-delà de FR/EN).
- **Création de commande manuelle** (comptoir/téléphone).
- **Création / édition client + adresses.**
- **Rôles / permissions multi-employés** (accès restreint ; on est mono-clé).
- **Alertes stock bas + réappro** (push sous seuil).
- **Scan code-barres produit** (retrouver un produit / ajuster le stock ; étendre le scan QR/DataMatrix existant au catalogue).
- **Mouvements de stock avancés** (entrées/sorties motivées, multi-entrepôt, commandes fournisseurs).
- **Analytics avancés** (meilleures ventes, taux de conversion, perf par catégorie sur le dashboard).
- **Modération des avis** (revws) · **widget écran d'accueil** (KPI/nouvelles commandes) · **file d'attente hors-ligne** (écritures en cache).
