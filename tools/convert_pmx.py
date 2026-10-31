"""PMX -> .mesh converter with a baked static pose (idle stance: shield up, glaive held diagonally).
Usage: python convert_pmx.py <model.pmx> <out.mesh>
MMD (front = -Z, left-handed) -> Minecraft (front = +Z), 1 unit = 1/8 block.
Edit POSE to change the stance. Values are degrees (rx, ry, rz); ("W", rx, ry, rz) = absolute world orientation.
If the mob faces backwards in game, set FLIP_FRONT = True and re-run.
"""
import struct, sys, math
import numpy as np
import pmx

SCALE = 0.125
FLIP_FRONT = False
POSE = {
    "J_R_Arm":    (55, 0, -20),
    "Shield":     ("W", -90, 180, 0),
    "J_L_Arm":    (30, 0, 65),
    "J_L_Weapon": ("W", -3, 0, -8),
}

def rot(rx, ry, rz):
    a, b, c = map(math.radians, (rx, ry, rz))
    Rx = np.array([[1,0,0],[0,math.cos(a),-math.sin(a)],[0,math.sin(a),math.cos(a)]])
    Ry = np.array([[math.cos(b),0,math.sin(b)],[0,1,0],[-math.sin(b),0,math.cos(b)]])
    Rz = np.array([[math.cos(c),-math.sin(c),0],[math.sin(c),math.cos(c),0],[0,0,1]])
    M = np.eye(4); M[:3,:3] = Rz @ Ry @ Rx
    return M

def T(v):
    M = np.eye(4); M[:3,3] = v
    return M

def local_rot(name, parent, G):
    v = POSE.get(name, (0, 0, 0))
    if len(v) == 4:
        t = rot(*v[1:])
        if parent < 0: return t
        M = np.eye(4); M[:3,:3] = np.linalg.inv(G[parent][:3,:3]) @ t[:3,:3]
        return M
    return rot(*v)

m = pmx.load(sys.argv[1])
B = m["B"]
G = [None] * len(B)
for i, (n, p, par) in enumerate(B):
    p = np.array(p)
    if par < 0: G[i] = T(p) @ local_rot(n, par, G)
    else: G[i] = G[par] @ T(p - np.array(B[par][1])) @ local_rot(n, par, G)

posed = []
for pos, nor, uv, b, w in m["V"]:
    bi = b[0]
    v = G[bi] @ np.append(np.array(pos) - np.array(B[bi][1]), 1.0)
    nn = G[bi][:3,:3] @ np.array(nor)
    posed.append((v[:3], nn, uv))

out = bytearray(struct.pack(">i", len(m["M"])))
o = 0
for name, tex, cnt, _ in m["M"]:
    out += struct.pack(">ii", tex, cnt // 3)
    for t in range(cnt // 3):
        i0, i1, i2 = m["F"][o+t*3:o+t*3+3]
        for i in (i0, i2, i1):  # reversed winding because one axis is mirrored
            (x, y, z), (nx, ny, nz), (u, v) = posed[i]
            if FLIP_FRONT: x, z, nx, nz = -x, -z, -nx, -nz
            out += struct.pack(">8f", x*SCALE, y*SCALE, -z*SCALE, nx, ny, -nz, u, v)
    o += cnt
open(sys.argv[2], "wb").write(out)
print("wrote", len(out), "bytes")
