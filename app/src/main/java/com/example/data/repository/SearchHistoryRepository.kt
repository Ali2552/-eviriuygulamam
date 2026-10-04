package com.example.data.repository

import com.example.data.db.SearchHistoryDao
import com.example.data.db.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

class SearchHistoryRepository(private val searchHistoryDao: SearchHistoryDao) {

    val recentSearches: Flow<List<SearchHistoryEntity>> = searchHistoryDao.getRecentSearches()

    suspend fun addSearch(query: String, filterType: String) {
        if (query.isBlank()) return
        searchHistoryDao.insertSearch(
            SearchHistoryEntity(
                query = query.trim(),
                filterType = filterType,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteSearch(id: Long) = searchHistoryDao.deleteSearchById(id)

    suspend fun clearHistory() = searchHistoryDao.clearHistory()
}
