package com.devdeepsy.nidusflora

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.devdeepsy.nidusflora.community.CommunityViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class AccountFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()

    // ── Reuse the same ViewModel instance as CommunityFragment ──
    private val communityVm: CommunityViewModel by activityViewModels {
        CommunityViewModel.Factory()
    }

    private lateinit var ivAvatar: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnAction: MaterialButton
    private lateinit var btnClose: ImageView

    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                try {
                    val account = GoogleSignIn
                        .getSignedInAccountFromIntent(result.data)
                        .getResult(ApiException::class.java)
                    firebaseAuthWithGoogle(account.idToken!!)
                } catch (e: ApiException) {
                    Toast.makeText(
                        requireContext(),
                        "Sign-in failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_account, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ivAvatar  = view.findViewById(R.id.iv_account_avatar)
        tvName    = view.findViewById(R.id.tv_account_name)
        tvEmail   = view.findViewById(R.id.tv_account_email)
        tvStatus  = view.findViewById(R.id.tv_account_status)
        btnAction = view.findViewById(R.id.btn_account_action)
        btnClose  = view.findViewById(R.id.btn_account_close)

        btnClose.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        renderState()
    }

    private fun renderState() {
        val user = auth.currentUser

        if (user == null) {
            ivAvatar.setImageResource(R.drawable.ic_person_placholder)
            tvName.text   = "Not signed in"
            tvEmail.text  = ""
            tvStatus.text = "Sign in to post in the Community,\nlike posts and leave comments."

            btnAction.text = "Sign in with Google"
            btnAction.setIconResource(R.drawable.ic_google)
            btnAction.setOnClickListener {
                startGoogleSignIn()
            }

        } else {
            Glide.with(this)
                .load(user.photoUrl)
                .apply(RequestOptions().transform(CircleCrop()))
                .placeholder(R.drawable.ic_person_placholder)
                .into(ivAvatar)

            tvName.text   = user.displayName ?: "Anonymous"
            tvEmail.text  = user.email ?: ""
            tvStatus.text = "You can post, like and comment\nin the Community."

            btnAction.text = "Sign Out"
            btnAction.setIconResource(R.drawable.ic_logout)
            btnAction.setOnClickListener {
                auth.signOut()
                communityVm.signOut()   // ← reuses existing instance, no crash
                (requireActivity() as? MainActivity)?.updateAccountButton()
                renderState()
                Toast.makeText(requireContext(), "Signed out", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(requireActivity(), gso)
        signInLauncher.launch(client.signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener {
                communityVm.onSignInSuccess()   // ← reuses existing instance, no crash
                (requireActivity() as? MainActivity)?.updateAccountButton()
                renderState()
                Toast.makeText(
                    requireContext(),
                    "Signed in as ${auth.currentUser?.displayName}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    "Auth failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}