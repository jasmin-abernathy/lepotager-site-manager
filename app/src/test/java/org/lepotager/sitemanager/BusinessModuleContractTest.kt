package org.lepotager.sitemanager

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lepotager.sitemanager.model.SiteConfig

class BusinessModuleContractTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    @Test
    fun genericBusinessModulesParseWithoutClientSpecificTypes() {
        val config = json.decodeFromString<SiteConfig>(
            """
            {
              "schema_version":1,
              "config_version":18,
              "site":{"id":"client-example","display_name":"Client Exemple"},
              "modules":[
                {
                  "id":"appointments",
                  "kind":"calendar",
                  "title":"Rendez-vous",
                  "actions":[{
                    "id":"cancel",
                    "label":"Annuler",
                    "tone":"danger",
                    "requires_confirmation":true,
                    "confirmation_text":"Confirmer l'annulation ?"
                  }]
                },
                {
                  "id":"orders",
                  "kind":"records",
                  "title":"Commandes",
                  "fields":[{"id":"amount","type":"text","label":"Montant"}]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("calendar", "records"), config.modules.map { it.kind })
        val cancel = config.modules.first().actions.single()
        assertTrue(cancel.requiresConfirmation)
        assertFalse(cancel.allowOffline)
        assertEquals("danger", cancel.tone)
        assertEquals("amount", config.modules.last().fields.single().id)
    }

    @Test
    fun businessActionMayExplicitlyAllowOfflineReplay() {
        val config = json.decodeFromString<SiteConfig>(
            """
            {
              "schema_version":1,
              "config_version":1,
              "site":{"id":"example","display_name":"Example"},
              "modules":[{
                "id":"tasks",
                "kind":"records",
                "title":"Tâches",
                "actions":[{"id":"archive","label":"Archiver","allow_offline":true}]
              }]
            }
            """.trimIndent(),
        )

        assertTrue(config.modules.single().actions.single().allowOffline)
    }
}
