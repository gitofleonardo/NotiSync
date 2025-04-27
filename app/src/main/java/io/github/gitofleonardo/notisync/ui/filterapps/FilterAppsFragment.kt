package io.github.gitofleonardo.notisync.ui.filterapps

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import io.github.gitofleonardo.notisync.databinding.FragmentFilterAppsBinding

class FilterAppsFragment : Fragment() {

    private val viewModel by viewModels<FilteredAppsViewModel>()

    private var _binding: FragmentFilterAppsBinding? = null
    private val binding: FragmentFilterAppsBinding
        get() = _binding!!

    private val apps = ArrayList<AppItem>()
    private val adapter = FilteredAppsAdapter(apps)

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.appItemsState.observe(this) {
            when (it) {
                is AppItemsState.LoadingState -> {
                    binding.appsSrl.isRefreshing = true
                }

                is AppItemsState.AllAppsState -> {
                    binding.appsSrl.isRefreshing = false
                    apps.clear()
                    apps.addAll(it.allApps)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFilterAppsBinding.inflate(inflater, container, false)
        binding.appsSrl.setOnRefreshListener {
            viewModel.loadAllApps()
        }
        binding.appsRv.adapter = adapter
        adapter.setOnSwitchListener { item, checked ->
            viewModel.setAppFiltered(item, !checked)
        }
        viewModel.loadAllApps()
        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}