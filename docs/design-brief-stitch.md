# PrestaFlow — Brief de design (Google Stitch)

Brief pour régénérer le design des écrans de l'app Android **PrestaFlow** via
Google Stitch (MCP `stitch` configuré). Outil métier pour marchand PrestaShop
(boutique **pensebonheur.fr**, mercerie / loisirs créatifs).

## Charte (à injecter dans le thème Stitch)
- **Primaire / marque** : `#C99587` (terracotta rose poudré)
- **Accent surface** : `#D1C6BD` (beige chaud)
- **Fond app** : `#FAF7F4` (blanc cassé chaud) · **Cartes** : `#FFFFFF`
- **Succès** `#4CBB6C` · **Danger** `#FF4C4C` · **Alerte/Highlight** `#FF9A52`
- **Texte** `#2B2B2B` · **Texte atténué** `#7A746E`
- **Police** : Plus Jakarta Sans (ou Inter). Gros chiffres gras pour les KPI.
- Material 3 (Material You), Android, portrait, light d'abord + variante dark.
- Style : épuré, aéré, cartes arrondies (20dp), ombres douces, chaleureux mais pro.
- **Tous les textes UI en FRANÇAIS.**

> Côté MCP Stitch, `DesignTheme` accepte des couleurs hex (primary, etc.), un
> `bodyFont` (enum : `PLUS_JAKARTA_SANS`, `INTER`, `LEXEND`, `MANROPE`…) et
> light/dark. Outils dispo : `create_project` (+ génération de designs/écrans).

---

## 1. Brief global (coller en premier dans Stitch)

```
Design a modern Android mobile app called "PrestaFlow" — a management tool for a
small French craft & haberdashery e-commerce shop owner (PrestaShop merchant) to
run their store on the go: orders, products, stock, customers, KPIs and push
notifications. The user is non-technical and busy; prioritise clarity, scannable
data and fast actions over decoration — but make it feel warm and crafted, not cold.

Platform & system: Android, Material 3 (Material You), mobile portrait, light mode
first (also provide a dark variant).

Visual style: clean, airy, generous whitespace, large rounded cards (20dp radius),
soft subtle shadows, friendly and premium. Artisanal-but-professional vibe.

Color palette:
- Primary / brand: #C99587 (warm dusty terracotta rose)
- Secondary surface accent: #D1C6BD (warm beige)
- App background: warm off-white #FAF7F4
- Cards: pure white #FFFFFF
- Success: #4CBB6C   Danger: #FF4C4C   Warning/Highlight: #FF9A52
- Text: near-black #2B2B2B, muted text #7A746E

Typography: clean geometric sans-serif (Plus Jakarta Sans or Inter). Big bold
numbers for KPIs, strong section titles, comfortable body.

All in-UI text must be in FRENCH. Bottom navigation bar with 5 items:
Tableau de bord, Commandes, Produits, Clients, Réglages.
```

## 2. Dashboard — « Tableau de bord » (écran prioritaire)

```
Screen: "Tableau de bord" (merchant dashboard home).
Top: greeting "Bonjour 👋" + shop name "Pense Bonheur", a period selector chip
(Aujourd'hui / 7 jours / 30 jours) and a refresh icon.
A 2x2 grid of KPI cards (icon, label, big bold value):
- "Chiffre d'affaires" 1 240 € (+12% green pill)
- "Commandes" 38
- "Panier moyen" 32,60 €
- "Nouveaux clients" 9
Below: a "Chiffre d'affaires" card with a smooth line/area chart (terracotta
gradient fill) over the period, total + trend.
Then "Commandes récentes": list of 3-4 recent orders (initials avatar, customer
name, order ref, amount, colored status badge: "Payée", "Expédiée", "En attente").
Warm, airy, rounded cards on a #FAF7F4 background. Calm and premium.
```

## 3. Commandes (liste + détail)

```
A) "Commandes" list: search field, filter chips (Toutes / En attente / Payées /
Expédiées), scrollable list of order cards (initials avatar, name, ref #1042, date,
amount, status badge).
B) "Détail commande": header with ref + status badge; "Client" card (name, email);
"Articles" card (thumbnail, name, qty, price); "Livraison" card (carrier + tracking);
totals row "Total payé". Bottom: two actions "Changer le statut" (outlined) and
"Ajouter un suivi" (filled terracotta).
```

## 4. Produit (détail, édition rapide)

```
Screen "Détail produit": large product image, name, reference, editable price field
and editable stock field with +/- steppers, availability toggle (Actif/Inactif),
"Enregistrer" filled button. Clean, form-light, warm palette.
```

## 5. Clients (liste + détail)

```
A) "Clients" list: search, list of clients (initials avatar, name, email, total
spent, order count).
B) "Détail client": header (name, email, since date), KPI row (commandes, total
dépensé), and "Historique des commandes" list (ref, date, amount, status badge).
```

## 6. Paniers — « Paniers »

```
Screen "Paniers": list of carts (recent / abandoned) with customer, item count,
total, date, and a badge "Converti en commande" or "Panier abandonné". Tappable to
a simple cart detail (products, quantities, total).
```

## 7. Réglages

```
Screen "Réglages": theme/skin selector as a horizontal row of color swatches,
dark-mode selector (Système / Clair / Sombre) as segmented chips, the shop URL,
and a "Se déconnecter" button. Minimal and tidy.
```

---

## Méthode Stitch
Coller le brief global d'abord, puis générer écran par écran dans la même session
pour la cohérence. Itérer ensuite ("plus aéré", "variante sombre") et exporter vers
Figma / code. Le Dashboard est la priorité (point noir actuel).
