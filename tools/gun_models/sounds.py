"""
Gun sounds for the guns pack (mzguns namespace), made from scratch (no recordings): each shot is a sharp crack, a
low boom with a falling thump, the action's metallic clack and a room tail, tuned per kind of gun and varied a little
per gun so no two sound alike. Also the reload and handling sounds the client mod plays with its animations.

    python sounds.py <pack dir>      (writes assets/mzguns/sounds/*.ogg and assets/mzguns/sounds.json; needs ffmpeg)
"""
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np
from scipy.signal import lfilter

SR = 44100

# what each gun sounds like: its kind (below) and how big it is (1: the kind's usual; lower is deeper and longer)
GUN_SOUNDS = {
    'm1911': ('pistol', 1.0), 'b23r': ('pistol', 1.1), 'five_seven': ('pistol', 1.2), 'kap40': ('pistol', 1.05),
    'executioner': ('revolver', 0.9), 'python': ('revolver', 1.0), 'ray_gun': ('ray', 1.0), 'wunderwaffe': ('tesla', 1.0),
    'thompson': ('smg', 0.9), 'mp40': ('smg', 1.0), 'type100': ('smg', 1.05), 'mp5': ('smg', 1.15), 'pdw57': ('smg', 1.25),
    'chicom': ('smg', 1.1),
    'stg44': ('rifle', 0.95), 'an94': ('rifle', 1.0), 'm16a1': ('rifle', 1.08), 'm8a1': ('rifle', 1.12), 'm27': ('rifle', 1.05),
    'mtar': ('rifle', 1.1), 'type25': ('rifle', 1.0), 'smr': ('rifle', 0.95), 'fal': ('battle', 1.0), 'm14': ('battle', 1.05),
    'fg42': ('battle', 1.1), 'm1_garand': ('battle', 1.0), 'gewehr43': ('battle', 1.05), 'm1_carbine': ('rifle', 1.15),
    'svu': ('battle', 0.95),
    'kar98k': ('bolt', 1.0), 'springfield': ('bolt', 1.03), 'arisaka': ('bolt', 1.06),
    'barrett': ('sniper', 0.85), 'dsr50': ('sniper', 0.9), 'ptrs41': ('sniper', 0.8),
    'bar': ('lmg', 0.95), 'hamr': ('lmg', 1.05), 'lsat': ('lmg', 1.0), 'rpd': ('lmg', 1.0),
    'trench_gun': ('shotgun', 1.0), 'r870': ('shotgun', 1.05), 'double_barrel': ('shotgun', 0.92), 'olympia': ('shotgun', 0.95),
    'sawed_off': ('shotgun', 0.9), 's12': ('shotgun', 1.1), 'm1216': ('shotgun', 1.12),
}

# per kind: thump pitch (Hz), boom length (s), tail length (s), crack, boom and clack loudness, clack delay (s)
KINDS = {
    'pistol':   dict(f0=150, boom=0.05, tail=0.35, crack=0.9, body=0.8, clack=0.35, clack_t=0.018),
    'revolver': dict(f0=110, boom=0.08, tail=0.6, crack=1.0, body=1.0, clack=0.15, clack_t=0.03),
    'smg':      dict(f0=165, boom=0.04, tail=0.28, crack=0.7, body=0.75, clack=0.45, clack_t=0.014),
    'rifle':    dict(f0=125, boom=0.055, tail=0.5, crack=1.0, body=0.9, clack=0.35, clack_t=0.016),
    'battle':   dict(f0=105, boom=0.075, tail=0.7, crack=1.0, body=1.0, clack=0.3, clack_t=0.02),
    'bolt':     dict(f0=95, boom=0.09, tail=0.9, crack=1.0, body=1.0, clack=0.0, clack_t=0.0),
    'sniper':   dict(f0=70, boom=0.14, tail=1.3, crack=1.0, body=1.2, clack=0.2, clack_t=0.03),
    'lmg':      dict(f0=110, boom=0.06, tail=0.6, crack=0.9, body=1.0, clack=0.4, clack_t=0.016),
    'shotgun':  dict(f0=80, boom=0.12, tail=0.9, crack=0.7, body=1.3, clack=0.0, clack_t=0.0),
}


def rng(name):
    return np.random.default_rng(int(hashlib.md5(name.encode()).hexdigest()[:8], 16))


def lowpass(x, fc):
    a = np.exp(-2 * np.pi * fc / SR)
    return lfilter([1 - a], [1, -a], x)


def lowpass_fast(x, fc, passes=2):
    # a one-pole filter run a few times: a steeper roll-off
    for _ in range(passes):
        x = lowpass(x, fc)
    return x


def highpass(x, fc):
    return x - lowpass(x, fc)


def env(t, tau):
    return np.exp(-t / tau)


def shot(name, kind, size):
    r = rng(name)
    k = KINDS[kind]
    jitter = lambda: 1 + r.uniform(-0.08, 0.08)
    f0 = k['f0'] * size * jitter()
    boom_t = k['boom'] / size * jitter()
    tail_t = k['tail'] / size * jitter()
    length = min(2.2, 0.08 + tail_t * 2.2)
    n = int(SR * length)
    t = np.arange(n) / SR
    out = np.zeros(n)
    noise = r.standard_normal(n)
    # the crack: a few milliseconds of bright noise
    out += k['crack'] * highpass(noise, 1500) * env(t, 0.0035 / size) * 1.4
    # the boom: low noise, and the thump falling in pitch
    out += k['body'] * lowpass_fast(noise, 900 * size) * env(t, boom_t) * 3.0
    phase = 2 * np.pi * np.cumsum(f0 * (1 + 1.5 * env(t, 0.012))) / SR
    out += k['body'] * np.sin(phase) * env(t, boom_t * 1.2) * 0.9
    # the action's clack: a short metallic ring a moment later
    if k['clack'] > 0:
        c0 = int(k['clack_t'] * SR)
        tc = t[: n - c0]
        ring = (np.sin(2 * np.pi * 2300 * jitter() * tc) + 0.6 * np.sin(2 * np.pi * 3700 * jitter() * tc)) * env(tc, 0.012)
        click = highpass(r.standard_normal(len(tc)), 2500) * env(tc, 0.003)
        out[c0:] += k['clack'] * (ring * 0.5 + click)
    # the room: a long soft rumble
    out += lowpass_fast(r.standard_normal(n), 400) * env(t, tail_t) * 1.2 * (t > 0.01)
    return finish(out)


def ray(name, pap=False):
    r = rng(name)
    n = int(SR * 0.45)
    t = np.arange(n) / SR
    f = 2400 * np.exp(-t / 0.09) + 260
    mod = 1 + 0.35 * np.sin(2 * np.pi * 55 * t)
    phase = 2 * np.pi * np.cumsum(f * mod) / SR
    out = np.sin(phase) * env(t, 0.16) + 0.5 * np.sin(phase * 2.01) * env(t, 0.07)
    out += 0.25 * highpass(r.standard_normal(n), 3000) * env(t, 0.02)
    if pap:
        out += 0.5 * np.sin(phase * 0.5) * env(t, 0.2)
    return finish(out)


def crackle(r, n, t, density_tau, rate=900):
    """Electric crackle: sharp sparks at random moments, thinning out over density_tau seconds."""
    out = np.zeros(n)
    spark = highpass(r.standard_normal(n), 2500)
    k = 0
    while k < n:
        k += int(SR / rate * r.exponential(1.0) / max(0.05, np.exp(-k / SR / density_tau)))
        if k >= n:
            break
        m = min(n - k, int(SR * r.uniform(0.001, 0.004)))
        out[k:k + m] += spark[k:k + m] * r.uniform(0.4, 1.0) * np.exp(-k / SR / (density_tau * 1.5))
    return out


def tesla(name, pap=False):
    """The Wunderwaffe: a crack of lightning, a falling electric zap, sparks, and thunder rolling away."""
    r = rng(name)
    n = int(SR * (1.6 if pap else 1.3))
    t = np.arange(n) / SR
    out = crackle(r, n, t, 0.18) * 1.2
    f = 3200 * np.exp(-t / 0.07) + (90 if pap else 140)
    buzz = 1 + 0.5 * np.sign(np.sin(2 * np.pi * 60 * t))                 # mains hum in the zap
    out += 0.7 * np.sin(2 * np.pi * np.cumsum(f * buzz) / SR) * env(t, 0.12)
    out += 1.6 * highpass(r.standard_normal(n), 1200) * env(t, 0.006)    # the strike
    thunder = lowpass_fast(r.standard_normal(n), 180, 3) * (1 - env(t, 0.03)) * env(t, 0.45 if pap else 0.35)
    out += thunder * 9.0
    if pap:
        out += 0.4 * np.sin(2 * np.pi * np.cumsum(f * 0.5) / SR) * env(t, 0.2)
    return finish(out)


def arc(name):
    """A bolt jumping to the next zombie: a short snap of sparks."""
    r = rng(name)
    n = int(SR * 0.4)
    t = np.arange(n) / SR
    out = crackle(r, n, t, 0.07, rate=1400) + 1.2 * highpass(r.standard_normal(n), 1500) * env(t, 0.004)
    out += 0.4 * np.sin(2 * np.pi * np.cumsum(1800 * np.exp(-t / 0.04) + 300) / SR) * env(t, 0.06)
    return finish(out) * 0.85


def pap_layer(name, x):
    """Pack-a-Punched: the same shot with an electric zap over it."""
    r = rng(name + 'pap')
    n = len(x)
    t = np.arange(n) / SR
    f = 1800 * np.exp(-t / 0.05) + 500
    zap = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(t, 0.08) * 0.35
    zap += highpass(r.standard_normal(n), 4000) * env(t, 0.03) * 0.15
    return finish(x + zap)


def finish(x):
    x = x / (np.max(np.abs(x)) + 1e-9)
    x = np.tanh(x * 2.2) / np.tanh(2.2)
    fade = np.ones_like(x)
    m = min(len(x), int(SR * 0.05))
    fade[-m:] = np.linspace(1, 0, m)
    return x * fade * 0.95


# ---- handling sounds: clicks, slides and clunks
def clunk(name, freqs, ring, slide=0.0, length=0.25, click=1.0):
    r = rng(name)
    n = int(SR * length)
    t = np.arange(n) / SR
    out = np.zeros(n)
    s0 = 0
    if slide > 0:
        s = int(slide * SR)
        out[:s] += highpass(r.standard_normal(s), 1800) * np.hanning(s) * 0.35
        s0 = s
    tt = t[: n - s0]
    out[s0:] += click * highpass(r.standard_normal(n - s0), 2000) * env(tt, 0.004)
    for f in freqs:
        out[s0:] += np.sin(2 * np.pi * f * tt) * env(tt, ring) * 0.4
    return finish(out) * 0.8


# ---- perks, grenades, the monkey bomb
def bandpass(x, lo, hi):
    return highpass(lowpass_fast(x, hi), lo)


def perk_open():
    r = rng('perk_open')
    n = int(SR * 0.7)
    t = np.arange(n) / SR
    pop = bandpass(r.standard_normal(n), 200, 1500) * env(t, 0.008) * 2 + np.sin(2 * np.pi * 420 * t) * env(t, 0.03)
    fizz = highpass(r.standard_normal(n), 4000) * env(t, 0.35) * 0.35 * (r.random(n) < 0.4)
    return finish(pop + fizz) * 0.8


def gulp(seed):
    r = rng('gulp' + str(seed))
    n = int(SR * 0.22)
    t = np.arange(n) / SR
    f = 210 * np.exp(-t / 0.08) + 85
    out = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * np.minimum(1, t / 0.18)) ** 2
    out += lowpass_fast(r.standard_normal(n), 300) * env(t, 0.05) * 1.5
    return finish(out) * 0.7


def burp():
    r = rng('burp')
    n = int(SR * 0.75)
    t = np.arange(n) / SR
    f0 = 95 + 12 * np.sin(2 * np.pi * 6 * t) + r.standard_normal(n).cumsum() * 0.0
    saw = ((np.cumsum(f0) / SR) % 1.0) * 2 - 1
    voiced = bandpass(saw, 250, 900) + 0.6 * bandpass(saw, 1000, 1600)
    shape = np.sin(np.pi * np.minimum(1, t / 0.7)) ** 0.6
    return finish(voiced * shape + 0.1 * lowpass_fast(r.standard_normal(n), 800) * shape) * 0.8


def smash():
    r = rng('smash')
    n = int(SR * 0.9)
    t = np.arange(n) / SR
    out = highpass(r.standard_normal(n), 2500) * env(t, 0.06) * 1.5
    for _ in range(14):
        f = r.uniform(2500, 9000)
        t0 = int(r.uniform(0, 0.25) * SR)
        tt = t[: n - t0]
        out[t0:] += np.sin(2 * np.pi * f * tt) * env(tt, r.uniform(0.03, 0.12)) * r.uniform(0.2, 0.5)
    return finish(out) * 0.8


def pin():
    r = rng('pin')
    n = int(SR * 0.3)
    t = np.arange(n) / SR
    out = highpass(r.standard_normal(n), 3000) * env(t, 0.004) + 0.5 * np.sin(2 * np.pi * 3400 * t) * env(t, 0.06)
    out += 0.3 * np.sin(2 * np.pi * 5100 * t) * env(t, 0.04)
    return finish(out) * 0.7


def cymbal():
    r = rng('cymbal')
    n = int(SR * 0.45)
    t = np.arange(n) / SR
    out = highpass(r.standard_normal(n), 5000) * env(t, 0.12)
    for f in (420, 1130, 2270, 3410, 5230, 7100):
        out += 0.25 * np.sin(2 * np.pi * f * r.uniform(0.98, 1.02) * t) * env(t, r.uniform(0.08, 0.25))
    return finish(out) * 0.75


EXTRA = {
    'perk.open': perk_open, 'perk.gulp_0': lambda: gulp(0), 'perk.gulp_1': lambda: gulp(1), 'perk.gulp_2': lambda: gulp(2),
    'perk.burp': burp, 'perk.smash': smash, 'grenade.pin': pin, 'monkey.cymbal': cymbal,
    'wunderwaffe.arc_0': lambda: arc('arc0'), 'wunderwaffe.arc_1': lambda: arc('arc1'), 'wunderwaffe.arc_2': lambda: arc('arc2'),
    'handling.inspect': lambda: clunk('inspect', [1400, 2500], 0.02, slide=0.05, length=0.2, click=0.7),
}


HANDLING = {
    'mag_out': lambda: clunk('mag_out', [900, 1700], 0.02, slide=0.06, length=0.2),
    'mag_in': lambda: clunk('mag_in', [700, 1500, 2600], 0.025, slide=0.04, length=0.22),
    'charge': lambda: clunk('charge', [1900, 3100], 0.03, slide=0.07, length=0.3),
    'bolt_open': lambda: clunk('bolt_open', [1500, 2600], 0.02, slide=0.08, length=0.25),
    'bolt_close': lambda: clunk('bolt_close', [1200, 2400], 0.03, slide=0.07, length=0.25),
    'pump': lambda: clunk('pump', [600, 1100, 2000], 0.03, slide=0.09, length=0.32),
    'shell': lambda: clunk('shell', [2600, 4100], 0.015, slide=0.03, length=0.15, click=0.6),
    'break_open': lambda: clunk('break_open', [800, 1600], 0.04, length=0.3),
    'break_close': lambda: clunk('break_close', [500, 1300], 0.05, length=0.3),
    'cyl_open': lambda: clunk('cyl_open', [2200, 3300], 0.02, slide=0.03, length=0.2),
    'cyl_close': lambda: clunk('cyl_close', [1600, 2800], 0.03, length=0.22),
    'dry': lambda: clunk('dry', [3000], 0.01, length=0.1),
}


def write_ogg(path, x):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pcm = (np.clip(x, -1, 1) * 32767).astype('<i2')
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
        tmp = f.name
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '4', path], check=True)
    os.remove(tmp)


def write(pack):
    root = os.path.join(pack, 'assets', 'mzguns')
    sounds = {}
    for gid, (kind, size) in GUN_SOUNDS.items():
        for pap in (False, True):
            name = 'gun.%s%s' % (gid, '_pap' if pap else '')
            files = []
            for v in range(2):
                key = '%s_%d' % (name, v)
                if kind == 'ray':
                    x = ray(key, pap)
                elif kind == 'tesla':
                    x = tesla(key, pap)
                else:
                    x = shot(gid + str(v), kind, size)
                    if pap:
                        x = pap_layer(key, x)
                write_ogg(os.path.join(root, 'sounds', 'gun', key.replace('gun.', '') + '.ogg'), x)
                files.append({'name': 'mzguns:gun/' + key.replace('gun.', ''), 'attenuation_distance': 48})
            sounds[name] = {'subtitle': 'Gunshot', 'sounds': files}
            if kind == 'tesla':
                for f in files:
                    f['attenuation_distance'] = 64
        print('shot', gid)
    for name, fn in HANDLING.items():
        write_ogg(os.path.join(root, 'sounds', 'handling', name + '.ogg'), fn())
        sounds['handling.' + name] = {'sounds': [{'name': 'mzguns:handling/' + name, 'attenuation_distance': 16}]}
    for name, fn in EXTRA.items():
        file = name.replace('.', '/')
        write_ogg(os.path.join(root, 'sounds', file + '.ogg'), fn())
        event = name.rsplit('_', 1)[0] if name.startswith(('perk.gulp', 'wunderwaffe.arc')) else name
        sounds.setdefault(event, {'sounds': []})['sounds'].append({'name': 'mzguns:' + file, 'attenuation_distance': 16})
    with open(os.path.join(root, 'sounds.json'), 'w') as f:
        json.dump(sounds, f, indent=1)
    print('wrote', len(sounds), 'sound events')


if __name__ == '__main__':
    write(sys.argv[1])
