package com.deepgamer.agriexpert.store

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
import com.deepgamer.agriexpert.R
import kotlinx.coroutines.launch

class StoreFragment : Fragment() {

    private lateinit var rvSeasonalPicks: RecyclerView
    private lateinit var rvCategories: RecyclerView
    private lateinit var progressBar: ProgressBar

    private val viewModel: StoreViewModel by viewModels()

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

        rvSeasonalPicks.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvCategories.layoutManager = LinearLayoutManager(requireContext())

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
                            // Silent fallback — no error toast, user sees data normally
                        }
                        is StoreState.Error -> {
                            progressBar.visibility = View.GONE
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun bindData(picks: List<StoreProduct>, categories: List<StoreCategory>) {
        rvSeasonalPicks.adapter = ProductAdapter(picks) { product ->
            openUrl(product.affiliateUrl)
        }
        rvCategories.adapter = StoreAdapter(categories) { product ->
            openUrl(product.affiliateUrl)
        }
    }

    private fun openUrl(url: String) {
        if (url == "#" || url.isEmpty()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}