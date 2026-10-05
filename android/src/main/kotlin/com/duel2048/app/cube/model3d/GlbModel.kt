package com.duel2048.app.cube.model3d

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal glTF 2.0 binary (.glb) reader, scoped to what the cube model uses:
 * a JSON chunk with flat nodes/meshes, FLOAT VEC3 positions/normals, USHORT indices,
 * and flat baseColorFactor materials. No textures, skins, or animations are needed —
 * turn animation is driven by the game, not by the baked demo clip.
 */
class GlbModel(
    val nodes: List<Node>,
    val meshes: List<Mesh>,
    val materials: List<Material>,
) {
    class Node(
        val name: String,
        val mesh: Int,
        /** Column-major local transform (identity when the glb node has none). */
        val transform: FloatArray,
    )

    class Primitive(
        val positions: FloatArray,
        val normals: FloatArray,
        val indices: ShortArray,
        val material: Int,
    )

    class Mesh(val name: String, val primitives: List<Primitive>)
    class Material(val name: String, val color: FloatArray)

    companion object {
        private const val MAGIC = 0x46546C67 // 'glTF'
        private const val CHUNK_JSON = 0x4E4F534A
        private const val CHUNK_BIN = 0x004E4942
        private const val COMPONENT_FLOAT = 5126
        private const val COMPONENT_USHORT = 5123
        private const val TYPE_VEC3 = "VEC3"
        private const val TYPE_SCALAR = "SCALAR"

        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class GlbFile(
            val nodes: List<GlbNode> = emptyList(),
            val meshes: List<GlbMesh> = emptyList(),
            val accessors: List<GlbAccessor> = emptyList(),
            val bufferViews: List<GlbBufferView> = emptyList(),
            val materials: List<GlbMaterial> = emptyList(),
        )

        @Serializable
        private data class GlbNode(
            val name: String? = null,
            val mesh: Int = -1,
            val children: List<Int> = emptyList(),
            val translation: List<Float>? = null,
            val rotation: List<Float>? = null,
            val scale: List<Float>? = null,
            val matrix: List<Float>? = null,
        )

        @Serializable
        private data class GlbMesh(val name: String? = null, val primitives: List<GlbPrimitive> = emptyList())

        @Serializable
        private data class GlbPrimitive(val attributes: Map<String, Int> = emptyMap(), val indices: Int = -1, val material: Int = 0)

        @Serializable
        private data class GlbAccessor(
            val bufferView: Int = -1,
            val byteOffset: Int = 0,
            val componentType: Int = 0,
            val count: Int = 0,
            val type: String = "",
        )

        @Serializable
        private data class GlbBufferView(val buffer: Int = 0, val byteOffset: Int = 0, val byteLength: Int = 0, val byteStride: Int? = null)

        @Serializable
        private data class GlbMaterial(val name: String? = null, val pbrMetallicRoughness: GlbPbr? = null)

        @Serializable
        private data class GlbPbr(val baseColorFactor: List<Float>? = null)

        fun parse(bytes: ByteArray): GlbModel {
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            check(header.int == MAGIC) { "not a glb file" }
            check(header.int == 2) { "unsupported glb version" }
            header.int // total length; chunk walk below is authoritative

            var jsonText: String? = null
            var bin: ByteArray? = null
            while (header.hasRemaining()) {
                val len = header.int
                val type = header.int
                val chunk = ByteArray(len).also { header.get(it) }
                when (type) {
                    CHUNK_JSON -> jsonText = String(chunk, Charsets.UTF_8)
                    CHUNK_BIN -> bin = chunk
                }
            }
            checkNotNull(jsonText) { "glb has no JSON chunk" }
            val parsed = json.decodeFromString<GlbFile>(jsonText)
            val binBuf = bin?.let { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN) }
            checkNotNull(binBuf) { "glb has no BIN chunk" }

            fun viewBytes(accessor: GlbAccessor): ByteBuffer {
                check(accessor.bufferView >= 0) { "accessor without bufferView" }
                val bv = parsed.bufferViews[accessor.bufferView]
                val start = bv.byteOffset + accessor.byteOffset
                val elementSize = when (accessor.type) {
                    TYPE_VEC3 -> 12
                    TYPE_SCALAR -> 2
                    else -> error("unsupported accessor type ${accessor.type}")
                }
                val stride = bv.byteStride ?: elementSize
                return ByteBuffer.wrap(bin, start, stride * (accessor.count - 1) + elementSize)
                    .order(ByteOrder.LITTLE_ENDIAN)
            }

            fun readVec3(accessorIndex: Int): FloatArray {
                val acc = parsed.accessors[accessorIndex]
                check(acc.componentType == COMPONENT_FLOAT && acc.type == TYPE_VEC3) { "expected FLOAT VEC3" }
                val src = viewBytes(acc)
                return FloatArray(acc.count * 3) { src.float }
            }

            fun readIndices(accessorIndex: Int): ShortArray {
                val acc = parsed.accessors[accessorIndex]
                check(acc.componentType == COMPONENT_USHORT && acc.type == TYPE_SCALAR) { "expected USHORT indices" }
                val src = viewBytes(acc)
                return ShortArray(acc.count) { src.short }
            }

            fun nodeTransform(n: GlbNode): FloatArray {
                n.matrix?.let { return it.toFloatArray() }
                val t = n.translation ?: listOf(0f, 0f, 0f)
                val r = n.rotation // xyzw quaternion
                val s = n.scale ?: listOf(1f, 1f, 1f)
                if (r == null) return CubeMath.translation(t[0], t[1], t[2])
                val (x, y, z, w) = listOf(r[0], r[1], r[2], r[3])
                val rMat = floatArrayOf(
                    1f - 2f * (y * y + z * z), 2f * (x * y + w * z), 2f * (x * z - w * y), 0f,
                    2f * (x * y - w * z), 1f - 2f * (x * x + z * z), 2f * (y * z + w * x), 0f,
                    2f * (x * z + w * y), 2f * (y * z - w * x), 1f - 2f * (x * x + y * y), 0f,
                    0f, 0f, 0f, 1f,
                )
                val sMat = floatArrayOf(
                    s[0], 0f, 0f, 0f,
                    0f, s[1], 0f, 0f,
                    0f, 0f, s[2], 0f,
                    0f, 0f, 0f, 1f,
                )
                val tMat = CubeMath.translation(t[0], t[1], t[2])
                return CubeMath.multiply(tMat, CubeMath.multiply(rMat, sMat))
            }

            val meshes = parsed.meshes.map { mesh ->
                GlbModel.Mesh(
                    name = mesh.name ?: "",
                    primitives = mesh.primitives.map { prim ->
                        GlbModel.Primitive(
                            positions = readVec3(prim.attributes.getValue("POSITION")),
                            normals = readVec3(prim.attributes.getValue("NORMAL")),
                            indices = readIndices(prim.indices),
                            material = prim.material,
                        )
                    },
                )
            }

            return GlbModel(
                nodes = parsed.nodes.map { GlbModel.Node(it.name ?: "", it.mesh, nodeTransform(it)) },
                meshes = meshes,
                materials = parsed.materials.map {
                    GlbModel.Material(it.name ?: "", (it.pbrMetallicRoughness?.baseColorFactor ?: listOf(1f, 1f, 1f, 1f)).toFloatArray())
                },
            )
        }

    }
}
