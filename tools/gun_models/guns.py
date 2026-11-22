"""
Gun models for MinecraftZombies: each gun built from boxes, roughly true to the real one, as a resource pack (item
models, which any client draws: no mod needed). The plugin points a gun's item at its model with the item_model setting in
guns.json ("mzguns:<id>", and "mzguns:<id>_pap" for its Pack-a-Punched version, the same model in purple camo).

    python -I tools/gun_models/guns.py <pack dir>               -> the pack (pack.mcmeta, assets/mzguns/...)
    python tools/gun_models/preview.py <png> [gun ...]          -> each gun in first person, third person and the inventory

Units: each gun is drawn in pixels of `u` centimetres (so its length comes out right), along z: the muzzle at the front
(north, -z), y up, x across (right is +x). The model is then centred, and its display scale set from u.
"""
import json, os, struct, sys, zlib

NS = 'mzguns'

# ------------------------------------------------------------------------------------------------------------ texture
# One 32x32 sheet of 4x4 swatches: wood, metal, bakelite, the Ray Gun's red / silver / glow ...
SWATCH = {
    'wood': ((112, 70, 38), 0.18, 'grain'),
    'wood_dark': ((78, 46, 26), 0.18, 'grain'),
    'blued': ((44, 46, 52), 0.10, 'noise'),
    'steel': ((92, 94, 98), 0.10, 'noise'),
    'steel_dark': ((58, 60, 64), 0.14, 'holes'),
    'bakelite': ((52, 34, 26), 0.10, 'noise'),
    'brass': ((176, 140, 64), 0.12, 'noise'),
    'grip_black': ((30, 30, 32), 0.12, 'noise'),
    'ray_red': ((168, 36, 32), 0.10, 'noise'),
    'ray_silver': ((176, 180, 186), 0.08, 'noise'),
    'ray_glow': ((150, 255, 90), 0.10, 'noise'),
    'olive': ((70, 76, 52), 0.10, 'noise'),
    'polymer': ((38, 38, 40), 0.08, 'noise'),
    'tan': ((150, 128, 92), 0.10, 'noise'),
    'chrome': ((196, 198, 204), 0.08, 'noise'),
    'glass': ((40, 70, 110), 0.10, 'noise'),
    'wood_light': ((150, 100, 56), 0.18, 'grain'),
    # the perk bottles (World at War colours), the monkey bomb
    'jug_red': ((170, 24, 24), 0.08, 'noise'),
    'speed_green': ((40, 150, 50), 0.08, 'noise'),
    'revive_blue': ((60, 140, 220), 0.08, 'noise'),
    'dtap_orange': ((200, 110, 30), 0.08, 'noise'),
    'stamin_yellow': ((230, 200, 40), 0.08, 'noise'),
    'phd_purple': ((120, 50, 170), 0.08, 'noise'),
    'deadshot_gray': ((60, 70, 76), 0.08, 'noise'),
    'mule_green': ((40, 120, 100), 0.08, 'noise'),
    'cherry_blue': ((90, 190, 230), 0.08, 'noise'),
    'label_cream': ((232, 220, 186), 0.05, 'noise'),
    'cap_gold': ((210, 170, 60), 0.08, 'noise'),
    'fur': ((96, 62, 36), 0.16, 'grain'),
    'fur_light': ((196, 160, 116), 0.10, 'noise'),
    'fez_red': ((150, 20, 28), 0.08, 'noise'),
    # the Wunderwaffe's coils and its glowing bulbs
    'copper': ((176, 98, 54), 0.12, 'noise'),
    'tesla_glow': ((150, 215, 255), 0.10, 'noise'),
}
NAMES = list(SWATCH)


def swatch_uv(mat):
    i = NAMES.index(mat)
    x, y = (i % 8) * 4, (i // 8) * 4
    # Model UVs are 0..16 across the whole texture whatever its size: the 32-pixel sheet's pixels are halves
    return [(x + 0.5) / 2, (y + 0.5) / 2, (x + 3.5) / 2, (y + 3.5) / 2]


def noise(x, y, s):
    n = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0


def sheet(pap=False):
    """The texture: 32x32, swatch i at ((i % 8) * 4, (i // 8) * 4). pap: the Pack-a-Punch camo version."""
    px = [[(0, 0, 0, 0)] * 32 for _ in range(32)]
    for i, name in enumerate(NAMES):
        (r, g, b), amount, kind = SWATCH[name]
        if pap and name not in ('ray_glow', 'tesla_glow'):
            # purple and violet blotches; the wood and grips darker than the metal
            r, g, b = (70, 40, 110) if name.startswith(('wood', 'bakelite', 'grip')) else (110, 60, 170)
        for j in range(4):
            for k in range(4):
                x, y = (i % 8) * 4 + k, (i // 8) * 4 + j
                n = noise(x, y, 3) - 0.5
                if kind == 'grain':
                    n = (noise(0, y, 5) - 0.5) * 1.3 + n * 0.4
                if kind == 'holes' and (j + k) % 2 == 0:
                    n = -0.6
                if pap and noise(x // 2, y // 2, 9) > 0.6:
                    n += 0.35
                f = 1 + n * amount * 2
                px[y][x] = (max(0, min(255, int(r * f))), max(0, min(255, int(g * f))), max(0, min(255, int(b * f))), 255)
    return px


def png(px):
    h, w = len(px), len(px[0])
    raw = b''.join(b'\0' + b''.join(bytes(c) for c in row) for row in px)

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


# -------------------------------------------------------------------------------------------------------------- guns

def B(z0, z1, y0, y1, w, mat, x=0.0, rot=None, glow=False, mag=False, brk=False, mv=False):
    """A box from z0 to z1 (front to back), y0 to y1, w wide centred on x; rot = (axis, angle, (oz, oy) pivot).
    mag: part of the magazine (drawn on its own in a reload: <id>_mag, the gun without it <id>_nomag).
    brk: part of a break-action's barrels (they swing open on the hinge in a reload: <id>_barrel, the rest <id>_frame).
    mv: the part that works (a bolt handle, a pump, a revolver's cylinder: <id>_moving, the rest <id>_fixed)."""
    return {'from': [8 + x - w / 2, y0, z0], 'to': [8 + x + w / 2, y1, z1], 'mat': mat, 'rot': rot, 'glow': glow, 'mag': mag,
            'brk': brk, 'mv': mv}


def tilt(z, y, angle=-22.5):
    """Raked back like a pistol grip: turned about x through (z, y)."""
    return ('x', angle, (z, y))


GUNS = {}


def gun(gid, name, u, kind, grip):
    """grip: (z, y) where the hand holds it (the pistol grip or the wrist of the stock), in the gun's own pixels."""
    def wrap(fn):
        GUNS[gid] = {'name': name, 'u': u, 'kind': kind, 'grip': grip, 'boxes': fn()}
        return fn
    return wrap


@gun('m1911', 'M1911', 1.0, 'pistol', grip=(17, 5))
def _():
    return [B(0, 21.5, 9, 12.4, 2.2, 'blued'),            # slide
            B(-0.3, 1, 9.4, 12, 1.6, 'steel'),            # barrel bushing
            B(3, 20, 7.6, 9, 2.0, 'blued'),               # frame
            B(1, 2, 12.4, 13.1, 0.5, 'blued'),            # front sight
            B(18.5, 19.8, 12.4, 13.3, 1.4, 'blued'),      # rear sight
            B(19.8, 21.6, 10.6, 12.2, 0.8, 'blued'),      # hammer
            B(8.6, 13, 5.2, 5.9, 0.9, 'blued'),           # trigger guard
            B(8.6, 9.3, 5.2, 7.6, 0.9, 'blued'),
            B(11.2, 11.8, 6.1, 7.6, 0.6, 'steel'),        # trigger
            B(14, 20, -1.5, 7.6, 2.5, 'wood', rot=tilt(17, 7.6)),        # grip
            B(14.6, 19.4, -2.2, -1.5, 2.1, 'blued', rot=tilt(17, 7.6), mag=True)]  # magazine base


@gun('thompson', 'Thompson', 2.0, 'smg', grip=(26, 9))
def _():
    return [B(0, 3, 13.4, 16.2, 2.0, 'blued'),            # compensator
            B(3, 14, 14, 15.6, 1.4, 'steel'),             # barrel
            B(4, 12, 13.6, 16, 1.9, 'blued'),             # cooling fins
            B(14, 28, 12, 17, 2.6, 'blued'),              # receiver
            B(17, 19, 17, 17.8, 1.2, 'steel'),            # bolt knob
            B(9, 12, 6.5, 13.6, 2.0, 'wood'),             # front grip
            B(15, 21, 5, 11, 3.2, 'blued', mag=True),               # drum (an octagon from the side)
            B(15, 21, 5, 11, 3.2, 'blued', rot=('x', 45, (18, 8)), mag=True),
            B(17, 19, 11, 12, 2.0, 'blued', mag=True),
            B(22, 26, 10, 11, 1.0, 'blued'),              # trigger guard
            B(24, 28, 4, 12, 2.0, 'wood', rot=tilt(26, 12)),   # pistol grip
            B(28, 42, 11, 16, 2.4, 'wood'),               # stock
            B(36, 42, 8, 12, 2.4, 'wood'),
            B(41.5, 42.2, 8, 16, 2.5, 'steel'),           # butt plate
            B(25, 26, 17, 18, 1.0, 'blued')]              # rear sight


@gun('mp40', 'MP40', 2.0, 'smg', grip=(22, 7))
def _():
    return [B(-0.2, 1.2, 12, 14.6, 1.6, 'blued'),         # muzzle
            B(1, 9, 12.6, 14, 1.3, 'blued'),              # barrel
            B(5, 8.5, 11.2, 12.6, 1.2, 'blued'),          # barrel rest
            B(9, 26, 12, 15.6, 2.4, 'blued'),             # receiver tube
            B(10.5, 14.5, 9, 12, 2.2, 'blued'),           # magazine well
            B(11, 14, 0.5, 9, 1.6, 'blued', mag=True),              # magazine
            B(14, 24, 10, 12, 2.2, 'bakelite'),           # lower housing
            B(15, 19, 8, 9, 1.0, 'blued'),                # trigger guard
            B(20, 24, 3, 10, 2.0, 'bakelite', rot=tilt(22, 10)),  # grip
            B(18, 19.5, 14, 15, 0.8, 'steel', x=1.6),     # bolt handle
            B(26, 40, 12, 13, 0.6, 'steel', x=1.0),       # stock struts
            B(26, 40, 12, 13, 0.6, 'steel', x=-1.0),
            B(39, 40, 8, 14, 2.2, 'steel'),               # butt plate
            B(1, 2, 14, 15.4, 0.5, 'blued'),              # front sight
            B(24, 25, 15.6, 16.6, 1.0, 'blued')]          # rear sight


@gun('stg44', 'STG-44', 2.5, 'rifle', grip=(23.5, 9))
def _():
    return [B(0, 8, 14, 15.2, 1.2, 'blued'),              # barrel
            B(1, 2, 15, 16.6, 1.0, 'blued'),              # front sight
            B(3, 12, 13, 14.6, 1.4, 'blued'),             # gas tube
            B(8, 14, 12.4, 15.6, 2.2, 'blued'),           # front of receiver
            B(14, 28, 12, 16, 2.4, 'steel'),              # receiver
            B(14, 17, 8, 12, 1.6, 'blued', mag=True),               # magazine (curved forward)
            B(13, 16, 2.6, 8.4, 1.6, 'blued', rot=tilt(14.5, 8.2, 22.5), mag=True),
            B(19, 23, 10, 11, 1.0, 'blued'),              # trigger guard
            B(22, 25, 5, 12, 2.0, 'wood', rot=tilt(23.5, 12)),  # grip
            B(28, 38, 10, 15.6, 2.4, 'wood'),             # stock
            B(35, 38, 7.4, 10, 2.4, 'wood'),
            B(15, 16, 16, 17, 1.0, 'blued')]              # rear sight


@gun('trench_gun', 'Trench Gun', 2.5, 'shotgun', grip=(24, 12))
def _():
    return [B(0, 18, 14, 15.2, 1.4, 'blued'),             # barrel
            B(2, 15, 14.1, 16.3, 2.0, 'steel_dark'),      # heat shield
            B(0, 2, 12.8, 14, 1.0, 'steel'),              # bayonet lug
            B(6, 18, 12.6, 14, 1.2, 'blued'),             # magazine tube
            B(10, 17, 11.8, 14.4, 2.0, 'wood', mv=True),           # pump
            B(18, 25, 11.4, 16, 2.4, 'blued'),            # receiver
            B(24.6, 25.8, 15.6, 17.2, 0.8, 'blued'),      # hammer
            B(20, 24, 10, 11.4, 1.0, 'blued'),            # trigger guard
            B(25, 33, 11, 15, 2.4, 'wood'),               # wrist
            B(32, 40, 8.6, 14.2, 2.4, 'wood'),            # butt
            B(39.4, 40.2, 8.6, 14.2, 2.5, 'grip_black')]  # butt plate


@gun('double_barrel', 'Double-Barrel', 2.5, 'shotgun', grip=(27, 12))
def _():
    return [B(0, 22, 13.5, 14.9, 1.3, 'blued', x=0.7, brk=True),    # barrels
            B(0, 22, 13.5, 14.9, 1.3, 'blued', x=-0.7, brk=True),
            B(0, 22, 14.9, 15.3, 0.6, 'blued', brk=True),           # rib
            B(12, 20, 12.2, 13.8, 2.4, 'wood', brk=True),           # fore-end
            B(22, 27, 11.4, 15.2, 2.6, 'steel'),          # receiver
            B(25, 26.2, 14.6, 16.2, 0.6, 'steel', x=0.8),  # hammers
            B(25, 26.2, 14.6, 16.2, 0.6, 'steel', x=-0.8),
            B(23, 27, 10, 11.4, 1.0, 'steel'),            # trigger guard
            B(27, 35, 10.6, 14.6, 2.4, 'wood_dark'),      # wrist
            B(34, 44, 8.4, 14, 2.4, 'wood_dark')]         # butt


@gun('kar98k', 'Kar98K', 2.5, 'rifle', grip=(30, 12))
def _():
    return [B(0, 8, 14.4, 15.4, 1.0, 'blued'),            # barrel
            B(0, 1.5, 14.4, 16.4, 1.2, 'blued'),          # front sight hood
            B(7, 8.5, 12.4, 15.6, 2.0, 'steel'),          # nose cap
            B(8, 24, 12.4, 15, 2.0, 'wood'),              # fore stock
            B(12, 22, 15, 15.8, 1.6, 'wood'),             # handguard
            B(13, 14, 12.2, 15.8, 2.1, 'steel'),          # band
            B(20, 22, 15.8, 16.6, 1.0, 'blued'),          # rear sight
            B(22, 30, 14, 15.8, 1.8, 'blued'),            # receiver
            B(28, 29, 12.6, 14.6, 0.8, 'steel', x=1.6, mv=True),   # bolt handle
            B(28, 29.5, 12.2, 13.2, 1.2, 'steel', x=2.0, mv=True),  # bolt knob
            B(24, 28, 11.6, 12.4, 1.8, 'steel'),          # floorplate
            B(26, 30, 10.4, 11.6, 0.9, 'steel'),          # trigger guard
            B(24, 31, 11.6, 14, 2.2, 'wood'),             # stock under the receiver
            B(30, 34, 10, 14.2, 2.2, 'wood'),             # wrist
            B(34, 44, 8.4, 14.6, 2.2, 'wood'),            # butt
            B(43.4, 44.2, 8.4, 14.6, 2.3, 'steel')]       # butt plate


@gun('m1_garand', 'M1 Garand', 2.5, 'rifle', grip=(30, 12))
def _():
    return [B(0, 9, 14.4, 15.4, 1.0, 'blued'),            # barrel
            B(0, 3, 13.2, 16.6, 1.4, 'blued'),            # gas cylinder and front sight
            B(6, 24, 12.2, 15.2, 2.2, 'wood'),            # fore stock
            B(9, 21, 15.2, 16, 1.8, 'wood'),              # handguard
            B(21, 30, 14, 16.4, 2.0, 'steel'),            # receiver
            B(23, 27, 16, 16.6, 1.2, 'brass'),            # clip of rounds
            B(25, 29, 10.8, 12.2, 1.0, 'steel'),          # trigger guard
            B(29, 30, 16.4, 17.6, 1.4, 'steel'),          # rear sight
            B(24, 31, 11.4, 14, 2.3, 'wood'),
            B(30, 34, 10, 14.6, 2.3, 'wood'),             # wrist
            B(34, 44, 8.4, 15, 2.3, 'wood'),              # butt
            B(43.4, 44.2, 8.4, 15, 2.4, 'steel')]


@gun('gewehr43', 'Gewehr 43', 2.5, 'rifle', grip=(31, 12))
def _():
    return [B(0, 8, 14.4, 15.4, 1.0, 'blued'),
            B(0, 1.6, 14.2, 16.4, 1.3, 'blued'),          # front sight hood
            B(3, 13, 15.4, 16.2, 1.1, 'blued'),           # gas piston tube
            B(8, 22, 12.4, 15.2, 2.1, 'wood_dark'),
            B(22, 31, 13.8, 16, 2.0, 'blued'),            # receiver
            B(25, 29, 16, 16.6, 1.0, 'blued'),            # scope rail
            B(23, 27, 8.6, 12.4, 1.6, 'blued', mag=True),           # magazine
            B(27, 31, 10.4, 11.4, 0.9, 'steel'),
            B(22, 31, 11.4, 13.8, 2.2, 'wood_dark'),
            B(31, 35, 10, 14.2, 2.2, 'wood_dark'),
            B(35, 44, 8.4, 14.6, 2.2, 'wood_dark'),
            B(43.4, 44.2, 8.4, 14.6, 2.3, 'steel')]


@gun('bar', 'BAR', 3.0, 'lmg', grip=(29, 12))
def _():
    return [B(0, 2, 13.4, 15.8, 1.6, 'blued'),            # flash hider
            B(2, 12, 14, 15.2, 1.2, 'blued'),             # barrel
            B(3, 14, 12.4, 14, 1.4, 'blued'),             # gas cylinder
            B(10, 19, 11.8, 15.4, 2.4, 'wood'),           # forearm
            B(19, 30, 11.4, 16, 2.6, 'blued'),            # receiver
            B(21, 26, 5.6, 11.4, 2.0, 'blued', mag=True),           # magazine
            B(26, 29, 10, 11.4, 1.0, 'blued'),            # trigger guard
            B(28, 31, 9.6, 14.6, 2.4, 'wood'),            # wrist
            B(30, 40, 8.4, 14.6, 2.4, 'wood'),            # butt
            B(39.4, 40.2, 8.4, 14.6, 2.5, 'steel'),
            B(29, 30, 16, 17.4, 1.2, 'blued')]            # rear sight


@gun('fg42', 'FG42', 2.5, 'rifle', grip=(21.5, 9))
def _():
    return [B(0, 2, 13, 15, 1.6, 'blued'),                # muzzle brake
            B(2, 9, 13.5, 14.5, 1.0, 'blued'),
            B(5, 14, 12.4, 13.2, 1.6, 'steel'),           # folded bipod
            B(3, 4, 14.5, 17.4, 0.5, 'blued'),            # front sight
            B(9, 16, 12.2, 15.2, 2.2, 'bakelite'),        # handguard
            B(16, 29, 12.4, 15.8, 2.2, 'steel'),          # receiver
            B(18, 21, 13, 14.6, 4.2, 'blued', x=-3.2, mag=True),    # side magazine (left)
            B(19, 23, 10.4, 11.4, 1.0, 'steel'),
            B(20, 23, 4, 12.4, 2.0, 'bakelite', rot=tilt(21.5, 12.4)),  # grip
            B(29, 38, 11, 15, 2.0, 'bakelite'),           # stock
            B(25, 26, 15.8, 17.4, 1.0, 'blued')]          # rear sight


@gun('type100', 'Type 100', 2.0, 'smg', grip=(24, 11))
def _():
    return [B(-0.2, 1, 12.8, 15.6, 2.0, 'blued'),         # muzzle
            B(1, 11, 13, 15.4, 1.8, 'steel_dark'),        # perforated jacket
            B(11, 23, 12, 15.4, 2.2, 'blued'),            # receiver
            B(12, 15, 12.6, 14, 5.0, 'blued', x=-3.6, mag=True),    # side magazine (left)
            B(10, 22, 10.4, 12.4, 2.2, 'wood'),           # fore stock
            B(20, 24, 9, 10.4, 1.0, 'blued'),             # trigger guard
            B(22, 30, 9.4, 14.4, 2.2, 'wood'),            # wrist
            B(30, 43, 8, 14.4, 2.2, 'wood'),              # butt
            B(42.4, 43.2, 8, 14.4, 2.3, 'steel'),
            B(21, 22, 15.4, 16.6, 1.0, 'blued')]


@gun('ptrs41', 'PTRS 41', 5.0, 'rifle', grip=(29.8, 12))
def _():
    return [B(0, 2, 15.2, 17.8, 2.2, 'blued'),            # muzzle brake
            B(2, 20, 16, 17, 1.0, 'blued'),               # barrel
            B(12, 13, 8, 16, 0.6, 'steel', x=1.2, rot=('x', 22.5, (12.5, 16))),   # bipod legs
            B(12, 13, 8, 16, 0.6, 'steel', x=-1.2, rot=('x', 22.5, (12.5, 16))),
            B(18, 22, 17.4, 18.2, 0.8, 'steel'),          # carry handle
            B(20, 30, 14.4, 17.6, 2.2, 'blued'),          # receiver
            B(22, 26, 11.6, 14.4, 1.6, 'blued', mag=True),          # magazine
            B(28.6, 31, 9.6, 14.4, 1.8, 'wood', rot=tilt(29.8, 14.4)),  # grip
            B(30, 43, 12.4, 16.6, 1.6, 'steel'),          # stock
            B(33, 39, 16.6, 17.6, 1.8, 'wood'),           # cheek piece
            B(42, 43, 11, 17.6, 2.2, 'grip_black')]       # butt pad


@gun('ray_gun', 'Ray Gun', 1.0, 'pistol', grip=(20, 6))
def _():
    out = [B(10, 24, 9, 15, 4.0, 'ray_red'),               # body
           B(23, 25, 9.4, 14.6, 4.4, 'ray_silver'),        # rear cap
           B(0, 10, 10.6, 13.4, 2.6, 'ray_silver'),        # barrel
           B(1, 9.5, 11, 13, 3.0, 'ray_glow', glow=True),  # glowing coil between the rings
           B(-0.4, 0.6, 10.8, 13.2, 2.8, 'ray_glow', glow=True),  # muzzle
           B(14, 22, 15, 16.4, 1.0, 'ray_silver'),         # top fin
           B(14, 17, 6, 7, 1.0, 'ray_silver'),             # trigger guard
           B(15, 15.8, 7, 9, 0.6, 'ray_silver'),           # trigger
           B(17, 23, -0.5, 9.5, 2.6, 'grip_black', rot=tilt(20, 9.5))]  # grip
    for z in (2, 5, 8):                                    # the three rings (octagons seen from the front)
        out.append(B(z, z + 1, 8.6, 15.4, 5.6, 'ray_red'))
        out.append(B(z, z + 1, 8.6, 15.4, 5.6, 'ray_red', rot=('z', 45, (z + 0.5, 12))))
    return out


# ------------------------------------------------------------------------------------------------- second batch
# Shared pieces for the modern guns

def scope(z0, z1, y, w=1.4, mounts=True):
    """A scope along the top at height y: tube, bigger front and rear bells, lens, mounts."""
    out = [B(z0 + 2, z1 - 2, y, y + w, w, 'blued'),
           B(z0, z0 + 2, y - 0.3, y + w + 0.3, w + 0.6, 'blued'),
           B(z1 - 2, z1, y - 0.2, y + w + 0.2, w + 0.4, 'blued'),
           B(z0 - 0.1, z0, y - 0.1, y + w + 0.1, w + 0.4, 'glass')]
    if mounts:
        out += [B(z0 + 3, z0 + 4, y - 1, y, 0.8, 'blued'), B(z1 - 4, z1 - 3, y - 1, y, 0.8, 'blued')]
    return out


def ak_body(stock, wood='polymer', mag='polymer', receiver='blued'):
    """Barrel, gas tube, handguard, receiver, curved magazine, grip: the AK pattern (S12, AN-94, Type 25)."""
    return [B(0, 2, 13.6, 15.4, 1.4, 'blued'),            # muzzle device
            B(2, 10, 14, 15, 1.0, 'blued'),               # barrel
            B(7, 8, 15, 16.6, 0.6, 'blued'),              # front sight
            B(8, 16, 15.2, 16.2, 1.4, wood),              # gas tube cover
            B(8, 16, 12.2, 15.2, 2.3, wood),              # handguard
            B(16, 30, 12, 16, 2.4, receiver),             # receiver
            B(17, 20.5, 8, 12, 1.6, mag, mag=True),                 # magazine (curved forward)
            B(16, 19.5, 3, 8.3, 1.6, mag, rot=tilt(17.6, 8.2, 22.5), mag=True),
            B(22, 26, 10.6, 12, 0.9, 'blued'),            # trigger guard
            B(25, 28, 5, 12, 2.0, wood, rot=tilt(26.5, 12)),
            B(27, 28, 16, 17, 1.0, 'blued')] + stock      # rear sight


def bolt_rifle(wood, front, receiver_top=None, monopod=False):
    """The Kar98k pattern: barrel, full wooden stock, bolt handle (Springfield, Arisaka)."""
    out = [B(0, 8, 14.4, 15.4, 1.0, 'blued'),
           front,
           B(7, 8.5, 12.4, 15.6, 2.0, 'steel'),
           B(8, 24, 12.4, 15, 2.0, wood),
           B(12, 22, 15, 15.8, 1.6, wood),
           B(13, 14, 12.2, 15.8, 2.1, 'steel'),
           B(20, 22, 15.8, 16.8, 1.0, 'blued'),
           B(22, 30, 14, 15.8, 1.8, 'blued'),
           B(28, 29, 12.8, 14.6, 0.8, 'steel', x=1.6, mv=True),
           B(28, 29.5, 12.4, 13.4, 1.2, 'steel', x=2.0, mv=True),
           B(24, 28, 11.6, 12.4, 1.8, 'steel'),
           B(26, 30, 10.4, 11.6, 0.9, 'steel'),
           B(24, 31, 11.6, 14, 2.2, wood),
           B(30, 34, 10, 14.2, 2.2, wood),
           B(34, 44, 8.4, 14.6, 2.2, wood),
           B(43.4, 44.2, 8.4, 14.6, 2.3, 'steel')]
    if receiver_top:
        out.append(receiver_top)
    if monopod:
        out.append(B(9, 10, 8, 12.4, 0.5, 'steel'))
    return out


# -- pistols ------------------------------------------------------------------------------------------------------

@gun('b23r', 'B23R', 1.0, 'pistol', grip=(17, 5))
def _():
    return [B(-1, 1, 9.2, 12.2, 2.4, 'blued'),            # compensator
            B(1, 23, 9, 12.4, 2.2, 'blued'),              # slide
            B(3, 20, 7.6, 9, 2.0, 'blued'),               # frame
            B(4, 6, 4, 7.6, 1.0, 'blued', rot=('x', 22.5, (5, 7.6))),  # folding front grip
            B(7, 13, 5, 5.7, 1.0, 'blued'),               # long trigger guard
            B(7, 7.7, 5, 7.6, 1.0, 'blued'),
            B(10.6, 11.2, 5.8, 7.6, 0.6, 'steel'),
            B(14, 20, -3, 7.6, 2.5, 'polymer', rot=tilt(17, 7.6)),     # grip with the long magazine
            B(14.6, 19.4, -3.7, -3, 2.1, 'blued', rot=tilt(17, 7.6)),
            B(1.5, 2.5, 12.4, 13.1, 0.5, 'blued'), B(20, 21.5, 12.4, 13.3, 1.4, 'blued')]


@gun('executioner', 'Executioner', 1.0, 'pistol', grip=(18, 5))
def _():
    return [B(0, 9, 10, 12, 1.6, 'steel'),                # barrel
            B(1, 9, 9, 10, 1.2, 'steel'),                 # under-lug
            B(0.5, 1.5, 12, 13, 0.5, 'steel'),            # front sight
            B(9, 16, 8, 12.8, 3.2, 'steel', mv=True),              # cylinder
            B(9, 19, 7.4, 12.8, 2.0, 'steel'),            # frame
            B(9, 19, 12.8, 13.4, 1.4, 'steel'),           # top strap
            B(18, 20, 12, 13.6, 0.8, 'steel'),            # hammer
            B(11, 16, 5.6, 6.3, 0.9, 'steel'),            # trigger guard
            B(13.8, 14.4, 6.3, 7.4, 0.6, 'steel'),
            B(15.5, 21, -2, 7.4, 2.4, 'polymer', rot=tilt(18, 7.4))]


@gun('five_seven', 'Five-Seven', 1.0, 'pistol', grip=(15, 5))
def _():
    return [B(0, 20.5, 9, 12, 2.0, 'polymer'),            # slide
            B(-0.2, 0.6, 9.4, 11.4, 1.0, 'blued'),        # muzzle
            B(2, 18, 7.4, 9, 2.0, 'polymer'),             # frame with its rail
            B(2, 8, 6.8, 7.4, 1.6, 'polymer'),
            B(7, 12, 5.2, 5.9, 0.9, 'polymer'),           # trigger guard
            B(7, 7.7, 5.2, 7.4, 0.9, 'polymer'),
            B(9.4, 10, 6, 7.4, 0.6, 'blued'),
            B(12, 19, -2, 7.4, 2.4, 'polymer', rot=tilt(15.5, 7.4)),
            B(1, 2, 12, 12.6, 0.5, 'blued'), B(18.5, 19.8, 12, 12.8, 1.4, 'blued')]


@gun('kap40', 'Kap-40', 1.0, 'pistol', grip=(14, 5))
def _():
    return [B(0, 19, 9, 12.2, 2.1, 'blued'),              # slide, with serrations
            B(14, 18, 12, 12.4, 2.2, 'steel'),
            B(2, 17, 7.4, 9, 2.0, 'tan'),                 # polymer frame
            B(2, 7, 6.8, 7.4, 1.6, 'tan'),
            B(7, 12, 5.2, 5.9, 0.9, 'tan'),
            B(7, 7.7, 5.2, 7.4, 0.9, 'tan'),
            B(9.4, 10, 6, 7.4, 0.6, 'blued'),
            B(11.5, 18, -1.6, 7.4, 2.4, 'tan', rot=tilt(14.8, 7.4)),
            B(1, 2, 12.2, 12.8, 0.5, 'blued'), B(17, 18.3, 12.2, 13, 1.4, 'blued')]


@gun('python', 'Python', 1.0, 'pistol', grip=(22, 5))
def _():
    return [B(0, 14, 10.2, 12.2, 1.4, 'chrome'),          # barrel
            B(0, 14, 12.2, 13, 0.8, 'chrome'),            # vent rib
            B(1, 14, 8.8, 10.2, 1.2, 'chrome'),           # full under-lug
            B(0.5, 2, 13, 14, 0.5, 'chrome'),             # front sight
            B(14, 20, 8.2, 12.8, 3.0, 'chrome', mv=True),          # cylinder
            B(14, 23, 7.6, 12.8, 2.0, 'chrome'),          # frame
            B(14, 23, 12.8, 13.4, 1.4, 'chrome'),
            B(22, 24, 12, 13.6, 0.8, 'chrome'),           # hammer
            B(16, 21, 5.6, 6.3, 0.9, 'chrome'),
            B(18.6, 19.2, 6.3, 7.6, 0.6, 'chrome'),
            B(19.5, 25, -2, 7.6, 2.4, 'wood', rot=tilt(22, 7.6))]


# -- shotguns ----------------------------------------------------------------------------------------------------

@gun('m1216', 'M1216', 2.5, 'shotgun', grip=(13.5, 9))
def _():
    return [B(0, 14, 11.6, 15.6, 3.0, 'blued'),           # four rotating tubes
            B(0, 1, 11.3, 15.9, 3.3, 'polymer'),
            B(14, 26, 11, 16, 2.6, 'polymer'),            # bullpup body
            B(3, 22, 16, 16.6, 1.2, 'polymer'),           # rail
            B(10, 14, 9.6, 11, 1.0, 'polymer'),           # trigger guard
            B(12, 15, 4, 11, 2.0, 'polymer', rot=tilt(13.5, 11)),
            B(25.4, 26.4, 10.4, 16.4, 2.8, 'grip_black')]


@gun('olympia', 'Olympia', 2.5, 'shotgun', grip=(28, 12))
def _():
    return [B(0, 24, 13.5, 14.9, 1.3, 'blued', x=0.7, brk=True),    # side-by-side barrels
            B(0, 24, 13.5, 14.9, 1.3, 'blued', x=-0.7, brk=True),
            B(0, 24, 14.9, 15.3, 0.6, 'blued', brk=True),
            B(14, 22, 12.2, 13.8, 2.4, 'wood_light', brk=True),     # fore-end
            B(24, 29, 11.4, 15.2, 2.6, 'chrome'),         # engraved action
            B(27, 28.2, 14.6, 16.2, 0.6, 'chrome', x=0.8),
            B(27, 28.2, 14.6, 16.2, 0.6, 'chrome', x=-0.8),
            B(25, 29, 10, 11.4, 1.0, 'chrome'),
            B(29, 36, 10.6, 14.6, 2.4, 'wood_light'),
            B(35, 44, 8.4, 14, 2.4, 'wood_light'),
            B(43.4, 44.2, 8.4, 14, 2.5, 'grip_black')]


@gun('r870', 'R870 MCS', 2.5, 'shotgun', grip=(20.5, 9))
def _():
    return [B(0, 13, 14, 15.2, 1.4, 'blued'),             # barrel
            B(0.5, 1.5, 15.2, 16, 0.6, 'blued'),
            B(2, 13, 12.6, 14, 1.2, 'blued'),             # magazine tube
            B(6, 12, 11.8, 14.6, 2.2, 'polymer', mv=True),         # pump
            B(13, 20, 11.4, 16, 2.4, 'blued'),            # receiver
            B(13, 21, 16, 16.6, 1.2, 'polymer'),          # rail
            B(15, 19, 10, 11.4, 1.0, 'blued'),
            B(19, 22, 4, 11.4, 2.0, 'polymer', rot=tilt(20.5, 11.4)),
            B(20, 33, 13.4, 14.6, 1.2, 'polymer'),        # stock tube
            B(26, 33, 10, 15.2, 1.8, 'polymer'),          # collapsible stock
            B(32.6, 33.6, 9.6, 15.6, 2.4, 'grip_black')]


@gun('s12', 'S12', 2.5, 'shotgun', grip=(26.5, 9))
def _():
    stock = [B(30, 32, 12.4, 15.6, 2.0, 'polymer'),      # folding stock
             B(32, 42, 14.4, 15.4, 0.8, 'polymer'),
             B(32, 42, 10.4, 11.4, 0.8, 'polymer', rot=('x', 22.5, (32, 11))),
             B(41, 42, 9.4, 15.4, 2.0, 'polymer')]
    body = ak_body(stock)
    body[6] = B(17, 21, 6.5, 12, 2.2, 'polymer', mag=True)          # straight box magazine for shells
    body[7] = B(17, 21, 6.5, 7, 2.2, 'polymer')
    return body


@gun('sawed_off', 'Sawed-off', 2.5, 'shotgun', grip=(15, 10))
def _():
    return [B(0, 10, 13.5, 14.9, 1.3, 'blued', x=0.7, brk=True),
            B(0, 10, 13.5, 14.9, 1.3, 'blued', x=-0.7, brk=True),
            B(0, 10, 14.9, 15.3, 0.6, 'blued', brk=True),
            B(4, 9, 12.2, 13.8, 2.4, 'wood_dark', brk=True),
            B(10, 14, 11.4, 15.2, 2.6, 'steel'),
            B(12.4, 13.6, 14.6, 16.2, 0.6, 'steel', x=0.8),
            B(12.4, 13.6, 14.6, 16.2, 0.6, 'steel', x=-0.8),
            B(10.6, 14, 10, 11.4, 1.0, 'steel'),
            B(14, 19, 6, 14, 2.4, 'wood_dark', rot=tilt(15, 14, -45))]   # cut-down grip


# -- assault rifles -------------------------------------------------------------------------------------------------

@gun('an94', 'AN-94', 2.5, 'rifle', grip=(26.5, 9))
def _():
    stock = [B(30, 40, 10, 15.6, 2.2, 'polymer'), B(39.4, 40.2, 9.6, 15.8, 2.4, 'grip_black')]
    body = ak_body(stock)
    body[6] = B(17, 20.5, 8, 12, 1.6, 'polymer', x=0.6, mag=True)  # its magazine leans off to the right
    body.append(B(14, 30, 16, 16.6, 1.2, 'polymer'))      # rail
    return body


@gun('m16a1', 'Colt M16A1', 2.5, 'rifle', grip=(26.5, 9))
def _():
    return [B(0, 2, 13.4, 15.4, 1.2, 'blued'),            # flash hider
            B(2, 9, 13.9, 14.9, 1.0, 'blued'),
            B(7, 8, 14.9, 17.6, 0.6, 'blued'),            # front sight post
            B(8, 19, 12.4, 15.6, 2.6, 'polymer'),         # handguard
            B(19, 28, 11.4, 16.2, 2.4, 'blued'),          # receiver
            B(19, 28, 16.2, 17.6, 1.0, 'blued'),          # carry handle
            B(20, 23, 5.6, 11.4, 1.6, 'steel', mag=True),           # 20-round magazine
            B(22.5, 26, 10, 11.4, 1.0, 'blued'),
            B(25, 28, 4, 11.4, 2.0, 'polymer', rot=tilt(26.5, 11.4)),
            B(28, 40, 9, 15.2, 2.2, 'polymer'),           # fixed stock
            B(39.4, 40.2, 9, 15.2, 2.3, 'grip_black')]


@gun('fal', 'FAL', 2.5, 'rifle', grip=(29.5, 9))
def _():
    return [B(0, 2, 13.6, 15.4, 1.4, 'blued'),
            B(2, 10, 14, 15, 1.0, 'blued'),
            B(8, 9, 15, 16.8, 0.6, 'blued'),
            B(10, 20, 12, 15.6, 2.4, 'polymer'),          # handguard
            B(20, 32, 12, 16, 2.4, 'blued'),              # receiver
            B(23, 27, 16, 17, 0.8, 'blued'),              # carry handle
            B(22, 26, 5.4, 12, 1.8, 'steel', mag=True),             # magazine
            B(25, 29, 10.6, 12, 0.9, 'blued'),
            B(28, 31, 4.6, 12, 2.0, 'polymer', rot=tilt(29.5, 12)),
            B(32, 44, 9, 15, 2.2, 'polymer'),
            B(43.4, 44.2, 9, 15, 2.3, 'grip_black')]


@gun('m8a1', 'M8A1', 2.5, 'rifle', grip=(22.5, 8))
def _():
    return [B(0, 2, 12.8, 14.4, 1.2, 'blued'),
            B(2, 6, 13, 14.2, 1.0, 'blued'),
            B(6, 30, 10.4, 16.4, 2.6, 'olive'),           # one-piece rounded body
            B(6, 8, 10.8, 16, 2.8, 'olive'),
            B(12, 22, 16.4, 17.2, 1.0, 'olive'),          # carry handle with the sight
            B(14, 20, 17.2, 18.4, 1.4, 'polymer'),
            B(14.6, 18, 5.4, 10.4, 1.6, 'polymer', mag=True),       # magazine
            B(18, 21, 8.8, 10.4, 1.0, 'olive'),
            B(21, 24, 3.6, 10.4, 2.0, 'olive', rot=tilt(22.5, 10.4)),
            B(30, 38, 10.4, 15.6, 2.2, 'olive'),          # stock
            B(37.4, 38.2, 10.4, 15.6, 2.3, 'grip_black')]


@gun('m14', 'M14', 2.5, 'rifle', grip=(30, 12))
def _():
    return [B(0, 2, 14, 15.8, 1.2, 'blued'),              # flash suppressor
            B(2, 9, 14.4, 15.4, 1.0, 'blued'),
            B(0.5, 1.5, 15.8, 16.8, 0.5, 'blued'),
            B(5, 24, 12.2, 15.2, 2.2, 'wood'),
            B(9, 20, 15.2, 16, 1.8, 'wood'),
            B(20, 30, 13.8, 16.2, 2.0, 'steel'),
            B(29, 30, 16.2, 17.4, 1.4, 'steel'),
            B(22, 26, 8, 12.2, 1.8, 'steel', mag=True),             # box magazine
            B(25, 29, 10.8, 12.2, 1.0, 'steel'),
            B(24, 31, 11.4, 14, 2.3, 'wood'),
            B(30, 34, 10, 14.6, 2.3, 'wood'),
            B(34, 44, 8.4, 15, 2.3, 'wood'),
            B(43.4, 44.2, 8.4, 15, 2.4, 'steel')]


@gun('m27', 'M27', 2.5, 'rifle', grip=(22.5, 8))
def _():
    return [B(0, 2, 13.4, 15.2, 1.3, 'blued'),
            B(2, 5, 13.8, 14.8, 1.0, 'blued'),
            B(5, 16, 12.2, 15.8, 2.6, 'polymer'),         # rail handguard
            B(16, 25, 11.6, 15.8, 2.4, 'blued'),
            B(5, 25, 15.8, 16.5, 1.2, 'polymer'),         # flat-top rail
            B(10, 12, 16.5, 17.6, 0.8, 'polymer'), B(22, 24, 16.5, 17.6, 1.2, 'polymer'),  # sights
            B(17, 20, 7.4, 11.6, 1.6, 'polymer', mag=True),         # magazine
            B(16.4, 19.4, 3.4, 7.6, 1.6, 'polymer', rot=tilt(17.4, 7.4, 22.5), mag=True),
            B(19, 23, 10.2, 11.6, 1.0, 'blued'),
            B(21, 24, 4, 11.6, 2.0, 'polymer', rot=tilt(22.5, 11.6)),
            B(25, 31, 13.4, 14.6, 1.2, 'polymer'),        # buffer tube
            B(28, 34, 10, 15.2, 2.0, 'polymer'),          # collapsible stock
            B(33.6, 34.4, 9.6, 15.4, 2.2, 'grip_black')]


@gun('mtar', 'MTAR', 2.5, 'rifle', grip=(12.5, 8))
def _():
    return [B(0, 4, 12.8, 14.2, 1.1, 'blued'),
            B(4, 29, 9.6, 15.6, 2.6, 'polymer'),          # bullpup body
            B(6, 18, 15.6, 16.4, 1.2, 'polymer'),         # rail
            B(6, 14, 6.2, 7, 1.0, 'polymer'),             # long trigger guard
            B(6, 7, 7, 9.6, 1.0, 'polymer'),
            B(11, 14, 3.6, 9.6, 2.0, 'polymer', rot=tilt(12.5, 9.6)),
            B(17, 20.5, 3.4, 9.6, 1.6, 'polymer', mag=True),        # magazine behind the grip
            B(28.4, 29.4, 9, 15.8, 2.8, 'grip_black')]


@gun('smr', 'SMR', 2.5, 'rifle', grip=(22.5, 8))
def _():
    return [B(0, 3, 13.4, 15.4, 1.4, 'blued'),            # muzzle brake
            B(3, 9, 13.9, 14.9, 1.0, 'blued'),
            B(9, 17, 12, 15.6, 2.4, 'tan'),               # handguard
            B(17, 27, 11.6, 16, 2.4, 'tan'),              # receiver
            B(9, 27, 16, 16.6, 1.2, 'polymer'),
            B(18, 21, 6, 11.6, 1.8, 'polymer', mag=True),           # magazine
            B(19.5, 23, 10.2, 11.6, 1.0, 'tan'),
            B(21, 24, 4, 11.6, 2.0, 'polymer', rot=tilt(22.5, 11.6)),
            B(27, 36, 14.2, 15.4, 1.0, 'tan'),            # skeleton stock
            B(27, 36, 10.6, 11.6, 1.0, 'tan'),
            B(35, 36.4, 10, 15.8, 2.2, 'tan')] + scope(12, 24, 17.2)


@gun('type25', 'Type 25', 2.5, 'rifle', grip=(26.5, 9))
def _():
    stock = [B(30, 38, 13.6, 14.8, 1.0, 'blued'), B(30, 38, 10.6, 11.6, 1.0, 'blued'),   # folding wire stock
             B(37, 38, 9.6, 15, 2.0, 'polymer')]
    return ak_body(stock, wood='polymer', mag='steel')


# -- light machine guns ----------------------------------------------------------------------------------------------

@gun('hamr', 'HAMR', 3.0, 'lmg', grip=(24, 9))
def _():
    return [B(0, 2, 12.8, 15.2, 1.8, 'blued'),
            B(2, 10, 12.4, 15.6, 2.4, 'polymer'),         # barrel shroud
            B(10, 28, 10.6, 16.4, 3.0, 'polymer'),        # body
            B(8, 28, 16.4, 17.2, 1.2, 'polymer'),
            B(13, 20, 3.6, 10.6, 3.4, 'polymer', mag=True),         # drum
            B(13, 20, 3.6, 10.6, 3.4, 'polymer', rot=('x', 45, (16.5, 7.1)), mag=True),
            B(4, 11, 10, 11, 1.6, 'steel'),               # folded bipod
            B(22, 25, 9.2, 10.6, 1.0, 'polymer'),
            B(23, 26, 3, 10.6, 2.2, 'polymer', rot=tilt(24.5, 10.6)),
            B(28, 36, 10.4, 16, 2.2, 'polymer'),
            B(35.4, 36.2, 10, 16.2, 2.4, 'grip_black')]


@gun('lsat', 'LSAT', 3.0, 'lmg', grip=(24.5, 9))
def _():
    return [B(0, 2, 13.2, 15, 1.4, 'blued'),
            B(2, 9, 13.6, 14.6, 1.0, 'blued'),
            B(9, 17, 12, 15.6, 2.6, 'polymer'),           # rail handguard
            B(17, 27, 11.4, 16.4, 2.8, 'polymer'),        # receiver
            B(17, 24, 16.4, 17.2, 2.4, 'olive'),          # feed cover
            B(15, 21, 3.4, 11.4, 3.8, 'olive', x=-0.6, mag=True),   # big box magazine
            B(19, 24, 17.2, 18.2, 0.8, 'polymer'),        # carry handle
            B(22, 25, 10, 11.4, 1.0, 'polymer'),
            B(23, 26, 3.8, 11.4, 2.0, 'polymer', rot=tilt(24.5, 11.4)),
            B(27, 33, 13.2, 14.4, 1.2, 'polymer'),
            B(29, 36, 10, 15.4, 2.0, 'polymer'),
            B(35.6, 36.4, 9.6, 15.6, 2.2, 'grip_black')]


@gun('rpd', 'RPD', 3.0, 'lmg', grip=(25.5, 10))
def _():
    return [B(0, 15, 13.8, 14.8, 1.0, 'blued'),           # long barrel
            B(0.5, 1.5, 14.8, 16, 0.5, 'blued'),
            B(4, 12, 12.6, 13.4, 1.6, 'steel'),           # folded bipod
            B(9, 15, 12, 15.2, 2.4, 'wood'),              # handguard
            B(15, 24, 11.8, 16, 2.4, 'blued'),            # receiver
            B(15, 21, 4.4, 11, 3.2, 'blued', mag=True),             # drum
            B(15, 21, 4.4, 11, 3.2, 'blued', rot=('x', 45, (18, 7.7)), mag=True),
            B(23, 26, 10.4, 11.8, 1.0, 'blued'),
            B(24, 27, 4.6, 11.8, 2.0, 'wood', rot=tilt(25.5, 11.8)),
            B(24, 30, 10.6, 15.4, 2.2, 'wood'),
            B(29, 35, 8.6, 15, 2.2, 'wood'),
            B(34.4, 35.2, 8.6, 15, 2.3, 'steel')]


# -- submachine guns ------------------------------------------------------------------------------------------------

@gun('chicom', 'Chicom CQB', 2.0, 'smg', grip=(12.5, 6))
def _():
    return [B(0, 9, 11.8, 14.8, 2.4, 'blued'),            # built-in silencer
            B(9, 25, 9, 15.6, 2.6, 'polymer'),            # bullpup body
            B(10, 19, 15.6, 16.6, 1.0, 'polymer'),        # carry handle / sight
            B(12, 17, 16.6, 17.4, 1.4, 'polymer'),
            B(10, 14, 7, 7.8, 0.9, 'polymer'),
            B(11, 14, 1.6, 9, 2.0, 'polymer', rot=tilt(12.5, 9)),
            B(16, 19, 1.4, 9, 1.6, 'polymer', mag=True),            # magazine behind the grip
            B(24.4, 25.4, 8.6, 15.8, 2.8, 'grip_black')]


@gun('mp5', 'MP5', 2.0, 'smg', grip=(19.5, 7))
def _():
    return [B(0, 5, 13.4, 14.6, 1.0, 'blued'),
            B(0, 8, 14.6, 15.4, 1.2, 'blued'),            # cocking tube
            B(1, 3, 14.2, 16.8, 1.4, 'blued'),            # front sight ring
            B(5, 12, 11.8, 14.8, 2.2, 'polymer'),         # handguard
            B(12, 24, 12, 15.6, 2.2, 'blued'),            # receiver
            B(21, 23, 15.6, 17, 1.4, 'blued'),            # drum sight
            B(13, 16, 7.6, 12, 1.4, 'blued', mag=True),             # curved magazine
            B(12.2, 15.2, 2.6, 7.8, 1.4, 'blued', rot=tilt(13.4, 7.6, 22.5), mag=True),
            B(16, 22, 10, 12, 2.0, 'polymer'),            # trigger group
            B(18, 21, 3.6, 10, 2.0, 'polymer', rot=tilt(19.5, 10)),
            B(24, 34, 9.6, 14.6, 2.0, 'polymer'),         # fixed stock
            B(33.4, 34.2, 9.4, 14.8, 2.2, 'grip_black')]


@gun('pdw57', 'PDW-57', 2.0, 'smg', grip=(13.5, 7))
def _():
    return [B(-1, 1, 10.6, 12.4, 1.2, 'blued'),           # muzzle
            B(1, 25, 7.6, 14.6, 3.0, 'polymer'),          # body
            B(3, 20, 14.6, 16, 2.4, 'tan', mag=True),               # magazine along the top
            B(20, 23, 14.6, 16.4, 1.4, 'polymer'),        # sight
            B(4, 7, 3.4, 7.6, 2.2, 'polymer'),            # front of the thumb hole
            B(11, 15, 3.4, 7.6, 2.2, 'polymer'),          # grip
            B(4, 15, 2.6, 3.4, 2.2, 'polymer'),
            B(24.4, 25.4, 6.4, 14.8, 3.2, 'grip_black')]


# -- sniper rifles --------------------------------------------------------------------------------------------------

@gun('barrett', 'Barret 50KCAL', 3.5, 'rifle', grip=(30, 9))
def _():
    return [B(0, 3, 12.8, 16.6, 3.0, 'blued'),            # muzzle brake
            B(3, 16, 14, 15.4, 1.4, 'blued'),             # barrel
            B(14, 32, 12.4, 16.2, 2.6, 'blued'),          # upper receiver
            B(23, 27, 7.6, 12.4, 2.2, 'blued', mag=True),           # magazine
            B(12, 13, 8, 12.4, 0.6, 'steel', x=1.2), B(12, 13, 8, 12.4, 0.6, 'steel', x=-1.2),  # bipod
            B(27, 31, 10.6, 12.4, 1.0, 'blued'),
            B(29, 32, 4.6, 12.4, 2.0, 'polymer', rot=tilt(30.5, 12.4)),
            B(32, 41, 10.4, 16.2, 2.4, 'blued'),
            B(40.4, 41.4, 10, 16.6, 2.8, 'grip_black')] + scope(17, 29, 17.2, 1.6)


@gun('dsr50', 'DSR 50', 3.5, 'rifle', grip=(19.5, 9))
def _():
    return [B(0, 3, 13.2, 16, 2.4, 'blued'),
            B(3, 14, 14, 15.2, 1.2, 'blued'),
            B(14, 37, 11, 16.2, 2.6, 'tan'),              # bullpup body
            B(10, 14, 12, 15.6, 2.2, 'polymer'),
            B(24, 28, 7, 11, 2.0, 'polymer', mag=True),             # magazine behind the grip
            B(17, 21, 9.6, 11, 1.0, 'polymer'),
            B(18, 21, 3.6, 11, 2.0, 'polymer', rot=tilt(19.5, 11)),
            B(28, 35, 16.2, 17.2, 1.8, 'polymer'),        # cheek rest
            B(36.4, 37.4, 10.4, 16.6, 2.8, 'grip_black'),
            B(30, 31, 13, 15, 0.8, 'steel', x=1.8, mv=True)] + scope(10, 24, 17.6, 1.6)   # bolt handle


@gun('svu', 'SVU-AS', 2.5, 'rifle', grip=(17.5, 9))
def _():
    return [B(0, 4, 13, 15.6, 2.0, 'blued'),              # long muzzle brake
            B(4, 12, 13.8, 14.8, 1.0, 'blued'),
            B(8, 14, 12, 15.4, 2.2, 'polymer'),           # handguard
            B(14, 36, 11, 15.8, 2.4, 'polymer'),          # bullpup body
            B(23, 27, 7, 11, 1.6, 'polymer', mag=True),             # magazine
            B(22.4, 25.6, 2.8, 7.2, 1.6, 'polymer', rot=tilt(23.4, 7, 22.5), mag=True),
            B(16, 19, 9.6, 11, 1.0, 'polymer'),
            B(16, 19, 3.6, 11, 2.0, 'polymer', rot=tilt(17.5, 11)),
            B(35.4, 36.4, 10.4, 16, 2.6, 'grip_black')] + scope(10, 22, 16.8)


@gun('springfield', 'Springfield', 2.5, 'rifle', grip=(30, 12))
def _():
    return bolt_rifle('wood_light', B(0, 1.2, 15.4, 16.8, 0.6, 'blued'))


@gun('arisaka', 'Arisaka', 2.5, 'rifle', grip=(30, 12))
def _():
    return bolt_rifle('wood', B(0, 1.5, 15.4, 16.6, 0.6, 'blued'),
                      receiver_top=B(22, 28, 15.8, 16.4, 2.0, 'steel'), monopod=True)   # dust cover, monopod


@gun('m1_carbine', 'M1 Carbine', 2.5, 'rifle', grip=(26, 11))
def _():
    return [B(0, 8, 14, 15, 1.0, 'blued'),
            B(0, 1.2, 15, 16.2, 0.6, 'blued'),
            B(1, 4, 13, 14, 1.0, 'steel'),                # bayonet lug
            B(4, 24, 12, 14.8, 2.0, 'wood'),
            B(8, 18, 14.8, 15.6, 1.6, 'wood'),
            B(7, 8.5, 11.8, 15.8, 2.1, 'steel'),          # band
            B(18, 26, 14, 15.8, 1.8, 'blued'),            # receiver
            B(24, 25.6, 15.8, 16.8, 1.0, 'blued'),
            B(20, 23, 9, 12, 1.4, 'blued', mag=True),               # short magazine
            B(23, 27, 10.2, 11.4, 0.9, 'blued'),
            B(24, 28, 10, 14, 2.0, 'wood'),
            B(28, 36, 8.6, 14.2, 2.0, 'wood'),
            B(35.4, 36.2, 8.6, 14.2, 2.1, 'steel')]


@gun('wunderwaffe', 'Wunderwaffe DG-2', 2.5, 'rifle', grip=(29.5, 9))
def _():
    out = [B(-0.4, 1.4, 11, 17, 4.2, 'steel_dark'),           # the emitter dish at the muzzle
           B(-1.0, 0, 12.6, 15.4, 1.6, 'tesla_glow', glow=True),  # its glowing tip
           B(1.4, 16, 12.6, 15.4, 2.6, 'steel'),             # barrel
           B(2, 3, 15.4, 17.6, 0.5, 'steel'),                # front sight
           B(16, 30, 10, 17, 4.0, 'blued'),                  # the generator body
           B(18, 28, 11, 15.6, 4.8, 'copper'),               # copper windings, both sides
           B(25.5, 28, 13.6, 16.2, 4.8, 'brass'),            # gauge
           B(14, 18, 16, 16.8, 1.0, 'copper'),               # cable to the barrel
           B(17, 27.8, 11.4, 15.6, 0.6, 'brass', x=-2.3),    # the bulbs' socket plate, on the left side
           B(28, 29.2, 17, 18.4, 0.6, 'steel'),              # rear sight
           B(17, 20, 5, 10, 1.8, 'wood_dark'),               # front grip
           B(26, 29, 7.4, 8.2, 1.0, 'steel'),                # trigger guard
           B(27, 27.6, 8.2, 10, 0.6, 'steel'),               # trigger
           B(28, 31, 3, 10, 2.2, 'wood_dark', rot=tilt(29.5, 10)),  # pistol grip
           B(30, 44, 9, 15, 2.6, 'wood'),                    # stock
           B(36, 44, 6, 9, 2.6, 'wood'),
           B(43.4, 44.4, 6, 15, 2.8, 'steel_dark')]          # butt plate
    for z in range(3, 15, 2):                                # copper coils round the barrel
        out.append(B(z, z + 1, 12.2, 15.8, 3.2, 'copper'))
    for z in (17.6, 20.9, 24.2):                             # the three bulbs, sticking out sideways to the left (the "magazine")
        out += [B(z, z + 2.8, 12, 15, 3.8, 'tesla_glow', x=-4.5, glow=True, mag=True),
                B(z + 0.6, z + 2.2, 12.6, 14.4, 0.8, 'brass', x=-6.8, mag=True)]
    return out


# ------------------------------------------------------------------------------------------------- model json, display

# How a gun is held: its rotation, and where its grip goes (Minecraft's display terms; worked out with preview.py, which
# follows the game's own transforms). First person: the grip low on the right, the gun pointing a little in towards the
# crosshair. Third person: level, forwards, the grip in the hand (the arm is raised 18 degrees when holding an item).
FP_ROTATION, FP_GRIP = [3, 14, 0], [-0.24, 0.1, -0.2]
TP_ROTATION, TP_GRIP = [72, 0, 0], [0.0, 0.0, 0.0]


def centred(boxes):
    """The boxes moved so the gun's bounding box is centred on (8, 8, 8), and its size."""
    lo = [min(b['from'][i] for b in boxes) for i in range(3)]
    hi = [max(b['to'][i] for b in boxes) for i in range(3)]
    d = [8 - (lo[i] + hi[i]) / 2 for i in range(3)]
    out = []
    for b in boxes:
        c = dict(b)
        c['from'] = [b['from'][i] + d[i] for i in range(3)]
        c['to'] = [b['to'][i] + d[i] for i in range(3)]
        if b['rot']:
            axis, ang, (oz, oy) = b['rot']
            c['rot'] = (axis, ang, (oz + d[2], oy + d[1]))
        out.append(c)
    return out, [hi[i] - lo[i] for i in range(3)]


def element(b):
    uv = swatch_uv(b['mat'])
    e = {'from': [round(v, 3) for v in b['from']], 'to': [round(v, 3) for v in b['to']],
         'faces': {f: {'uv': uv, 'texture': '#gun'} for f in ('north', 'east', 'south', 'west', 'up', 'down')}}
    if b['rot']:
        axis, ang, (oz, oy) = b['rot']
        ox = (b['from'][0] + b['to'][0]) / 2
        e['rotation'] = {'angle': ang, 'axis': axis, 'origin': [round(ox, 3), round(oy, 3), round(oz, 3)]}
    if b['glow']:
        e['light_emission'] = 15
    return e


def grip_point(gid):
    """The grip in item space (blocks, the model centred: what the display transform turns and scales)."""
    g = GUNS[gid]
    lo = [min(b['from'][i] for b in g['boxes']) for i in range(3)]
    hi = [max(b['to'][i] for b in g['boxes']) for i in range(3)]
    z, y = g['grip']
    return [0.0, (y - (lo[1] + hi[1]) / 2) / 16, (z - (lo[2] + hi[2]) / 2) / 16]


def translation(rotation, s, grip, at):
    """The display translation (pixels) that puts the grip at `at` (blocks) once turned and scaled."""
    import math
    ax, ay, az = (math.radians(a) for a in rotation)
    x, y, z = (c * s for c in grip)
    # rotate XYZ (x first applied last: R = Rx * Ry * Rz)
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    return [round((at[0] - x) * 16, 3), round((at[1] - y) * 16, 3), round((at[2] - z) * 16, 3)]


# Held a bit bigger than life: Minecraft's hands and arms are big, and at true size the guns looked small in them
HELD_SIZE = 1.5


def scale(gid):
    """Held at its real size: one metre is a block (16 pixels of item space), a model pixel is u centimetres."""
    return round(16 * GUNS[gid]['u'] / 100 * HELD_SIZE, 4)


# -- not guns: grenades and perk bottles (drawn with the same transforms, but held upright) ------------------------------
# Built in centimetres (u = 1). Held at the middle; the mod's drinking animation turns the bottle to the mouth.
PERKS = {'juggernog': 'jug_red', 'speed_cola': 'speed_green', 'quick_revive': 'revive_blue', 'double_tap': 'dtap_orange',
         'stamin_up': 'stamin_yellow', 'phd_flopper': 'phd_purple', 'deadshot_daiq': 'deadshot_gray', 'mule_kick': 'mule_green',
         'electric_c': 'cherry_blue'}
UPRIGHT = [0, 0, 0]


def bottle(glass):
    """A soda bottle: body, label, shoulder, neck, crown cap (20 cm tall)."""
    return [B(5, 11, 0, 12, 6.0, glass),                       # body
            B(4.8, 11.2, 3.5, 9.5, 6.4, 'label_cream'),        # label, all round
            B(4.7, 11.3, 5.6, 7.4, 6.6, glass),                # the label's colour stripe
            B(6, 10, 12, 14.5, 4.4, glass),                    # shoulder
            B(6.8, 9.2, 14.5, 18.5, 2.4, glass),               # neck
            B(6.6, 9.4, 18.5, 19.6, 2.8, 'cap_gold')]          # cap


for _perk, _glass in PERKS.items():
    GUNS['perk_' + _perk] = {'name': _perk, 'u': 1.0, 'kind': 'prop', 'grip': (8, 8), 'boxes': bottle(_glass),
                             'fp_rot': UPRIGHT, 'tp_rot': UPRIGHT}


@gun('frag_grenade', 'Grenade', 1.0, 'prop', grip=(8, 4))
def _():
    return [B(5.2, 10.8, 0, 7, 5.6, 'olive'),                  # body
            B(5, 11, 1.5, 2.2, 5.9, 'olive'), B(5, 11, 3.2, 3.9, 5.9, 'olive'), B(5, 11, 4.9, 5.6, 5.9, 'olive'),  # segments
            B(6.8, 9.2, 7, 8.6, 2.4, 'steel'),                 # fuse
            B(9.2, 10.2, 2.5, 8.6, 1.2, 'steel'),              # spoon
            B(5.6, 6.8, 7.6, 9.2, 0.4, 'brass', x=1.0)]        # pin ring


@gun('monkey_bomb', 'Monkey Bomb', 1.0, 'prop', grip=(8, 8))
def _():
    return [B(7, 9, 0, 5, 1.8, 'fur', x=-1.3), B(7, 9, 0, 5, 1.8, 'fur', x=1.3),   # legs
            B(6, 10, 5, 12, 5.5, 'fur'),                       # body
            B(5.8, 6.4, 6, 10.5, 3.4, 'fur_light'),            # belly
            B(5.5, 10.5, 12, 17, 5.0, 'fur'),                  # head
            B(5.2, 6, 12.6, 15.4, 3.6, 'fur_light'),           # face
            B(7, 9, 14, 16, 1.0, 'fur', x=-3.0), B(7, 9, 14, 16, 1.0, 'fur', x=3.0),  # ears
            B(6.6, 9.4, 17, 19.2, 2.8, 'fez_red'),             # fez
            B(4, 7, 8, 9.4, 1.4, 'fur', x=-3.4), B(4, 7, 8, 9.4, 1.4, 'fur', x=3.4),  # arms, forward
            B(2, 6, 6.6, 10.8, 0.5, 'brass', x=-3.9), B(2, 6, 6.6, 10.8, 0.5, 'brass', x=3.9),  # cymbals
            B(10, 12, 8, 9, 1.0, 'steel'), B(11.6, 12.2, 7, 10, 2.6, 'steel')]  # wind-up key


@gun('teddy_bear', 'Teddy Bear', 2.2, 'prop', grip=(8, 9))
def _():
    """The mystery box's teddy bear, sitting up, its face to +z (south: what a billboarded display turns to the viewer)."""
    out = [B(5, 11, 3, 11, 7.0, 'fur'),                       # body
           B(10.6, 11.4, 4, 9.5, 4.4, 'fur_light'),           # belly
           B(5, 11.5, 11, 17.5, 7.0, 'fur'),                  # head
           B(11.5, 13.5, 12, 14.5, 3.0, 'fur_light'),         # snout
           B(13.5, 14.0, 13.4, 14.4, 1.2, 'grip_black')]      # nose
    for x in (-1, 1):
        out += [B(11.5, 12.0, 15, 16, 0.9, 'grip_black', x=1.5 * x),     # eye
                B(7, 9.5, 17, 19.5, 2.2, 'fur', x=2.8 * x),              # ear
                B(9.5, 9.9, 17.5, 19, 1.2, 'fur_light', x=2.8 * x),
                B(6.5, 10, 5, 10, 2.0, 'fur', x=4.4 * x),                # arm
                B(8, 14, 0, 3.5, 2.8, 'fur', x=2.0 * x),                 # leg, out in front
                B(14, 14.4, 0.4, 3.1, 2.2, 'fur_light', x=2.0 * x)]      # foot pad
    return out


@gun('box_broken', 'Broken Mystery Box', 1.0, 'prop', grip=(8, 8))
def _():
    """A mystery box spot the box isn't at: the chest smashed in. Block pixels as they are (raw: not centred), the front
    to -z (north); shown with the display transform NONE at the block's middle, turned to the chest's facing."""
    return [B(1, 15, 0, 2.5, 14, 'wood_dark'),                                    # what's left of the bottom
            B(3, 13, 2.5, 2.8, 10, 'bakelite'),                                   # the dark inside
            B(13, 15, 2.5, 8, 14, 'wood'),                                        # back wall, broken off
            B(13.2, 14.8, 8, 10, 5, 'wood', x=-3.5),                              #   a jagged bit still standing
            B(3, 13, 2.5, 6, 2, 'wood', x=-6),                                    # left wall, low
            B(4, 12, 2, 9, 1.6, 'wood', x=6.6, rot=('z', -22.5, (8, 2))),         # right wall, falling outwards
            B(2, 14, 2.5, 4.5, 13, 'wood_dark', rot=('x', -22.5, (14, 3.5))),     # the lid, caved in
            B(0.4, 1.4, 1.5, 4, 2.4, 'steel'),                                    # the latch
            B(-2, 6, 0, 1, 2.2, 'wood', x=-8.5, rot=('y', 22.5, (2, 0.5))),       # loose planks
            B(11, 18, 0, 1, 2.2, 'wood', x=8.5, rot=('y', -45, (14.5, 0.5))),
            B(-1.5, 1.5, 0, 0.8, 4.5, 'wood_dark', x=3, rot=('y', 22.5, (0, 0.4)))]


GUNS['box_broken']['raw'] = True

for _g in ('frag_grenade', 'monkey_bomb', 'teddy_bear'):
    GUNS[_g]['fp_rot'] = UPRIGHT
    GUNS[_g]['tp_rot'] = UPRIGHT


def model(gid, part=None):
    """part: None (the whole gun), 'nomag' (without its magazine) or 'mag' (the magazine alone), all placed alike."""
    g = GUNS[gid]
    boxes, size = centred(g['boxes'])
    if g.get('raw'):
        boxes = [dict(b) for b in g['boxes']]
    if part == 'nomag':
        boxes = [b for b in boxes if not b['mag']]
    elif part == 'mag':
        boxes = [b for b in boxes if b['mag']]
    elif part == 'frame':
        boxes = [b for b in boxes if not b['brk']]
    elif part == 'barrel':
        boxes = [b for b in boxes if b['brk']]
    elif part == 'fixed':
        boxes = [b for b in boxes if not b['mv']]
    elif part == 'moving':
        boxes = [b for b in boxes if b['mv']]
    s = scale(gid)
    gui = round(12.0 / max(size[2], size[1]), 3)  # fills a slot, seen from the side
    grip = grip_point(gid)
    fp_rot, tp_rot = g.get('fp_rot', FP_ROTATION), g.get('tp_rot', TP_ROTATION)
    fp = translation(fp_rot, s, grip, FP_GRIP)
    tp = translation(tp_rot, s, grip, TP_GRIP)
    display = {
        'firstperson_righthand': {'rotation': fp_rot, 'translation': fp, 'scale': [s, s, s]},
        'firstperson_lefthand': {'rotation': fp_rot, 'translation': fp, 'scale': [s, s, s]},
        'thirdperson_righthand': {'rotation': tp_rot, 'translation': tp, 'scale': [s, s, s]},
        'thirdperson_lefthand': {'rotation': tp_rot, 'translation': tp, 'scale': [s, s, s]},
        'gui': {'rotation': [15, 150, 0] if g['kind'] == 'prop' else [0, -90, 0], 'translation': [0, 0, 0], 'scale': [gui, gui, gui]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [round(s * 0.7, 4)] * 3},
        'fixed': {'rotation': [0, -90, 0], 'translation': [0, 0, 0], 'scale': [gui, gui, gui]},
    }
    return {'textures': {'gun': NS + ':item/gun', 'particle': NS + ':item/gun'},
            'elements': [element(b) for b in boxes], 'display': display}


# ------------------------------------------------------------------------------------------------- mystery box effects
# Flat sprites (one 16x16-pixel square, both sides, full bright) with soft translucent textures: the box's aura (a halo that
# faces the camera, and the same lying on the floor), its beam (levels 0-3: fainter and fainter as a player gets near) and
# the "?" over it (glow levels 0-2, cycled for a pulse). The plugin's BoxEffects shows them with item displays.
BEAM_LEVELS = (1.0, 0.7, 0.45, 0.28)
QMARK_GLOW = (0.55, 0.85, 1.15)
QMARK = ['.###.', '#...#', '....#', '...#.', '..#..', '.....', '..#..']


def _rgba(c, a):
    return (int(c[0]), int(c[1]), int(c[2]), max(0, min(255, int(a))) if a >= 28 else 0)


def _mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def fx_halo():
    import math
    n = 64
    px = [[(0, 0, 0, 0)] * n for _ in range(n)]
    for y in range(n):
        for x in range(n):
            r = math.hypot(x + 0.5 - n / 2, y + 0.5 - n / 2) / (n / 2)
            if r < 1:
                px[y][x] = _rgba(_mix((240, 252, 255), (80, 175, 255), min(1, r ** 0.5)), 240 * (1 - r) ** 1.3)
    return px


def fx_beam(level):
    import math
    w, h = 16, 64
    f = BEAM_LEVELS[level]
    px = [[(0, 0, 0, 0)] * w for _ in range(h)]
    for y in range(h):
        v = (y + 0.5) / h                                   # 0 at the top (the sky end), 1 at the box
        for x in range(w):
            dx = abs(x + 0.5 - w / 2) / (w / 2)
            core, haze = math.exp(-(dx / 0.22) ** 2), math.exp(-(dx / 0.75) ** 2)
            streak = 0.85 + 0.3 * noise(x, y // 6, 17)
            a = (core * 0.95 + haze * 0.5) * 255 * f * v ** 0.6 * streak
            px[y][x] = _rgba(_mix((70, 150, 255), (235, 250, 255), core), a)
    return px


def fx_qmark(level):
    n, k = 32, 3
    g = QMARK_GLOW[level]
    on = set()
    ox, oy = (n - 5 * k) // 2, (n - 7 * k) // 2
    for j, row in enumerate(QMARK):
        for i, ch in enumerate(row):
            if ch == '#':
                for a in range(k):
                    for b in range(k):
                        on.add((ox + i * k + a, oy + j * k + b))
    import math
    px = [[(0, 0, 0, 0)] * n for _ in range(n)]
    for y in range(n):
        for x in range(n):
            if (x, y) in on:
                px[y][x] = (235, 250, 255, 255)
                continue
            d = min(math.hypot(x - a, y - b) for a, b in on)
            px[y][x] = _rgba((90, 190, 255), 200 * g * math.exp(-d / 2.6))
    return px


def sprite(tex):
    """A flat square sprite (16 pixels = a block when shown with the transform NONE), each side reads the right way round seen from its own side."""
    t = NS + ':item/' + tex
    return {'textures': {'t': t, 'particle': t},
            'elements': [{'from': [0, 0, 8], 'to': [16, 16, 8], 'light_emission': 15, 'shade': False,
                          'faces': {'south': {'uv': [0, 0, 16, 16], 'texture': '#t'},
                                    'north': {'uv': [0, 0, 16, 16], 'texture': '#t'}}}]}


def fx_sprites():
    out = {'fx_halo': fx_halo()}
    for i in range(len(BEAM_LEVELS)):
        out['fx_beam_%d' % i] = fx_beam(i)
    for i in range(len(QMARK_GLOW)):
        out['fx_qmark_%d' % i] = fx_qmark(i)
    return out


def write_pack(out):
    def put(rel, data):
        p = os.path.join(out, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, 'wb') as f:
            f.write(data if isinstance(data, bytes) else json.dumps(data, indent=1).encode())
    put('pack.mcmeta', {'pack': {'description': 'MinecraftZombies gun models', 'pack_format': 97,
                                 'supported_formats': {'min_inclusive': 46, 'max_inclusive': 97},
                                 'min_format': [46, 0], 'max_format': [97, 1]}})
    put('assets/%s/textures/item/gun.png' % NS, png(sheet()))
    put('assets/%s/textures/item/gun_pap.png' % NS, png(sheet(True)))
    for gid in GUNS:
        put('assets/%s/models/item/%s.json' % (NS, gid), model(gid))
        put('assets/%s/models/item/%s_pap.json' % (NS, gid),
            {'parent': NS + ':item/' + gid, 'textures': {'gun': NS + ':item/gun_pap', 'particle': NS + ':item/gun_pap'}})
        names = [gid]
        parts = []
        if any(b['mag'] for b in GUNS[gid]['boxes']):
            parts += ['nomag', 'mag']
        if any(b['brk'] for b in GUNS[gid]['boxes']):
            parts += ['frame', 'barrel']
        if any(b['mv'] for b in GUNS[gid]['boxes']):
            parts += ['fixed', 'moving']
        if parts:
            for part in parts:
                put('assets/%s/models/item/%s_%s.json' % (NS, gid, part), model(gid, part))
                put('assets/%s/models/item/%s_%s_pap.json' % (NS, gid, part),
                    {'parent': NS + ':item/' + gid + '_' + part,
                     'textures': {'gun': NS + ':item/gun_pap', 'particle': NS + ':item/gun_pap'}})
                names.append(gid + '_' + part)
        for name in names:
            for v in ('', '_pap'):
                put('assets/%s/items/%s%s.json' % (NS, name, v), {'model': {'type': 'minecraft:model', 'model': NS + ':item/' + name + v}})
    for name, px in fx_sprites().items():
        put('assets/%s/textures/item/%s.png' % (NS, name), png(px))
        put('assets/%s/models/item/%s.json' % (NS, name), sprite(name))
        put('assets/%s/items/%s.json' % (NS, name), {'model': {'type': 'minecraft:model', 'model': NS + ':item/' + name}})
    print('wrote', len(GUNS), 'guns to', out)


def mag_table():
    """Java lines for the mod: each gun's magazine centre from its grip (blocks as held: ahead, up)."""
    out = []
    for gid, g in GUNS.items():
        mags = [b for b in g['boxes'] if b['mag']]
        if not mags:
            continue
        k = g['u'] / 100 * HELD_SIZE
        zc = sum((b['from'][2] + b['to'][2]) / 2 for b in mags) / len(mags)
        yc = sum((b['from'][1] + b['to'][1]) / 2 for b in mags) / len(mags)
        gz, gy = g['grip']
        out.append('\t\tMAG.put("%s", new float[] {%.3fF, %.3fF});' % (gid, (gz - zc) * k, (yc - gy) * k))
    return '\n'.join(out)


def hinge_table():
    """Java lines for the mod: each break-action's hinge from its grip (blocks as held: ahead, up)."""
    out = []
    for gid, g in GUNS.items():
        brk = [b for b in g['boxes'] if b['brk']]
        if not brk:
            continue
        k = g['u'] / 100 * HELD_SIZE
        z = max(b['to'][2] for b in brk)
        y = min(b['from'][1] for b in brk)
        gz, gy = g['grip']
        out.append('\t\tHINGE.put("%s", new float[] {%.3fF, %.3fF});' % (gid, (gz - z) * k, (y - gy) * k))
    return '\n'.join(out)


def moving_table():
    """Java lines for the mod: each working part's centre from the grip (blocks as held: ahead, up, right)."""
    out = []
    for gid, g in GUNS.items():
        mv = [b for b in g['boxes'] if b['mv']]
        if not mv:
            continue
        k = g['u'] / 100 * HELD_SIZE
        zc = sum((b['from'][2] + b['to'][2]) / 2 for b in mv) / len(mv)
        yc = sum((b['from'][1] + b['to'][1]) / 2 for b in mv) / len(mv)
        xc = sum((b['from'][0] + b['to'][0]) / 2 - 8 for b in mv) / len(mv)
        gz, gy = g['grip']
        out.append('\t\tMOVING.put("%s", new float[] {%.3fF, %.3fF, %.3fF});' % (gid, (gz - zc) * k, (yc - gy) * k, xc * k))
    return '\n'.join(out)


if __name__ == '__main__':
    if sys.argv[1] == '--moving':
        print(moving_table())
    elif sys.argv[1] == '--hinges':
        print(hinge_table())
    elif sys.argv[1] == '--mags':
        print(mag_table())
    else:
        write_pack(sys.argv[1])
