#!/usr/bin/env python3
"""
Reverse-engineering de l'impression thermique MUNBYN ITPP941B (TSPL, BITMAP mode 3 propriétaire).

Décode un log Bluetooth HCI (btsnoop) capturé pendant une impression depuis l'app CONSTRUCTEUR
MUNBYN, reconstitue le flux RFCOMM/SPP envoyé à l'imprimante, isole les commandes `BITMAP ...,3,...`,
décompresse le payload (zlib STANDARD) et rend l'image pour vérifier la convention de bits.

RÉSULTAT ÉTABLI (cf. docs/thermal-tspl.md) :
  - BITMAP mode 3 = zlib standard (en-tête 0x78 0x9c), reproductible avec zlib.compress().
  - Bitmap = row-major, MSB d'abord, bit 1 = blanc (pas de point), bit 0 = noir (point).
  - PIÈGE : RFCOMM insère un octet de CRÉDIT (flow-control) après la longueur quand le bit P/F est
    mis sur une trame UIH → il FAUT le sauter, sinon le payload est décalé et zlib échoue.

Comment capturer le btsnoop (Android) :
  1. Options développeur → « Activer le journal de suivi HCI Bluetooth » → ON, puis Bluetooth OFF/ON.
  2. Imprimer depuis l'app MUNBYN.
  3. `adb bugreport bugreport.zip` puis extraire `FS/data/misc/bluetooth/logs/btsnoop_hci.log`.

Usage : python3 parse_btsnoop.py btsnoop_hci.log [dossier_sortie]
"""
import struct, sys, zlib, os

def parse(path):
    data = open(path, "rb").read()
    assert data[:8] == b"btsnoop\x00", "pas un fichier btsnoop"
    off = 16
    reasm = {}         # handle -> bytearray (réassemblage L2CAP en cours)
    sent_l2cap = []    # (cid, payload) pour les PDUs L2CAP ENVOYÉS (host -> contrôleur)

    def flush(h):
        buf = reasm.get(h)
        if not buf:
            return
        if len(buf) >= 4:
            l2len, cid = struct.unpack("<HH", buf[:4])
            pl = bytes(buf[4:4 + l2len])
            if len(pl) == l2len:
                sent_l2cap.append((cid, pl))
        reasm[h] = bytearray()

    while off + 24 <= len(data):
        _ol, il, flags, _dr = struct.unpack(">IIII", data[off:off + 16])
        off += 24
        pkt = data[off:off + il]; off += il
        if not pkt:
            continue
        if (flags & 1) != 0:          # bit0=1 => reçu ; on ne garde que l'ENVOYÉ (host->ctrl)
            continue
        if pkt[0] != 0x02:            # 0x02 = ACL data
            continue
        body = pkt[1:]
        if len(body) < 4:
            continue
        hf, aclen = struct.unpack("<HH", body[:4])
        handle = hf & 0x0FFF
        pb = (hf >> 12) & 3
        payload = body[4:4 + aclen]
        if pb in (0, 2):              # début de PDU L2CAP
            flush(handle); reasm[handle] = bytearray(payload)
        else:                        # continuation
            reasm.setdefault(handle, bytearray()).extend(payload)
        buf = reasm.get(handle)
        if buf and len(buf) >= 4:
            l2len, _ = struct.unpack("<HH", buf[:4])
            if len(buf) >= 4 + l2len:
                flush(handle)
    for h in list(reasm):
        flush(h)

    # RFCOMM : extrait la data des trames UIH (canal data DLCI>0), en SAUTANT l'octet de crédit.
    rf = bytearray()
    for _cid, p in sent_l2cap:
        if len(p) < 4:
            continue
        addr = p[0]; ctrl = p[1]; dlci = addr >> 2
        l = p[2]
        if l & 1:
            length = l >> 1; hdr = 3
        else:
            length = (l >> 1) | (p[3] << 7); hdr = 4
        is_uih = (ctrl & 0xEF) == 0xEF
        pf = (ctrl & 0x10) != 0
        if is_uih and pf:
            hdr += 1                 # <-- octet de crédit (flow-control), à sauter
        frm = p[hdr:hdr + length]
        if is_uih and dlci > 0 and length > 0:
            rf.extend(frm)
    return bytes(rf)


def extract_bitmaps(rf, outdir):
    try:
        from PIL import Image
        have_pil = True
    except ImportError:
        have_pil = False
    idxs = [i for i in range(len(rf)) if rf[i:i + 6] == b"BITMAP"]
    print(f"RFCOMM: {len(rf)} octets ; commandes BITMAP @ {idxs}")
    for bi in idxs:
        k = bi; c = 0
        while c < 6 and k < len(rf):
            if rf[k] == 0x2c:
                c += 1
            k += 1
        parts = rf[bi:k].decode("ascii", "replace").replace("BITMAP ", "").rstrip(",").split(",")
        x, y, wb, h, mode, size = (int(v) for v in parts)
        payload = bytes(rf[k:k + size])
        print(f"  @{bi}: x={x} y={y} widthBytes={wb} height={h} mode={mode} size={size} head={payload[:4].hex()}")
        if mode == 3:
            try:
                raw = zlib.decompress(payload)
                ok = "OK" if len(raw) == wb * h else "!! taille inattendue"
                print(f"     zlib -> {len(raw)} octets (attendu {wb*h}) {ok}")
                if have_pil and len(raw) == wb * h:
                    # row-major, MSB, bit 1 = blanc
                    img = Image.new("1", (wb * 8, h))
                    px = img.load()
                    for r in range(h):
                        for cc in range(wb):
                            b = raw[r * wb + cc]
                            for t in range(8):
                                px[cc * 8 + t, r] = 255 if ((b >> (7 - t)) & 1) else 0
                    p = os.path.join(outdir, f"vendor_bitmap_{bi}.png")
                    img.save(p)
                    print(f"     -> rendu {p}")
            except Exception as e:
                print(f"     zlib FAIL: {e}  (as-tu bien sauté l'octet de crédit RFCOMM ?)")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(1)
    log = sys.argv[1]
    outdir = sys.argv[2] if len(sys.argv) > 2 else "."
    rf = parse(log)
    open(os.path.join(outdir, "rfcomm_stream.bin"), "wb").write(rf)
    extract_bitmaps(rf, outdir)
