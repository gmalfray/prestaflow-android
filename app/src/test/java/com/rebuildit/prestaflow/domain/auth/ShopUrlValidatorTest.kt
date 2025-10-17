package com.rebuildit.prestaflow.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopUrlValidatorTest {

    private val validator = ShopUrlValidator()

    @Test
    fun `valid https url returns normalized value`() {
        val result = validator.validate("https://example.com/")
        assertTrue(result is ShopUrlValidator.Result.Valid)
        assertEquals("https://example.com", (result as ShopUrlValidator.Result.Valid).normalizedUrl)
    }

    @Test
    fun `http url is rejected`() {
        val result = validator.validate("http://example.com")
        assertTrue(result is ShopUrlValidator.Result.Invalid.NonHttps)
    }

    @Test
    fun `empty string is rejected`() {
        val result = validator.validate("   ")
        assertTrue(result is ShopUrlValidator.Result.Invalid.Empty)
    }
}
