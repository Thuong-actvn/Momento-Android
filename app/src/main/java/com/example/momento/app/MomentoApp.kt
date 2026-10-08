package com.example.momento.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.momento.app.navigation.AppNavHost

@Composable
fun MomentoApp(onExit: () -> Unit) {
    val navController = rememberNavController()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        AppNavHost(
            navController = navController,
            onExit = onExit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
