package com.devdeepsy.nidusflora.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.devdeepsy.nidusflora.R
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

abstract class BaseFeedFragment : Fragment() {

    protected val viewModel: CommunityViewModel by activityViewModels {
        CommunityViewModel.Factory()
    }

    private lateinit var adapter: CommunityAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView

    abstract val feedState: StateFlow<FeedState>
    abstract val emptyMessage: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_feed_tab, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.recycler_feed)
        progressBar = view.findViewById(R.id.progress_feed)
        emptyView = view.findViewById(R.id.tv_feed_empty)

        adapter = CommunityAdapter(
            currentUid = viewModel.authState.value.uid.ifEmpty { null },
            onLikeClicked = { post -> viewModel.toggleLike(post.id) },
            onDeleteClicked = { post -> viewModel.deletePost(post.id) },
            onCommentClicked = { post -> openComments(post) }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    feedState.collect { state ->
                        when (state) {
                            is FeedState.Loading -> {
                                progressBar.visibility = View.VISIBLE
                                recycler.visibility = View.GONE
                                emptyView.visibility = View.GONE
                            }
                            is FeedState.Success -> {
                                progressBar.visibility = View.GONE
                                if (state.posts.isEmpty()) {
                                    recycler.visibility = View.GONE
                                    emptyView.visibility = View.VISIBLE
                                    emptyView.text = emptyMessage
                                } else {
                                    recycler.visibility = View.VISIBLE
                                    emptyView.visibility = View.GONE
                                    adapter.submitList(state.posts)
                                }
                            }
                            is FeedState.Error -> {
                                progressBar.visibility = View.GONE
                                recycler.visibility = View.GONE
                                emptyView.visibility = View.VISIBLE
                                emptyView.text = "Couldn't load posts.\n${state.message}"
                            }
                        }
                    }
                }
                launch {
                    viewModel.authState.collect { auth ->
                        adapter.updateCurrentUid(auth.uid.ifEmpty { null })
                    }
                }
            }
        }


    }

    private fun openComments(post: CommunityPost) {
        CommentsFragment.newInstance(post.id)
            .show(childFragmentManager, "comments")
    }

}

// ─── Tab Fragments ────────────────────────────────────────────────────────────

class TrendingFeedFragment : BaseFeedFragment() {
    override val feedState get() = viewModel.trendingState
    override val emptyMessage = "No trending posts yet."
}

class RecentFeedFragment : BaseFeedFragment() {
    override val feedState get() = viewModel.recentState
    override val emptyMessage = "No posts yet. Be the first!"
}

class MyPostsFeedFragment : BaseFeedFragment() {
    override val feedState get() = viewModel.myPostsState
    override val emptyMessage = "You haven't posted anything yet.\nSign in and share your first tip!"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Trigger load when tab is shown (user might have just signed in)
        viewModel.loadMyPosts()
    }
}