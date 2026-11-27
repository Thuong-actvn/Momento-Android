package com.example.momento.feature.moment.domain.model

import java.io.File

data class MyJournalPhoto(
    val moment: Moment,
    val preview: File? = null,
    val isOffline: Boolean = false,
)
