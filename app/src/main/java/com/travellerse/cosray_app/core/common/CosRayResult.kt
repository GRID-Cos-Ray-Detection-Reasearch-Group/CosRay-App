package com.travellerse.cosray_app.core.common

sealed interface CosRayResult<out T> {
  data class Success<T>(val data: T) : CosRayResult<T>

  data class Error(val throwable: Throwable, val message: String? = throwable.message) :
    CosRayResult<Nothing>
}

inline fun <T> runCosRayCatching(block: () -> T): CosRayResult<T> =
  try {
    CosRayResult.Success(block())
  } catch (error: Throwable) {
    CosRayResult.Error(error)
  }

inline fun <T> CosRayResult<T>.onSuccess(action: (T) -> Unit): CosRayResult<T> {
  if (this is CosRayResult.Success) action(data)
  return this
}

inline fun <T> CosRayResult<T>.onError(action: (Throwable) -> Unit): CosRayResult<T> {
  if (this is CosRayResult.Error) action(throwable)
  return this
}

inline fun <T, R> CosRayResult<T>.map(transform: (T) -> R): CosRayResult<R> =
  when (this) {
    is CosRayResult.Success -> CosRayResult.Success(transform(data))
    is CosRayResult.Error -> this
  }

inline fun <T, R> CosRayResult<T>.flatMap(transform: (T) -> CosRayResult<R>): CosRayResult<R> =
  when (this) {
    is CosRayResult.Success -> transform(data)
    is CosRayResult.Error -> this
  }

fun <T> CosRayResult<T>.getOrNull(): T? = (this as? CosRayResult.Success)?.data

fun <T> CosRayResult<T>.getOrDefault(default: T): T = getOrNull() ?: default

inline fun <T> CosRayResult<T>.getOrElse(onError: (Throwable) -> T): T =
  when (this) {
    is CosRayResult.Success -> data
    is CosRayResult.Error -> onError(throwable)
  }

fun <T> CosRayResult<T>.isSuccess(): Boolean = this is CosRayResult.Success

fun <T> CosRayResult<T>.isError(): Boolean = this is CosRayResult.Error
