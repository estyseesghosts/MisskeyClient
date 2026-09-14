package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.AccountId

/**
 * Account-level presentation for the account switcher.
 *
 * This contract carries the account list and account-level operations only. It must never carry
 * session secrets, access tokens, sources, or feature state.
 *
 * [Accounts.Empty] is an inert value for previews and for tests that exercise navigation chrome.
 * Production composition must supply a contract backed by the account manager.
 */
data class AccountSwitcher(
    val accounts: List<AccountRef>,
    val actions: Actions,
) {
    /** Account-level operations. Each operation is owned by the account manager. */
    interface Actions {
        fun switchTo(accountId: AccountId)
        fun addAccount()
        fun openSettings()
        fun signOut()
    }

    companion object {
        val Empty = AccountSwitcher(accounts = emptyList(), actions = EmptyActions)
    }
}

private object EmptyActions : AccountSwitcher.Actions {
    override fun switchTo(accountId: AccountId) = Unit
    override fun addAccount() = Unit
    override fun openSettings() = Unit
    override fun signOut() = Unit
}
