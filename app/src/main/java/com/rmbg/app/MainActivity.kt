package com.rmbg.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.rmbg.app.presentation.MainViewModel
import com.rmbg.app.ui.MainScreen
import com.rmbg.app.ui.theme.RMBGTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RMBGTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

