package com.alexm.mynut.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexm.mynut.MyNutApplication
import com.alexm.mynut.data.DailyEntry
import com.alexm.mynut.data.DailyEntryWithItem
import com.alexm.mynut.data.DailySummary
import com.alexm.mynut.data.NutItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MyNutApplication
    private val nutItemDao = app.database.nutItemDao()
    private val dailyEntryDao = app.database.dailyEntryDao()

    private val _selectedDate = MutableStateFlow(LocalDate.now().toString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val nutItems: StateFlow<List<NutItem>> = nutItemDao.getAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyEntries: StateFlow<List<DailyEntryWithItem>> = _selectedDate
        .flatMapLatest { date -> dailyEntryDao.getEntriesForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailySummary: StateFlow<DailySummary> = dailyEntries
        .map { entries -> DailySummary.fromEntries(entries) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailySummary())

    fun onDateSelected(date: String) {
        _selectedDate.value = date
    }

    fun addEntry(nutItemId: Long, portionCount: Double) {
        viewModelScope.launch {
            dailyEntryDao.insert(
                DailyEntry(
                    nutItemId = nutItemId,
                    date = _selectedDate.value,
                    portionCount = portionCount
                )
            )
        }
    }

    fun removeEntry(entry: DailyEntry) {
        viewModelScope.launch {
            dailyEntryDao.delete(entry)
        }
    }
}