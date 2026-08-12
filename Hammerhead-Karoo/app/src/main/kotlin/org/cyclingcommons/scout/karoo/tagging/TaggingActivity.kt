package org.cyclingcommons.scout.karoo.tagging

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.cyclingcommons.scout.karoo.theme.ScoutKarooTheme

class TaggingActivity : ComponentActivity() {
    private val viewModel: TaggingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScoutKarooTheme {
                val model by viewModel.ui.collectAsState()
                TaggingScreen(
                    model = model,
                    onTileTap = viewModel::onTileTap,
                    onEndOpenSurface = viewModel::onEndOpenSurface,
                    onDismissMessage = viewModel::dismissMessage,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setUiVisible(true)
    }

    override fun onStop() {
        viewModel.setUiVisible(false)
        super.onStop()
    }
}
