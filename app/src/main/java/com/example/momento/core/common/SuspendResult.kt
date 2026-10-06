package com.example.momento.core.common

import kotlinx.coroutines.CancellationException

/** Like runCatching, but lets the caller's coroutine cancel normally. */
suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
