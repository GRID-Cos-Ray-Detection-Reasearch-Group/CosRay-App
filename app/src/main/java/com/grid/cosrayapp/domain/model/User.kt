package com.grid.cosrayapp.domain.model

data class User(
  val id: UserId,
  val email: String,
  val displayName: String,
  val avatarUrl: String? = null,
  val organization: String? = null,
  val roles: List<String> = emptyList(),
)
