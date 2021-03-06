package dev.pimentel.shows.data.repository

import app.cash.turbine.test
import dev.pimentel.shows.data.body.EpisodeResponseBody
import dev.pimentel.shows.data.body.ImageResponseBody
import dev.pimentel.shows.data.body.ShowResponseBody
import dev.pimentel.shows.data.body.ShowSearchResponseBody
import dev.pimentel.shows.data.dto.ShowDTO
import dev.pimentel.shows.data.model.EpisodeModelImpl
import dev.pimentel.shows.data.model.ShowInformationModelImpl
import dev.pimentel.shows.data.model.ShowModelImpl
import dev.pimentel.shows.data.model.ShowsPageModelImpl
import dev.pimentel.shows.data.sources.local.ShowsLocalDataSource
import dev.pimentel.shows.data.sources.remote.ShowsRemoteDataSource
import dev.pimentel.shows.domain.model.ShowInformationModel
import dev.pimentel.shows.domain.model.ShowsPageModel
import dev.pimentel.shows.domain.repository.ShowsRepository
import dev.pimentel.shows.domain.usecase.GetShows
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import kotlin.time.seconds

class ShowsRepositoryTest {

    private val showsLocalDataSource = mockk<ShowsLocalDataSource>()
    private val showsRemoteDataSource = mockk<ShowsRemoteDataSource>()

    private val dispatcher = TestCoroutineDispatcher()

    @BeforeEach
    fun `set up dispatcher`() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun `tear down`() {
        Dispatchers.resetMain()
        dispatcher.cleanupTestCoroutines()
    }

    @Test
    fun `should get two pages of shows`() = runBlocking {
        val showsFirstPageResponseBody = listOf(
            ShowResponseBody(
                id = 0,
                name = "name0",
                summary = "0",
                status = "0",
                premieredDate = "0",
                rating = ShowResponseBody.RatingResponseBody(average = 0F),
                image = ImageResponseBody(originalUrl = "0"),
            ),
            ShowResponseBody(
                id = 1,
                name = "name1",
                summary = "1",
                status = "1",
                premieredDate = "1",
                rating = ShowResponseBody.RatingResponseBody(average = 1F),
                image = ImageResponseBody(originalUrl = "1"),
            ),
        )
        val showsSecondPageResponseBody = listOf(
            ShowResponseBody(
                id = 2,
                name = "name2",
                summary = "2",
                status = "2",
                premieredDate = "2",
                rating = ShowResponseBody.RatingResponseBody(average = 2F),
                image = ImageResponseBody(originalUrl = "2"),
            ),
            ShowResponseBody(
                id = 3,
                name = "name3",
                summary = "3",
                status = "3",
                premieredDate = "3",
                rating = ShowResponseBody.RatingResponseBody(average = 3F),
                image = ImageResponseBody(originalUrl = "3"),
            ),
        )

        val favoriteIds = listOf(1, 2)

        val showsFirstPageModel: ShowsPageModel = ShowsPageModelImpl(
            shows = listOf(
                ShowModelImpl(
                    id = 0,
                    name = "name0",
                    status = "0",
                    premieredDate = "0",
                    rating = 0F,
                    imageUrl = "0",
                    isFavorite = false
                ),
                ShowModelImpl(
                    id = 1,
                    name = "name1",
                    status = "1",
                    premieredDate = "1",
                    rating = 1F,
                    imageUrl = "1",
                    isFavorite = true
                ),
            ),
            nextPage = 1
        )
        val showsSecondPageModel: ShowsPageModel = ShowsPageModelImpl(
            shows = listOf(
                ShowModelImpl(
                    id = 0,
                    name = "name0",
                    status = "0",
                    premieredDate = "0",
                    rating = 0F,
                    imageUrl = "0",
                    isFavorite = false
                ),
                ShowModelImpl(
                    id = 1,
                    name = "name1",
                    status = "1",
                    premieredDate = "1",
                    rating = 1F,
                    imageUrl = "1",
                    isFavorite = true
                ),
                ShowModelImpl(
                    id = 2,
                    name = "name2",
                    status = "2",
                    premieredDate = "2",
                    rating = 2F,
                    imageUrl = "2",
                    isFavorite = true
                ),
                ShowModelImpl(
                    id = 3,
                    name = "name3",
                    status = "3",
                    premieredDate = "3",
                    rating = 3F,
                    imageUrl = "3",
                    isFavorite = false
                ),
            ),
            nextPage = 2
        )

        coEvery { showsRemoteDataSource.getShows(page = 0) } returns showsFirstPageResponseBody
        coEvery { showsRemoteDataSource.getShows(page = 1) } returns showsSecondPageResponseBody
        coEvery { showsLocalDataSource.getFavoriteShowsIds() } returns flowOf(favoriteIds)

        val repository = getRepositoryInstance()

        repository.getShows().test(2.seconds) {
            assertEquals(expectItem(), initialShowsPageValue)

            delay(1.seconds)
            repository.getMoreShows(0)
            assertEquals(expectItem(), showsFirstPageModel)

            delay(1.seconds)
            repository.getMoreShows(1)
            assertEquals(expectItem(), showsSecondPageModel)

            cancel()
        }

        coVerify(exactly = 1) {
            showsRemoteDataSource.getShows(page = 0)
            showsRemoteDataSource.getShows(page = 1)
            showsLocalDataSource.getFavoriteShowsIds()
        }
        confirmEverythingVerified()
    }

    @Test
    fun `should just get one page and then one emission from NO_MORE_PAGES error`() = runBlocking {
        val showsFirstPageResponseBody = listOf(
            ShowResponseBody(
                id = 0,
                name = "name0",
                summary = "0",
                status = "0",
                premieredDate = "0",
                rating = ShowResponseBody.RatingResponseBody(average = 0F),
                image = ImageResponseBody(originalUrl = "0"),
            ),
            ShowResponseBody(
                id = 1,
                name = "name1",
                summary = "1",
                status = "1",
                premieredDate = "1",
                rating = ShowResponseBody.RatingResponseBody(average = 1F),
                image = ImageResponseBody(originalUrl = "1"),
            ),
        )

        val favoriteIds = listOf(1, 2)

        val showsFirstPageModel: ShowsPageModel = ShowsPageModelImpl(
            shows = listOf(
                ShowModelImpl(
                    id = 0,
                    name = "name0",
                    status = "0",
                    premieredDate = "0",
                    rating = 0F,
                    imageUrl = "0",
                    isFavorite = false
                ),
                ShowModelImpl(
                    id = 1,
                    name = "name1",
                    status = "1",
                    premieredDate = "1",
                    rating = 1F,
                    imageUrl = "1",
                    isFavorite = true
                ),
            ),
            nextPage = 1
        )
        val showsSecondPageModel: ShowsPageModel = ShowsPageModelImpl(
            shows = listOf(
                ShowModelImpl(
                    id = 0,
                    name = "name0",
                    status = "0",
                    premieredDate = "0",
                    rating = 0F,
                    imageUrl = "0",
                    isFavorite = false
                ),
                ShowModelImpl(
                    id = 1,
                    name = "name1",
                    status = "1",
                    premieredDate = "1",
                    rating = 1F,
                    imageUrl = "1",
                    isFavorite = true
                ),
            ),
            nextPage = GetShows.NO_MORE_PAGES
        )

        val httpException = HttpException(
            Response.error<String>(404, "".toResponseBody("text/text".toMediaType()))
        )

        coEvery { showsRemoteDataSource.getShows(page = 0) } returns showsFirstPageResponseBody
        coEvery { showsRemoteDataSource.getShows(page = 1) } throws httpException
        coEvery { showsLocalDataSource.getFavoriteShowsIds() } returns flowOf(favoriteIds)

        val repository = getRepositoryInstance()

        repository.getShows().test(2.seconds) {
            assertEquals(expectItem(), initialShowsPageValue)

            delay(1.seconds)
            repository.getMoreShows(0)
            assertEquals(expectItem(), showsFirstPageModel)

            delay(1.seconds)
            repository.getMoreShows(1)
            assertEquals(expectItem(), showsSecondPageModel)

            cancel()
        }

        coVerify(exactly = 1) {
            showsRemoteDataSource.getShows(page = 0)
            showsRemoteDataSource.getShows(page = 1)
            showsLocalDataSource.getFavoriteShowsIds()
        }
        confirmEverythingVerified()
    }

    @Test
    fun `should search for two distinct values`() = runBlocking {
        val showsFirstSearchResponseBody = listOf(
            ShowSearchResponseBody(
                ShowResponseBody(
                    id = 0,
                    name = "breaking bad",
                    summary = "0",
                    status = "0",
                    premieredDate = "0",
                    rating = ShowResponseBody.RatingResponseBody(average = 0F),
                    image = ImageResponseBody(originalUrl = "0")
                )
            )
        )
        val showsSecondSearchResponseBody = listOf(
            ShowSearchResponseBody(
                ShowResponseBody(
                    id = 1,
                    name = "true detective",
                    summary = "1",
                    status = "1",
                    premieredDate = "1",
                    rating = ShowResponseBody.RatingResponseBody(average = 1F),
                    image = ImageResponseBody(originalUrl = "1")
                )
            )
        )

        val showsFirstPageModel: ShowsPageModel = ShowsPageModelImpl(
            shows = listOf(
                ShowModelImpl(
                    id = 0,
                    name = "breaking bad",
                    status = "0",
                    premieredDate = "0",
                    rating = 0F,
                    imageUrl = "0",
                    isFavorite = false
                )
            ),
            nextPage = 0
        )
        val showsSecondPageModel: ShowsPageModel = ShowsPageModelImpl(
            shows = listOf(
                ShowModelImpl(
                    id = 1,
                    name = "true detective",
                    status = "1",
                    premieredDate = "1",
                    rating = 1F,
                    imageUrl = "1",
                    isFavorite = false
                )
            ),
            nextPage = 0
        )

        coEvery { showsRemoteDataSource.getShows(query = "breaking") } returns showsFirstSearchResponseBody
        coEvery { showsRemoteDataSource.getShows(query = "true") } returns showsSecondSearchResponseBody
        coEvery { showsLocalDataSource.getFavoriteShowsIds() } returns flowOf(emptyList())

        val repository = getRepositoryInstance()

        repository.getShows().test(2.seconds) {
            assertEquals(expectItem(), initialShowsPageValue)

            repository.searchShows("breaking")
            delay(1.seconds)
            assertEquals(expectItem(), showsFirstPageModel)

            repository.searchShows("true")
            delay(1.seconds)
            assertEquals(expectItem(), showsSecondPageModel)

            cancelAndConsumeRemainingEvents()
        }

        coVerify(exactly = 1) {
            showsRemoteDataSource.getShows(query = "breaking")
            showsRemoteDataSource.getShows(query = "true")
            showsLocalDataSource.getFavoriteShowsIds()
        }
        confirmEverythingVerified()
    }

    @Test
    fun `should search only one time while on debounce delay`() = runBlocking {
        val showsFirstSearchResponseBody = listOf(
            ShowSearchResponseBody(
                ShowResponseBody(
                    id = 1,
                    name = "true detective",
                    summary = "1",
                    status = "1",
                    premieredDate = "1",
                    rating = ShowResponseBody.RatingResponseBody(average = 1F),
                    image = ImageResponseBody(originalUrl = "1")
                )
            )
        )

        val showsFirstPageModel: ShowsPageModel = ShowsPageModelImpl(
            shows = listOf(
                ShowModelImpl(
                    id = 1,
                    name = "true detective",
                    status = "1",
                    premieredDate = "1",
                    rating = 1F,
                    imageUrl = "1",
                    isFavorite = false
                )
            ),
            nextPage = 0
        )

        coEvery { showsRemoteDataSource.getShows(query = "true") } returns showsFirstSearchResponseBody
        coEvery { showsLocalDataSource.getFavoriteShowsIds() } returns flowOf(emptyList())

        val repository = getRepositoryInstance()

        repository.getShows().test(2.seconds) {
            assertEquals(expectItem(), initialShowsPageValue)

            repository.searchShows("breaking")
            repository.searchShows("true")
            delay(1.seconds)
            assertEquals(expectItem(), showsFirstPageModel)

            cancelAndConsumeRemainingEvents()
        }

        coVerify(exactly = 1) {
            showsRemoteDataSource.getShows(query = "true")
            showsLocalDataSource.getFavoriteShowsIds()
        }
        confirmEverythingVerified()
    }

    @Test
    fun `should favorite show when it is not already a favorite`() = runBlocking {
        val showId = 0

        val showBody = ShowResponseBody(
            id = 1,
            name = "true detective",
            summary = "1",
            status = "1",
            premieredDate = "1",
            rating = ShowResponseBody.RatingResponseBody(average = 1F),
            image = ImageResponseBody(originalUrl = "1")
        )

        val showToBeSaved = ShowDTO(
            id = 1,
            name = "true detective",
            status = "1",
            premieredDate = "1",
            rating = 1F,
            imageUrl = "1"
        )

        coEvery { showsLocalDataSource.isFavorite(showId) } returns false
        coEvery { showsRemoteDataSource.getShowInformation(showId) } returns showBody
        coJustRun { showsLocalDataSource.saveFavoriteShow(showToBeSaved) }

        val repository = getRepositoryInstance()

        repository.favoriteOrRemoveShow(showId)

        coVerify(exactly = 1) {
            showsLocalDataSource.isFavorite(showId)
            showsRemoteDataSource.getShowInformation(showId)
            showsLocalDataSource.saveFavoriteShow(showToBeSaved)
        }
        confirmEverythingVerified()
    }

    @Test
    fun `should remove show from favorites when it is already a favorite`() = runBlocking {
        val showId = 0

        coEvery { showsLocalDataSource.isFavorite(showId) } returns true
        coJustRun { showsLocalDataSource.removeShowFromFavorites(showId) }

        val repository = getRepositoryInstance()

        repository.favoriteOrRemoveShow(showId)

        coVerify(exactly = 1) {
            showsLocalDataSource.isFavorite(showId)
            showsLocalDataSource.removeShowFromFavorites(showId)
        }
        confirmEverythingVerified()
    }

    @Test
    fun `should search favorites`() = runBlocking {
        val query = "query"

        val favoriteShows = listOf(
            ShowDTO(
                id = 1,
                name = "true detective",
                status = "1",
                premieredDate = "1",
                rating = 1F,
                imageUrl = "1"
            ),
            ShowDTO(
                id = 2,
                name = "breaking bad",
                status = "2",
                premieredDate = "2",
                rating = 2F,
                imageUrl = "2"
            ),
        )

        val showModels = listOf(
            ShowModelImpl(
                id = 1,
                name = "true detective",
                status = "1",
                premieredDate = "1",
                rating = 1F,
                imageUrl = "1",
                isFavorite = true
            ),
            ShowModelImpl(
                id = 2,
                name = "breaking bad",
                status = "2",
                premieredDate = "2",
                rating = 2F,
                imageUrl = "2",
                isFavorite = true
            )
        )

        coEvery { showsLocalDataSource.getFavoriteShows(query) } returns flowOf(favoriteShows)

        val repository = getRepositoryInstance()

        repository.getFavoriteShows().test {
            repository.searchFavorites(query)
            assertEquals(expectItem(), showModels)

            cancel()
        }

        coVerify(exactly = 1) {
            showsLocalDataSource.getFavoriteShows(query)
        }
        confirmEverythingVerified()
    }

    @Test
    fun `should get show information with its favorites`() = runBlocking {
        val showId = 0

        val showResponseBody = ShowResponseBody(
            id = 0,
            name = "0",
            summary = "0",
            status = "0",
            premieredDate = "0",
            rating = ShowResponseBody.RatingResponseBody(average = 0F),
            image = ImageResponseBody(originalUrl = "0"),
            schedule = ShowResponseBody.ScheduleResponseBody("0", listOf("0")),
            embedded = ShowResponseBody.EmbeddedResponseBody(
                episodes = listOf(
                    EpisodeResponseBody(
                        id = 0,
                        number = 0,
                        season = 0,
                        name = "0",
                        summary = "0",
                        image = ImageResponseBody(originalUrl = "0"),
                        airDate = "0",
                        airTime = "0"
                    )
                )
            )
        )

        val showInfoModel: ShowInformationModel = ShowInformationModelImpl(
            id = 0,
            name = "0",
            summary = "0",
            status = "0",
            premieredDate = "0",
            rating = 0F,
            imageUrl = "0",
            isFavorite = true,
            schedule = ShowInformationModelImpl.ScheduleModelImpl(time = "0", days = listOf("0")),
            episodes = listOf(
                EpisodeModelImpl(
                    id = 0,
                    number = 0,
                    season = 0,
                    name = "0",
                    summary = "0",
                    imageUrl = "0",
                    airDate = "0",
                    airTime = "0"
                )
            )
        )

        every { showsLocalDataSource.getFavoriteShowsIds() } returns flowOf(listOf(showId))
        coEvery { showsRemoteDataSource.getShowInformation(showId) } returns showResponseBody

        val repository = getRepositoryInstance()

        repository.getShowInformation().test {
            repository.searchShowInformation(showId)
            assertEquals(expectItem(), showInfoModel)

            cancel()
        }

        coVerify(exactly = 1) {
            showsLocalDataSource.getFavoriteShowsIds()
            showsRemoteDataSource.getShowInformation(showId)
        }
        confirmEverythingVerified()
    }

    @Test
    fun `should get episode information`() = runBlocking {
        val episodeResponseBody = EpisodeResponseBody(
            id = 0,
            number = 0,
            season = 0,
            name = "0",
            summary = "0",
            image = ImageResponseBody(originalUrl = "0"),
            airDate = "0",
            airTime = "0"
        )

        val episodeModel = EpisodeModelImpl(
            id = 0,
            number = 0,
            season = 0,
            name = "0",
            summary = "0",
            imageUrl = "0",
            airDate = "0",
            airTime = "0"
        )

        coEvery { showsRemoteDataSource.getEpisodeInformation(0, 0, 0) } returns episodeResponseBody

        val repository = getRepositoryInstance()

        assertEquals(repository.getEpisodeInformation(0, 0, 0), episodeModel)

        coVerify(exactly = 1) { showsRemoteDataSource.getEpisodeInformation(0, 0, 0) }
        confirmEverythingVerified()
    }

    private fun confirmEverythingVerified() {
        confirmVerified(
            showsRemoteDataSource,
            showsLocalDataSource
        )
    }

    private fun getRepositoryInstance(): ShowsRepository = ShowsRepositoryImpl(
        showsRemoteDataSource = showsRemoteDataSource,
        showsLocalDataSource = showsLocalDataSource
    )

    companion object {
        val initialShowsPageValue = ShowsPageModelImpl(
            shows = emptyList(),
            nextPage = 0
        )
    }
}
