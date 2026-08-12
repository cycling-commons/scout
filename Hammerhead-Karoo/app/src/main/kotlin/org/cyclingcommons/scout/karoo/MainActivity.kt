package org.cyclingcommons.scout.karoo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.cyclingcommons.scout.karoo.screens.MainScreen
import org.cyclingcommons.scout.karoo.tagging.TaggingActivity
import org.cyclingcommons.scout.karoo.theme.ScoutKarooTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScoutKarooTheme {
                MainScreen(
                    onOpenTagging = {
                        startActivity(android.content.Intent(this, TaggingActivity::class.java))
                    },
                )
            }
        }
    }
}
