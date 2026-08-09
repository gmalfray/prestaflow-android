#!/usr/bin/env python3
"""
Génère `app/src/main/res/raw/payment_alert.wav`, le son du canal « Paiement en panne ».

Intention sonore : deux notes DESCENDANTES répétées deux fois — l'inverse mélodique d'une
validation (qui monte) et rien à voir avec la caisse enregistreuse des ventes. Sérieux sans
être une sirène : ça doit faire lever la tête, pas sursauter.

Pourquoi un fichier et pas le son système : le son d'un canal Android est **figé à sa
création**. Autant en choisir un identifiable, et le versionner pour pouvoir le régénérer.
Modifier le son après coup imposerait de créer un nouveau canal (cf. `sales_v2`).

Usage : python3 tools/sounds/make_payment_alert.py
"""
import math
import pathlib
import struct
import wave

SAMPLE_RATE = 44100
AMPLITUDE = 0.62

# (fréquence Hz, durée s, silence après s) — sol5 → ré5, deux fois.
MOTIF = [
    (784.0, 0.16, 0.06),
    (587.33, 0.22, 0.14),
    (784.0, 0.16, 0.06),
    (587.33, 0.30, 0.00),
]

OUT = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/res/raw/payment_alert.wav"


def envelope(i: int, total: int) -> float:
    """Attaque/extinction douces : sans ça, chaque note claque sur un haut-parleur de téléphone."""
    attack = int(0.012 * SAMPLE_RATE)
    release = int(0.055 * SAMPLE_RATE)
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
        # Fondamentale + une pointe d'harmonique 3 : porte mieux sur un petit haut-parleur.
        sample = math.sin(2 * math.pi * freq * t) + 0.18 * math.sin(2 * math.pi * freq * 3 * t)
        out.append(sample / 1.18 * envelope(i, total) * AMPLITUDE)
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
