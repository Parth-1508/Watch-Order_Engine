package com.example.watchorderengine.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import java.time.temporal.ChronoUnit

@Composable
fun HorizontalDateSelectorBar(
    selectedDate: LocalDate,
    onDateSelect: (LocalDate) -> Unit
) {
    val theme = LocalAppTheme.current
    val days = remember(selectedDate) {
        (-3..3).map { selectedDate.plusDays(it.toLong()) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F1420))
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onDateSelect(selectedDate.minusDays(1)) },
            modifier = Modifier.padding(start = 4.dp).size(36.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous Day", tint = theme.textPrimary, modifier = Modifier.size(28.dp))
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            days.forEach { date ->
                val isSelected = date == selectedDate
                val monthDay = remember(date) {
                    val monthStr = date.month.name.take(3)
                    "$monthStr ${date.dayOfMonth}"
                }
                val dayOfWeek = remember(date) {
                    date.dayOfWeek.name.take(3)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onDateSelect(date) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        monthDay,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.4f)
                    )
                    Text(
                        dayOfWeek,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .height(3.dp)
                            .width(28.dp)
                            .background(if (isSelected) Color.White else Color.Transparent)
                    )
                }
            }
        }

        IconButton(
            onClick = { onDateSelect(selectedDate.plusDays(1)) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next Day", tint = theme.textPrimary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun DailyAiringScheduleRow(
    episode: UpcomingEpisode,
    onEpisodeClick: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    Surface(
        onClick = { onEpisodeClick(episode.mediaId) },
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF141A28),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = episode.episodeName.ifBlank { "12:00" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = theme.accent,
                modifier = Modifier.width(52.dp)
            )

            Text(
                text = episode.showTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )

            Surface(
                color = Color(0xFF1E2638),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = theme.accent,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Episode ${episode.episodeNumber}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val dailyGlobalSchedule by viewModel.dailyGlobalSchedule.collectAsStateWithLifecycle()
    val isGlobalScheduleLoading by viewModel.isGlobalScheduleLoading.collectAsStateWithLifecycle()

    val today         = remember { LocalDate.now() }
    val listState     = rememberLazyListState()
    val scope         = rememberCoroutineScope()

    var isCalendarExpanded by remember { mutableStateOf(false) }
    var selectedCalendarTab by remember { mutableStateOf(0) } // 0 = Global Schedule, 1 = Watchlist Releases

    val episodes = (uiState as? CalendarUiState.Success)?.episodes ?: emptyList()

    val scrollToDate = remember(episodes) {
        { targetDate: LocalDate ->
            if (episodes.isNotEmpty()) {
                val targetStr = targetDate.toString()
                var index = 0
                var lastDateKey: String? = null
                var bestIndex = -1

                for (ep in episodes) {
                    if (ep.airDate != lastDateKey) {
                        if (ep.airDate >= targetStr && bestIndex == -1) {
                            bestIndex = index
                        }
                        index++ // header
                        lastDateKey = ep.airDate
                    }
                    if (ep.airDate >= targetStr && bestIndex == -1) {
                        bestIndex = index
                    }
                    index++ // item
                }

                if (bestIndex == -1) {
                    bestIndex = (index - 1).coerceAtLeast(0)
                }

                scope.launch {
                    listState.animateScrollToItem(bestIndex)
                }
            }
        }
    }

    // Auto-scroll Watchlist Releases tab directly to Today's date / first upcoming episode
    LaunchedEffect(selectedCalendarTab, episodes) {
        if (selectedCalendarTab == 1 && episodes.isNotEmpty()) {
            scrollToDate(selectedDate)
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

        PrimaryTabRow(
            selectedTabIndex = selectedCalendarTab,
            containerColor = theme.background,
            contentColor = theme.accent
        ) {
            Tab(
                selected = selectedCalendarTab == 0,
                onClick = { selectedCalendarTab = 0 },
                text = { Text("GLOBAL SCHEDULE", fontWeight = FontWeight.Black, fontSize = 12.sp) }
            )
            Tab(
                selected = selectedCalendarTab == 1,
                onClick = { selectedCalendarTab = 1 },
                text = { Text("MY WATCHLIST RELEASES", fontWeight = FontWeight.Black, fontSize = 12.sp) }
            )
        }

        if (selectedCalendarTab == 0) {
            // Global Live Schedule Mode (Matching screenshot)
            HorizontalDateSelectorBar(
                selectedDate = selectedDate,
                onDateSelect = { date -> viewModel.selectDate(date) }
            )

            if (isGlobalScheduleLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = theme.accent)
                }
            } else if (dailyGlobalSchedule.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CalendarMonth, null, tint = theme.textSecondary, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No scheduled airings found for this date.", color = theme.textSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(dailyGlobalSchedule, key = { it.mediaId + it.episodeNumber + it.episodeName }) { episode ->
                        DailyAiringScheduleRow(
                            episode = episode,
                            onEpisodeClick = { onEpisodeClick(episode.mediaId) }
                        )
                    }
                }
            }
        } else {
            // Watchlist Mode
            // ── Month grid ───────────────────────────────────────────────────────
            AnimatedVisibility(visible = isCalendarExpanded) {
                MonthGridCalendar(
                    yearMonth    = selectedMonth,
                    selectedDate = selectedDate,
                    today        = today,
                    markedDates  = markedDates,
                    onPrevMonth  = { viewModel.previousMonth() },
                    onNextMonth  = { viewModel.nextMonth() },
                    onDateClick  = { date ->
                        viewModel.selectDate(date)
                        scrollToDate(date)
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
                        var timeRangeFilter by remember { mutableStateOf("ALL") }
                        val filteredEpisodes = remember(state.episodes, timeRangeFilter, today) {
                            when (timeRangeFilter) {
                                "WEEK" -> {
                                    val weekEnd = today.plusDays(7).toString()
                                    state.episodes.filter { it.airDate >= today.toString() && it.airDate <= weekEnd }
                                }
                                "MONTH" -> {
                                    val monthEnd = today.plusDays(30).toString()
                                    state.episodes.filter { it.airDate >= today.toString() && it.airDate <= monthEnd }
                                }
                                else -> state.episodes
                            }
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = timeRangeFilter == "ALL",
                                    onClick = {
                                        timeRangeFilter = "ALL"
                                        scrollToDate(selectedDate)
                                    },
                                    label = { Text("All (${state.episodes.size})", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = timeRangeFilter == "WEEK",
                                    onClick = { timeRangeFilter = "WEEK" },
                                    label = { Text("Next 7 Days", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = timeRangeFilter == "MONTH",
                                    onClick = { timeRangeFilter = "MONTH" },
                                    label = { Text("Next 30 Days", fontSize = 11.sp) }
                                )
                            }

                            if (filteredEpisodes.isEmpty()) {
                                EmptyCalendarState()
                            } else {
                                LazyColumn(
                                    state             = listState,
                                    modifier          = Modifier.fillMaxSize(),
                                    contentPadding    = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    var lastDateKey: String? = null
                                    filteredEpisodes.forEach { episode ->
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
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Month grid calendar
// ═════════════════════════════════════════════════════════════════════════════

private data class GridDay(val date: LocalDate?, val inCurrentMonth: Boolean)

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
            GridDay(null, inCurrentMonth = false)
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
    val locale = Locale.getDefault()

    val firstDayOfWeek = remember(locale) {
        WeekFields.of(locale).firstDayOfWeek
    }

    val gridDays = remember(yearMonth, firstDayOfWeek) {
        buildMonthGrid(yearMonth, firstDayOfWeek)
    }

    val monthHeaderLabel = remember(yearMonth, locale) {
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
        yearMonth.format(formatter).uppercase(locale)
    }

    val dayOfWeekLabels = remember(firstDayOfWeek, locale) {
        (0..6).map { offset ->
            val day = firstDayOfWeek.plus(offset.toLong())
            day.getDisplayName(TextStyle.SHORT, locale).uppercase(locale).take(2)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrevMonth, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", tint = theme.textPrimary)
            }

            Text(
                monthHeaderLabel,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Black,
                color      = theme.textPrimary,
                letterSpacing = 1.sp
            )

            IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", tint = theme.textPrimary)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            dayOfWeekLabels.forEach { label ->
                Text(
                    label,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color      = theme.textSecondary,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.width(36.dp)
                )
            }
        }

        val rows = gridDays.chunked(7)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    row.forEach { day ->
                        GridDayCell(
                            day          = day,
                            selectedDate = selectedDate,
                            today        = today,
                            markedCount  = day.date?.let { markedDates[it] } ?: 0,
                            onClick      = { day.date?.let(onDateClick) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridDayCell(
    day: GridDay,
    selectedDate: LocalDate,
    today: LocalDate,
    markedCount: Int,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    val date = day.date

    if (date == null) {
        Box(Modifier.size(36.dp))
        return
    }

    val isSelected = date == selectedDate
    val isToday    = date == today

    val backgroundColor = when {
        isSelected -> theme.accent
        isToday    -> theme.accent.copy(alpha = 0.15f)
        else       -> Color.Transparent
    }

    val textColor = when {
        isSelected -> Color.Black
        isToday    -> theme.accent
        else       -> theme.textPrimary
    }

    val borderColor = when {
        isToday && !isSelected -> theme.accent.copy(alpha = 0.5f)
        else                   -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text       = date.dayOfMonth.toString(),
                fontSize   = 12.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Black else FontWeight.Normal,
                color      = textColor
            )

            if (markedCount > 0 && !isSelected) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isToday) theme.accent else theme.statusCanon)
                )
            }
        }
    }
}

@Composable
private fun DateHeader(label: String) {
    val theme = LocalAppTheme.current
    Text(
        text          = label.uppercase(),
        fontSize      = 11.sp,
        fontWeight    = FontWeight.Black,
        color         = theme.accent,
        letterSpacing = 1.sp,
        modifier      = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun UpcomingEpisodeCard(episode: UpcomingEpisode, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(12.dp),
        color   = theme.surface,
        border  = BorderStroke(1.dp, theme.border.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier          = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model              = episode.posterUrl,
                contentDescription = episode.showTitle,
                modifier           = Modifier
                    .width(42.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(theme.background),
                contentScale       = ContentScale.Crop,
                error              = rememberVectorPainter(Icons.Default.Tv)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    episode.showTitle,
                    color      = theme.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
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

private fun relativeDateLabel(date: LocalDate, today: LocalDate): String {
    val diff = ChronoUnit.DAYS.between(today, date)
    return when (diff) {
        0L   -> "Today — ${date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}"
        1L   -> "Tomorrow — ${date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}"
        -1L  -> "Yesterday — ${date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}"
        in 2..6 -> "${date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
    }
}
