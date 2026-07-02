#!/usr/bin/env python3
"""
Génère des jobs TSPL de test pour l'imprimante MUNBYN ITPP941B, à rejouer via le HOOK DEBUG de
l'app (ThermalLabelPrinter dépose/relit `test_job.bin` — voir la méthode print()).

Boucle de test SANS rebuild de l'app :
  1. python3 make_test_job.py <type> [image.png] -> produit test_job.bin
  2. adb push test_job.bin /sdcard/Android/data/com.rebuildit.prestaflow.debug/files/test_job.bin
  3. Dans l'app : déclencher une impression du bordereau -> le hook rejoue test_job.bin tel quel.
  4. Retirer test_job.bin du tél pour revenir au fonctionnement normal.

FAITS ÉTABLIS (cf. docs/thermal-tspl.md) :
  - En-tête vendeur : SIZE / DIRECTION 0,0 / SET CUTTER 1 / SET GAP ON / SPEED 4.0 / DENSITY 5 /
    REFERENCE 0,0 / CLS.
  - BITMAP 0,0,<widthBytes>,<height>,3,<taille_zlib>,<zlib(row-major MSB, bit1=blanc)>.
  - mode 0 (brut) et mode 1 (OR) => l'imprimante ERREUR (bips/rouge). SEUL le mode 3 zlib passe.
  - L'imprimante STRIE/pâlit les APLATS (0x00 et 0xFF) ; n'imprime proprement que les points isolés
    (dither). Il ne faut donc JAMAIS d'octet 0x00 ou 0xFF plein -> tramer TOUT.
  - PROBLÈME OUVERT : le dither rend le bordereau lisible (#11) mais trop clair / non calibré.
"""
import sys, zlib

HDR = ("SIZE 102 mm,152 mm\r\nDIRECTION 0,0\r\nSET GAP ON\r\n"
       "SPEED 4.0\r\nDENSITY 8\r\nREFERENCE 0,0\r\nCLS\r\n").encode("ascii")

def job_bitmap_mode3(wb, h, raw):
    comp = zlib.compress(bytes(raw))
    return HDR + f"BITMAP 0,0,{wb},{h},3,{len(comp)},".encode("ascii") + comp + b"\r\nPRINT 1,1\r\n"

def native_test():
    """TSPL natif (texte/code-barres/QR) — PROUVÉ NET sur cette imprimante (sert de référence)."""
    return (b"SIZE 102 mm,152 mm\r\nDIRECTION 0,0\r\nSET GAP ON\r\nSPEED 4.0\r\nDENSITY 8\r\n"
            b"REFERENCE 0,0\r\nCLS\r\n"
            b'TEXT 40,40,"4",0,1,1,"TEST TSPL NATIF"\r\nBAR 40,110,640,36\r\n'
            b'BARCODE 40,220,"128",120,1,0,3,3,"HELLO12345"\r\nQRCODE 40,380,L,6,A,0,"pensebonheur.fr"\r\n'
            b"PRINT 1,1\r\n")

def dither_label(png_path, white_max=176):
    """Rend une image en BITMAP mode 3 tramé Bayer 8x8 (approche app, cf. ThermalLabelPrinter.kt)."""
    import numpy as np
    from PIL import Image
    W, H = 816, 1216
    im = Image.open(png_path).convert("L")
    canvas = Image.new("L", (W, H), 255)
    canvas.paste(im.resize((W, min(im.height, H))), (0, 0))
    g = np.asarray(canvas, dtype=np.float32) * (white_max / 255.0)
    b = np.array([[0,48,12,60,3,51,15,63],[32,16,44,28,35,19,47,31],
                  [8,56,4,52,11,59,7,55],[40,24,36,20,43,27,39,23],
                  [2,50,14,62,1,49,13,61],[34,18,46,30,33,17,45,29],
                  [10,58,6,54,9,57,5,53],[42,26,38,22,41,25,37,21]], dtype=np.float32)
    thr = (b + 0.5) / 64.0 * 255.0
    tile = np.tile(thr, (H // 8 + 1, W // 8 + 1))[:H, :W]
    white = (g >= tile)
    wt = np.packbits(white.astype(np.uint8), axis=1)  # MSB, bit1=blanc
    wt[wt == 0xFF] = 0xFE                              # jamais d'aplat blanc
    return job_bitmap_mode3(W // 8, H, wt.tobytes())

if __name__ == "__main__":
    kind = sys.argv[1] if len(sys.argv) > 1 else "native"
    out = "test_job.bin"
    if kind == "native":
        data = native_test()
    elif kind == "dither":
        data = dither_label(sys.argv[2])
    elif kind == "black":       # diag : BITMAP mode 3 tout noir (aplat -> doit strier)
        wb, h = 102, 200
        data = job_bitmap_mode3(wb, h, bytes([0x00]) * (wb * h))
    else:
        print("types: native | dither <png> | black"); sys.exit(1)
    open(out, "wb").write(data)
    print(f"{out}: {len(data)} octets ({kind})")
