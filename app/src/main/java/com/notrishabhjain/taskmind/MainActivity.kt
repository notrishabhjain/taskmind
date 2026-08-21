package com.notrishabhjain.taskmind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.notrishabhjain.taskmind.ui.home.HomeScreen
import com.notrishabhjain.taskmind.ui.home.HomeUiState
import com.notrishabhjain.taskmind.ui.theme.TaskMindTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaskMindTheme {
                HomeScreen(state = HomeUiState())
            }
        }
    }
}
