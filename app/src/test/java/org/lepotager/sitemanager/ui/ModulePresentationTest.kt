package org.lepotager.sitemanager.ui

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lepotager.sitemanager.model.SiteConfig

class ModulePresentationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    @Test
    fun sectionedSettingsRemainGenericAndCoverEveryField() {
        val config = json.decodeFromString<SiteConfig>(
            """
            {
              "schema_version":1,
              "config_version":30,
              "site":{"id":"example","display_name":"Example"},
              "modules":[{
                "id":"privacy_legal",
                "kind":"settings",
                "title":"Documentation",
                "order":910,
                "fields":[
                  {"id":"controller_name","type":"text","label":"Responsable"},
                  {"id":"privacy_contact","type":"text","label":"Contact"}
                ],
                "options":{
                  "group_id":"privacy",
                  "group_title":"Vie privée",
                  "group_description":"Parcours guidé",
                  "group_order":900,
                  "field_sections":[
                    {"id":"identity","title":"Identité","description":"Qui êtes-vous ?","field_ids":["controller_name"]},
                    {"id":"contact","title":"Contact","field_ids":["privacy_contact"]}
                  ]
                }
              }]
            }
            """.trimIndent(),
        )

        val module = config.modules.single()
        val sections = moduleFieldSections(module)
        val group = moduleGroupSpec(module)

        assertEquals(listOf("identity", "contact"), sections.map { it.id })
        assertEquals(listOf("controller_name", "privacy_contact"), sections.flatMap { it.fieldIds })
        assertEquals("Vie privée", group?.title)
        assertEquals(900, group?.order)
    }

    @Test
    fun malformedSectionLayoutFallsBackToFlatForm() {
        val config = json.decodeFromString<SiteConfig>(
            """
            {
              "schema_version":1,
              "config_version":31,
              "site":{"id":"example","display_name":"Example"},
              "modules":[{
                "id":"settings",
                "kind":"settings",
                "title":"Réglages",
                "fields":[
                  {"id":"a","type":"text","label":"A"},
                  {"id":"b","type":"text","label":"B"}
                ],
                "options":{
                  "field_sections":[
                    {"id":"one","title":"Un","field_ids":["a"]},
                    {"id":"two","title":"Deux","field_ids":["a"]}
                  ]
                }
              }]
            }
            """.trimIndent(),
        )

        assertTrue(moduleFieldSections(config.modules.single()).isEmpty())
    }

    @Test
    fun groupedModulesStayTogetherWithoutChangingTheirOwnOrder() {
        val config = json.decodeFromString<SiteConfig>(
            """
            {
              "schema_version":1,
              "config_version":32,
              "site":{"id":"example","display_name":"Example"},
              "modules":[
                {"id":"z","kind":"dashboard","title":"Z","order":50},
                {"id":"privacy_b","kind":"records","title":"B","order":920,
                 "options":{"group_id":"privacy","group_title":"Vie privée","group_order":900}},
                {"id":"privacy_a","kind":"settings","title":"A","order":910,
                 "options":{"group_id":"privacy","group_title":"Vie privée","group_order":900}}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("z", "privacy_a", "privacy_b"), sortModulesForHome(config.modules).map { it.id })
    }
}
