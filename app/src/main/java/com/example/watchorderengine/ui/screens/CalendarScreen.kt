package com.example.watchorderengine.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.UpcomingEpisode
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.CalendarUiState
import com.example.watchorderengine.ui.viewmodel.CalendarViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onEpisodeClick: (mediaId: String) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val theme         = LocalAppTheme.current
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing  by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val selectedDate  by viewModel.selectedDate.collectAsStateWithLifecycle()
    val today         = remember { LocalDate.now() }
    val listState     = rememberLazyListState()
    val scope         = rememberCoroutineScope()

    var isCalendarExpanded by remember { mutableStateOf(true) }

    val episodes = (uiState as? CalendarUiState.Success)?.episodes ?: emptyList()

    // Initial scroll to Today
    LaunchedEffect(episodes) {
        if (episodes.isNotEmpty()) {
            val todayStr = today.toString()
            var index = 0
            var lastDateKey: String? = null
            var targetIndex = -1
            
            for (ep in episodes) {
                if (ep.airDate != lastDateKey) {
                    if (ep.airDate == todayStr) {
                        targetIndex = index
                        break
                    }
                    index++ // header
                    lastDateKey = ep.airDate
                }
                if (ep.airDate == todayStr) {
                    targetIndex = index
                    break
                }
                index++ // item
            }
            
            if (targetIndex >= 0) {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    // date -> how many episodes air that day, for the grid's per-cell badge
    val markedDates: Map<LocalDate, Int> = remember(episodes) {
        episodes
            .mapNotNull { ep -> runCatching { LocalDate.parse(ep.airDate) }.getOrNull() }
            .groupingBy { it }
            .eachCount()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = theme.textPrimary)
            }
            Icon(Icons.Default.CalendarMonth, null, tint = theme.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "RELEASE CALENDAR",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Black,
                color      = theme.textPrimary,
                modifier   = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.goToToday() }) {
                Icon(Icons.Default.Today, "Jump to today", tint = theme.textSecondary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { viewModel.refresh(showSpinner = true) }, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(color = theme.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Default.Refresh, "Refresh", tint = theme.textSecondary)
                }
            }
            IconButton(onClick = { isCalendarExpanded = !isCalendarExpanded }) {
                Icon(
                    if (isCalendarExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null,
                    tint = theme.textSecondary
                )
            }
        }

        // ── Month grid ───────────────────────────────────────────────────────
        androidx.compose.animation.AnimatedVisibility(visible = isCalendarExpanded) {
            MonthGridCalendar(
                yearMonth    = selectedMonth,
                selectedDate = selectedDate,
                today        = today,
                markedDates  = markedDates,
                onPrevMonth  = { viewModel.previousMonth() },
                onNextMonth  = { viewModel.nextMonth() },
                onDateClick  = { date ->
                    viewModel.selectDate(date)
                    val targetDateStr = date.toString()
                    var index = 0
                    var lastDateKey: String? = null
                    var foundIndex = -1
                    for (ep in episodes) {
                        if (ep.airDate != lastDateKey) {
                            if (ep.airDate == targetDateStr) {
                                foundIndex = index
                                break
                            }
                            index++ // header
                            lastDateKey = ep.airDate
                        }
                        if (ep.airDate == targetDateStr) {
                            foundIndex = index
                            break
                        }
                        index++ // item
                    }
                    
                    if (foundIndex >= 0) {
                        scope.launch { listState.animateScrollToItem(foundIndex) }
                    }
                }
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

        // ── Agenda list ──────────────────────────────────────────────────────
        AnimatedContent(
            targetState    = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label          = "calendar_state"
        ) { state ->
            when (state) {
                is CalendarUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = theme.accent)
                    }
                }

                is CalendarUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                state.message,
                                color     = Color(0xFFFF6B6B),
                                fontSize  = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier  = Modifier.padding(horizontal = 32.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.refresh() },
                                colors  = ButtonDefaults.buttonColors(containerColor = theme.accent)
                            ) { Text("Retry") }
                        }
                    }
                }

                is CalendarUiState.Success -> {
                    if (state.episodes.isEmpty()) {
                        EmptyCalendarState()
                    } else {
                        LazyColumn(
                            state             = listState,
                            modifier          = Modifier.fillMaxSize(),
                            contentPadding    = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            var lastDateKey: String? = null
                            state.episodes.forEach { episode ->
                                val episodeDate = runCatching { LocalDate.parse(episode.airDate) }.getOrNull()
                                val dateKey     = episode.airDate
                                val headerLabel = episodeDate?.let { relativeDateLabel(it, today) } ?: episode.airDate

                                if (dateKey != lastDateKey) {
                                    lastDateKey = dateKey
                                    item(key = "header_$dateKey") {
                                        DateHeader(headerLabel)
                                    }
                                }
                                item(key = "${episode.mediaId}_${episode.airDate}_${episode.seasonEpisodeLabel}") {
                                    UpcomingEpisodeCard(
                                        episode = episode,
                                        onClick = { onEpisodeClick(episode.mediaId) }
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Month grid calendar
// ═════════════════════════════════════════════════════════════════════════════

/** One cell in the grid. [date] is null for leading/trailing blanks outside the visible month. */
private data class GridDay(val date: LocalDate?, val inCurrentMonth: Boolean)

/**
 * Builds a full-weeks grid (always a multiple of 7 cells) for [yearMonth],
 * starting on the locale's actual first day of the week (Monday in most of
 * Europe, Sunday in the US, etc.) rather than hardcoding one.
 */
private fun buildMonthGrid(yearMonth: YearMonth, firstDayOfWeek: DayOfWeek): List<GridDay> {
    val firstOfMonth = yearMonth.atDay(1)
    val daysInMonth  = yearMonth.lengthOfMonth()
    val leadingBlanks = (firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val totalCells    = ((leadingBlanks + daysInMonth + 6) / 7) * 7

    return (0 until totalCells).map { i ->
        val dayNum = i - leadingBlanks + 1
        if (dayNum in 1..daysInMonth) {
            GridDay(yearMonth.atDay(dayNum), inCurrentMonth = true)
        } else {
            // Show the real adjacent-month date (dimmed) rather than a blank
            // cell — tapping it is still useful, it just flips the header
            // month via CalendarViewModel.selectDate()'s own month-sync logic.
            val adjacentDate = firstOfMonth.plusDays((i - leadingBlanks).toLong())
            GridDay(adjacentDate, inCurrentMonth = false)
        }
    }
}

@Composable
private fun MonthGridCalendar(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    markedDates: Map<LocalDate, Int>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateClick: (LocalDate) -> Unit
) {
    val theme = LocalAppTheme.current
    val firstDayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val weekdayLabels = remember(firstDayOfWeek) {
        (0..6).map { firstDayOfWeek.plus(it.toLong()) }
    }
    val grid = remember(yearMonth, firstDayOfWeek) { buildMonthGrid(yearMonth, firstDayOfWeek) }

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {

        // Month header + nav arrows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", tint = theme.textPrimary)
            }
            Text(
                yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())).uppercase(),
                fontSize   = 15.sp,
                fontWeight = FontWeight.Black,
                color      = theme.textPrimary
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", tint = theme.textPrimary)
            }
        }

        // Weekday header row
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2).uppercase(),
                    modifier   = Modifier.weight(1f),
                    textAlign  = TextAlign.Center,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color      = theme.textSecondary
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Grid — plain rows, never more than 6 weeks (42 cells), so no need
        // for LazyVerticalGrid or its nested-scroll complications here.
        grid.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    GridDayCell(
                        cell         = cell,
                        isToday      = cell.date == today,
                        isSelected   = cell.date == selectedDate,
                        episodeCount = cell.date?.let { markedDates[it] } ?: 0,
                        onClick      = { cell.date?.let(onDateClick) },
                        modifier     = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GridDayCell(
    cell: GridDay,
    isToday: Boolean,
    isSelected: Boolean,
    episodeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val date = cell.date
    if (date == null) {
        // Shouldn't normally happen (buildMonthGrid always fills in the real
        // adjacent-month date), but Spacer is the correct leaf composable for
        // an empty grid slot if it ever does — Box requires a content lambda
        // with no default, so a bare Box(modifier) here wouldn't compile.
        Spacer(modifier.aspectRatio(1f))
        return
    }

    val theme = LocalAppTheme.current
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(CircleShape)
            .background(if (isSelected) theme.accent else Color.Transparent)
            .border(
                width = if (isToday && !isSelected) 1.dp else 0.dp,
                color = theme.accent,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                fontSize   = 11.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Black else FontWeight.Normal,
                color = when {
                    isSelected -> Color.White
                    !cell.inCurrentMonth -> theme.textSecondary.copy(alpha = 0.35f)
                    else -> theme.textPrimary
                }
            )
            if (episodeCount > 0) {
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White else theme.accent)
                )
            } else {
                Spacer(Modifier.size(3.dp).padding(top = 1.dp))
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Agenda rows
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun DateHeader(label: String) {
    val theme = LocalAppTheme.current
    Text(
        text       = label.uppercase(),
        fontSize   = 12.sp,
        fontWeight = FontWeight.Black,
        color      = theme.accent,
        modifier   = Modifier.padding(top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun UpcomingEpisodeCard(episode: UpcomingEpisode, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Surface(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = theme.surface,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model              = episode.posterUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .size(44.dp, 62.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(theme.surfaceHover)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    episode.showTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                    color      = theme.textPrimary,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = theme.background, shape = RoundedCornerShape(3.dp)) {
                        Text(
                            episode.seasonEpisodeLabel,
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color      = theme.textSecondary,
                            modifier   = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        episode.episodeName,
                        fontSize = 12.sp,
                        color    = theme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Tv, null, tint = theme.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun EmptyCalendarState() {
    val theme = LocalAppTheme.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(Icons.Default.CalendarMonth, null, tint = Color.Gray, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                "No upcoming episodes",
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
                color      = theme.textPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Shows on your Watching list will show up here once TMDB has confirmed air dates.",
                fontSize  = 12.sp,
                color     = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Date label helper ──────────────────────────────────────────────────────

private fun relativeDateLabel(date: LocalDate, today: LocalDate): String = when {
    date == today                                            -> "Today"
    date == today.plusDays(1)                                -> "Tomorrow"
    date == today.minusDays(1)                               -> "Yesterday"
    date.isAfter(today) && date.isBefore(today.plusDays(7))  -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
}
