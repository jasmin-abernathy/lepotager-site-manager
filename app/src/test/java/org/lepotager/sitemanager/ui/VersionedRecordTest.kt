package org.lepotager.sitemanager.ui

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import org.lepotager.sitemanager.model.ModuleConfig

class VersionedRecordTest {
    private val page=ModuleConfig(id="pages",kind="records",title="Pages",
        writable=true,options=buildJsonObject {put("require_version",true)})
    private val legacy=ModuleConfig(id="commandes",kind="records",title="Commandes",
        writable=true)
    @Test fun nativePagesIncludeExpectedRevision() {
        val item=buildJsonObject {put("id","abcdef0123456789abcdef0123456789");put("version",4)}
        assertTrue(hasRecordVersion(page,item))
        val payload=recordIdentityPayload(page,item)
        assertEquals(4,(payload["expected_version"] as JsonPrimitive).int)
        assertEquals("abcdef0123456789abcdef0123456789",(payload["item_id"] as JsonPrimitive).content)
    }
    @Test fun legacyModulesRemainCompatibleWithoutVersion() {
        val item=buildJsonObject {put("id","order-18")}
        assertTrue(hasRecordVersion(legacy,item))
        assertFalse(recordIdentityPayload(legacy,item).containsKey("expected_version"))
    }
    @Test fun unknownOrInvalidVersionCannotUpdateVersionedCmsPage() {
        val missing=buildJsonObject {put("id","record-1")}
        val invalid=buildJsonObject {put("id","record-1");put("version",0)}
        assertFalse(hasRecordVersion(page,missing))
        assertFalse(hasRecordVersion(page,invalid))
        try {
            recordIdentityPayload(page,missing)
            fail("Allowed versionless CMS update")
        } catch (_: IllegalArgumentException) {}
    }
}
