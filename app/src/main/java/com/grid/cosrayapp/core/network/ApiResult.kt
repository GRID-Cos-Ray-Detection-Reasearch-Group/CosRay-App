package com.grid.cosrayapp.core.network

/**
 * Represents the result of an API call.
 *
 * A sealed class that encapsulates success, error, and loading states for network operations.
 */
sealed class ApiResult<out T> {
  /**
   * Successful API response with data.
   *
   * @property data The response data.
   */
  data class Success<T>(val data: T) : ApiResult<T>()

  /**
   * API error with details.
   *
   * @property code HTTP status code or custom error code.
   * @property message Human-readable error message.
   * @property cause Underlying exception, if any.
   */
  data class Error(val code: Int, val message: String, val cause: Throwable? = null) :
          ApiResult<Nothing>()

  /** Loading state for async operations. */
  data object Loading : ApiResult<Nothing>()

  /** Check if this result is successful. */
  val isSuccess: Boolean
    get() = this is Success

  /** Check if this result is an error. */
  val isError: Boolean
    get() = this is Error

  /** Check if this result is loading. */
  val isLoading: Boolean
    get() = this is Loading

  /** Get the data if successful, or null otherwise. */
  fun getOrNull(): T? =
          when (this) {
            is Success -> data
            else -> null
          }

  /** Get the data if successful, or throw the error. */
  fun getOrThrow(): T =
          when (this) {
            is Success -> data
            is Error -> throw cause ?: ApiException(code, message)
            is Loading -> error("Result is still loading")
          }

  /** Get the data if successful, or return the default value. */
  fun getOrDefault(default: @UnsafeVariance T): T =
          when (this) {
            is Success -> data
            else -> default
          }
}

/** Exception representing an API error. */
class ApiException(val code: Int, override val message: String) : Exception(message)

/** Transform the data of a successful result. */
inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> =
        when (this) {
          is ApiResult.Success -> ApiResult.Success(transform(data))
          is ApiResult.Error -> this
          is ApiResult.Loading -> this
        }

/** Perform an action if the result is successful. */
inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
  if (this is ApiResult.Success) action(data)
  return this
}

/** Perform an action if the result is an error. */
inline fun <T> ApiResult<T>.onError(action: (ApiResult.Error) -> Unit): ApiResult<T> {
  if (this is ApiResult.Error) action(this)
  return this
}

/** Perform an action if the result is loading. */
inline fun <T> ApiResult<T>.onLoading(action: () -> Unit): ApiResult<T> {
  if (this is ApiResult.Loading) action()
  return this
}

/** Fold the result into a single value. */
inline fun <T, R> ApiResult<T>.fold(
        onSuccess: (T) -> R,
        onError: (ApiResult.Error) -> R,
        onLoading: () -> R,
): R =
        when (this) {
          is ApiResult.Success -> onSuccess(data)
          is ApiResult.Error -> onError(this)
          is ApiResult.Loading -> onLoading()
        }

/** Convert a suspending function result to ApiResult. */
suspend fun <T> apiResultOf(block: suspend () -> T): ApiResult<T> =
        try {
          ApiResult.Success(block())
        } catch (e: ApiException) {
          ApiResult.Error(e.code, e.message, e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
          ApiResult.Error(-1, e.message ?: "Unknown error", e)
        }
