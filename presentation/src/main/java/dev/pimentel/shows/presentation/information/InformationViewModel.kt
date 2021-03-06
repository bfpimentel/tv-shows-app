package dev.pimentel.shows.presentation.information

import android.util.Log
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pimentel.shows.di.NavigatorRouterQualifier
import dev.pimentel.shows.domain.usecase.FavoriteOrRemoveShow
import dev.pimentel.shows.domain.usecase.GetShowInformation
import dev.pimentel.shows.domain.usecase.NoParams
import dev.pimentel.shows.domain.usecase.SearchShowInformation
import dev.pimentel.shows.presentation.information.data.InformationIntention
import dev.pimentel.shows.presentation.information.data.InformationState
import dev.pimentel.shows.presentation.information.mapper.InformationViewDataMapper
import dev.pimentel.shows.shared.dispatchers.DispatchersProvider
import dev.pimentel.shows.shared.mvi.StateViewModelImpl
import dev.pimentel.shows.shared.mvi.toEvent
import dev.pimentel.shows.shared.navigator.NavigatorRouter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InformationViewModel @Inject constructor(
    @NavigatorRouterQualifier private val navigator: NavigatorRouter,
    private val getShowInformation: GetShowInformation,
    private val searchShowInformation: SearchShowInformation,
    private val favoriteOrRemoveShow: FavoriteOrRemoveShow,
    private val informationViewDataMapper: InformationViewDataMapper,
    dispatchersProvider: DispatchersProvider,
    @InformationStateQualifier initialState: InformationState
) : StateViewModelImpl<InformationState, InformationIntention>(
    dispatchersProvider = dispatchersProvider,
    initialState = initialState
), InformationContract.ViewModel {

    private var showId: Int? = null
    private var openOrCloseSeasonPublisher = MutableSharedFlow<Int>()

    init {
        viewModelScope.launch(dispatchersProvider.io) { getShowInformation() }
    }

    override suspend fun handleIntentions(intention: InformationIntention) {
        when (intention) {
            is InformationIntention.SearchShowInformation -> searchInformation(intention.showId)
            is InformationIntention.FavoriteOrRemoveShow -> favoriteOrRemoveShow()
            is InformationIntention.OpenOrCloseSeason -> openOrCloseSeasonPublisher.emit(intention.seasonNumber)
            is InformationIntention.OpenEpisode -> openEpisode(intention.seasonNumber, intention.episodeNumber)
        }
    }

    private suspend fun getShowInformation() {
        try {
            val openOrCloseSeasonFlow = openOrCloseSeasonPublisher.scan(emptyList<Int>()) { accumulator, value ->
                accumulator.toMutableList().apply { if (contains(value)) remove(value) else add(value) }
            }

            getShowInformation(NoParams)
                .combine(openOrCloseSeasonFlow, informationViewDataMapper::map)
                .collect { viewData -> updateState { copy(informationEvent = viewData.toEvent()) } }
        } catch (error: Exception) {
            Log.d("GET_SHOW_INFORMATION", "ERROR", error)
        }
    }

    private suspend fun searchInformation(showId: Int) {
        this.showId = showId
        searchShowInformation(SearchShowInformation.Params(showId))
    }

    private suspend fun favoriteOrRemoveShow() {
        favoriteOrRemoveShow(FavoriteOrRemoveShow.Params(this.showId!!))
    }

    private suspend fun openEpisode(seasonNumber: Int, episodeNumber: Int) {
        val directions = InformationFragmentDirections.toEpisodeFragment(
            showId = this.showId!!,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
        navigator.navigate(directions)
    }
}
