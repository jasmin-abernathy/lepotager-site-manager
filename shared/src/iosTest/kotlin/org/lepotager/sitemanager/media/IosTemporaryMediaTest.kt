package org.lepotager.sitemanager.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosTemporaryMediaTest {
    @Test
    fun generatedPathBelongsToManagedTemporaryDirectory() {
        val path = assertNotNull(newIosTemporaryMediaPath("photo.jpg"))

        assertTrue(path.endsWith("/mon-manager-web-media/photo.jpg"))
        assertEquals(path, normalizedOwnedIosTemporaryMediaPath(path))
    }

    @Test
    fun nestedSourceNameCannotEscapeManagedDirectory() {
        val path = assertNotNull(newIosTemporaryMediaPath("../../outside.jpg"))

        assertTrue(path.endsWith("/mon-manager-web-media/outside.jpg"))
        assertEquals(path, normalizedOwnedIosTemporaryMediaPath(path))
    }

    @Test
    fun traversalOutsideManagedDirectoryIsRejected() {
        val managed = assertNotNull(newIosTemporaryMediaPath("photo.jpg"))
        val directory = managed.substringBeforeLast("/")
        val escaped = "$directory/../outside.jpg"

        assertNull(normalizedOwnedIosTemporaryMediaPath(escaped))
    }

    @Test
    fun directoryItselfIsNotAcceptedAsOwnedFile() {
        val managed = assertNotNull(newIosTemporaryMediaPath("photo.jpg"))
        val directory = managed.substringBeforeLast("/")

        assertNull(normalizedOwnedIosTemporaryMediaPath(directory))
    }

    @Test
    fun relativePathIsRejected() {
        assertNull(normalizedOwnedIosTemporaryMediaPath("mon-manager-web-media/photo.jpg"))
    }
}
