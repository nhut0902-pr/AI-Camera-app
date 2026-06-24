package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.PhotoRepositoryImpl
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CameraViewModel
import com.example.ui.viewmodel.CameraViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize offline Room Database, DAO, and clean Repository
        val database = AppDatabase.getDatabase(this)
        val repository = PhotoRepositoryImpl(database.photoDao())
        
        // Build ViewModel using local manual Constructor Factory
        val viewModelFactory = CameraViewModelFactory(repository)
        val cameraViewModel = ViewModelProvider(this, viewModelFactory)[CameraViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    // Main layout
                    MainAppScreen(
                        viewModel = cameraViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
