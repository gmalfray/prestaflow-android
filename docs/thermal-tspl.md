# Impression thermique MUNBYN ITPP941B (TSPL) — état du chantier

> **Statut : NON RÉSOLU.** Le format d'impression est **entièrement rétro-conçu et fonctionnel**
> (les octets du constructeur sortent nickel), mais notre conversion image→1-bit sort **trop claire /
> non calibrée**. Il reste un travail de **calibrage du contraste**, pas de reverse.
> Branche : **`feat/thermal-tspl-v2`** (partie de `main` @ v0.25.5).

## Objectif
Imprimer un **bordereau de transport PDF** (ex. Mondial Relay, arbitraire = raster) sur l'imprimante
**Bluetooth MUNBYN ITPP941B** (étiquettes 4×6" / 102×152 mm, 203 dpi = 8 dots/mm, langage **TSPL**).
Déclenché depuis l'écran détail commande (`ThermalLabelPrinter.print(context, pdfBytes, macAddress)`).

## Ce qui MARCHE (prouvé)
1. **TSPL natif** (`TEXT`, `BAR`, `BARCODE`, `QRCODE`) → **impression nette, codes-barres scannables.**
   La connexion Bluetooth (`RobustBluetoothConnection.writeRaw`, SPP/RFCOMM) est donc parfaite.
2. **BITMAP mode 3 = zlib STANDARD.** Rétro-conçu depuis une capture Bluetooth de l'app constructeur :
   - En-tête envoyé par le vendeur :
     ```
     SIZE 50 mm,50 mm
     DIRECTION 0,0
     SET CUTTER 1
     SET GAP ON
     SPEED 4.0
     DENSITY 5
     REFERENCE 0,0
     CLS
     BITMAP 0,0,<widthBytes>,<height>,3,<taille_compressée>,<zlib...>
     PRINT 1,1
     ```
   - Payload = **`zlib.compress()` standard** (en-tête `78 9c`), qui décompresse **exactement** en
     `widthBytes × height` octets. **Layout = row-major, MSB d'abord, bit 1 = blanc (pas de point),
     bit 0 = noir (point).** (Confirmé en rendant la capture : voir `parse_btsnoop.py`.)
   - Rejouer les **octets exacts du vendeur** → l'imprimante sort une image **propre** (référence
     `tools/thermal-tspl/samples/vendor_thankyou_job.bin`).

## Ce qui NE marche PAS / pièges
- **BITMAP mode 0 (brut) et mode 1 (OR) → l'imprimante ERREUR** (bips / LED rouge, rien n'imprime),
  quelle que soit la taille. Ce firmware **n'accepte QUE le mode 3 compressé.**
- **L'imprimante STRIE / pâlit les APLATS.** Diagnostic à 3 zones (blanc plein `0xFF` / noir plein
  `0x00` / dither `0xDD`) : seul le **dither** sort uniforme ; les aplats `0x00` et `0xFF` sortent
  **striés (peigne vertical)**. ⇒ Il ne faut **jamais** d'octet `0x00` ni `0xFF` plein : **tramer TOUT**
  (c'est ce que fait le vendeur — sa data n'a quasi aucun `0x00`/`0xFF`).
- **Piège RFCOMM (capture)** : une trame UIH avec bit P/F mis contient un **octet de crédit**
  (flow-control) après la longueur → il FAUT le sauter, sinon le payload est décalé et `zlib` échoue
  (« incorrect data check » / « truncated »). Géré dans `parse_btsnoop.py`.
- **Piège rendu** : le fond « blanc » d'un PDF rasterisé n'est pas 255 pur (anti-alias ~250).

## Historique des essais (pour ne pas refaire)
| Essai | Résultat |
|------|----------|
| ESC/POS (lib DantSu, sur `main`) | inadapté aux étiquettes → passage à TSPL |
| BITMAP mode 3 zlib, seuillage, aplats | **strié** (aplats non supportés) |
| BITMAP mode 0/1 brut | **erreur** (bips/rouge) |
| Rejeu octets vendeur exacts | **propre** ✅ (= format cible confirmé) |
| Dither Bayer partout (`#11`, WHITE_MAX≈176) | **lisible** mais un peu clair — meilleur rendu obtenu |
| Contenu solide + fond tramé (`#12`) | pâle (le noir solide sort faible) |
| « Balanced » 90/15 % | encore plus clair |

**Leçon clé** : même le **noir** doit être un **dither dense** (pas `0x00` solide, qui sort pâle/strié).

## Reste à faire (calibrage)
1. **Contraste/noirceur** : le rendu sort trop clair. Pistes : monter `DENSITY` (5→10/12), **abaisser
   `WHITE_MAX`** (fond plus dense), revoir la **courbe de trame** pour que le noir soit ~90 % de points
   **sans** jamais atteindre l'aplat `0x00` (le forcer à `0x01` comme on force le blanc à `0xFE`).
2. **Scannabilité des codes-barres** : à VALIDER (scanner un code-barres du tirage). Le dither peut
   flouter les traits fins ; si non scannable, envisager de garder les codes-barres plus nets.
3. **En-tête** : tester l'ajout de `SET CUTTER 1` (le vendeur l'envoie ; effet sur qualité à vérifier).
4. Une fois calibré : **retirer les hooks debug** de `ThermalLabelPrinter` (rejeu `test_job.bin` +
   dump PNG `last_label_render.png`), remettre le test unitaire au vert, puis merger sur `main`.

## Boucle de test (sans rebuild de l'app)
Le hook debug de `ThermalLabelPrinter.print()` rejoue un `test_job.bin` déposé tel quel :
```bash
cd tools/thermal-tspl
python3 make_test_job.py dither samples/last_label_render.png   # ou: native | black
adb push test_job.bin /sdcard/Android/data/com.rebuildit.prestaflow.debug/files/test_job.bin
# dans l'app : imprimer un bordereau -> le hook rejoue test_job.bin
adb shell rm /sdcard/Android/data/com.rebuildit.prestaflow.debug/files/test_job.bin   # revenir normal
```
Le dump `last_label_render.png` (dossier files/ de l'app debug) permet d'inspecter le bitmap rendu.

## Fichiers
- `app/src/main/java/com/rebuildit/prestaflow/core/print/ThermalLabelPrinter.kt` — TSPL mode 3 zlib +
  tramage Bayer (`toMonochrome1Bpp`), hooks debug encore présents.
- `app/src/main/java/com/rebuildit/prestaflow/core/print/RobustBluetoothConnection.kt` — `writeRaw`.
- `tools/thermal-tspl/parse_btsnoop.py` — décode une capture HCI → format vendeur.
- `tools/thermal-tspl/make_test_job.py` — génère des jobs de test (native/dither/black).
- `tools/thermal-tspl/samples/` — `vendor_thankyou_job.bin` (référence connue-bonne),
  `rfcomm_stream.bin` (capture décodée), `last_label_render.png` (bordereau de test rendu).

## Comment capturer un job vendeur (si besoin de re-reverser)
1. Tél : Options développeur → **« Activer le journal de suivi HCI Bluetooth »** ON, puis BT OFF/ON.
2. Imprimer une image depuis l'**app MUNBYN officielle** (Écart / 4×6").
3. `adb bugreport bugreport.zip` ; extraire `FS/data/misc/bluetooth/logs/btsnoop_hci.log`.
4. `python3 parse_btsnoop.py btsnoop_hci.log samples/`.
