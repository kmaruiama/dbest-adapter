package dbest.features.catalog

import dbest.features.canvas.graph.json
import dbest.kernel.util.existsInCollection
import dbest.kernel.util.isEmpty
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogTest {

    private fun everySpec(specs: List<FieldSpec>): List<FieldSpec> {
        val flat = ArrayList<FieldSpec>()
        for (spec in specs) {
            flat.add(spec)
            for (cell in spec.of) {
                flat.add(cell)
            }
        }
        return flat
    }

    @Test
    fun `every declared kind is a kind the palette actually serves`() {
        for (kind in FIELDS.keys) {
            assertTrue(existsInCollection(kind, catalogKinds()), "FIELDS tem '$kind', que nao esta no catalogo")
        }
    }

    @Test
    fun `a type is editable exactly when it declares form fields`() {
        for (kind in catalogKinds()) {
            assertEquals(existsInCollection(kind, FIELDS), isEditable(kind), kind)
        }
    }

    @Test
    fun `every field spec points at a real property of its type's template`() {
        for ((kind, specs) in FIELDS) {
            val template = json(sampleOf(kind)) as JsonObject
            for (spec in specs) {
                assertTrue(template.containsKey(spec.at), "$kind.${spec.at} nao existe no template")
            }
        }
    }

    @Test
    fun `every row spec points at a real property of its row template`() {
        for ((kind, specs) in FIELDS) {
            val template = json(sampleOf(kind)) as JsonObject
            for (spec in specs) {
                if (spec.widget != Widget.ROWS) {
                    continue
                }
                val rows = template[spec.at] as JsonArray
                val row = rows[0] as JsonObject
                for (cell in spec.of) {
                    assertTrue(row.containsKey(cell.at), "$kind.${spec.at}[].${cell.at} nao existe no template")
                }
            }
        }
    }

    @Test
    fun `each widget carries the extras it needs and nothing else`() {
        for ((kind, specs) in FIELDS) {
            for (spec in everySpec(specs)) {
                val where = "$kind.${spec.at}"
                if (spec.widget == Widget.PICK || spec.widget == Widget.CONDITION) {
                    assertTrue(!isEmpty(spec.options), "$where: ${spec.widget} sem options")
                } else if (spec.widget == Widget.LIST) {
                    assertTrue(spec.item != null, "$where: LIST sem item")
                } else if (spec.widget == Widget.ROWS) {
                    assertTrue(!isEmpty(spec.of), "$where: ROWS sem of")
                } else {
                    assertTrue(isEmpty(spec.options), "$where: ${spec.widget} nao usa options")
                    assertTrue(spec.item == null, "$where: ${spec.widget} nao usa item")
                    assertTrue(isEmpty(spec.of), "$where: ${spec.widget} nao usa of")
                }
            }
        }
    }
}
