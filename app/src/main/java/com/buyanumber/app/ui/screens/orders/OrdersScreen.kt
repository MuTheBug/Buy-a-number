package com.buyanumber.app.ui.screens.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyanumber.app.domain.model.NumberOrder
import com.buyanumber.app.domain.model.OrderSort
import com.buyanumber.app.ui.components.EmptyState
import com.buyanumber.app.ui.components.ErrorBanner
import com.buyanumber.app.ui.components.LoadingState
import com.buyanumber.app.ui.components.OrderCard
import com.buyanumber.app.ui.components.SortMenu

/** Every number the account has bought: live ones first, then the archive. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    onOpenOrder: (Long) -> Unit,
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Infinite scroll: fetch the next page as the last few rows come into view.
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore }
            .collect { if (it) viewModel.loadMore() }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Your numbers") },
            actions = {
                SortMenu(
                    selected = state.sort,
                    options = OrderSort.entries,
                    label = OrderSort::label,
                    onSelect = viewModel::setSort,
                )
            },
        )

        if (state.isLoading) {
            LoadingState()
            return@Column
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.error?.let { message -> item { ErrorBanner(message) } }

                if (state.active.isNotEmpty()) {
                    item { SectionTitle("Active") }
                    items(state.active, key = { "active-${it.id}" }) { order ->
                        OrderCard(order = order, onClick = { onOpenOrder(order.id) })
                    }
                }

                if (state.history.isNotEmpty()) {
                    item { SectionTitle("History") }
                    items(state.history, key = { "history-${it.id}" }) { order: NumberOrder ->
                        OrderCard(order = order, onClick = { onOpenOrder(order.id) })
                    }
                }

                if (state.active.isEmpty() && state.history.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No numbers yet",
                            body = "Numbers you buy will be listed here with their messages.",
                        )
                    }
                }

                if (state.isLoadingMore) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}
