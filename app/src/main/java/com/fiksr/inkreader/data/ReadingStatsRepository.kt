package com.fiksr.inkreader.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class DayReadingStat(
    val date: String, // "yyyy-MM-dd"
    val minutesRead: Int,
    val pagesTurned: Int,
    val wordsRead: Int
)

data class ReadingOverview(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalMinutesRead: Int = 0,
    val totalPagesTurned: Int = 0,
    val totalBooksCompleted: Int = 0,
    val averageWpm: Int = 230,
    val dailyActivity: Map<String, DayReadingStat> = emptyMap()
)

class ReadingStatsRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("inkreader_stats_prefs", Context.MODE_PRIVATE)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val _overview = MutableStateFlow(loadOverview())
    val overview: StateFlow<ReadingOverview> = _overview.asStateFlow()

    private fun loadOverview(): ReadingOverview {
        val json = prefs.getString("reading_stats_v1", null) ?: return calculateOverview(emptyMap(), 0)
        return try {
            val root = JSONObject(json)
            val dailyObj = root.optJSONObject("daily") ?: JSONObject()
            val map = mutableMapOf<String, DayReadingStat>()
            val keys = dailyObj.keys()
            while (keys.hasNext()) {
                val date = keys.next()
                val d = dailyObj.getJSONObject(date)
                map[date] = DayReadingStat(
                    date = date,
                    minutesRead = d.optInt("m", 0),
                    pagesTurned = d.optInt("p", 0),
                    wordsRead = d.optInt("w", 0)
                )
            }
            val completed = root.optInt("books_completed", 0)
            calculateOverview(map, completed)
        } catch (_: Exception) {
            calculateOverview(emptyMap(), 0)
        }
    }

    private fun calculateOverview(
        daily: Map<String, DayReadingStat>,
        completedBooks: Int
    ): ReadingOverview {
        val totalMinutes = daily.values.sumOf { it.minutesRead }
        val totalPages = daily.values.sumOf { it.pagesTurned }

        // Calculate streaks
        val sortedDates = daily.keys
            .filter { (daily[it]?.minutesRead ?: 0) > 0 || (daily[it]?.pagesTurned ?: 0) > 0 }
            .sorted()

        var currentStreak = 0
        var longestStreak = 0
        var runningStreak = 0

        val today = dateFormat.format(Date())
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = dateFormat.format(cal.time)

        if (sortedDates.isNotEmpty()) {
            val dateSet = sortedDates.toSet()
            // Check longest streak
            val calIter = Calendar.getInstance()
            try {
                val firstDate = dateFormat.parse(sortedDates.first())
                val lastDate = dateFormat.parse(sortedDates.last())
                if (firstDate != null && lastDate != null) {
                    calIter.time = firstDate
                    var tempStreak = 0
                    while (!calIter.time.after(lastDate)) {
                        val dStr = dateFormat.format(calIter.time)
                        if (dateSet.contains(dStr)) {
                            tempStreak++
                            if (tempStreak > longestStreak) longestStreak = tempStreak
                        } else {
                            tempStreak = 0
                        }
                        calIter.add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
            } catch (_: Exception) {}

            // Check current streak starting from today or yesterday
            val checkStart = if (dateSet.contains(today)) today else if (dateSet.contains(yesterday)) yesterday else null
            if (checkStart != null) {
                var c = 0
                val streakCal = Calendar.getInstance()
                streakCal.time = dateFormat.parse(checkStart) ?: Date()
                while (true) {
                    val dStr = dateFormat.format(streakCal.time)
                    if (dateSet.contains(dStr)) {
                        c++
                        streakCal.add(Calendar.DAY_OF_YEAR, -1)
                    } else {
                        break
                    }
                }
                currentStreak = c
            }
        }

        if (currentStreak > longestStreak) longestStreak = currentStreak

        return ReadingOverview(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            totalMinutesRead = totalMinutes,
            totalPagesTurned = totalPages,
            totalBooksCompleted = completedBooks,
            averageWpm = 240,
            dailyActivity = daily
        )
    }

    @Synchronized
    fun recordReadingTime(minutes: Int = 1) {
        val today = dateFormat.format(Date())
        val currentMap = _overview.value.dailyActivity.toMutableMap()
        val existing = currentMap[today] ?: DayReadingStat(today, 0, 0, 0)
        currentMap[today] = existing.copy(minutesRead = existing.minutesRead + minutes)
        save(currentMap, _overview.value.totalBooksCompleted)
    }

    @Synchronized
    fun recordPageTurn(wordsOnPage: Int = 250) {
        val today = dateFormat.format(Date())
        val currentMap = _overview.value.dailyActivity.toMutableMap()
        val existing = currentMap[today] ?: DayReadingStat(today, 0, 0, 0)
        currentMap[today] = existing.copy(
            pagesTurned = existing.pagesTurned + 1,
            wordsRead = existing.wordsRead + wordsOnPage
        )
        save(currentMap, _overview.value.totalBooksCompleted)
    }

    @Synchronized
    fun recordBookCompleted() {
        val currentMap = _overview.value.dailyActivity
        val completed = _overview.value.totalBooksCompleted + 1
        save(currentMap, completed)
    }

    private fun save(daily: Map<String, DayReadingStat>, completedBooks: Int) {
        try {
            val root = JSONObject()
            val dailyObj = JSONObject()
            daily.forEach { (date, stat) ->
                val d = JSONObject().apply {
                    put("m", stat.minutesRead)
                    put("p", stat.pagesTurned)
                    put("w", stat.wordsRead)
                }
                dailyObj.put(date, d)
            }
            root.put("daily", dailyObj)
            root.put("books_completed", completedBooks)
            prefs.edit().putString("reading_stats_v1", root.toString()).apply()

            _overview.value = calculateOverview(daily, completedBooks)
        } catch (_: Exception) {}
    }

    /**
     * Returns a list of last 52 weeks (364 days) formatted for the GitHub-style heatmap grid.
     */
    fun getLast365DaysHeatmap(): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        val cal = Calendar.getInstance()
        val activity = _overview.value.dailyActivity

        // Align to start of week (Sunday) 52 weeks ago
        cal.add(Calendar.WEEK_OF_YEAR, -52)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)

        val endCal = Calendar.getInstance()
        while (!cal.after(endCal)) {
            val dStr = dateFormat.format(cal.time)
            val stat = activity[dStr]
            val mins = stat?.minutesRead ?: 0
            val pages = stat?.pagesTurned ?: 0

            val intensity = when {
                mins >= 45 || pages >= 50 -> 4
                mins >= 25 || pages >= 25 -> 3
                mins >= 10 || pages >= 10 -> 2
                mins > 0 || pages > 0 -> 1
                else -> 0
            }
            result.add(Pair(dStr, intensity))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }
}
