package com.travellerse.cosray_app.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Represents a piece of text that can be resolved to a localized string. Either [resId] or
 * [rawMessage] must be provided.
 */
data class UiMessage
@JvmOverloads
constructor(
  @param:StringRes val resId: Int? = null,
  val args: List<Any> = emptyList(),
  val rawMessage: String? = null,
) {
  init {
    require(resId != null || rawMessage != null) {
      "UiMessage requires either a string resource id or a raw message"
    }
  }

  companion object {
    fun from(@StringRes resId: Int, vararg args: Any): UiMessage =
      UiMessage(resId = resId, args = args.toList())

    fun fromRaw(message: String): UiMessage = UiMessage(rawMessage = message)
  }
}

@Composable
fun UiMessage.asString(): String {
  val resourceId = resId
  return if (resourceId != null) {
    stringResource(resourceId, *args.toTypedArray())
  } else {
    rawMessage.orEmpty()
  }
}
