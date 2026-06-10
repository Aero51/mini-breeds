package com.profico.minibreeds.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

/**
 * Circular monogram avatar for a breed.
 *
 * The background hue is derived from [name]'s hash code, so each breed has a
 * stable, distinct color everywhere it appears. Colors are memoized per name
 * and theme mode to avoid recalculating on every recomposition.
 *
 * @param name Breed name; the first character is shown as the monogram letter.
 * @param modifier Modifier applied to the outer [androidx.compose.ui.layout.Layout].
 * @param size Diameter of the circle.
 */
@Composable
fun BreedAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val (container, content) = remember(name, darkTheme) {
        val hue = (name.hashCode().absoluteValue % 360).toFloat()
        Color.hsl(
            hue = hue,
            saturation = if (darkTheme) 0.30f else 0.55f,
            lightness = if (darkTheme) 0.30f else 0.85f,
        ) to Color.hsl(
            hue = hue,
            saturation = if (darkTheme) 0.55f else 0.50f,
            lightness = if (darkTheme) 0.85f else 0.28f,
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercase().orEmpty(),
            color = content,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}
