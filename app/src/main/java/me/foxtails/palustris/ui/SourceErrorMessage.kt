package me.foxtails.palustris.ui

import me.foxtails.palustris.domain.SourceError

internal fun sourceErrorMessage(error: Exception): String = when (error) {
    is SourceError.Unauthorized -> "Access was denied. Sign in again and allow access to your account and timeline."
    is SourceError.AccountMismatch -> "The signed-in account did not match the account being upgraded. Nothing was changed."
    is SourceError.RateLimited -> "This instance is busy. Wait a moment and try again."
    is SourceError.Unsupported -> "This instance doesn't support ${error.feature}."
    is SourceError.NetworkUnavailable -> "Could not reach the instance. Check your connection and try again."
    is SourceError.ServerError -> error.detail ?: "Could not complete the request. Please try again."
    else -> "Could not complete the request. Please try again."
}

internal fun requiresSignIn(error: Exception): Boolean = error is SourceError.Unauthorized
