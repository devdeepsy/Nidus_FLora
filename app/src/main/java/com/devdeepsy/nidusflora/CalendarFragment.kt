package com.devdeepsy.nidusflora

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.devdeepsy.nidusflora.models.AppDatabase
import com.devdeepsy.nidusflora.models.CropEntry
import com.devdeepsy.nidusflora.models.CropEntryDao
import com.devdeepsy.nidusflora.models.FarmActivity
import com.devdeepsy.nidusflora.models.FarmActivityDao
import com.devdeepsy.nidusflora.notifications.WateringScheduler
import com.devdeepsy.nidusflora.workers.ActivityReminderWorker
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CalendarFragment : Fragment() {

    private lateinit var calendarView: CalendarView
    private lateinit var db: AppDatabase
    private lateinit var dao: FarmActivityDao
    private var activityDates: Set<LocalDate> = emptySet()
    private lateinit var bannerContainer: FrameLayout
    private var selectedDate: Long = Calendar.getInstance().timeInMillis
    private lateinit var cropEntryDao: CropEntryDao
    private lateinit var cropCardsContainer: LinearLayout
    private lateinit var btnAddCrop: Button
    private lateinit var tvNoCrops: TextView

    private val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private var pickedSowingDate: Long = 0L
    private var pickedHarvestDate: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_calendar, container, false)

        calendarView = view.findViewById(R.id.calendarView)
        db = AppDatabase.getDatabase(requireContext().applicationContext)
        dao = db.farmActivityDao()
        cropEntryDao = db.cropEntryDao()
        cropCardsContainer = view.findViewById(R.id.cropCardsContainer)
        btnAddCrop = view.findViewById(R.id.btnAddCrop)
        bannerContainer = view.findViewById(R.id.banner_container)
        tvNoCrops = view.findViewById(R.id.tvNoCrops)

        btnAddCrop.setOnClickListener {
            showAddCropDialog()
        }

        val currentMonth = YearMonth.now()
        calendarView.setup(
            currentMonth.minusMonths(12),
            currentMonth.plusMonths(12),
            DayOfWeek.MONDAY
        )
        calendarView.scrollToMonth(currentMonth)

        val monthTitle = view.findViewById<TextView>(R.id.monthTitle)
        monthTitle.text = currentMonth.format(monthYearFormatter)

        calendarView.monthScrollListener = { month ->
            monthTitle.text = month.yearMonth.format(monthYearFormatter)
        }

        calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View): DayViewContainer = DayViewContainer(view)

            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.textView.background = null
                container.textView.setTextColor(Color.BLACK)
                container.dot.visibility = View.GONE

                if (data.position != DayPosition.MonthDate) {
                    container.textView.text = ""
                    return
                }

                container.textView.text = data.date.dayOfMonth.toString()

                if (data.date == LocalDate.now()) {
                    container.textView.setBackgroundResource(R.drawable.bg_today)
                    container.textView.setTextColor(Color.WHITE)
                }

                if (activityDates.contains(data.date)) {
                    container.dot.visibility = View.VISIBLE
                }

                container.view.setOnClickListener {
                    selectedDate = data.date
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    showBottomSheet()
                }
            }
        }

        observeCalendarAndCrops()
        return view
    }

    private fun observeCalendarAndCrops() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    dao.getAllActivityDates().collect { timestamps ->
                        if (!isAdded) return@collect
                        val zone = ZoneId.systemDefault()
                        activityDates = timestamps.map {
                            Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                        }.toSet()
                        calendarView.notifyCalendarChanged()
                    }
                }
                launch {
                    cropEntryDao.getAllCrops().collect { crops ->
                        if (!isAdded) return@collect
                        renderCropCards(crops)
                    }
                }
            }
        }
    }

    private fun refreshCalendarAndCrops() {
        viewLifecycleOwner.lifecycleScope.launch {
            val (dates, crops) = withContext(Dispatchers.IO) {
                reloadActivityDates() to cropEntryDao.getAllCrops().first()
            }
            if (!isAdded) return@launch
            activityDates = dates
            calendarView.notifyCalendarChanged()
            renderCropCards(crops)
        }
    }

    private fun showBottomSheet() {
        val bottomSheetView = layoutInflater.inflate(
            R.layout.bottomsheet_activities,
            null,
            false
        )
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(bottomSheetView)

        val recyclerView = bottomSheetView.findViewById<RecyclerView>(R.id.activityRecyclerView)
        val addFab = bottomSheetView.findViewById<FloatingActionButton>(R.id.addActivityFab)

        val adapter = ActivityAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        fun loadActivities() {
            val startOfDay = Instant.ofEpochMilli(selectedDate)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val endOfDay = getEndOfDay(startOfDay)

            viewLifecycleOwner.lifecycleScope.launch {
                val activities = withContext(Dispatchers.IO) {
                    dao.getActivitiesByDate(startOfDay, endOfDay).first()
                }
                if (!isAdded) return@launch
                adapter.submitList(activities)
            }
        }

        adapter.onDelete = { activity ->
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    dao.deleteActivity(activity)
                }
                if (!isAdded) return@launch
                WateringScheduler.cancel(requireContext(), activity.id)
                ActivityReminderWorker.cancel(requireContext(), activity.id)
                loadActivities()
            }
        }

        adapter.onEdit = { activity ->
            showEditDialog(activity) {
                loadActivities()
            }
        }

        dialog.setTitle("Activities on ${sdf.format(Date(selectedDate))}")

        addFab.setOnClickListener {
            showAddDialog {
                loadActivities()
            }
        }

        loadActivities()
        dialog.show()
    }

    private fun renderCropCards(crops: List<CropEntry>) {
        cropCardsContainer.removeAllViews()

        if (crops.isEmpty()) {
            tvNoCrops.visibility = View.VISIBLE
            return
        }

        tvNoCrops.visibility = View.GONE
        val dayMs = TimeUnit.DAYS.toMillis(1)

        for (crop in crops) {
            val cardView = layoutInflater.inflate(
                R.layout.item_crop_card,
                cropCardsContainer,
                false
            )

            val tvCropName = cardView.findViewById<TextView>(R.id.tvCropName)
            val tvDateRange = cardView.findViewById<TextView>(R.id.tvDateRange)
            val progressBar = cardView.findViewById<ProgressBar>(R.id.progressCrop)
            val tvProgress = cardView.findViewById<TextView>(R.id.tvProgress)
            val tvDaysRemaining = cardView.findViewById<TextView>(R.id.tvDaysRemaining)
            val tvNextWatering = cardView.findViewById<TextView>(R.id.tvNextWatering)
            val btnMarkWatered = cardView.findViewById<Button>(R.id.btnMarkWatered)
            val btnDeleteCrop = cardView.findViewById<Button>(R.id.btnDeleteCrop)

            tvCropName.text = "🌱 ${crop.cropName}"
            tvDateRange.text = "${sdf.format(crop.sowingDate)} → ${sdf.format(crop.expectedHarvestDate)}"

            val now = System.currentTimeMillis()
            val totalDuration = crop.expectedHarvestDate - crop.sowingDate
            val progressPercent = if (totalDuration > 0L) {
                (((now - crop.sowingDate).toFloat() / totalDuration.toFloat()) * 100f)
                    .coerceIn(0f, 100f)
                    .toInt()
            } else {
                0
            }
            val daysRemaining = ((crop.expectedHarvestDate - now) / dayMs).toInt()

            progressBar.progress = progressPercent
            tvProgress.text = "$progressPercent% grown"
            tvDaysRemaining.text = if (daysRemaining > 0) "$daysRemaining days left" else "Ready to harvest! 🎉"

            val lastWatered = crop.lastWateredDate ?: crop.sowingDate
            val nextWatering = lastWatered + (crop.wateringIntervalDays.toLong() * dayMs)
            val daysUntilWater = ((nextWatering - now) / dayMs).toInt()
            tvNextWatering.text = when {
                daysUntilWater < 0 -> "💧 Water now! (overdue)"
                daysUntilWater == 0 -> "💧 Water today"
                else -> "💧 Next watering in $daysUntilWater days"
            }

            btnMarkWatered.setOnClickListener {
                val updated = crop.copy(lastWateredDate = System.currentTimeMillis())
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        cropEntryDao.updateCrop(updated)
                    }
                    if (!isAdded) return@launch
                    WateringScheduler.schedule(requireContext(), updated)
                    Toast.makeText(
                        requireContext(),
                        "✅ ${crop.cropName} marked as watered!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            btnDeleteCrop.setOnClickListener {
                val cropId = crop.id
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete ${crop.cropName}?")
                    .setMessage("This will remove the crop tracking entry.")
                    .setPositiveButton("Delete") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                cropEntryDao.deleteCrop(crop)
                            }
                            if (!isAdded) return@launch
                            WateringScheduler.cancel(requireContext(), cropId)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            cropCardsContainer.addView(cardView)
        }
    }

    private fun showAddCropDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val cropCount = withContext(Dispatchers.IO) {
                cropEntryDao.getCropCount()
            }
            if (!isAdded) return@launch

            if (cropCount >= 2) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Free Limit Reached")
                    .setMessage("Free users can track up to 2 crops. Upgrade to Premium for unlimited crop tracking.")
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }

            pickedSowingDate = 0L
            pickedHarvestDate = 0L

            val dialogView = layoutInflater.inflate(R.layout.dialog_add_crop, null, false)
            val etCropName = dialogView.findViewById<TextInputEditText>(R.id.etCropName)
            val etSowingDate = dialogView.findViewById<TextInputEditText>(R.id.etSowingDate)
            val etHarvestDate = dialogView.findViewById<TextInputEditText>(R.id.etHarvestDate)
            val etWateringInterval = dialogView.findViewById<TextInputEditText>(R.id.etWateringInterval)
            val etCropNotes = dialogView.findViewById<TextInputEditText>(R.id.etCropNotes)

            etSowingDate.setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(
                    requireContext(),
                    { _, y, m, d ->
                        cal.set(y, m, d, 0, 0, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        pickedSowingDate = cal.timeInMillis
                        etSowingDate.setText(sdf.format(pickedSowingDate))
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }

            etHarvestDate.setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(
                    requireContext(),
                    { _, y, m, d ->
                        cal.set(y, m, d, 0, 0, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        pickedHarvestDate = cal.timeInMillis
                        etHarvestDate.setText(sdf.format(pickedHarvestDate))
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Add Crop")
                .setView(dialogView)
                .setPositiveButton("Add") { _, _ ->
                    val name = etCropName.text.toString().trim()
                    val interval = etWateringInterval.text.toString().trim().toIntOrNull() ?: 2
                    val notes = etCropNotes.text.toString().trim()

                    if (name.isEmpty() || pickedSowingDate == 0L || pickedHarvestDate == 0L) {
                        Toast.makeText(
                            requireContext(),
                            "Please fill in crop name and both dates",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setPositiveButton
                    }

                    val crop = CropEntry(
                        cropName = name,
                        sowingDate = pickedSowingDate,
                        expectedHarvestDate = pickedHarvestDate,
                        wateringIntervalDays = interval,
                        notes = notes.ifEmpty { null }
                    )

                    viewLifecycleOwner.lifecycleScope.launch {
                        val savedCrop = withContext(Dispatchers.IO) {
                            cropEntryDao.insertCrop(crop)
                            cropEntryDao.getCropByName(crop.cropName)
                        }
                        if (!isAdded) return@launch
                        if (savedCrop != null) {
                            WateringScheduler.schedule(requireContext(), savedCrop)
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    class DayViewContainer(view: View) : ViewContainer(view) {
        val textView: TextView = view.findViewById(R.id.dayText)
        val dot: View = view.findViewById(R.id.dot)
    }

    private fun getEndOfDay(startDateMs: Long): Long {
        val nextDayStart = Instant.ofEpochMilli(startDateMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return nextDayStart - 1L
    }

    private suspend fun reloadActivityDates(): Set<LocalDate> {
        val zone = ZoneId.systemDefault()
        return dao.getAllActivityDates().first().map {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
        }.toSet()
    }

    private fun showAddDialog(onAdded: () -> Unit) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_activity, null, false)

        val cropInput = dialogView.findViewById<EditText>(R.id.cropEditText)
        val typeInput = dialogView.findViewById<EditText>(R.id.typeEditText)
        val notesInput = dialogView.findViewById<EditText>(R.id.notesEditText)
        val etReminder = dialogView.findViewById<TextInputEditText>(R.id.etReminderTime)

        var pickedReminderMs: Long? = null

        etReminder.setOnClickListener {
            val cal = Calendar.getInstance().apply {
                timeInMillis = selectedDate
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
            }

            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, minute)
                    cal.set(Calendar.SECOND, 0)
                    pickedReminderMs = cal.timeInMillis
                    etReminder.setText(
                        String.format(
                            Locale.getDefault(),
                            "%02d:%02d on %s",
                            hour,
                            minute,
                            SimpleDateFormat("dd MMM", Locale.getDefault()).format(cal.time)
                        )
                    )
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
            ).show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Add Activity")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val normalizedDate = Instant.ofEpochMilli(selectedDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                val activity = FarmActivity(
                    date = normalizedDate,
                    crop = cropInput.text.toString(),
                    activityType = typeInput.text.toString(),
                    notes = notesInput.text.toString(),
                    reminderTime = pickedReminderMs
                )

                viewLifecycleOwner.lifecycleScope.launch {
                    val inserted = withContext(Dispatchers.IO) {
                        dao.insertActivity(activity)
                        dao.getLastInserted()
                    }
                    if (!isAdded) return@launch

                    pickedReminderMs?.let { reminderMs ->
                        if (inserted != null) {
                            ActivityReminderWorker.schedule(
                                context = requireContext(),
                                activityId = inserted.id,
                                crop = inserted.crop,
                                activityType = inserted.activityType,
                                notes = inserted.notes ?: "",
                                reminderTimeMs = reminderMs
                            )
                        }
                    }

                    onAdded()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(
        activity: FarmActivity,
        onUpdated: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_activity, null, false)

        val cropInput = dialogView.findViewById<EditText>(R.id.cropEditText)
        val typeInput = dialogView.findViewById<EditText>(R.id.typeEditText)
        val notesInput = dialogView.findViewById<EditText>(R.id.notesEditText)

        cropInput.setText(activity.crop)
        typeInput.setText(activity.activityType)
        notesInput.setText(activity.notes)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Activity")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val updated = activity.copy(
                    crop = cropInput.text.toString(),
                    activityType = typeInput.text.toString(),
                    notes = notesInput.text.toString()
                )

                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        dao.updateActivity(updated)
                    }
                    if (!isAdded) return@launch
                    onUpdated()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}