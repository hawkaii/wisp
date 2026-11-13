package app.wispnotes.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import app.wispnotes.ui.theme.LocalReduceMotion

/** The two scopes a shared-element (container transform) transition needs. */
@OptIn(ExperimentalSharedTransitionApi::class)
class Shared(val scope: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

/** Marks a queue card and the note header as the same container, so the card grows into the note. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.noteBounds(shared: Shared?, fileName: String): Modifier {
    if (shared == null) return this
    val reduce = LocalReduceMotion.current
    val self = this
    return with(shared.scope) {
        self.sharedBounds(
            sharedContentState = rememberSharedContentState("note-$fileName"),
            animatedVisibilityScope = shared.visibility,
            boundsTransform = { _, _ ->
                if (reduce) snap<Rect>() else spring<Rect>(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
            },
        )
    }
}
