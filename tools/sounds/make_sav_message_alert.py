#!/usr/bin/env python3
"""
Génère `app/src/main/res/raw/sav_message_alert.wav`, le son du canal « Messages SAV ».

Intention sonore : deux notes courtes et ASCENDANTES (mi5 -> la5), timbre clair et bref, comme
une clochette de message qui arrive — l'inverse mélodique du canal « Paiement en panne »
(descendant, cf. make_payment_alert.py) qui annonce un problème. Ici il ne s'agit pas d'une
alerte mais d'une cliente qui attend une réponse : le son doit inviter à consulter, pas alarmer.
Distinct aussi du son de caisse (canal Ventes) qui signifie « une vente est tombée », l'inverse du
message porté par un fil SAV.

Pourquoi un fichier et pas le son système : le son d'un canal Android est **figé à sa
création**. Autant en choisir un identifiable, et le versionner pour pouvoir le régénérer.
Modifier le son après coup imposerait de créer un nouveau canal (cf. `sales_v2`).

Usage : python3 tools/sounds/make_sav_message_alert.py
"""
import math
import pathlib
import struct
import wave

SAMPLE_RATE = 44100
AMPLITUDE = 0.58

# (fréquence Hz, durée s, silence après s) — mi5 -> la5, une seule fois : court et net, pas répété
# (contrairement à payment_alert dont la répétition x2 sert à insister sur une urgence).
MOTIF = [
    (659.25, 0.11, 0.03),
    (880.0, 0.20, 0.00),
]

OUT = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/res/raw/sav_message_alert.wav"


def envelope(i: int, total: int) -> float:
    """Attaque/extinction douces : sans ça, chaque note claque sur un haut-parleur de téléphone."""
    attack = int(0.008 * SAMPLE_RATE)
    release = int(0.045 * SAMPLE_RATE)
    if i < attack:
        return i / attack
    if i > total - release:
        return max(0.0, (total - i) / release)
    return 1.0


def tone(freq: float, duration: float) -> list[float]:
    total = int(duration * SAMPLE_RATE)
    out = []
    for i in range(total):
        t = i / SAMPLE_RATE
        # Fondamentale + une pointe d'harmonique 2 (octave) : timbre plus cristallin/cloche que
        # payment_alert (harmonique 3), pour rester distinguable à l'oreille.
        sample = math.sin(2 * math.pi * freq * t) + 0.22 * math.sin(2 * math.pi * freq * 2 * t)
        out.append(sample / 1.22 * envelope(i, total) * AMPLITUDE)
    return out


def silence(duration: float) -> list[float]:
    return [0.0] * int(duration * SAMPLE_RATE)


def main() -> None:
    samples: list[float] = []
    for freq, dur, gap in MOTIF:
        samples.extend(tone(freq, dur))
        if gap:
            samples.extend(silence(gap))

    frames = b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in samples)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(frames)

    print(f"{OUT} — {len(samples) / SAMPLE_RATE:.2f} s, {len(frames) // 1024} Kio")


if __name__ == "__main__":
    main()
