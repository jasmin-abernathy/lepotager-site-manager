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
    fun settingsAndEditableRecordsRemainGenericV1Primitives() {
        val config = json.decodeFromString<SiteConfig>(
            """
            {
              "schema_version":1,
              "config_version":21,
              "site":{"id":"dendrila-example","display_name":"Dendrila Example"},
              "modules":[
                {
                  "id":"privacy",
                  "kind":"settings",
                  "title":"Vie privée",
                  "writable":true,
                  "fields":[
                    {"id":"consent_enabled","type":"boolean","label":"Consentement","hint":"Active la gestion native."}
                  ]
                },
                {
                  "id":"privacy_treatments",
                  "kind":"records",
                  "title":"Traitements",
                  "writable":true,
                  "options":{"allow_create":true,"allow_update":true,"allow_delete":true},
                  "fields":[
                    {"id":"purpose","type":"text","label":"Finalité"}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val settings = config.modules.first()
        val records = config.modules.last()
        assertEquals("settings", settings.kind)
        assertTrue(settings.writable)
        assertEquals("Active la gestion native.", settings.fields.single().hint)
        assertEquals("records", records.kind)
        assertTrue(records.writable)
        assertEquals("true", records.options["allow_create"].toString())
        assertEquals("true", records.options["allow_update"].toString())
        assertEquals("true", records.options["allow_delete"].toString())
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
