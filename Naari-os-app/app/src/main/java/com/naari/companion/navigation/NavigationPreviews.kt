package com.naari.companion.navigation

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.naari.companion.designsystem.NaariTheme

@Preview(name = "Small Screen 320dp Width", widthDp = 320, heightDp = 640, showBackground = true)
@Composable
fun AppShellPreview320dp() {
    NaariTheme(darkTheme = false) {
        Surface {
            NaariAppShell()
        }
    }
}

@Preview(name = "Dark Theme Shell", widthDp = 360, heightDp = 720, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AppShellPreviewDark() {
    NaariTheme(darkTheme = true) {
        Surface {
            NaariAppShell()
        }
    }
}

@Preview(name = "200% Font Scale Shell", widthDp = 360, heightDp = 720, showBackground = true, fontScale = 2.0f)
@Composable
fun AppShellPreviewFontScale() {
    NaariTheme(darkTheme = false) {
        Surface {
            NaariAppShell()
        }
    }
}
