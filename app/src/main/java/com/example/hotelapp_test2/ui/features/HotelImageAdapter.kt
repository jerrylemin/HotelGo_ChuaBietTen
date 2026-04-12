package com.example.hotelapp_test2.ui.features

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.hotelapp_test2.R

class HotelImageAdapter : ListAdapter<String, HotelImageAdapter.ImageViewHolder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_catalog_image, parent, false) as ImageView
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ImageViewHolder(
        private val imageView: ImageView
    ) : RecyclerView.ViewHolder(imageView) {
        fun bind(imageUrl: String) {
            imageView.load(imageUrl.ifBlank { null }) {
                placeholder(R.mipmap.ic_launcher)
                error(R.mipmap.ic_launcher)
                crossfade(true)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        }
    }
}
