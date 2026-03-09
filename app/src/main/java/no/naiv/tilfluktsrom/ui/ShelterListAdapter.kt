package no.naiv.tilfluktsrom.ui

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import no.naiv.tilfluktsrom.R
import no.naiv.tilfluktsrom.databinding.ItemShelterBinding
import no.naiv.tilfluktsrom.location.ShelterWithDistance
import no.naiv.tilfluktsrom.util.DistanceUtils

/**
 * Adapter for the list of nearest shelters shown in the bottom sheet.
 */
class ShelterListAdapter(
    private val onShelterSelected: (ShelterWithDistance) -> Unit
) : ListAdapter<ShelterWithDistance, ShelterListAdapter.ViewHolder>(DIFF_CALLBACK) {

    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShelterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position == selectedPosition)
    }

    fun selectPosition(position: Int) {
        val old = selectedPosition
        selectedPosition = position
        notifyItemChanged(old)
        notifyItemChanged(position)
    }

    inner class ViewHolder(
        private val binding: ItemShelterBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ShelterWithDistance, isSelected: Boolean) {
            val ctx = binding.root.context
            binding.shelterAddress.text = item.shelter.adresse
            binding.shelterDistance.text = DistanceUtils.formatDistance(item.distanceMeters)
            binding.shelterCapacity.text = ctx.getString(
                R.string.shelter_capacity, item.shelter.plasser
            )
            binding.shelterRoomNr.text = ctx.getString(
                R.string.shelter_room_nr, item.shelter.romnr
            )

            binding.root.contentDescription = ctx.getString(
                R.string.content_desc_shelter_item,
                item.shelter.adresse,
                DistanceUtils.formatDistance(item.distanceMeters),
                item.shelter.plasser
            )

            binding.root.isSelected = isSelected
            binding.root.alpha = if (isSelected) 1.0f else 0.7f

            binding.root.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    selectPosition(pos)
                    onShelterSelected(getItem(pos))
                }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ShelterWithDistance>() {
            override fun areItemsTheSame(a: ShelterWithDistance, b: ShelterWithDistance) =
                a.shelter.lokalId == b.shelter.lokalId

            override fun areContentsTheSame(a: ShelterWithDistance, b: ShelterWithDistance) =
                a == b
        }
    }
}
