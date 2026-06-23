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

    /** Canonical happy-path body: three breeds, one of them with an empty sub-breed list. */
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

    /**
     * Boots a fresh [MockWebServer], builds an [OkHttpClient] with aggressive
     * 500ms read / 2s connect timeouts (so timeout tests resolve quickly),
     * wires Retrofit with kotlinx-serialization, and constructs the SUT with
     * a [FakeFavoritesDataSource] and a [TestDispatcherProvider].
     */
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

    /** Shuts down the [MockWebServer] so the next test starts clean. */
    @After
    fun tearDown() {
        server.close()
    }

    /** Helper that enqueues one [MockResponse] with the given body/code. */
    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    /**
     * Happy-path refresh: the response is mapped to [Breed]s, sorted
     * alphabetically (`akita`, `bulldog`, `hound`), and copied into
     * `cachedBreeds`.
     */
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

    /**
     * HTTP 500 surfaces as [AppError.Http] with the status code preserved, and
     * a cold cache stays `null` — failures must not poison subsequent reads.
     */
    @Test
    fun `http 500 maps to Http error and leaves cache untouched`() = runTest(testDispatcher) {
        enqueue("oops", code = 500)

        val result = repository.refreshBreeds()

        assertEquals(AppResult.Failure(AppError.Http(500)), result)
        assertNull(repository.cachedBreeds.value)
    }

    /** A 200 with a body whose `message` isn't a map maps to [AppError.Serialization]. */
    @Test
    fun `malformed body maps to Serialization error`() = runTest(testDispatcher) {
        enqueue("""{"message": "definitely not a map"}""")

        val result = repository.refreshBreeds()

        assertEquals(AppResult.Failure(AppError.Serialization), result)
    }

    /**
     * An empty `message: {}` with `status: success` is a legitimate empty list,
     * not an error.
     */
    @Test
    fun `successful response with no breeds maps to empty list`() = runTest(testDispatcher) {
        enqueue("""{"message": {}, "status": "success"}""")

        val result = repository.refreshBreeds()

        assertEquals(AppResult.Success(emptyList<Breed>()), result)
        assertEquals(emptyList<Breed>(), repository.cachedBreeds.value)
    }

    /** A non-`success` payload `status` field surfaces as [AppError.ApiStatus]. */
    @Test
    fun `api status other than success maps to ApiStatus error`() = runTest(testDispatcher) {
        enqueue("""{"message": {}, "status": "error"}""")

        val result = repository.refreshBreeds()

        assertEquals(AppResult.Failure(AppError.ApiStatus("error")), result)
    }

    /** Closing the server before the call simulates a refused connection → [AppError.NoConnection]. */
    @Test
    fun `unreachable server maps to NoConnection`() = runTest(testDispatcher) {
        server.close() // connection refused from now on

        val result = repository.refreshBreeds()

        assertEquals(AppResult.Failure(AppError.NoConnection), result)
    }

    /**
     * `bodyDelay` longer than OkHttp's `readTimeout` triggers a socket
     * timeout, which the repository surfaces as [AppError.Timeout].
     */
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

    /** After a successful refresh, `observeBreed("bulldog")` emits the matching entry. */
    @Test
    fun `observeBreed emits matching breed after refresh`() = runTest(testDispatcher) {
        enqueue(successBody)
        repository.refreshBreeds()

        val breed = repository.observeBreed("bulldog").first()

        assertEquals(Breed("bulldog", listOf("boston", "french")), breed)
    }

    /**
     * `observeBreed` is non-throwing: it emits `null` both on a cold cache and
     * for unknown names after a successful refresh.
     */
    @Test
    fun `observeBreed emits null for unknown breed and cold cache`() = runTest(testDispatcher) {
        assertNull(repository.observeBreed("bulldog").first())

        enqueue(successBody)
        repository.refreshBreeds()

        assertNull(repository.observeBreed("not-a-breed").first())
    }

    /** Last-known-good semantics: a failing refresh leaves the previous cache intact. */
    @Test
    fun `failed refresh keeps previous cache`() = runTest(testDispatcher) {
        enqueue(successBody)
        repository.refreshBreeds()
        enqueue("oops", code = 500)

        repository.refreshBreeds()

        assertEquals(3, repository.cachedBreeds.value?.size)
    }

    /** `toggleFavorite` delegates to the data source and the flow surfaces the change. */
    @Test
    fun `toggleFavorite delegates to data source`() = runTest(testDispatcher) {
        repository.toggleFavorite("hound")

        assertEquals(listOf("hound"), favorites.toggledNames)
        assertTrue(repository.favorites.first().contains("hound"))
    }

    /** Image endpoint happy path: the URL string is returned wrapped in [AppResult.Success]. */
    @Test
    fun `successful image fetch returns the url`() = runTest(testDispatcher) {
        enqueue("""{"message": "https://images.dog.ceo/breeds/hound/n123.jpg", "status": "success"}""")

        val result = repository.fetchBreedImageUrl("hound")

        assertEquals(AppResult.Success("https://images.dog.ceo/breeds/hound/n123.jpg"), result)
    }

    /** Healthy 200 with `status: error` from the image endpoint → [AppError.ApiStatus]. */
    @Test
    fun `image fetch with api status error maps to ApiStatus`() = runTest(testDispatcher) {
        enqueue("""{"message": "Breed not found", "status": "error"}""")

        val result = repository.fetchBreedImageUrl("not-a-breed")

        assertEquals(AppResult.Failure(AppError.ApiStatus("error")), result)
    }

    /** 404 from the image endpoint surfaces as [AppError.Http] with the code preserved. */
    @Test
    fun `image fetch http 404 maps to Http error`() = runTest(testDispatcher) {
        enqueue("not found", code = 404)

        val result = repository.fetchBreedImageUrl("not-a-breed")

        assertEquals(AppResult.Failure(AppError.Http(404)), result)
    }
}
