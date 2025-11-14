package com.travellerse.cosray_app.data.auth

object AuthValidator {
    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

    fun isEmailValid(email: String): Boolean = email.isNotBlank() && emailRegex.matches(email)
    fun isPasswordStrong(password: String): Boolean =
            password.length >= 8 && password.any(Char::isUpperCase) && password.any(Char::isDigit)
}
