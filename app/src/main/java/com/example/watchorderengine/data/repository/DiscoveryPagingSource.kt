package com.example.watchorderengine.data.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.network.TmdbConfig

class DiscoveryPagingSource(
    private val repository: MediaRepository,
    private val category: TmdbConfig.DiscoveryCategory?,
    private val providerIds: Set<Int>
) : PagingSource<Int, MediaSummary>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaSummary> {
        val page = params.key ?: 1
        return try {
            val rawItems = if (category != null) {
                repository.discoverByGenrePaged(category, providerIds, page)
            } else {
                repository.getTrendingPaged(providerIds, page)
            }

            // Exclude already tracked or skipped items at the source
            val tracked = repository.getAllTrackedMediaIds()
            val skipped = repository.getSkippedMediaIds()
            
            val items = rawItems.filter { it.id !in tracked && it.id !in skipped }

            LoadResult.Page(
                data = items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (items.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MediaSummary>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
