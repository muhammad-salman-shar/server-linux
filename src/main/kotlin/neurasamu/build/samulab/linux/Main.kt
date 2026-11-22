package neurasamu.build.samulab.linux

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Dimension

fun main() = application {
    val state = rememberWindowState(size = DpSize(1100.dp, 750.dp))
    val shortcuts = remember { MutableSharedFlow<ShortcutAction>(extraBufferCapacity = 16) }
    val windowIcon = remember {
        runCatching {
            Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use {
                BitmapPainter(SkiaImage.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
            }
        }.getOrNull()
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "SaMu Lab",
        icon = windowIcon,
        state = state,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown) {
                when {
                    event.isCtrlPressed && event.isShiftPressed && event.key == Key.K -> {
                        shortcuts.tryEmit(ShortcutAction.CLEAR_CHAT); true
                    }
                    event.isCtrlPressed && event.key == Key.N -> {
                        shortcuts.tryEmit(ShortcutAction.NEW_CHAT); true
                    }
                    event.isCtrlPressed && event.key == Key.Comma -> {
                        shortcuts.tryEmit(ShortcutAction.OPEN_SETTINGS); true
                    }
                    event.isCtrlPressed && event.key == Key.E -> {
                        shortcuts.tryEmit(ShortcutAction.EDIT_LAST_USER); true
                    }
                    event.isCtrlPressed && event.key == Key.R -> {
                        shortcuts.tryEmit(ShortcutAction.REGENERATE_LAST); true
                    }
                    event.key == Key.Escape -> {
                        shortcuts.tryEmit(ShortcutAction.CLOSE_MODALS); false
                    }
                    else -> false
                }
            } else false
        }
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(600, 500)
        }
        App(shortcuts = shortcuts)
    }
}
