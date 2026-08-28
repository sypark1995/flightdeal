package com.sypark.flightdeal.feed

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sypark.flightdeal.R
import com.sypark.flightdeal.databinding.FragmentDealFeedBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DealFeedFragment : Fragment(R.layout.fragment_deal_feed) {

    private val viewModel: DealFeedViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDealFeedBinding.bind(view)

        val adapter = DealAdapter { /* 딥링크 연결은 이후 계획서에서 */ }
        binding.dealList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state is DealFeedUiState.Success) adapter.submitList(state.deals)
                }
            }
        }
    }
}
