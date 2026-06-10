package com.profico.minibreeds.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.profico.minibreeds.R

/**
 * Heart icon that spring-bounces and re-tints whenever [isFavorite] changes.
 *
 * @param isFavorite Whether the breed is currently marked as a favorite.
 * @param breedName Used for the accessibility content description.
 */
@Composable
fun FavoriteIcon(
    isFavorite: Boolean,
    breedName: String,
) {
    val tint by animateColorAsState(
        targetValue = if (isFavorite) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "favoriteTint",
    )
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "favoriteScale",
    )

    Icon(
        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        contentDescription = stringResource(
            if (isFavorite) R.string.favorite_remove else R.string.favorite_add,
            breedName,
        ),
        tint = tint,
        modifier = Modifier.scale(scale),
    )
}
