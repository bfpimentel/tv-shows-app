package dev.pimentel.shows.presentation.favorites

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pimentel.shows.di.NavigatorRouterQualifier
import dev.pimentel.shows.domain.usecase.FavoriteOrRemoveShow
import dev.pimentel.shows.domain.usecase.GetFavorites
import dev.pimentel.shows.domain.usecase.NoParams
import dev.pimentel.shows.domain.usecase.SearchFavorites
import dev.pimentel.shows.presentation.favorites.data.FavoritesIntention
import dev.pimentel.shows.presentation.favorites.data.FavoritesState
import dev.pimentel.shows.shared.dispatchers.DispatchersProvider
import dev.pimentel.shows.shared.mvi.StateViewModelImpl
import dev.pimentel.shows.shared.mvi.toEvent
import dev.pimentel.shows.shared.navigator.NavigatorRouter
import dev.pimentel.shows.shared.shows.ShowViewDataMapper
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    @NavigatorRouterQualifier private val navigator: NavigatorRouter,
    private val getFavorites: GetFavorites,
    private val searchFavorites: SearchFavorites,
    private val favoriteOrRemoveShow: FavoriteOrRemoveShow,
    private val showViewDataMapper: ShowViewDataMapper,
    dispatchersProvider: DispatchersProvider,
    @FavoritesStateQualifier initialState: FavoritesState
) : StateViewModelImpl<FavoritesState, FavoritesIntention>(
    dispatchersProvider = dispatchersProvider,
    initialState = initialState
), FavoritesContract.ViewModel {

    init {
        viewModelScope.launch(dispatchersProvider.io) { getFavorites() }
    }

    override suspend fun handleIntentions(intention: FavoritesIntention) {
        when (intention) {
            is FavoritesIntention.SearchFavorites -> searchShows(intention.query)
            is FavoritesIntention.FavoriteOrRemoveShow -> favoriteOrRemoveShow(intention.showId)
            is FavoritesIntention.NavigateToInformation -> navigateToInformation(intention.showId)
        }
    }

    private suspend fun getFavorites() {
        getFavorites(NoParams).collect { shows ->
            val showsViewData = showViewDataMapper.mapAll(shows)
            updateState { copy(showsEvent = showsViewData.toEvent()) }
        }
    }

    private suspend fun searchShows(query: String?) {
        searchFavorites(SearchFavorites.Params(query.orEmpty()))
    }

    private suspend fun favoriteOrRemoveShow(showId: Int) {
        favoriteOrRemoveShow(FavoriteOrRemoveShow.Params(showId))
    }

    private suspend fun navigateToInformation(showId: Int) {
        val directions = FavoritesFragmentDirections.toInformationFragment(showId)
        navigator.navigate(directions)
    }
}
