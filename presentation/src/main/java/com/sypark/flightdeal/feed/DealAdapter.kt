package com.sypark.flightdeal.feed

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sypark.flightdeal.databinding.ItemDealBinding
import com.sypark.flightdeal.domain.model.DealItem

/**
 * 테스트에서 직접 호출할 수 있도록 object로 분리한다.
 */
object DealDiffCallback : DiffUtil.ItemCallback<DealItem>() {

    override fun areItemsTheSame(oldItem: DealItem, newItem: DealItem): Boolean =
        oldItem.quote.route == newItem.quote.route &&
            oldItem.quote.departDate == newItem.quote.departDate

    override fun areContentsTheSame(oldItem: DealItem, newItem: DealItem): Boolean =
        oldItem == newItem
}

class DealAdapter(
    private val onClick: (DealItem) -> Unit,
) : ListAdapter<DealItem, DealAdapter.ViewHolder>(DealDiffCallback) {

    class ViewHolder(val binding: ItemDealBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemDealBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.item = item
        // Won은 인라인 값 클래스라 XML 표현식에서 접근자를 찾지 못한다.
        // DealBindingAdapters.kt 참고. 그래서 여기서 직접 호출한다.
        holder.binding.price.setWonPrice(item.quote.price)
        holder.binding.originalPrice.setStrikethroughPrice(item.originalPrice)
        holder.binding.root.setOnClickListener { onClick(item) }
        holder.binding.executePendingBindings()
    }
}
