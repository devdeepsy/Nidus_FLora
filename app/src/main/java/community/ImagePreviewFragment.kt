package com.deepgamer.agriexpert.community

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.*
import android.widget.ImageButton
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.deepgamer.agriexpert.R
import com.github.chrisbanes.photoview.PhotoView

class ImagePreviewFragment : DialogFragment() {

    companion object {
        private const val ARG_IMAGE = "arg_image"   // URL or Base64 string

        fun newInstance(imageData: String): ImagePreviewFragment =
            ImagePreviewFragment().apply {
                arguments = Bundle().apply { putString(ARG_IMAGE, imageData) }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_image_preview, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val photoView = view.findViewById<PhotoView>(R.id.photo_view)
        val btnClose  = view.findViewById<ImageButton>(R.id.btn_close_preview)

        btnClose.setOnClickListener { dismiss() }

        val imageData = arguments?.getString(ARG_IMAGE) ?: return

        val isBase64 = !imageData.startsWith("http")
        if (isBase64) {
            try {
                val bytes  = Base64.decode(imageData, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                photoView.setImageBitmap(bitmap)
            } catch (_: Exception) { dismiss() }
        } else {
            Glide.with(this)
                .load(imageData)
                .apply(RequestOptions().transform(RoundedCorners(0)))
                .into(photoView)
        }
    }
}