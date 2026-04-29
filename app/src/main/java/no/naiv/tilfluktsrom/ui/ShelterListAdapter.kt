package no.naiv.tilfluktsrom.ui

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import no.naiv.tilfluktsrom.R
import no.naiv.tilfluktsrom.databinding.ItemShelterBinding
import no.naiv.tilfluktsrom.location.ShelterWithDistance
import no.naiv.tilfluktsrom.util.DistanceUtils

/**
 * One row in the bottom-sheet list. The list normally holds the N nearest
 * shelters to the user, but a deep-linked / explicitly-selected shelter that
 * is *not* among them is appended with isOutsideNearest=true so the user can
 * see what they picked. See Forgejo #13 / beads tilfluktsrom-9sf.
 */
data class ShelterListItem(
    val swd: ShelterWithDistance,
    val isOutsideNearest: Boolean
)

/**
 * Adapter for the list of nearest shelters shown in the bottom sheet.
 */
class ShelterListAdapter(
    private val onShelterSelected: (ShelterWithDistance) -> Unit
) : ListAdapter<ShelterListItem, ShelterListAdapter.ViewHolder>(DIFF_CALLBACK) {

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

        fun bind(item: ShelterListItem, isSelected: Boolean) {
            val ctx = binding.root.context
            val swd = item.swd
            binding.shelterAddress.text = swd.shelter.adresse
            binding.shelterDistance.text = DistanceUtils.formatDistance(swd.distanceMeters)
            binding.shelterCapacity.text = ctx.getString(
                R.string.shelter_capacity, swd.shelter.plasser
            )
            binding.shelterRoomNr.text = ctx.getString(
                R.string.shelter_room_nr, swd.shelter.romnr
            )

            binding.outsideNearestBadge.visibility =
                if (item.isOutsideNearest) View.VISIBLE else View.GONE

            // Build accessible description; suffix the badge text so screen-
            // reader users learn the same context that sighted users see.
            val baseDesc = ctx.getString(
                R.string.content_desc_shelter_item,
                swd.shelter.adresse,
                DistanceUtils.formatDistance(swd.distanceMeters),
                swd.shelter.plasser
            )
            binding.root.contentDescription = if (item.isOutsideNearest) {
                ctx.getString(R.string.shelter_outside_nearest_badge) + ". " + baseDesc
            } else {
                baseDesc
            }

            binding.root.isSelected = isSelected
            binding.root.alpha = if (isSelected) 1.0f else 0.7f

            binding.root.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    selectPosition(pos)
                    onShelterSelected(getItem(pos).swd)
                }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ShelterListItem>() {
            override fun areItemsTheSame(a: ShelterListItem, b: ShelterListItem) =
                a.swd.shelter.lokalId == b.swd.shelter.lokalId

            override fun areContentsTheSame(a: ShelterListItem, b: ShelterListItem) =
                a == b
        }
    }
}
