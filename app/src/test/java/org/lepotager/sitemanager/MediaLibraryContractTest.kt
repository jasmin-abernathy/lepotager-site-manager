package org.lepotager.sitemanager

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lepotager.sitemanager.model.SiteConfig

class MediaLibraryContractTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    @Test
    fun mediaLibraryModuleParsesWithoutClientSpecificCode() {
        val config = json.decodeFromString<SiteConfig>(
            """
            {
              "schema_version":1,
              "config_version":24,
              "site":{"id":"client-example","display_name":"Client Exemple"},
              "modules":[{
                "id":"photos",
                "kind":"media_library",
                "title":"Photos",
                "writable":true,
                "fields":[
                  {
                    "id":"kind",
                    "type":"single_choice",
                    "label":"Type de photo",
                    "choices":[
                      {"value":"normal","label":"Galerie"},
                      {"value":"before","label":"Avant"},
                      {"value":"after","label":"Après"}
                    ]
                  },
                  {"id":"alt","type":"text","label":"Texte alternatif","max_length":240},
                  {"id":"caption","type":"text","label":"Légende","max_length":240},
                  {"id":"position","type":"number","label":"Ordre","min":-100000,"max":100000}
                ],
                "options":{"allow_delete":true,"allow_rotate":true}
              }]
            }
            """.trimIndent(),
        )

        val photos = config.modules.single()
        assertEquals("media_library", photos.kind)
        assertTrue(photos.writable)
        assertEquals(listOf("normal", "before", "after"), photos.fields.first().choices.map { it.value })
        assertEquals(240, photos.fields[1].maxLength)
        assertTrue(photos.options.getValue("allow_rotate").jsonPrimitive.boolean)
        assertTrue(photos.options.getValue("allow_delete").jsonPrimitive.boolean)
    }
}
