package com.example.momento.feature.profile.data.remote.dto

import kotlinx.serialization.Serializable

/** Only the fields editable in Momento; omitted fields retain their values. */
@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val birthday: String? = null,
    val avatarUrl: String? = null,
)
