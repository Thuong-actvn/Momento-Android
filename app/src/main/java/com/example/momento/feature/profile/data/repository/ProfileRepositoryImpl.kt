package com.example.momento.feature.profile.data.repository

import com.example.momento.core.common.suspendRunCatching
import com.example.momento.core.network.ApiResponse
import com.example.momento.core.network.toApiException
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.core.user.data.mapper.toDomain
import com.example.momento.core.user.data.remote.dto.ProfileDto
import com.example.momento.core.user.domain.model.CurrentUser
import com.example.momento.feature.auth.domain.repository.AuthRepository
import com.example.momento.feature.profile.data.remote.ProfileApi
import com.example.momento.feature.profile.data.remote.dto.UpdateProfileRequest
import com.example.momento.feature.profile.domain.repository.ProfileRepository
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val api: ProfileApi,
    private val userStore: CurrentUserStore,
    private val authRepository: AuthRepository,
) : ProfileRepository {
    override val currentUser = userStore.currentUser
    // A refresh started before a save must not overwrite the saved profile later.
    private val requests = Mutex()

    override suspend fun refreshProfile(): Result<CurrentUser> = profileRequest { api.me() }

    override suspend fun updateProfile(
        displayName: String?,
        birthday: LocalDate?,
        avatarUrl: String?,
    ): Result<CurrentUser> = requestResult {
        val name = displayName?.trim()
        if (name != null) {
            require(name.isNotEmpty()) { "Display name is required" }
            require(name.length <= 50) { "Display name must not exceed 50 characters" }
        }
        require(birthday == null || birthday.isBefore(LocalDate.now())) {
            "Birthday must be before today"
        }
        val avatar = avatarUrl?.trim()
        require(avatar == null || avatar.toHttpUrlOrNull()?.isHttps == true) {
            "Avatar must be an HTTPS URL"
        }
        val session = userStore.requireSession()
        requests.withLock {
            userStore.checkSession(session)
            val current = checkNotNull(currentUser.value)
            val body = UpdateProfileRequest(
                displayName = name?.takeIf { it != current.displayName },
                birthday = birthday?.takeIf { it != current.birthday }?.toString(),
                avatarUrl = avatar?.takeIf { it != current.avatarUrl },
            )
            if (body == UpdateProfileRequest()) return@withLock current
            api.update(body).requireData().toDomain().also { userStore.update(session, it) }
        }
    }

    override suspend fun uploadAvatar(image: File, mimeType: String): Result<String> = requestResult {
        val session = userStore.requireSession()
        require(image.isFile && image.length() in 1..5L * 1024 * 1024) {
            "Avatar must be a nonempty image of at most 5 MiB"
        }
        val extension = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> error("Unsupported avatar image type")
        }
        val part = MultipartBody.Part.createFormData(
            "image", "avatar.$extension", image.asRequestBody(mimeType.toMediaType()),
        )
        val url = api.uploadAvatar(part).requireData().avatarUrl
        userStore.checkSession(session)
        check(url.toHttpUrlOrNull()?.isHttps == true) { "Upload returned an invalid avatar URL" }
        url
    }

    override suspend fun signOut(): Result<Unit> = requestResult {
        authRepository.signOut().getOrThrow()
    }

    private suspend fun profileRequest(
        request: suspend () -> ApiResponse<ProfileDto>,
    ): Result<CurrentUser> = requestResult { fetchProfile(request) }

    private suspend fun fetchProfile(
        request: suspend () -> ApiResponse<ProfileDto>,
    ): CurrentUser {
        val session = userStore.requireSession()
        return requests.withLock {
            userStore.checkSession(session)
            request().requireData().toDomain().also { userStore.update(session, it) }
        }
    }

    private suspend fun <T> requestResult(block: suspend () -> T): Result<T> =
        suspendRunCatching(block).recoverCatching {
            throw if (it is HttpException) it.toApiException() else it
        }
}
