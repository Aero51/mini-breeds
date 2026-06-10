package com.profico.minibreeds.data.repository

import com.profico.minibreeds.core.AppError
import com.profico.minibreeds.core.AppResult
import com.profico.minibreeds.data.remote.DogApiService
import com.profico.minibreeds.domain.model.Breed
import com.profico.minibreeds.testutil.FakeFavoritesDataSource
import com.profico.minibreeds.testutil.TestDispatcherProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Exercises the real Retrofit + OkHttp + kotlinx.serialization stack against
 * a local MockWebServer, so the exception→AppError mapping is tested
 * end to end rather than against hand-thrown exceptions.
 */
class BreedRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: BreedRepositoryImpl
    private lateinit var favorites: FakeFavoritesDataSource

    private val testDispatcher = StandardTestDispatcher()

    private val successBody = """
        {
          "message": {
            "hound": ["afghan", "basset"],
            "bulldog": ["boston", "french"],
            "akita": []
          },
          "status": "success"
        }
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(500, TimeUnit.MILLISECONDS)
            .build()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DogApiService::class.java)

        favorites = FakeFavoritesDataSource()
        repository = BreedRepositoryImpl(api, favorites, TestDispatcherProvider(testDispatcher))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    @Test
    fun `successful refresh maps and sorts breeds and fills cache`() = runTest(testDispatcher) {
        enqueue(successBody)

        val result = repository.refreshBreeds()

        val expected = listOf(
            Breed("akita", emptyList()),
            Breed("bulldog", listOf("boston", "french")),
            Breed("hound", listOf("afghan", "basset")),
        )
        assertEquals(AppResult.Success(expected), result)
        assertEquals(expected, repository.cachedBreeds.value)
    }

    @Test
    fun `http 500 maps to Http error and leaves cache untouched`() = runTest(testDispatcher) {
        enqueue("oops", code = 500)

        val result = repository.refreshBreeds()

        assertEquals(AppResult.Failure(AppError.Http(500)), result)
        assertNull(repository.cachedBreeds.value)
    }

    @Test
    fun `malformed body maps to Serialization error`() = runTest(testDispatcher) {
        enqueue("""{"message": "definitely not a map"}""")

        val result = repository.refreshBreeds()

        assertEquals(AppResult.Failure(AppError.Serialization), result)
    }

    @Test
    fun `api status other than success maps to ApiStatus error`() = runTest(testDispatcher) {
        enqueue("""{"message": {}, "status": "error"}""")

        val result = repository.refreshBreeds()

        assertEquals(AppResult.Failure(AppError.ApiStatus("error")), result)
    }

    @Test
    fun `unreachable server maps to NoConnection`() = runTest(testDispatcher) {
        server.close() // connection refused from now on

        val result = repository.refreshBreeds()

        assertEquals(AppResult.Failure(AppError.NoConnection), result)
    }

    @Test
    fun `slow response maps to Timeout`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(successBody)
                .bodyDelay(2, TimeUnit.SECONDS) // longer than the 500ms read timeout
                .build(),
        )

        val result = repository.refreshBreeds()

        assertEquals(AppResult.Failure(AppError.Timeout), result)
    }

    @Test
    fun `observeBreed emits matching breed after refresh`() = runTest(testDispatcher) {
        enqueue(successBody)
        repository.refreshBreeds()

        val breed = repository.observeBreed("bulldog").first()

        assertEquals(Breed("bulldog", listOf("boston", "french")), breed)
    }

    @Test
    fun `observeBreed emits null for unknown breed and cold cache`() = runTest(testDispatcher) {
        assertNull(repository.observeBreed("bulldog").first())

        enqueue(successBody)
        repository.refreshBreeds()

        assertNull(repository.observeBreed("not-a-breed").first())
    }

    @Test
    fun `failed refresh keeps previous cache`() = runTest(testDispatcher) {
        enqueue(successBody)
        repository.refreshBreeds()
        enqueue("oops", code = 500)

        repository.refreshBreeds()

        assertEquals(3, repository.cachedBreeds.value?.size)
    }

    @Test
    fun `toggleFavorite delegates to data source`() = runTest(testDispatcher) {
        repository.toggleFavorite("hound")

        assertEquals(listOf("hound"), favorites.toggledNames)
        assertTrue(repository.favorites.first().contains("hound"))
    }
}
