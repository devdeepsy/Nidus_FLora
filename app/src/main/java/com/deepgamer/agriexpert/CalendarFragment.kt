package com.deepgamer.agriexpert

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deepgamer.agriexpert.notifications.WateringScheduler
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.kizitonwose.calendar.core.*
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import java.text.SimpleDateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*
import com.deepgamer.agriexpert.models.AppDatabase
import com.deepgamer.agriexpert.models.CropEntry
import com.deepgamer.agriexpert.models.CropEntryDao
import com.deepgamer.agriexpert.models.FarmActivity
import com.deepgamer.agriexpert.models.FarmActivityDao

class CalendarFragment : Fragment() {

    private lateinit var calendarView: CalendarView
    private lateinit var db: AppDatabase
    private lateinit var dao: FarmActivityDao
    private var activityDates: Set<LocalDate> = emptySet()


    private var selectedDate: Long = Calendar.getInstance().timeInMillis
    private lateinit var cropEntryDao: CropEntryDao
    private lateinit var cropCardsContainer: LinearLayout
    private lateinit var btnAddCrop: Button
    private lateinit var tvNoCrops: TextView
    private val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private var pickedSowingDate: Long = 0L
    private var pickedHarvestDate: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_calendar, container, false)

        calendarView = view.findViewById(R.id.calendarView)
        db = AppDatabase.getDatabase(requireContext())
        dao = db.farmActivityDao()
        activityDates = reloadActivityDates()
        cropCardsContainer = view.findViewById(R.id.cropCardsContainer)
        btnAddCrop = view.findViewById(R.id.btnAddCrop)
        tvNoCrops = view.findViewById(R.id.tvNoCrops)
        cropEntryDao = db.cropEntryDao()

        btnAddCrop.setOnClickListener {
            showAddCropDialog()
        }
        loadCropCards()
        // When user taps a date
        val currentMonth = YearMonth.now()

        calendarView.setup(
            currentMonth.minusMonths(12),
            currentMonth.plusMonths(12),
            DayOfWeek.MONDAY
        )

        calendarView.scrollToMonth(currentMonth)
        val monthTitle = view.findViewById<TextView>(R.id.monthTitle)

        calendarView.monthScrollListener = { month ->
            val formatter = DateTimeFormatter.ofPattern("MMMM yyyy")
            monthTitle.text = month.yearMonth.format(formatter)
        }

        monthTitle.text = currentMonth.format(
            DateTimeFormatter.ofPattern("MMMM yyyy")
        )

        calendarView.dayBinder = object : MonthDayBinder<DayViewContainer>  {

            override fun create(view: View) = DayViewContainer(view)

            override fun bind(container: DayViewContainer, day: CalendarDay) {

                // Always reset reused views
                container.textView.background = null
                container.textView.setTextColor(Color.WHITE)
                container.dot.visibility = View.GONE

                if (day.position == DayPosition.MonthDate) {
                    container.textView.text = day.date.dayOfMonth.toString()
                } else {
                    container.textView.text = ""
                    return
                }


                // ✅ Highlight TODAY
                if (day.position == DayPosition.MonthDate &&
                    day.date == LocalDate.now()) {

                    container.textView.setBackgroundResource(R.drawable.bg_today)
                    container.textView.setTextColor(Color.WHITE)
                }

                // ✅ Show activity dot
                if (activityDates.contains(day.date)) {
                    container.dot.visibility = View.VISIBLE
                }

                // Click listener
                container.view.setOnClickListener {
                    selectedDate = day.date
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()

                    showBottomSheet()
                }
            }

        }

        return view
    }

    private fun showBottomSheet() {
        val bottomSheetView = layoutInflater.inflate(R.layout.bottomsheet_activities, null)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(bottomSheetView)

        val recyclerView = bottomSheetView.findViewById<RecyclerView>(R.id.activityRecyclerView)
        val addFab = bottomSheetView.findViewById<FloatingActionButton>(R.id.addActivityFab)

        val adapter = ActivityAdapter()

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        fun loadActivities() {
            val endDate = getEndOfDay(selectedDate)
            val activities = dao.getActivitiesByDate(selectedDate, endDate)
            adapter.submitList(activities)
        }

        adapter.onDelete = { activity ->
            dao.deleteActivity(activity)
            activityDates = reloadActivityDates()
            calendarView.notifyCalendarChanged()
            loadActivities()
        }

        adapter.onEdit = { activity ->
            showEditDialog(activity) {
                activityDates = reloadActivityDates()
                calendarView.notifyCalendarChanged()
                loadActivities()
            }
        }

        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        dialog.setTitle("Activities on ${sdf.format(Date(selectedDate))}")

        addFab.setOnClickListener {
            showAddDialog {
                loadActivities()
            }
        }

        loadActivities()
        dialog.show()
    }

    private fun loadCropCards() {
        cropCardsContainer.removeAllViews()
        val crops = cropEntryDao.getAllCrops()

        if (crops.isEmpty()) {
            tvNoCrops.visibility = View.VISIBLE
            return
        }

        tvNoCrops.visibility = View.GONE

        for (crop in crops) {
            val cardView = layoutInflater.inflate(
                R.layout.item_crop_card,
                cropCardsContainer,
                false
            )

            // Bind views
            val tvCropName = cardView.findViewById<TextView>(R.id.tvCropName)
            val tvDateRange = cardView.findViewById<TextView>(R.id.tvDateRange)
            val progressBar = cardView.findViewById<ProgressBar>(R.id.progressCrop)
            val tvProgress = cardView.findViewById<TextView>(R.id.tvProgress)
            val tvDaysRemaining = cardView.findViewById<TextView>(R.id.tvDaysRemaining)
            val tvNextWatering = cardView.findViewById<TextView>(R.id.tvNextWatering)
            val btnMarkWatered = cardView.findViewById<Button>(R.id.btnMarkWatered)
            val btnDeleteCrop = cardView.findViewById<Button>(R.id.btnDeleteCrop)

            // Crop name
            tvCropName.text = "🌱 ${crop.cropName}"

            // Date range
            tvDateRange.text = "${sdf.format(crop.sowingDate)} → ${sdf.format(crop.expectedHarvestDate)}"

            // Progress calculation
            val now = System.currentTimeMillis()
            val totalDuration = crop.expectedHarvestDate - crop.sowingDate
            val elapsed = now - crop.sowingDate
            val progressPercent = ((elapsed.toFloat() / totalDuration) * 100)
                .coerceIn(0f, 100f).toInt()
            val daysRemaining = ((crop.expectedHarvestDate - now) / (1000 * 60 * 60 * 24)).toInt()

            progressBar.progress = progressPercent
            tvProgress.text = "$progressPercent% grown"
            tvDaysRemaining.text = if (daysRemaining > 0) "$daysRemaining days left"
            else "Ready to harvest! 🎉"

            // Next watering
            val lastWatered = crop.lastWateredDate ?: crop.sowingDate
            val nextWatering = lastWatered + (crop.wateringIntervalDays * 24 * 60 * 60 * 1000L)
            val daysUntilWater = ((nextWatering - now) / (1000 * 60 * 60 * 24)).toInt()
            tvNextWatering.text = when {
                daysUntilWater < 0 -> "💧 Water now! (overdue)"
                daysUntilWater == 0 -> "💧 Water today"
                else -> "💧 Next watering in $daysUntilWater days"
            }

            // Mark watered button
            btnMarkWatered.setOnClickListener {
                val updated = crop.copy(lastWateredDate = System.currentTimeMillis())
                cropEntryDao.updateCrop(updated)
                WateringScheduler.schedule(requireContext(), updated)
                Toast.makeText(
                    requireContext(),
                    "✅ ${crop.cropName} marked as watered!",
                    Toast.LENGTH_SHORT
                ).show()
                loadCropCards()
            }

            // Delete button
            btnDeleteCrop.setOnClickListener {
                val cropId = crop.id
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete ${crop.cropName}?")
                    .setMessage("This will remove the crop tracking entry.")
                    .setPositiveButton("Delete") { _, _ ->
                        cropEntryDao.deleteCrop(crop)
                        WateringScheduler.cancel(requireContext(), cropId)
                        loadCropCards()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            cropCardsContainer.addView(cardView)
        }
    }

    private fun showAddCropDialog() {
        val crops = cropEntryDao.getCropCount()
        if (crops >= 2) {
            AlertDialog.Builder(requireContext())
                .setTitle("Free Limit Reached")
                .setMessage("Free users can track up to 2 crops. Upgrade to Premium for unlimited crop tracking.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        pickedSowingDate = 0L
        pickedHarvestDate = 0L

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_crop, null)
        val etCropName = dialogView.findViewById<TextInputEditText>(R.id.etCropName)
        val etSowingDate = dialogView.findViewById<TextInputEditText>(R.id.etSowingDate)
        val etHarvestDate = dialogView.findViewById<TextInputEditText>(R.id.etHarvestDate)
        val etWateringInterval = dialogView.findViewById<TextInputEditText>(R.id.etWateringInterval)
        val etCropNotes = dialogView.findViewById<TextInputEditText>(R.id.etCropNotes)

        // Sowing date picker
        etSowingDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                cal.set(y, m, d, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                pickedSowingDate = cal.timeInMillis
                etSowingDate.setText(sdf.format(pickedSowingDate))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Harvest date picker
        etHarvestDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                cal.set(y, m, d, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                pickedHarvestDate = cal.timeInMillis
                etHarvestDate.setText(sdf.format(pickedHarvestDate))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
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
                cropEntryDao.insertCrop(crop)
                val inserted = cropEntryDao.getCropByName(crop.cropName) // see DAO note below
                if (inserted != null) {
                    WateringScheduler.schedule(requireContext(), inserted)
                }

                loadCropCards()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    class DayViewContainer(view: View) : ViewContainer(view) {
        val textView = view.findViewById<TextView>(R.id.dayText)
        val dot = view.findViewById<View>(R.id.dot)
    }

    private fun normalizeDate(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month, day, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getEndOfDay(startDate: Long): Long {
        return startDate + (24 * 60 * 60 * 1000) - 1
    }

    private fun reloadActivityDates(): Set<LocalDate> {
        return dao.getAllActivityDates().map {
            Instant.ofEpochMilli(it)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }.toSet()
    }


    private fun showAddDialog(onAdded: () -> Unit) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_activity, null)

        val cropInput = dialogView.findViewById<EditText>(R.id.cropEditText)
        val typeInput = dialogView.findViewById<EditText>(R.id.typeEditText)
        val notesInput = dialogView.findViewById<EditText>(R.id.notesEditText)

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
                    notes = notesInput.text.toString()
                )

                dao.insertActivity(activity)

                activityDates = reloadActivityDates()
                calendarView.notifyCalendarChanged()

                onAdded()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(
        activity: FarmActivity,
        onUpdated: () -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_activity, null)

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

                dao.updateActivity(updated)
                onUpdated()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

}
