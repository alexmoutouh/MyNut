package com.alexm.mynut.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private enum class CalendarView { DAYS, MONTHS, YEARS }

@Composable
fun CalendarScreen(
    currentDate: String,
    onDateSelected: (String) -> Unit
) {
    val selectedDate = remember { LocalDate.parse(currentDate) }
    var displayMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var displayYear by remember { mutableIntStateOf(selectedDate.year) }
    var calendarView by remember { mutableStateOf(CalendarView.DAYS) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (calendarView) {
                CalendarView.DAYS -> DaysView(
                    yearMonth = displayMonth,
                    selectedDate = selectedDate,
                    onTitleClick = { calendarView = CalendarView.MONTHS },
                    onPrevious = { displayMonth = displayMonth.minusMonths(1) },
                    onNext = { displayMonth = displayMonth.plusMonths(1) },
                    onDayClick = { day ->
                        val date = displayMonth.atDay(day)
                        onDateSelected(date.toString())
                    }
                )
                CalendarView.MONTHS -> MonthsView(
                    year = displayMonth.year,
                    onTitleClick = {
                        displayYear = displayMonth.year
                        calendarView = CalendarView.YEARS
                    },
                    onPrevious = { displayMonth = displayMonth.minusYears(1) },
                    onNext = { displayMonth = displayMonth.plusYears(1) },
                    onMonthClick = { month ->
                        displayMonth = YearMonth.of(displayMonth.year, month)
                        calendarView = CalendarView.DAYS
                    }
                )
                CalendarView.YEARS -> YearsView(
                    centerYear = displayYear,
                    onPrevious = { displayYear -= 12 },
                    onNext = { displayYear += 12 },
                    onYearClick = { year ->
                        displayMonth = YearMonth.of(year, displayMonth.monthValue)
                        calendarView = CalendarView.MONTHS
                    }
                )
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    title: String,
    onTitleClick: (() -> Unit)? = null,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Précédent")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = if (onTitleClick != null) Modifier.clickable { onTitleClick() } else Modifier
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Suivant")
        }
    }
}

@Composable
private fun DaysView(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    onTitleClick: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDayClick: (Int) -> Unit
) {
    val daysOfWeek = listOf("L", "M", "M", "J", "V", "S", "D")
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value
    val daysInMonth = yearMonth.lengthOfMonth()
    val title = "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale.FRENCH).replaceFirstChar { it.uppercase() }} ${yearMonth.year}"

    Column {
        CalendarHeader(title = title, onTitleClick = onTitleClick, onPrevious = onPrevious, onNext = onNext)

        Row(modifier = Modifier.fillMaxWidth()) {
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val cells = buildList {
            repeat(firstDayOfWeek - 1) { add(0) }
            for (day in 1..daysInMonth) { add(day) }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(cells) { day ->
                if (day == 0) {
                    Box(modifier = Modifier.aspectRatio(1f))
                } else {
                    val isSelected = yearMonth.year == selectedDate.year &&
                            yearMonth.monthValue == selectedDate.monthValue &&
                            day == selectedDate.dayOfMonth
                    val isToday = yearMonth.atDay(day) == LocalDate.now()

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .then(
                                if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary)
                                else if (isToday) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                                else Modifier
                            )
                            .clickable { onDayClick(day) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$day",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthsView(
    year: Int,
    onTitleClick: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMonthClick: (Int) -> Unit
) {
    val months = (1..12).map { month ->
        java.time.Month.of(month).getDisplayName(TextStyle.SHORT, Locale.FRENCH)
            .replaceFirstChar { it.uppercase() }
    }

    Column {
        CalendarHeader(title = "$year", onTitleClick = onTitleClick, onPrevious = onPrevious, onNext = onNext)

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(months.size) { index ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onMonthClick(index + 1) }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = months[index],
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun YearsView(
    centerYear: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onYearClick: (Int) -> Unit
) {
    val startYear = centerYear - 5
    val years = (startYear..startYear + 11).toList()

    Column {
        CalendarHeader(
            title = "${years.first()} - ${years.last()}",
            onPrevious = onPrevious,
            onNext = onNext
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(years) { year ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onYearClick(year) }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$year",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}