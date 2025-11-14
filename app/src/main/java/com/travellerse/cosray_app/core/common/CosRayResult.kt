package com.travellerse.cosray_app.core.common

sealed interface CosRayResult<out T> {
    data class Success<T>(val data: T) : CosRayResult<T>
    data class Error(val throwable: Throwable) : CosRayResult<Nothing>
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
