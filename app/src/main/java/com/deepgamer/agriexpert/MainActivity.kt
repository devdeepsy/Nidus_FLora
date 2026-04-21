package com.deepgamer.agriexpert

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.deepgamer.agriexpert.notifications.NotificationHelper
import com.deepgamer.agriexpert.workers.WeatherWorker
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.deepgamer.agriexpert.store.StoreFragment
import com.deepgamer.agriexpert.community.CommunityFragment

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var fab: FloatingActionButton

    // Track if chatbot is currently open
    private var isChatBotOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        setContentView(R.layout.activity_main)

        // Notification channels — must be first
        NotificationHelper.createAllChannels(this)

        // Schedule daily weather tip worker
        WeatherWorker.schedule(this)

        bottomNav = findViewById(R.id.bottom_navigation)
        fab       = findViewById(R.id.fab)

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(WeatherFragment())
            bottomNav.selectedItemId = R.id.nav_weather
        }

        // Bottom nav switching
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_weather   -> loadFragment(WeatherFragment())
                R.id.nav_calender  -> loadFragment(CalendarFragment())
                R.id.nav_disease   -> loadFragment(DiseaseFragment())
                R.id.nav_store     -> loadFragment(StoreFragment())
                R.id.nav_community -> loadFragment(CommunityFragment())
            }
            true
        }

        // FAB — open chatbot
        fab.setOnClickListener {
            if (!isChatBotOpen) {
                openChatBot()
            }
        }

        // Make FAB draggable
        makeFabDraggable()
    }

    // ── Fragment loading ──────────────────────────────────────────────

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    /** Use this from any fragment that needs to trigger navigation */
    fun navigateToFragment(fragment: Fragment) {
        loadFragment(fragment)
    }

    // ── ChatBot open / close ──────────────────────────────────────────

    private fun openChatBot() {
        isChatBotOpen = true
        bottomNav.visibility = View.GONE
        fab.visibility = View.GONE

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ChatBotFragment())
            .addToBackStack("chatbot")
            .commit()

        // Listen for when chatbot is popped off the back stack
        supportFragmentManager.addOnBackStackChangedListener(object :
            androidx.fragment.app.FragmentManager.OnBackStackChangedListener {
            override fun onBackStackChanged() {
                if (supportFragmentManager.backStackEntryCount == 0) {
                    // ChatBot was closed
                    isChatBotOpen = false
                    bottomNav.visibility = View.VISIBLE
                    fab.visibility = View.VISIBLE
                    supportFragmentManager.removeOnBackStackChangedListener(this)
                }
            }
        })
    }


    private fun closeChatBot() {
        supportFragmentManager.popBackStack()
        // visibility + flag are handled by the OnBackStackChangedListener above
    }

    // ── Back press ───────────────────────────────────────────────────

    override fun onBackPressed() {
        if (isChatBotOpen) {
            closeChatBot()
        } else {
            super.onBackPressed()
        }
    }

    // ── Draggable FAB ────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun makeFabDraggable() {
        var downRawX = 0f
        var downRawY = 0f
        var dX = 0f
        var dY = 0f
        var isDragging = false
        val clickThreshold = 10f

        fab.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val movedX = Math.abs(event.rawX - downRawX)
                    val movedY = Math.abs(event.rawY - downRawY)

                    if (movedX > clickThreshold || movedY > clickThreshold) {
                        isDragging = true
                        val parent = view.parent as View
                        val newX = (event.rawX + dX)
                            .coerceIn(0f, parent.width - view.width.toFloat())
                        val newY = (event.rawY + dY)
                            .coerceIn(0f, parent.height - view.height.toFloat())
                        view.x = newX
                        view.y = newY
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // It was a tap — fire manually
                        if (!isChatBotOpen) {
                            openChatBot()
                        }
                    }
                    isDragging = false
                    true
                }

                else -> false
            }
        }
    }
}