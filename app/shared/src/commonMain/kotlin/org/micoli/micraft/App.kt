package org.micoli.micraft

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import org.micoli.micraft.ui.McGameUI
import org.micoli.micraft.ui.McUiState

@Composable
@Preview
fun App() {
    val state = remember { McUiState() }
    MaterialTheme {
        McGameUI(state)
    }
}
