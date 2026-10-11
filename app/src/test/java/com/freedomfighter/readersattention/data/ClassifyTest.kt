package com.freedomfighter.readersattention.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifyTest {
    private val none = Classify.CATEGORY_UNDEFINED

    @Test fun `the system's categories decide first`() {
        assertTrue(Classify.defaultReflex("com.example.anything", Classify.CATEGORY_SOCIAL))
        assertTrue(Classify.defaultReflex("com.example.anything", Classify.CATEGORY_GAME))
        assertTrue(Classify.defaultReflex("com.example.anything", Classify.CATEGORY_VIDEO))
        assertFalse(Classify.defaultReflex("com.example.anything", 7)) // productivity
        assertFalse(Classify.defaultReflex("com.example.anything", none))
    }

    @Test fun `well-known reflex apps without a category`() {
        listOf(
            "com.instagram.android", "com.zhiliaoapp.musically", "com.facebook.katana", "com.snapchat.android",
            "com.twitter.android", "com.reddit.frontpage", "com.google.android.youtube", "com.netflix.mediaclient",
            "tv.twitch.android.app", "com.pinterest", "com.instagram.barcelona", "com.bereal.ft", "com.king.candycrushsaga"
        ).forEach { assertTrue(it, Classify.defaultReflex(it, none)) }
        listOf("com.google.android.apps.maps", "com.whatsapp", "com.google.android.apps.youtube.music", "org.mozilla.firefox", "com.android.chrome")
            .forEach { assertFalse(it, Classify.defaultReflex(it, none)) }
    }

    @Test fun `the person's choice wins over the default`() {
        assertFalse(Classify.isReflex("com.instagram.android", none, emptySet(), setOf("com.instagram.android")))
        assertTrue(Classify.isReflex("com.android.chrome", none, setOf("com.android.chrome"), emptySet()))
        assertTrue(Classify.isReflex("com.whatsapp", Classify.CATEGORY_SOCIAL, emptySet(), emptySet()))
        assertFalse(Classify.isReflex("com.whatsapp", Classify.CATEGORY_SOCIAL, emptySet(), setOf("com.whatsapp")))
    }
}
