package com.travellerse.cosray_app.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {

    @Test
    fun `valid email passes validation`() {
        assertTrue(AuthValidator.isEmailValid("user@example.com"))
    }

    @Test
    fun `invalid email fails validation`() {
        assertFalse(AuthValidator.isEmailValid("invalid-email"))
    }

    @Test
    fun `strong password passes validation`() {
        assertTrue(AuthValidator.isPasswordStrong("Secure123"))
    }

    @Test
    fun `weak password fails validation`() {
        assertFalse(AuthValidator.isPasswordStrong("abc"))
    }
}
