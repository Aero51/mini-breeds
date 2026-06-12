package com.profico.minibreeds.ui.breedlist

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.profico.minibreeds.R
import com.profico.minibreeds.ui.common.BreedAvatar
import com.profico.minibreeds.ui.common.ErrorContent
import com.profico.minibreeds.ui.common.FavoriteIcon
import com.profico.minibreeds.ui.common.LoadingContent
import com.profico.minibreeds.ui.common.capitalized
import com.profico.minibreeds.ui.theme.MiniBreedsTheme
import org.koin.androidx.compose.koinViewModel

/** Semantics test tags for breed list screen nodes. */
object BreedListTestTags {
    const val SEARCH_FIELD = "breed_list_search"
    const val LIST = "breed_list"
    const val EMPTY_RESULTS = "breed_list_empty"
    /** Tag for the card row identified by [name]. */
    fun row(name: String) = "breed_row_$name"
    /** Tag for the favorite toggle button inside the card identified by [name]. */
    fun favorite(name: String) = "breed_favorite_$name"
}

/**
 * Stateful navigation entry point for the breed list screen.
 * Collects state from [BreedListViewModel] and delegates rendering to [BreedListScreen].
 */
@Composable
fun BreedListRoute(
    onBreedClick: (String) -> Unit,
    viewModel: BreedListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    BreedListScreen(
        uiState = uiState,
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onBreedClick = onBreedClick,
        onToggleFavorite = viewModel::onToggleFavorite,
        onRetry = viewModel::retry,
    )
}

/**
 * Stateless breed list screen. Renders a search field above the content area and
 * switches between Loading, Error, and Content states based on [uiState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreedListScreen(
    uiState: BreedListUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onBreedClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.breed_list_title)) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchField(query = query, onQueryChange = onQueryChange)

            when (uiState) {
                is BreedListUiState.Loading -> LoadingContent()
                is BreedListUiState.Error -> ErrorContent(
                    error = uiState.error,
                    onRetry = onRetry,
                )
                is BreedListUiState.Content -> BreedList(
                    content = uiState,
                    onBreedClick = onBreedClick,
                    onToggleFavorite = onToggleFavorite,
                )
            }
        }
    }
}

/** Pill-shaped search field with a leading search icon and a trailing clear button. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        shape = CircleShape,
        placeholder = { Text(text = stringResource(R.string.search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.search_clear),
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(BreedListTestTags.SEARCH_FIELD),
    )
}

/** Scrollable list of breed cards, or [EmptySearchResults] when [content] has no rows. */
@Composable
private fun BreedList(
    content: BreedListUiState.Content,
    onBreedClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    if (content.noResultsForQuery) {
        EmptySearchResults()
        return
    }

    Text(
        text = pluralStringResource(
            R.plurals.breed_count,
            content.rows.size,
            content.rows.size,
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 4.dp),
    )
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag(BreedListTestTags.LIST),
    ) {
        items(content.rows, key = { it.name }) { row ->
            BreedCard(
                row = row,
                onClick = { onBreedClick(row.name) },
                onToggleFavorite = { onToggleFavorite(row.name) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** Single breed card showing a monogram avatar, the breed name, sub-breed count, and a favorite toggle. */
@Composable
private fun BreedCard(
    row: BreedRowUi,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(BreedListTestTags.row(row.name)),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BreedAvatar(name = row.name)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name.capitalized(),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (row.subBreedCount > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.sub_breed_count,
                            row.subBreedCount,
                            row.subBreedCount,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.testTag(BreedListTestTags.favorite(row.name)),
            ) {
                FavoriteIcon(isFavorite = row.isFavorite, breedName = row.name)
            }
        }
    }
}

/** Full-screen placeholder shown when the search query matches no breeds. */
@Composable
private fun EmptySearchResults() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "đźľ", fontSize = 56.sp)
        Text(
            text = stringResource(R.string.empty_search_results),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 12.dp)
                .testTag(BreedListTestTags.EMPTY_RESULTS),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BreedListScreenPreview() {
    MiniBreedsTheme {
        BreedListScreen(
            uiState = BreedListUiState.Content(
                rows = listOf(
                    BreedRowUi("bulldog", 2, isFavorite = true),
                    BreedRowUi("akita", 0, isFavorite = false),
                ),
                noResultsForQuery = false,
            ),
            query = "",
            onQueryChange = {},
            onBreedClick = {},
            onToggleFavorite = {},
            onRetry = {},
        )
    }
}
