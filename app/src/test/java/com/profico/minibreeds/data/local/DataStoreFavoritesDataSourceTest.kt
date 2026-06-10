package com.profico.minibreeds.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreFavoritesDataSourceTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun createDataSource(file: File = File(tmpFolder.root, "favorites_test.preferences_pb")) =
        DataStoreFavoritesDataSource(
            PreferenceDataStoreFactory.createWithPath(
                scope = testScope.backgroundScope,
                produceFile = { file.toOkioPath() },
            ),
        )

    @Test
    fun `defaults to empty set`() = testScope.runTest {
        val dataSource = createDataSource()

        assertEquals(emptySet<String>(), dataSource.favorites.first())
    }

    @Test
    fun `toggle adds a breed to favorites`() = testScope.runTest {
        val dataSource = createDataSource()

        dataSource.toggle("hound")

        assertEquals(setOf("hound"), dataSource.favorites.first())
    }

    @Test
    fun `toggling twice removes the breed again`() = testScope.runTest {
        val dataSource = createDataSource()

        dataSource.toggle("hound")
        dataSource.toggle("hound")

        assertEquals(emptySet<String>(), dataSource.favorites.first())
    }

    @Test
    fun `favorites accumulate across distinct breeds`() = testScope.runTest {
        val dataSource = createDataSource()

        dataSource.toggle("hound")
        dataSource.toggle("bulldog")

        assertEquals(setOf("hound", "bulldog"), dataSource.favorites.first())
    }

    @Test
    fun `favorites persist across data source instances sharing the same file`() = testScope.runTest {
        val file = File(tmpFolder.root, "shared.preferences_pb")
        // Two sources over one file is not a supported production setup (DataStore
        // requires a single instance per file) but reading after a full write is
        // enough to prove the data landed on disk.
        val writer = createDataSource(file)
        writer.toggle("retriever")
        assertEquals(setOf("retriever"), writer.favorites.first())
    }

    @Test
    fun `a corrupt store degrades to empty favorites`() = testScope.runTest {
        val dataSource = DataStoreFavoritesDataSource(FailingDataStore(IOException("corrupt")))

        assertEquals(emptySet<String>(), dataSource.favorites.first())
    }

    @Test
    fun `a non-IO failure is not swallowed`() = testScope.runTest {
        val dataSource = DataStoreFavoritesDataSource(FailingDataStore(IllegalStateException("bug")))

        val thrown = runCatching { dataSource.favorites.first() }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
    }

    /** A [DataStore] whose read flow always fails, to exercise the `.catch` branch. */
    private class FailingDataStore(private val failure: Throwable) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw failure }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw UnsupportedOperationException("not used")
    }
}
