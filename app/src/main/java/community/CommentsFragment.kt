package com.devdeepsy.nidusflora.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.devdeepsy.nidusflora.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class CommentsFragment : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_POST_ID = "post_id"

        fun newInstance(postId: String): CommentsFragment =
            CommentsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_POST_ID, postId)
                }
            }
    }

    private val postId: String by lazy {
        requireArguments().getString(ARG_POST_ID)!!
    }

    private val viewModel: CommentsViewModel by viewModels {
        CommentsViewModel.Factory(postId)
    }

    private lateinit var rvComments: RecyclerView
    private lateinit var etComment: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: CommentsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_comments, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvComments  = view.findViewById(R.id.rvComments)
        etComment   = view.findViewById(R.id.etComment)
        btnSend     = view.findViewById(R.id.btnSendComment)
        progressBar = view.findViewById(R.id.progressComments)

        setupRecyclerView()
        setupInput()
        observeStates()
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = CommentsAdapter(
            currentUid = viewModel.currentUid,
            onDeleteClicked = { comment -> confirmDelete(comment) }
        )
        rvComments.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        rvComments.adapter = adapter
    }

    // ── Input bar ─────────────────────────────────────────────────────────────

    private fun setupInput() {
        btnSend.setOnClickListener { postComment() }

        etComment.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                postComment(); true
            } else false
        }
    }

    private fun postComment() {
        val text = etComment.text.toString()
        if (text.isBlank()) return
        viewModel.submitComment(text, requireContext())
    }

    // ── State observers ───────────────────────────────────────────────────────

    private fun observeStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // comments list
                launch {
                    viewModel.commentsState.collect { state ->
                        when (state) {
                            is CommentsState.Loading -> {
                                progressBar.visibility = View.VISIBLE
                            }
                            is CommentsState.Success -> {
                                progressBar.visibility = View.GONE
                                adapter.submitList(state.comments) {
                                    if (state.comments.isNotEmpty()) {
                                        rvComments.smoothScrollToPosition(state.comments.size - 1)
                                    }
                                }
                            }
                            is CommentsState.Error -> {
                                progressBar.visibility = View.GONE
                                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // submit state
                launch {
                    viewModel.submitState.collect { state ->
                        when (state) {
                            is SubmitCommentState.Idle -> {
                                btnSend.isEnabled = true
                            }
                            is SubmitCommentState.Loading -> {
                                btnSend.isEnabled = false
                            }
                            is SubmitCommentState.Success -> {
                                etComment.text?.clear()
                                btnSend.isEnabled = true
                                viewModel.resetSubmitState()
                            }
                            is SubmitCommentState.Error -> {
                                btnSend.isEnabled = true
                                val msg = if (state.message == "sign_in_required") {
                                    "Please sign in to comment"
                                } else state.message
                                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                                viewModel.resetSubmitState()
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Delete confirmation ───────────────────────────────────────────────────

    private fun confirmDelete(comment: Comment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete comment?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteComment(comment.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }
}