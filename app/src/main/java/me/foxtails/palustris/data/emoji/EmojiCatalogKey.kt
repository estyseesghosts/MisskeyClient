package me.foxtails.palustris.data.emoji

import java.util.Base64
import me.foxtails.palustris.domain.AccountId

internal fun AccountId.emojiAccountKey(): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString("${connection.origin}\u0000$localId".toByteArray(Charsets.UTF_8))
