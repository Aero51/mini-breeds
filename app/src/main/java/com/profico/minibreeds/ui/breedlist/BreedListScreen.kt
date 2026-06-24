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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.profico.minibreeds.R
import com.profico.minibreeds.ui.common.BreedAvatar
import com.profico.minibreeds.ui.common.ErrorContent
import com.profico.minibreeds.ui.common.LoadingContent
import com.profico.minibreeds.ui.common.capitalized
import com.profico.minibreeds.ui.theme.MiniBreedsTheme

/**
 * Stateful entry point: obtains the [BreedListViewModel], collects its state,
 * and hands everything to the stateless [BreedListBody].
 */
@Composable
fun BreedListScreen(
    onBreedClick: (String) -> Unit,
    viewModel: BreedListViewModel = viewModel(factory = BreedListViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    BreedListBody(
        uiState = uiState,
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onBreedClick = onBreedClick,
        onToggleFavorite = viewModel::onToggleFavorite,
        onRetry = viewModel::retry,
    )
}

/** Stateless breed list UI: a search field above a Loading / Error / Content area. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BreedListBody(
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
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(text = stringResource(R.string.search_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when (uiState) {
                is BreedListUiState.Loading -> LoadingContent()
                is BreedListUiState.Error -> ErrorContent(isOffline = uiState.isOffline, onRetry = onRetry)
                is BreedListUiState.Content -> BreedList(
                    breeds = uiState.breeds,
                    onBreedClick = onBreedClick,
                    onToggleFavorite = onToggleFavorite,
                )
            }
        }
    }
}

@Composable
private fun BreedList(
    breeds: List<BreedListItem>,
    onBreedClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    if (breeds.isEmpty()) {
        Text(
            text = stringResource(R.string.empty_search_results),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
        )
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(breeds, key = { it.name }) { breed ->
            BreedRow(
                breed = breed,
                onClick = { onBreedClick(breed.name) },
                onToggleFavorite = { onToggleFavorite(breed.name) },
            )
        }
    }
}

@Composable
private fun BreedRow(
    breed: BreedListItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BreedAvatar(name = breed.name)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = breed.name.capitalized(), style = MaterialTheme.typography.titleMedium)
                if (breed.subBreedCount > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.sub_breed_count,
                            breed.subBreedCount,
                            breed.subBreedCount,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (breed.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(
                        if (breed.isFavorite) R.string.favorite_remove else R.string.favorite_add,
                        breed.name,
                    ),
                    tint = if (breed.isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BreedListPreview() {
    MiniBreedsTheme {
        BreedListBody(
            uiState = BreedListUiState.Content(
                breeds = listOf(
                    BreedListItem("bulldog", subBreedCount = 2, isFavorite = true),
                    BreedListItem("akita", subBreedCount = 0, isFavorite = false),
                ),
            ),
            query = "",
            onQueryChange = {},
            onBreedClick = {},
            onToggleFavorite = {},
            onRetry = {},
        )
    }
}
