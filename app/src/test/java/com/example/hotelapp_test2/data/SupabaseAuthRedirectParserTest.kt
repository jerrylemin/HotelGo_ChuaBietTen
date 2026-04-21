package com.example.hotelapp_test2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupabaseAuthRedirectParserTest {
    @Test
    fun parse_readsTokensFromFragment() {
        val result = SupabaseAuthRedirectParser.parse(
            "hotelapp://auth-callback#access_token=access123&refresh_token=refresh456&type=signup"
        )

        requireNotNull(result)
        assertEquals("access123", result.accessToken)
        assertEquals("refresh456", result.refreshToken)
        assertEquals("signup", result.type)
    }

    @Test
    fun parse_mergesQueryAndFragmentValues() {
        val result = SupabaseAuthRedirectParser.parse(
            "hotelapp://auth-callback?type=signup#access_token=access%20123&refresh_token=refresh%20456"
        )

        requireNotNull(result)
        assertEquals("access 123", result.accessToken)
        assertEquals("refresh 456", result.refreshToken)
        assertEquals("signup", result.type)
    }

    @Test
    fun parse_returnsNullWhenRequiredTokensMissing() {
        assertNull(SupabaseAuthRedirectParser.parse("hotelapp://auth-callback#type=signup"))
    }
}
