package com.example.inkreader.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inkreader.data.ReadingStatsRepository
import com.example.inkreader.theme.LocalEInkColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    statsRepository: ReadingStatsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEInkColors.current
    val overview by statsRepository.overview.collectAsState()
    val heatmapData = remember(overview) { statsRepository.getLast365DaysHeatmap() }
    var selectedDayInfo by remember { mutableStateOf<Pair<String, Int>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Reading Habits & Stats",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        containerColor = colors.background,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Streak Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFFF3E0),
                                border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🔥", fontSize = 26.sp)
                                }
                            }

                            Column {
                                Text(
                                    text = "${overview.currentStreak} Day Reading Streak",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = if (overview.currentStreak > 0) "Keep it up! Read today to stay on track." else "Read any book today to start a streak!",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "BEST",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${overview.longestStreak} d",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }

            // Summary Metrics Grid (2x2)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricBox(
                        title = "TOTAL TIME",
                        value = formatMinutesToHours(overview.totalMinutesRead),
                        icon = Icons.Default.AccessTime,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "PAGES READ",
                        value = "${overview.totalPagesTurned}",
                        icon = Icons.Default.AutoStories,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricBox(
                        title = "AVG SPEED",
                        value = "${overview.averageWpm} WPM",
                        icon = Icons.Default.Speed,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "COMPLETED",
                        value = "${overview.totalBooksCompleted} books",
                        icon = Icons.Default.CheckCircleOutline,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // GitHub-Style 365-Day Activity Heatmap
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Annual Reading Heatmap",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Last 52 Weeks",
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Grid container with horizontal scroll
                        val scrollState = rememberScrollState(Int.MAX_VALUE)
                        LaunchedEffect(Unit) {
                            scrollState.scrollTo(scrollState.maxValue)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            // Group into columns of 7 days (weeks)
                            val weeks = heatmapData.chunked(7)
                            weeks.forEach { weekDays ->
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    weekDays.forEach { (dateStr, intensity) ->
                                        val isSelected = selectedDayInfo?.first == dateStr
                                        Box(
                                            modifier = Modifier
                                                .size(13.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(
                                                    when (intensity) {
                                                        4 -> Color(0xFF2E7D32)
                                                        3 -> Color(0xFF4CAF50)
                                                        2 -> Color(0xFF81C784)
                                                        1 -> Color(0xFFC8E6C9)
                                                        else -> colors.background
                                                    }
                                                )
                                                .clickable {
                                                    selectedDayInfo = Pair(dateStr, intensity)
                                                }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Heatmap Legend
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Less", fontSize = 10.sp, color = colors.textSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            listOf(0, 1, 2, 3, 4).forEach { level ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 1.5.dp)
                                        .size(9.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(
                                            when (level) {
                                                4 -> Color(0xFF2E7D32)
                                                3 -> Color(0xFF4CAF50)
                                                2 -> Color(0xFF81C784)
                                                1 -> Color(0xFFC8E6C9)
                                                else -> colors.background
                                            }
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("More", fontSize = 10.sp, color = colors.textSecondary)
                        }

                        // Selected Day Details
                        selectedDayInfo?.let { (date, intensity) ->
                            val stat = overview.dailyActivity[date]
                            val mins = stat?.minutesRead ?: 0
                            val pages = stat?.pagesTurned ?: 0
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.background,
                                border = BorderStroke(1.dp, colors.divider),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = date,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "$mins mins read • $pages pages",
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricBox(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val colors = LocalEInkColors.current

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.divider),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary,
                    letterSpacing = 1.sp
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        }
    }
}

private fun formatMinutesToHours(minutes: Int): String {
    val hours = minutes / 60
    val remMin = minutes % 60
    return if (hours > 0) "${hours}h ${remMin}m" else "${remMin}m"
}
