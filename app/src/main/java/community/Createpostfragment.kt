package com.deepgamer.agriexpert.community

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.deepgamer.agriexpert.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class CreatePostFragment : Fragment() {

    private val viewModel: CommunityViewModel by activityViewModels {
        CommunityViewModel.Factory()
    }

    // ─── Views ─────────────────────────────────────────────────────────────────

    private lateinit var layoutSignedOut: View
    private lateinit var layoutSignedIn: View
    private lateinit var tvSignedInAs: TextView
    private lateinit var etTitle: EditText
    private lateinit var etBody: EditText
    private lateinit var chipGroupTags: ChipGroup
    private lateinit var imgPreview: ImageView
    private lateinit var btnPickImage: ImageButton
    private lateinit var btnRemoveImage: ImageButton
    private lateinit var btnSubmit: MaterialButton
    private lateinit var progressSubmit: ProgressBar

    // ─── State ─────────────────────────────────────────────────────────────────

    private var selectedImageBytes: ByteArray? = null
    private var selectedTag: PostTag = PostTag.QUESTION

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri = result.data?.data ?: return@registerForActivityResult
                handleImagePicked(uri)
            }
        }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_create_post, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)
        setupTagChips()
        observeAuthState()
        observeSubmitState()

        btnPickImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            pickImageLauncher.launch(intent)
        }

        btnRemoveImage.setOnClickListener {
            selectedImageBytes = null
            imgPreview.visibility = View.GONE
            btnRemoveImage.visibility = View.GONE
        }

        btnSubmit.setOnClickListener { submitPost() }
    }

    // ─── View binding ──────────────────────────────────────────────────────────

    private fun bindViews(view: View) {
        layoutSignedOut = view.findViewById(R.id.layout_signed_out)
        layoutSignedIn = view.findViewById(R.id.layout_signed_in)
        tvSignedInAs = view.findViewById(R.id.tv_signed_in_as)
        etTitle = view.findViewById(R.id.et_post_title)
        etBody = view.findViewById(R.id.et_post_body)
        chipGroupTags = view.findViewById(R.id.chip_group_tags)
        imgPreview = view.findViewById(R.id.img_preview)
        btnPickImage = view.findViewById(R.id.btn_pick_image)
        btnRemoveImage = view.findViewById(R.id.btn_remove_image)
        btnSubmit = view.findViewById(R.id.btn_submit_post)
        progressSubmit = view.findViewById(R.id.progress_submit)
    }

    // ─── Google Sign-In ────────────────────────────────────────────────────────


    // ─── Tags ──────────────────────────────────────────────────────────────────

    private fun setupTagChips() {
        PostTag.entries.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag.label
                isCheckable = true
                isChecked = tag == PostTag.QUESTION
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedTag = tag
                }
            }
            chipGroupTags.addView(chip)
        }
    }

    // ─── Observers ─────────────────────────────────────────────────────────────

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authState.collect { auth ->
                    if (auth.isSignedIn) {
                        layoutSignedOut.visibility = View.GONE
                        layoutSignedIn.visibility = View.VISIBLE
                        tvSignedInAs.text = "Posting as ${auth.displayName}"
                    } else {
                        layoutSignedOut.visibility = View.VISIBLE
                        layoutSignedIn.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun observeSubmitState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.submitState.collect { state ->
                    when (state) {
                        is CommunityViewModel.SubmitState.Idle -> {
                            btnSubmit.isEnabled = true
                            progressSubmit.visibility = View.GONE
                        }
                        is CommunityViewModel.SubmitState.Loading -> {
                            btnSubmit.isEnabled = false
                            progressSubmit.visibility = View.VISIBLE
                        }
                        is CommunityViewModel.SubmitState.Success -> {
                            viewModel.resetSubmitState()
                            requireActivity().onBackPressedDispatcher.onBackPressed()

                        }
                        is CommunityViewModel.SubmitState.Error -> {
                            btnSubmit.isEnabled = true
                            progressSubmit.visibility = View.GONE
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            viewModel.resetSubmitState()
                        }
                    }
                }
            }
        }
    }

    // ─── Image ─────────────────────────────────────────────────────────────────

    private fun handleImagePicked(uri: Uri) {
        val stream = requireContext().contentResolver.openInputStream(uri) ?: return
        val original = BitmapFactory.decodeStream(stream)
        selectedImageBytes = compressImage(original)

        Glide.with(this).load(uri).into(imgPreview)
        imgPreview.visibility = View.VISIBLE
        btnRemoveImage.visibility = View.VISIBLE
    }

    private fun compressImage(bitmap: Bitmap): ByteArray {
        // Hard cap at 600px to keep Base64 well under 1MB Firestore limit
        val maxDim = 600
        val scale = minOf(
            maxDim.toFloat() / bitmap.width,
            maxDim.toFloat() / bitmap.height,
            1f
        )
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else bitmap

        val out = ByteArrayOutputStream()
        // Try progressively lower quality until under 700KB
        var quality = 60
        do {
            out.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            quality -= 10
        } while (out.size() > 700_000 && quality > 10)

        return out.toByteArray()
    }

    // ─── Submit ────────────────────────────────────────────────────────────────

    private fun submitPost() {
        val title = etTitle.text.toString().trim()
        val body = etBody.text.toString().trim()

        if (title.isEmpty()) {
            etTitle.error = "Title is required"
            return
        }
        if (body.isEmpty()) {
            etBody.error = "Body is required"
            return
        }

        // Image upload skipped until Firebase Storage is enabled
        // selectedImageBytes is preserved so wiring Storage later
        // is just one repository call away — no changes needed here

        viewModel.submitPost(title, body, selectedTag.label,selectedImageBytes)
    }
}