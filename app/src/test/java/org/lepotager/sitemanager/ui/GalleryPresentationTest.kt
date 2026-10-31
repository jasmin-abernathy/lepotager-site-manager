package org.lepotager.sitemanager.ui

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import org.junit.Assert.*
import org.junit.Test

class GalleryPresentationTest {
    private val base = "https://vuesimprenables.lepotager.org"
    @Test fun oscuraPhotoUrlIsDisplayed() {
        val photo = buildJsonObject {
            put("id", "photo_1")
            put("title", "Sténopé")
            put("image_url", "https://www.vuesimprenables.com/IMG/photo.webp")
            put("album", "La photographie")
            put("alt_text", "Paysage noir et blanc")
        }
        assertEquals("https://www.vuesimprenables.com/IMG/photo.webp", galleryImageUrl(photo, base))
        assertEquals("La photographie", galleryAlbumName(photo))
        assertEquals("Paysage noir et blanc", galleryPhotoLabel(photo))
    }
    @Test fun nativeCmsMediaVariantsAndRelativeUrls() {
        assertEquals("https://dendrila.fr/media.php?t=abc", galleryImageUrl(buildJsonObject {
            put("thumb", "https://dendrila.fr/media.php?t=abc")
            put("path", "https://dendrila.fr/media.php?t=original")
        }, base))
        assertEquals("$base/album/image.webp", galleryImageUrl(buildJsonObject {
            put("image_url", "/album/image.webp")
        }, base))
        assertEquals("https://photos.example/1.webp", galleryImageUrl(buildJsonObject {
            put("photos", buildJsonArray {
                add(buildJsonObject { put("image_url", "https://photos.example/1.webp") })
            })
        }, base))
    }
    @Test fun unsafeImageAddressesAreNotRendered() {
        for (link in listOf("http://unsafe.test/a.jpg","javascript:alert(1)","file:///etc/passwd",
                         "//tracking.site/pixel.png","https://user:pass@host.tld/image.png", "")) {
            assertNull("Unsafe address was accepted: $link", galleryImageUrl(buildJsonObject {
                put("image_url", link)
            }, base))
        }
        assertEquals("Sans album", galleryAlbumName(buildJsonObject { }))
        assertEquals("Photo du site", galleryPhotoLabel(buildJsonObject { }))
    }
}
