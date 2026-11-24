package com.devdeepsy.nidusflora.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.devdeepsy.nidusflora.MainActivity
import com.devdeepsy.nidusflora.R
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import android.widget.FrameLayout

class CommunityFragment : Fragment() {

    private val viewModel: CommunityViewModel by activityViewModels {
        CommunityViewModel.Factory()
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var fab: ExtendedFloatingActionButton

    private val tabTitles = listOf("🔥 Trending", "🕐 Recent", "👤 My Posts")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_community, container, false)

    interface PostNavigationHost {
        fun openCreatePost()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.vp_community)
        tabLayout = view.findViewById(R.id.tabs_community)
        fab = view.findViewById(R.id.fab_new_post)

        fab.show()

        setupViewPager()
        observeAuthState()

        fab.setOnClickListener {
            (requireActivity() as MainActivity).navigateToFragment(CreatePostFragment())
            val host = activity as? PostNavigationHost
            if (host != null) {
                host.openCreatePost()
            } else {
                (activity as? MainActivity)?.navigateToFragment(CreatePostFragment())
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 2) viewModel.loadMyPosts()
            }
        })
    }

    private fun setupViewPager() {
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 3
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> TrendingFeedFragment()
                1 -> RecentFeedFragment()
                else -> MyPostsFeedFragment()
            }
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authState.collect { auth ->
                    // Always show FAB — sign-in gate is inside CreatePostFragment
                    fab.show()
                    requireActivity().invalidateOptionsMenu()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val uid = viewModel.authState.value.uid
        if (uid.isNotEmpty()) {
            viewModel.loadMyPosts()
        }
        // Always refresh auth state from Firebase directly
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (firebaseUser != null && !viewModel.authState.value.isSignedIn) {
            viewModel.onSignInSuccess()
        }
    }

    override fun onPause() {
        super.onPause()
    }
}