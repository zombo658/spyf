package com.spyf.geowalker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WaypointAdapter(
    private val items: MutableList<Waypoint>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<WaypointAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.wpTitle)
        val subtitle: TextView = v.findViewById(R.id.wpSubtitle)
        val delete: View = v.findViewById(R.id.wpDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_waypoint, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val w = items[position]
        holder.title.text = "${position + 1}. ${w.name}"
        holder.subtitle.text = "в %.1f мин · стоянка %d с · %.5f, %.5f"
            .format(w.atMinute, w.dwellSec, w.lat, w.lon)
        holder.delete.setOnClickListener {
            val idx = holder.bindingAdapterPosition
            if (idx != RecyclerView.NO_POSITION) onDelete(idx)
        }
    }
}
