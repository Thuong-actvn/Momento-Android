package com.example.momento.app.main

import androidx.lifecycle.ViewModel
import com.example.momento.feature.profile.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    repository: ProfileRepository,
) : ViewModel() {
    val currentUser = repository.currentUser
}
