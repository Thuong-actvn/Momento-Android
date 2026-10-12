package com.example.momento.feature.moment.presentation.journal

import androidx.annotation.StringRes
import com.example.momento.feature.moment.domain.model.Moment
import com.example.momento.feature.moment.domain.model.MomentOwner

sealed interface JournalFilter {
    data object All : JournalFilter
    data object OnlyMe : JournalFilter
    data class Friend(val id: String) : JournalFilter
}

data class JournalItem(
    val moment: Moment,
    val owner: MomentOwner? = null,
    val isOwn: Boolean = false,
)

data class JournalSection(
    val id: String,
    val title: String,
    val items: List<JournalItem>,
    val highlighted: Boolean = false,
)

data class JournalUiState(
    val items: List<JournalItem> = emptyList(),
    val friends: List<MomentOwner> = emptyList(),
    val filter: JournalFilter = JournalFilter.All,
    val isLoading: Boolean = false,
    @param:StringRes val error: Int? = null,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    @param:StringRes val loadMoreError: Int? = null,
    val isLoadingFriends: Boolean = false,
    @param:StringRes val friendsError: Int? = null,
)

enum class JournalLoad {
    INITIAL,
    REFRESH,
    MORE,
}
