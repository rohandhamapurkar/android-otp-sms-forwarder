package com.example.otpforwarder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch

class ConfigAdapter(
    private val onEdit: (ForwardConfig) -> Unit,
    private val onDelete: (ForwardConfig) -> Unit,
    private val onToggle: (ForwardConfig, Boolean) -> Unit
) : ListAdapter<ForwardConfig, ConfigAdapter.ConfigViewHolder>(ConfigDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_config, parent, false)
        return ConfigViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConfigViewHolder, position: Int) {
        val config = getItem(position)
        holder.bind(config)
    }

    inner class ConfigViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textForwardTo: TextView = itemView.findViewById(R.id.textForwardTo)
        private val textFilters: TextView = itemView.findViewById(R.id.textFilters)
        private val textPrefix: TextView = itemView.findViewById(R.id.textPrefix)
        private val switchEnabled: MaterialSwitch = itemView.findViewById(R.id.switchEnabled)
        private val btnEdit: Button = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: Button = itemView.findViewById(R.id.btnDelete)

        fun bind(config: ForwardConfig) {
            textForwardTo.text = config.forwardTo
            textFilters.text = "Filters: ${config.filterKeywords.joinToString(", ")}"
            textPrefix.text = "Prefix: ${config.messagePrefix}"
            
            // Remove listener before setting state to avoid infinite loop
            switchEnabled.setOnCheckedChangeListener(null)
            switchEnabled.isChecked = config.isEnabled
            switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(config, isChecked)
            }

            btnEdit.setOnClickListener { onEdit(config) }
            btnDelete.setOnClickListener { onDelete(config) }
        }
    }

    class ConfigDiffCallback : DiffUtil.ItemCallback<ForwardConfig>() {
        override fun areItemsTheSame(oldItem: ForwardConfig, newItem: ForwardConfig): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ForwardConfig, newItem: ForwardConfig): Boolean {
            return oldItem == newItem
        }
    }
}
