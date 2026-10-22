package com.irlstream.core.rtmp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RtmpUrlTest {

    @Test
    fun parsesStandardIngestUrl() {
        val url = RtmpUrl.parse("rtmp://live.twitch.tv/app/live_123456_abcdef")
        assertNotNull(url)
        assertEquals("live.twitch.tv", url!!.host)
        assertEquals(1935, url.port)
        assertEquals("app", url.app)
        assertEquals("live_123456_abcdef", url.streamName)
        assertFalse(url.secure)
    }

    @Test
    fun parsesRtmpsAndCustomPort() {
        val url = RtmpUrl.parse("rtmps://example.com:443/live/stream-key?token=abc")
        assertNotNull(url)
        assertTrue(url!!.secure)
        assertEquals(443, url.port)
        assertEquals("live", url.app)
        assertEquals("stream-key?token=abc", url.streamName)
    }

    @Test
    fun parsesIpv6() {
        val url = RtmpUrl.parse("rtmp://[2001:db8::1]:1936/live/test")
        assertNotNull(url)
        assertEquals("2001:db8::1", url!!.host)
        assertEquals(1936, url.port)
        assertEquals("test", url.streamName)
    }

    @Test
    fun rejectsIncompleteUrls() {
        assertNull(RtmpUrl.parse("rtmp://host/app"))
        assertNull(RtmpUrl.parse("http://host/app/key"))
        assertNull(RtmpUrl.parse("rtmp:///app/key"))
        assertNull(RtmpUrl.parse("nonsense"))
    }

    @Test
    fun masksStreamKey() {
        val url = RtmpUrl.parse("rtmp://a.rtmp.youtube.com/live2/abcd-1234-efgh")!!
        val masked = url.masked()
        assertFalse(masked.contains("abcd-1234"))
        assertTrue(masked.endsWith("efgh"))
    }
}
