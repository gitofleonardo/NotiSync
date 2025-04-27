package io.github.gitofleonardo.notisync.ui.filterapps

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.gitofleonardo.notisync.databinding.LayoutFilteredAppItemBinding

class FilteredAppHolder(
    val binding: LayoutFilteredAppItemBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(
        item: AppItem,
        onSwitchListener: ((AppItem, Boolean) -> Unit)?
    ) {
        binding.appName.text = item.appName
        binding.appPackage.text = item.packageName
        binding.appIcon.setImageDrawable(item.appIcon)

        binding.appSwitch.isChecked = item.filteredItem == null
        binding.appSwitch.jumpDrawablesToCurrentState()
        binding.appSwitch.setOnCheckedChangeListener { _, checked ->
            onSwitchListener?.invoke(item, checked)
        }

        binding.root.setOnClickListener {
            binding.appSwitch.isChecked = !binding.appSwitch.isChecked
        }
    }

    fun unbind() {
        binding.appSwitch.setOnCheckedChangeListener(null)
    }
}

class FilteredAppsAdapter(val allApps: List<AppItem>) : RecyclerView.Adapter<FilteredAppHolder>() {

    private var switchListener: ((AppItem, Boolean) -> Unit)? = null

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): FilteredAppHolder {
        val binding =
            LayoutFilteredAppItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FilteredAppHolder(binding)
    }

    override fun onBindViewHolder(
        holder: FilteredAppHolder,
        position: Int
    ) {
        holder.bind(allApps[position], switchListener)
    }

    override fun getItemCount(): Int = allApps.size

    override fun onViewRecycled(holder: FilteredAppHolder) {
        super.onViewRecycled(holder)
        holder.unbind()
    }

    fun setOnSwitchListener(listener: (AppItem, Boolean) -> Unit) {
        switchListener = listener
    }
}