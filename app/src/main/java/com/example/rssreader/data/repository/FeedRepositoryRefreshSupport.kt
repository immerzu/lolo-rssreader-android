package com.example.rssreader.data.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal class RefreshCoordinator<T>(
    private val parallelism: Int,
    private val task: suspend (T) -> RefreshFeedOutcome,
    private val onFailure: (T, Throwable) -> RefreshFeedOutcome
) {
    suspend fun run(items: List<T>): List<RefreshFeedOutcome> = supervisorScope {
        val semaphore = Semaphore(parallelism)
        items.map { item ->
            async {
                semaphore.withPermit {
                    runCatching { task(item) }
                        .getOrElse { throwable -> onFailure(item, throwable) }
                }
            }
        }.awaitAll()
    }
}

internal data class RefreshFeedOutcome(
    val insertedArticles: Int?,
    val retryableFailure: Boolean
)

internal data class RefreshAccumulator(
    val refreshedFeeds: Int = 0,
    val failedFeeds: Int = 0,
    val retryableFeeds: Int = 0,
    val newArticles: Int = 0
) {
    fun add(outcome: RefreshFeedOutcome): RefreshAccumulator {
        return if (outcome.insertedArticles != null) {
            copy(
                refreshedFeeds = refreshedFeeds + 1,
                newArticles = newArticles + outcome.insertedArticles
            )
        } else {
            copy(
                failedFeeds = failedFeeds + 1,
                retryableFeeds = retryableFeeds + if (outcome.retryableFailure) 1 else 0
            )
        }
    }

    fun addAll(outcomes: List<RefreshFeedOutcome>): RefreshAccumulator {
        return outcomes.fold(this) { accumulator, outcome -> accumulator.add(outcome) }
    }

    fun toStats(skippedFeeds: Int): RefreshRunStats {
        return RefreshRunStats(
            refreshedFeeds = refreshedFeeds,
            skippedFeeds = skippedFeeds,
            failedFeeds = failedFeeds,
            retryableFeeds = retryableFeeds,
            newArticles = newArticles
        )
    }
}

internal fun buildRefreshRunStats(
    outcomes: List<RefreshFeedOutcome>,
    skippedFeeds: Int
): RefreshRunStats {
    return RefreshAccumulator()
        .addAll(outcomes)
        .toStats(skippedFeeds)
}
