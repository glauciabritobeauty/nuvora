package com.nuvora.core.data.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderLinkTest {
    @Test
    fun `separa provedor usuario e senha do link get php`() {
        assertEquals(
            ProviderLogin("http://exemplo.com:8080", "maria123", "s3nh@"),
            parseProviderLink("http://exemplo.com:8080/get.php?username=maria123&password=s3nh%40&type=m3u_plus&output=hls"),
        )
    }

    @Test
    fun `funciona sem porta, com https e com espacos em volta`() {
        assertEquals(
            ProviderLogin("https://exemplo.com", "u", "p"),
            parseProviderLink("  https://exemplo.com/player_api.php?username=u&password=p  "),
        )
    }

    @Test
    fun `sem senha ou texto pela metade nao separa`() {
        assertNull(parseProviderLink("http://exemplo.com/get.php?username=maria&password="))
        assertNull(parseProviderLink("http://exemplo.com:8080"))
        assertNull(parseProviderLink("maria123"))
    }
}
