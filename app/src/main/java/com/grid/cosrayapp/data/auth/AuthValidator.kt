package com.grid.cosrayapp.data.auth

object AuthValidator {
  private const val MIN_PASSWORD_LENGTH = 8
  private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

  fun isEmailValid(email: String): Boolean = email.isNotBlank() && emailRegex.matches(email)

  fun isPasswordStrong(password: String): Boolean =
    password.length >= MIN_PASSWORD_LENGTH &&
      password.any(Char::isUpperCase) &&
      password.any(Char::isDigit)
}
