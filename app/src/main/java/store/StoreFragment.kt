package com.devdeepsy.nidusflora.store

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.devdeepsy.nidusflora.R
import kotlinx.coroutines.launch

class StoreFragment : Fragment() {

    private lateinit var rvSeasonalPicks: RecyclerView
    private lateinit var rvCategories: RecyclerView
    private lateinit var progressBar: ProgressBar

    private val viewModel: StoreViewModel by viewModels()
    private val sharedPool = RecyclerView.RecycledViewPool()

    private lateinit var seasonalAdapter: ProductAdapter
    private lateinit var categoryAdapter: StoreAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_store, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvSeasonalPicks = view.findViewById(R.id.rvSeasonalPicks)
        rvCategories = view.findViewById(R.id.rvCategories)
        progressBar = view.findViewById(R.id.progressBarStore)

        seasonalAdapter = ProductAdapter(emptyList()) { openUrl(it.affiliateUrl) }
        categoryAdapter = StoreAdapter(emptyList(), sharedPool) { openUrl(it.affiliateUrl) }

        rvSeasonalPicks.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = seasonalAdapter
            setRecycledViewPool(sharedPool)
            setHasFixedSize(true)
        }

        rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
        }

        observeStore()
    }

    private fun observeStore() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.storeState.collect { state ->
                    when (state) {
                        is StoreState.Loading -> {
                            progressBar.visibility = View.VISIBLE
                            rvSeasonalPicks.visibility = View.GONE
                            rvCategories.visibility = View.GONE
                        }
                        is StoreState.Success -> {
                            progressBar.visibility = View.GONE
                            rvSeasonalPicks.visibility = View.VISIBLE
                            rvCategories.visibility = View.VISIBLE
                            bindData(state.seasonalPicks, state.categories)
                        }
                        is StoreState.Fallback -> {
                            progressBar.visibility = View.GONE
                            rvSeasonalPicks.visibility = View.VISIBLE
                            rvCategories.visibility = View.VISIBLE
                            bindData(state.seasonalPicks, state.categories)
                        }
                        is StoreState.Error -> {
                            progressBar.visibility = View.GONE
                            if (isAdded) {
                                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bindData(picks: List<StoreProduct>, categories: List<StoreCategory>) {
        seasonalAdapter.updateItems(picks)
        categoryAdapter.updateCategories(categories)
    }

    private fun openUrl(url: String) {
        if (url.isBlank() || url == "#") return
        val uri = Uri.parse(url.trim())
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            if (isAdded) {
                Toast.makeText(requireContext(), "No browser available", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            // Prevent crash on malformed URI or restricted intent
        }
    }
}