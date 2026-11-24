package com.devdeepsy.nidusflora

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.devdeepsy.nidusflora.community.CommunityViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class AccountFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()
    private lateinit var credentialManager: CredentialManager

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_account, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        credentialManager = CredentialManager.create(requireContext())

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
            tvName.setText(R.string.account_not_signed_in)
            tvEmail.text = ""
            tvStatus.setText(R.string.account_signed_out_status)

            btnAction.setText(R.string.account_sign_in_google)
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

            tvName.text = user.displayName ?: getString(R.string.account_anonymous)
            tvEmail.text = user.email ?: ""
            tvStatus.setText(R.string.account_signed_in_status)

            btnAction.setText(R.string.account_sign_out)
            btnAction.setIconResource(R.drawable.ic_logout)
            btnAction.setOnClickListener {
                signOutUser()
            }
        }
    }

    private fun startGoogleSignIn() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = requireActivity()
                )
                val credential = result.credential
                if (
                    credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
                }
            } catch (e: GetCredentialException) {
                android.util.Log.e("AccountFragment", "Credential sign-in failed (${e.type})", e)
                if (!isAdded) return@launch
                Toast.makeText(
                    requireContext(),
                    getString(R.string.account_toast_sign_in_failed, e.errorMessage ?: e.type),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                communityVm.onSignInSuccess()
                (activity as? MainActivity)?.updateAccountButton()
                renderState()
                val displayName = auth.currentUser?.displayName
                    ?: getString(R.string.account_anonymous)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.account_toast_signed_in_as, displayName),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(
                    requireContext(),
                    getString(R.string.account_toast_auth_failed, e.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun signOutUser() {
        auth.signOut()
        communityVm.signOut()
        (activity as? MainActivity)?.updateAccountButton()
        renderState()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {
                // Non-fatal if no active credential state exists
            }
        }
        Toast.makeText(
            requireContext(),
            getString(R.string.account_toast_signed_out),
            Toast.LENGTH_SHORT
        ).show()
    }
}