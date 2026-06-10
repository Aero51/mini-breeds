package com.profico.minibreeds.ui.breeddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.profico.minibreeds.R
import com.profico.minibreeds.ui.common.BreedAvatar
import com.profico.minibreeds.ui.common.ErrorContent
import com.profico.minibreeds.ui.common.FavoriteIcon
import com.profico.minibreeds.ui.common.LoadingContent
import com.profico.minibreeds.ui.theme.MiniBreedsTheme
import org.koin.androidx.compose.koinViewModel

/** Semantics test tags for breed detail screen nodes. */
object BreedDetailTestTags {
    const val BACK_BUTTON = "breed_detail_back"
    const val FAVORITE_BUTTON = "breed_detail_favorite"
    const val TITLE = "breed_detail_title"
    const val NO_SUB_BREEDS = "breed_detail_no_sub_breeds"
    /** Tag for the sub-breed card identified by [name]. */
    fun subBreed(name: String) = "sub_breed_$name"
}

/**
 * Stateful navigation entry point for the breed detail screen.
 * Collects state from [BreedDetailViewModel] and delegates rendering to [BreedDetailScreen].
 */
@Composable
fun BreedDetailRoute(
    onNavigateBack: () -> Unit,
    viewModel: BreedDetailViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BreedDetailScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onToggleFavorite = viewModel::onToggleFavorite,
        onRetry = viewModel::retry,
    )
}

/**
 * Stateless breed detail screen. Shows a top bar with a back button and a favorite toggle,
 * then switches between Loading, Error, and Content states based on [uiState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreedDetailScreen(
    uiState: BreedDetailUiState,
    onNavigateBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag(BreedDetailTestTags.BACK_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    if (uiState is BreedDetailUiState.Content) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.testTag(BreedDetailTestTags.FAVORITE_BUTTON),
                        ) {
                            FavoriteIcon(
                                isFavorite = uiState.isFavorite,
                                breedName = uiState.name,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            is BreedDetailUiState.Loading -> LoadingContent(
                modifier = Modifier.padding(innerPadding),
            )
            is BreedDetailUiState.Error -> ErrorContent(
                error = uiState.error,
                onRetry = onRetry,
                modifier = Modifier.padding(innerPadding),
            )
            is BreedDetailUiState.Content -> BreedDetailContent(
                content = uiState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

/** Content area showing the breed header and either a sub-breed list or a "no sub-breeds" message. */
@Composable
private fun BreedDetailContent(
    content: BreedDetailUiState.Content,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        BreedHeader(content = content)

        if (content.subBreeds.isEmpty()) {
            Text(
                text = stringResource(R.string.no_sub_breeds),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 24.dp)
                    .testTag(BreedDetailTestTags.NO_SUB_BREEDS),
            )
        } else {
            Text(
                text = stringResource(R.string.sub_breeds_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(content.subBreeds, key = { it }) { subBreed ->
                    SubBreedCard(name = subBreed)
                }
            }
        }
    }
}

/** Centered hero section with a large [BreedAvatar], the breed name, and the sub-breed count. */
@Composable
private fun BreedHeader(content: BreedDetailUiState.Content) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BreedAvatar(name = content.name, size = 88.dp)
        Text(
            text = content.name.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(top = 12.dp)
                .testTag(BreedDetailTestTags.TITLE),
        )
        if (content.subBreeds.isNotEmpty()) {
            Text(
                text = pluralStringResource(
                    R.plurals.sub_breed_count,
                    content.subBreeds.size,
                    content.subBreeds.size,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Card row for a single sub-breed, showing a small [BreedAvatar] and the sub-breed name. */
@Composable
private fun SubBreedCard(name: String) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(BreedDetailTestTags.subBreed(name)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BreedAvatar(name = name, size = 28.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = name.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BreedDetailScreenPreview() {
    MiniBreedsTheme {
        BreedDetailScreen(
            uiState = BreedDetailUiState.Content(
                name = "bulldog",
                subBreeds = listOf("boston", "french"),
                isFavorite = true,
            ),
            onNavigateBack = {},
            onToggleFavorite = {},
            onRetry = {},
        )
    }
}
