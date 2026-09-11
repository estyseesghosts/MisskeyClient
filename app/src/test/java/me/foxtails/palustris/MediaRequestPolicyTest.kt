package me.foxtails.palustris

import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.MediaRequestDecision
import me.foxtails.palustris.domain.MediaRequestPolicy
import me.foxtails.palustris.domain.MediaRequestReason
import me.foxtails.palustris.domain.MediaRequestRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRequestPolicyTest {
    private val attachment = Attachment(
        id = "media-1",
        url = "https://cdn.example/full.jpg",
        previewUrl = "https://cdn.example/preview.jpg",
        mimeType = "image/jpeg",
        kind = MediaKind.Image,
    )

    @Test
    fun timelinePrefersServerPreviewWhenAvailable() {
        val result = MediaRequestPolicy.resolve(attachment, MediaRequestRole.Preview, revealed = true, explicitlyOpened = false)
        assertEquals(MediaRequestDecision.Request("https://cdn.example/preview.jpg", MediaRequestRole.Preview, true), result)
    }

    @Test
    fun timelineFallsBackToFullImageWhenPreviewMissing() {
        val noPreview = attachment.copy(previewUrl = null)
        assertEquals(
            MediaRequestDecision.Request(attachment.url!!, MediaRequestRole.Preview, true),
            MediaRequestPolicy.resolve(noPreview, MediaRequestRole.Preview, revealed = true, explicitlyOpened = false),
        )
    }

    @Test
    fun timelineFallsBackToFullImageWhenPreviewInvalid() {
        val invalidPreview = attachment.copy(previewUrl = "not-a-url")
        assertEquals(
            MediaRequestDecision.Request(attachment.url!!, MediaRequestRole.Preview, true),
            MediaRequestPolicy.resolve(invalidPreview, MediaRequestRole.Preview, revealed = true, explicitlyOpened = false),
        )
    }

    @Test
    fun fullRequiresExplicitOpenAndUsesFullResource() {
        assertEquals(
            MediaRequestDecision.NoRequest(MediaRequestReason.MissingFullResource),
            MediaRequestPolicy.resolve(attachment, MediaRequestRole.Full, revealed = true, explicitlyOpened = false),
        )
        assertEquals(
            MediaRequestDecision.Request("https://cdn.example/full.jpg", MediaRequestRole.Full, true),
            MediaRequestPolicy.resolve(attachment, MediaRequestRole.Full, revealed = true, explicitlyOpened = true),
        )
    }

    @Test
    fun hiddenSensitiveMediaDoesNotRequestEitherRole() {
        val hidden = attachment.copy(sensitive = true)
        assertEquals(
            MediaRequestDecision.NoRequest(MediaRequestReason.HiddenSensitiveMedia),
            MediaRequestPolicy.resolve(hidden, MediaRequestRole.Preview, revealed = false, explicitlyOpened = false),
        )
    }

    @Test
    fun timelineAllowsEqualPreviewAndFullUrls() {
        val equal = attachment.copy(previewUrl = attachment.url)
        assertEquals(
            MediaRequestDecision.Request(attachment.url!!, MediaRequestRole.Preview, true),
            MediaRequestPolicy.resolve(equal, MediaRequestRole.Preview, revealed = true, explicitlyOpened = false),
        )
    }

    @Test
    fun previewCanRemainVisibleWhenFullIsUnavailable() {
        val previewOnly = attachment.copy(url = null)
        val result = MediaRequestPolicy.resolve(previewOnly, MediaRequestRole.Full, revealed = true, explicitlyOpened = true)
        assertTrue(result is MediaRequestDecision.Request)
        assertEquals(false, (result as MediaRequestDecision.Request).fullResourceAvailable)
    }

    @Test
    fun timelineUsesPreviewWhenFullIsMissing() {
        val previewOnly = attachment.copy(url = null)
        assertEquals(
            MediaRequestDecision.Request(attachment.previewUrl!!, MediaRequestRole.Preview, false),
            MediaRequestPolicy.resolve(previewOnly, MediaRequestRole.Preview, revealed = true, explicitlyOpened = false),
        )
    }

    @Test
    fun timelineRejectsAttachmentWhenBothUrlsAreMissing() {
        val missing = attachment.copy(url = null, previewUrl = null)
        assertEquals(
            MediaRequestDecision.NoRequest(MediaRequestReason.MissingPreview),
            MediaRequestPolicy.resolve(missing, MediaRequestRole.Preview, revealed = true, explicitlyOpened = false),
        )
    }

    @Test
    fun timelineDoesNotUseFullUrlForUnsupportedMediaKinds() {
        val video = attachment.copy(kind = MediaKind.Video, mimeType = "video/mp4", previewUrl = null)
        assertEquals(
            MediaRequestDecision.NoRequest(MediaRequestReason.UnsupportedKind),
            MediaRequestPolicy.resolve(video, MediaRequestRole.Preview, revealed = true, explicitlyOpened = false),
        )
    }

    @Test
    fun invalidUrlsAreRejected() {
        assertEquals(null, MediaRequestPolicy.validWebUrl("not-a-url"))
        assertEquals(null, MediaRequestPolicy.validWebUrl("null"))
        assertEquals("https://cdn.example/a?sig=1", MediaRequestPolicy.validWebUrl("https://cdn.example/a?sig=1"))
    }
}
