"""
Preview of the gun models the way Minecraft places them: first person (the camera's view), third person (a player seen
from the side, holding it) and the inventory slot. Follows Minecraft's own transforms (ItemInHandRenderer, the humanoid
arm and ItemInHandLayer, ItemTransform), so a gun placed right here is placed right in the game.

    python tools/gun_models/preview.py <out.png> [gun ...]     (needs Pillow)

Also prints, for third person, which way each muzzle points (it should be forwards: +z, the way the player faces).
"""
import math, sys
from PIL import Image, ImageDraw
import guns


# ------------------------------------------------------------------------------------------------------------ maths
def mat(rows):
    return [list(r) for r in rows]


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def ident():
    return mat([[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]])


def tr(x, y, z):
    m = ident()
    m[0][3], m[1][3], m[2][3] = x, y, z
    return m


def sc(x, y, z):
    return mat([[x, 0, 0, 0], [0, y, 0, 0], [0, 0, z, 0], [0, 0, 0, 1]])


def rx(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return mat([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]])


def ry(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return mat([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]])


def rz(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return mat([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]])


def ap(m, p):
    return [sum(m[i][k] * (p[k] if k < 3 else 1) for k in range(4)) for i in range(3)]


def apd(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


# ------------------------------------------------------------------------------------------------------------- mesh
FACES = {  # corner indices (x, y, z bits) and normal
    'north': ([(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], (0, 0, -1)),
    'south': ([(0, 0, 1), (0, 1, 1), (1, 1, 1), (1, 0, 1)], (0, 0, 1)),
    'west': ([(0, 0, 0), (0, 1, 0), (0, 1, 1), (0, 0, 1)], (-1, 0, 0)),
    'east': ([(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)], (1, 0, 0)),
    'down': ([(0, 0, 0), (0, 0, 1), (1, 0, 1), (1, 0, 0)], (0, -1, 0)),
    'up': ([(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)], (0, 1, 0)),
}


def quads(gid):
    """The model's faces in model pixels: (corners, normal, colour)."""
    m = guns.model(gid)
    out = []
    for e in m['elements']:
        f, t = e['from'], e['to']
        r = e.get('rotation')
        rm = ident()
        if r:
            o = r['origin']
            rot = {'x': rx, 'y': ry, 'z': rz}[r['axis']](r['angle'])
            rm = mul(tr(*o), mul(rot, tr(-o[0], -o[1], -o[2])))
        u = e['faces']['north']['uv']
        i = guns.NAMES.index(next(n for n in guns.NAMES if guns.swatch_uv(n) == u))
        col = guns.SWATCH[guns.NAMES[i]][0]
        for name, (cs, n) in FACES.items():
            pts = [ap(rm, [(f, t)[cx][0], (f, t)[cy][1], (f, t)[cz][2]]) for cx, cy, cz in cs]
            out.append((pts, apd(rm, n), col, bool(e.get('light_emission'))))
    return out


def item_matrix(gid, ctx):
    """Model pixels -> the item's place: Minecraft's ItemTransform (translate, rotate XYZ, scale), after centring."""
    d = guns.model(gid)['display'][ctx]
    t = [max(-80, min(80, v)) / 16 for v in d['translation']]
    rot = mul(rx(d['rotation'][0]), mul(ry(d['rotation'][1]), rz(d['rotation'][2])))
    s = d['scale']
    return mul(tr(*t), mul(rot, mul(sc(*s), mul(tr(-0.5, -0.5, -0.5), sc(1 / 16, 1 / 16, 1 / 16)))))


def draw(img, quads_, to_screen, light=(0.3, 0.8, 0.5)):
    """Painter's algorithm: faces sorted far to near; to_screen(p) -> (x, y, depth)."""
    polys = []
    for pts, n, col, glow in quads_:
        sp = [to_screen(p) for p in pts]
        if any(s is None for s in sp):
            continue
        # back faces: the screen winding
        area = sum(sp[i][0] * sp[(i + 1) % 4][1] - sp[(i + 1) % 4][0] * sp[i][1] for i in range(4))
        if area >= 0:
            continue
        k = 1.0 if glow else 0.55 + 0.45 * max(0, (n[0] * light[0] + n[1] * light[1] + n[2] * light[2]) / math.sqrt(sum(v * v for v in n) or 1))
        polys.append((sum(s[2] for s in sp) / 4, [(s[0], s[1]) for s in sp], tuple(int(c * k) for c in col)))
    d = ImageDraw.Draw(img)
    for _, pts, col in sorted(polys, key=lambda p: -p[0]):
        d.polygon(pts, fill=col, outline=tuple(max(0, c - 25) for c in col))


# -------------------------------------------------------------------------------------------------------------- views
W, H = 480, 270


def first_person(gid):
    img = Image.new('RGB', (W, H), (96, 120, 150))
    m = mul(tr(0.56, -0.52, -0.72), item_matrix(gid, 'firstperson_righthand'))   # applyItemArmTransform, right hand
    f = 1 / math.tan(math.radians(70) / 2)

    def to_screen(p):
        x, y, z = ap(m, p)
        if z > -0.05:
            return None
        return (W / 2 + f * x / -z * H / 2, H / 2 - f * y / -z * H / 2, -z)
    draw(img, [(q[0], apd(m, q[1]), q[2], q[3]) for q in quads(gid)], to_screen, light=(0.2, 0.7, 0.7))
    d = ImageDraw.Draw(img)
    d.line([(W / 2 - 5, H / 2), (W / 2 + 5, H / 2)], fill='white')
    d.line([(W / 2, H / 2 - 5), (W / 2, H / 2 + 5)], fill='white')
    return img


def third_person_matrix(gid):
    """Model pixels -> the world, for a player at the origin facing +z, right arm as when holding an item (idle)."""
    ent = mul(ry(180), mul(sc(-1, -1, 1), tr(0, -1.501, 0)))
    arm = mul(tr(-5 / 16, 2 / 16, 0), rx(-18))                     # right arm, xRot = -pi/10 (holding an item)
    hand = mul(rx(-90), mul(ry(180), tr(1 / 16, 0.125, -0.625)))   # ItemInHandLayer
    return mul(ent, mul(arm, mul(hand, item_matrix(gid, 'thirdperson_righthand')))), mul(ent, arm)


def third_person(gid):
    img = Image.new('RGB', (W, H), (110, 140, 110))
    m, arm = third_person_matrix(gid)
    k = 120  # pixels per block, seen from the player's right (+x looking -x): screen x = world z, y up

    def to_screen(p):
        x, y, z = ap(m, p)
        return (W / 2 + z * k, H - 20 - y * k, x)
    # the player (side on): body, head and the right arm as boxes, in grey
    d = ImageDraw.Draw(img)
    d.rectangle([W / 2 - 0.125 * k, H - 20 - 1.5 * k, W / 2 + 0.125 * k, H - 20 - 0.75 * k], outline=(60, 60, 60))
    d.rectangle([W / 2 - 0.25 * k, H - 20 - 2.0 * k, W / 2 + 0.25 * k, H - 20 - 1.5 * k], outline=(60, 60, 60))
    a0, a1 = ap(arm, [0, -2 / 16, 0]), ap(arm, [0, 10 / 16, 0])
    d.line([(W / 2 + a0[2] * k, H - 20 - a0[1] * k), (W / 2 + a1[2] * k, H - 20 - a1[1] * k)], fill=(200, 170, 140), width=int(0.25 * k))
    draw(img, [(q[0], apd(m, q[1]), q[2], q[3]) for q in quads(gid)], lambda p: to_screen(p))
    return img


def muzzle_direction(gid):
    m, _ = third_person_matrix(gid)
    v = apd(m, [0, 0, -1])  # the model's front
    n = math.sqrt(sum(c * c for c in v))
    return [round(c / n, 2) for c in v]


def gui(gid):
    img = Image.new('RGB', (160, 160), (139, 139, 139))
    m = item_matrix(gid, 'gui')
    k = 160  # the slot (16 px) drawn 160 wide: one item-space unit is 160 px

    def to_screen(p):
        x, y, z = ap(m, p)
        return (80 + x * k, 80 - y * k, -z)
    draw(img, [(q[0], apd(m, q[1]), q[2], q[3]) for q in quads(gid)], to_screen, light=(0.4, 0.8, 0.4))
    ImageDraw.Draw(img).rectangle([0, 0, 159, 159], outline=(60, 60, 60))
    return img


def render(out, ids=None):
    ids = ids or list(guns.GUNS)
    sheet = Image.new('RGB', (W * 2 + 160 + 20, (H + 10) * len(ids)), (30, 30, 30))
    for i, gid in enumerate(ids):
        y = i * (H + 10)
        sheet.paste(first_person(gid), (0, y))
        sheet.paste(third_person(gid), (W + 5, y))
        sheet.paste(gui(gid), (W * 2 + 10, y))
        ImageDraw.Draw(sheet).text((4, y + 4), guns.GUNS[gid]['name'], fill='white')
        print(guns.GUNS[gid]['name'], 'third person muzzle points', muzzle_direction(gid))
    sheet.save(out)


if __name__ == '__main__':
    render(sys.argv[1], sys.argv[2:])
