package io.github.droidkaigi.confsched.sessions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import io.github.droidkaigi.confsched.compose.SafeLaunchedEffect
import io.github.droidkaigi.confsched.model.DroidKaigi2023Day
import io.github.droidkaigi.confsched.model.Filters
import io.github.droidkaigi.confsched.model.SessionsRepository
import io.github.droidkaigi.confsched.model.Timetable
import io.github.droidkaigi.confsched.model.TimetableItem
import io.github.droidkaigi.confsched.model.TimetableUiType
import io.github.droidkaigi.confsched.model.localSessionsRepository
import io.github.droidkaigi.confsched.sessions.section.TimetableGridUiState
import io.github.droidkaigi.confsched.sessions.section.TimetableListUiState
import io.github.droidkaigi.confsched.sessions.section.TimetableSheetUiState
import io.github.droidkaigi.confsched.ui.UserMessageStateHolder
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.flow.Flow

sealed interface TimetableScreenEvent {
    data class Bookmark(val timetableItem: TimetableItem, val bookmarked: Boolean) :
        TimetableScreenEvent

    data object UiTypeChange : TimetableScreenEvent
}

@Composable
fun timetableScreenViewModel(
    events: Flow<TimetableScreenEvent>,
    userMessageStateHolder: UserMessageStateHolder,
    sessionsRepository: SessionsRepository = localSessionsRepository(),
): TimetableScreenUiState {
    val sessions by rememberUpdatedState(sessionsRepository.timetable())
    var timetableUiType by remember { mutableStateOf(TimetableUiType.List) }
    var bookmarkAnimationStart by remember { mutableStateOf(false) }
    val timetableUiState by rememberUpdatedState(
        timetableSheet(
            sessionTimetable = sessions,
            uiType = timetableUiType,
        ),
    )
    SafeLaunchedEffect(Unit) {
        events.collect { event ->
            when (event) {
                is TimetableScreenEvent.Bookmark -> {
                    sessionsRepository.toggleBookmark(event.timetableItem.id)
                    if (event.bookmarked) {
                        bookmarkAnimationStart = true
                    }
                }

                TimetableScreenEvent.UiTypeChange -> {
                    timetableUiType =
                        if (timetableUiType == TimetableUiType.List) {
                            TimetableUiType.Grid
                        } else {
                            TimetableUiType.List
                        }
                }
            }
        }
    }

    return TimetableScreenUiState(
        contentUiState = timetableUiState,
        timetableUiType = timetableUiType,
        onBookmarkIconClickStatus = bookmarkAnimationStart,
    )
}

@Composable
fun timetableSheet(
    sessionTimetable: Timetable,
    uiType: TimetableUiType,
): TimetableSheetUiState {
    if (sessionTimetable.timetableItems.isEmpty()) {
        return TimetableSheetUiState.Empty
    }
    return if (uiType == TimetableUiType.List) {
        TimetableSheetUiState.ListTimetable(
            DroidKaigi2023Day.entries.associateWith { day ->
                val sortAndGroupedTimetableItems = sessionTimetable.filtered(
                    Filters(
                        days = listOf(day),
                    ),
                ).timetableItems.groupBy {
                    it.startsTimeString + it.endsTimeString
                }.mapValues { entries ->
                    entries.value.sortedWith(
                        compareBy({ it.day?.name.orEmpty() }, { it.startsTimeString }),
                    )
                }.toPersistentMap()
                TimetableListUiState(
                    timetableItemMap = sortAndGroupedTimetableItems,
                    timetable = sessionTimetable.dayTimetable(day),
                )
            },
        )
    } else {
        TimetableSheetUiState.GridTimetable(
            DroidKaigi2023Day.entries.associateWith { day ->
                TimetableGridUiState(
                    timetable = sessionTimetable.dayTimetable(day),
                )
            },
        )
    }
}
