# Maquettes Stitch — refonte Terracotta

Maquettes de référence exportées depuis Google Stitch
(projet **« PrestaFlow Merchant App »**, id `8723946924678165365`,
URL : https://stitch.withgoogle.com/projects/8723946924678165365).

Chaque écran a deux fichiers de référence :
- `*.png` — capture de la maquette (rendu visuel cible)
- `*.html` — code HTML/CSS généré par Stitch (tokens, spacing, structure exacts)

`design-tokens.json` = thème Material 3 complet généré par Stitch
(47 couleurs nommées, font `PLUS_JAKARTA_SANS`, roundness `ROUND_EIGHT`,
couleur de marque `#c99587`).

## Correspondance maquette → écran Compose

| Maquette Stitch | Fichier(s) Compose à refondre |
|-----------------|-------------------------------|
| `tableau-de-bord.png` (finale) | `ui/dashboard/DashboardScreen.kt` |
| `tableau-de-bord-v1.png` (1re itération, archive) | — |
| `commandes.png` | `ui/orders/OrdersScreen.kt` (+ `OrdersTwoPaneScreen.kt` tablette) |
| `detail-commande.png` | `ui/orders/OrderDetailScreen.kt` |
| `produits.png` | `ui/products/ProductsScreen.kt` + `ProductDetailScreen.kt` |
| `clients.png` | `ui/clients/ClientsScreen.kt` + `ClientDetailScreen.kt` |
| `reglages.png` | `ui/settings/SettingsScreen.kt` |
| *(pas de maquette)* | `ui/carts/CartsScreen.kt` + `CartDetailScreen.kt` → restyler par analogie |

## Notes d'implémentation

- **Tokens** : les couleurs des maquettes viennent de `design-tokens.json`
  (mapping M3 Stitch), qui **diffère** des valeurs codées à la main dans
  `ui/theme/ColorTerracotta.kt`. Réconcilier le skin Terracotta sur les tokens
  Stitch (ex. Stitch `primary = #7f5448`, `primary_container = #c99587`).
- Police cible **Plus Jakarta Sans**, rayon des cartes ~**20dp** (`ROUND_EIGHT`).
- Pas de maquette « Paniers » : conserver la nav existante, restyler avec les
  mêmes composants que les autres listes.

Brief texte d'origine : `docs/design-brief-stitch.md`.
