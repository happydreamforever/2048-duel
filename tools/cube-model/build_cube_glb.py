#!/usr/bin/env python3
"""Builds android/src/main/assets/models/rubiks_cube.glb from the Sketchfab source model.

Source: "Rubik's Cube" by DatSketch (CC BY 4.0)
        https://sketchfab.com/3d-models/rubiks-cube-eaba6bf1c7da497f926852006c7bd855
        (download the glTF binary .glb while logged in; it is not committed).

What the app needs from the model, and what this script produces:
  * 27 nodes named cubie_<x>_<y>_<z> (x,y,z in -1,0,1), translated to their grid cell,
    so the app can turn any layer by rotating 9 node transforms about the origin;
  * spacing 1.0 between cubie centers (the source uses 40 units);
  * every tile recolored by the face it sits on, standard WCA scheme with the app's
    orientation: white U (+Y), yellow D (-Y), green F (+Z), blue B (-Z), red R (+X),
    orange L (-X) - the solved state is correct by construction;
  * tiles lifted a hair off the body (the source tiles are coplanar -> z-fighting) and
    wound/normaled to face outward (several source tiles face inward);
  * PBR materials: satin black plastic body, glossy tiles.

Usage:  pip install numpy trimesh
        python3 tools/cube-model/build_cube_glb.py <source.glb> [out.glb]
"""
import json
import struct
import sys
from pathlib import Path

import numpy as np
import trimesh

SRC_SPACING = 40.0
TILE_LIFT = 0.004  # in output units (spacing 1)

# glTF baseColorFactor is linear RGB; values converted from the sRGB WCA-ish palette.
def _lin(c):
    c = c / 255.0
    return float(np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4))

def srgb(hexstr):
    return [_lin(int(hexstr[i:i + 2], 16)) for i in (0, 2, 4)] + [1.0]

# (axis, sign) -> (face, sRGB color)
FACES = {
    (1, 1): ("U", "F4F6F8"),   # white
    (1, -1): ("D", "FFD400"),  # yellow
    (2, 1): ("F", "00A651"),   # green
    (2, -1): ("B", "0051BA"),  # blue
    (0, 1): ("R", "C8102E"),   # red
    (0, -1): ("L", "FF6A00"),  # orange
}
FACE_ORDER = ["U", "D", "F", "B", "R", "L"]

MATERIALS = [
    {"name": "plastic", "pbrMetallicRoughness": {"baseColorFactor": srgb("101114"), "metallicFactor": 0.0, "roughnessFactor": 0.42}},
] + [
    {"name": "tile_" + face, "pbrMetallicRoughness": {"baseColorFactor": srgb(color), "metallicFactor": 0.0, "roughnessFactor": 0.22}}
    for face in FACE_ORDER
    for (face2, color) in [next(v for v in FACES.values() if v[0] == face)]
]
MATERIAL_INDEX = {"plastic": 0, **{f: 1 + i for i, f in enumerate(FACE_ORDER)}}


def load_parts(src):
    # process=False keeps the authored vertex normals (no welding/smoothing).
    scene = trimesh.load(src, force="scene", process=False)
    parts = []
    for node in scene.graph.nodes_geometry:
        transform, geom = scene.graph[node]
        mesh = scene.geometry[geom].copy()
        mesh.apply_transform(transform)
        mesh.apply_scale(1.0 / SRC_SPACING)
        parts.append(mesh)
    return parts


def classify(mesh):
    """-> (grid cell, tile axis/sign or None for a body)."""
    ext = mesh.extents
    center = mesh.bounds.mean(axis=0)
    flat = int(np.argmin(ext))
    if ext[flat] < 1e-4:  # zero-thickness plane = a tile
        sign = 1 if center[flat] > 0 else -1
        cell = np.rint(center).astype(int)
        cell[flat] = sign  # tile sits at +-1.5, its cubie at +-1
        return tuple(int(v) for v in cell), (flat, sign)
    return tuple(int(v) for v in np.rint(center)), None


def outward_tile(mesh, cell, axis, sign):
    """Local-space tile geometry, lifted off the body and facing outward."""
    v = mesh.vertices - np.array(cell, dtype=float)
    v[:, axis] = sign * 0.5 + sign * TILE_LIFT
    f = mesh.faces.copy()
    tri = v[f]
    n = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    inward = n[:, axis] * sign < 0
    f[inward] = f[inward][:, ::-1]
    normals = np.zeros_like(v)
    normals[:, axis] = sign
    return v, normals, f


def body_local(mesh, cell):
    v = mesh.vertices - np.array(cell, dtype=float)
    return v, np.asarray(mesh.vertex_normals), mesh.faces


class GlbWriter:
    def __init__(self):
        self.bin = bytearray()
        self.views = []
        self.accessors = []

    def _view(self, data, target):
        while len(self.bin) % 4:
            self.bin.append(0)
        off = len(self.bin)
        self.bin += data
        self.views.append({"buffer": 0, "byteOffset": off, "byteLength": len(data), "target": target})
        return len(self.views) - 1

    def vec3(self, arr):
        arr = np.ascontiguousarray(arr, dtype="<f4")
        view = self._view(arr.tobytes(), 34962)
        self.accessors.append({
            "bufferView": view, "componentType": 5126, "count": len(arr), "type": "VEC3",
            "min": arr.min(axis=0).tolist(), "max": arr.max(axis=0).tolist(),
        })
        return len(self.accessors) - 1

    def indices(self, faces):
        flat = np.ascontiguousarray(faces.reshape(-1), dtype="<u2")
        assert faces.max() < 65536
        view = self._view(flat.tobytes(), 34963)
        self.accessors.append({"bufferView": view, "componentType": 5123, "count": len(flat), "type": "SCALAR"})
        return len(self.accessors) - 1


def main():
    src = sys.argv[1]
    out = Path(sys.argv[2] if len(sys.argv) > 2 else "android/src/main/assets/models/rubiks_cube.glb")
    cubies = {}
    for mesh in load_parts(src):
        cell, tile = classify(mesh)
        assert all(c in (-1, 0, 1) for c in cell), (cell, mesh.bounds)
        entry = cubies.setdefault(cell, {"body": [], "tiles": []})
        if tile is None:
            entry["body"].append(body_local(mesh, cell))
        else:
            entry["tiles"].append((tile, outward_tile(mesh, cell, *tile)))

    assert len(cubies) == 27, len(cubies)
    tiles = sum(len(c["tiles"]) for c in cubies.values())
    assert tiles == 54, tiles
    for cell, c in cubies.items():
        assert len(c["body"]) == 1, (cell, len(c["body"]))
        expected = {(a, cell[a]) for a in range(3) if cell[a] != 0}
        assert {t for t, _ in c["tiles"]} == expected, (cell, [t for t, _ in c["tiles"]])

    w = GlbWriter()
    meshes, nodes = [], []
    for cell in sorted(cubies):
        c = cubies[cell]
        prims = []
        for v, n, f in c["body"]:
            prims.append({"attributes": {"POSITION": w.vec3(v), "NORMAL": w.vec3(n)}, "indices": w.indices(f), "material": 0})
        for (axis, sign), (v, n, f) in sorted(c["tiles"]):
            face = FACES[(axis, sign)][0]
            prims.append({"attributes": {"POSITION": w.vec3(v), "NORMAL": w.vec3(n)}, "indices": w.indices(f), "material": MATERIAL_INDEX[face]})
        meshes.append({"name": "cubie_mesh_%d_%d_%d" % cell, "primitives": prims})
        nodes.append({"name": "cubie_%d_%d_%d" % cell, "mesh": len(meshes) - 1, "translation": [float(x) for x in cell]})
    nodes.append({"name": "rubiks_cube", "children": list(range(27))})

    gltf = {
        "asset": {"version": "2.0", "generator": "2048-duel tools/cube-model/build_cube_glb.py",
                  "copyright": "Rubik's Cube by DatSketch (CC BY 4.0), regrouped/recolored for 2048 Duel"},
        "scene": 0,
        "scenes": [{"nodes": [27]}],
        "nodes": nodes,
        "meshes": meshes,
        "materials": MATERIALS,
        "accessors": w.accessors,
        "bufferViews": w.views,
        "buffers": [{"byteLength": len(w.bin)}],
    }
    js = json.dumps(gltf, separators=(",", ":")).encode()
    js += b" " * (-len(js) % 4)
    while len(w.bin) % 4:
        w.bin.append(0)
    body = struct.pack("<II", len(js), 0x4E4F534A) + js + struct.pack("<II", len(w.bin), 0x004E4942) + bytes(w.bin)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(struct.pack("<III", 0x46546C67, 2, 12 + len(body)) + body)
    print("wrote %s (%d bytes, %d tiles)" % (out, out.stat().st_size, tiles))


if __name__ == "__main__":
    main()
