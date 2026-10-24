package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.components.GlassCard
import com.example.ui.theme.SafeGuardTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun light_mode_theme_screenshot() {
    composeTestRule.setContent {
      SafeGuardTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
          GlassCard {
            Text("SafeGuard - Operator Console (Light Mode)")
          }
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/light_theme.png")
  }

  @Test
  fun dark_mode_theme_screenshot() {
    composeTestRule.setContent {
      SafeGuardTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
          GlassCard {
            Text("SafeGuard - Operator Console (Dark Mode)")
          }
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/dark_theme.png")
  }
}
