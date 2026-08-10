package com.bitoneko.gallery

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.AbsListView
import android.widget.GridView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

data class MediaItem(
    val path: String,
    val name: String,
    val date: Long,
    val mime: String,
    val size: Long,
    val isVideo: Boolean
)

class GalleryGridAdapter(
    private val context: Context,
    private val mediaList: ArrayList<com.bitoneko.gallery.MediaItem>
) : BaseAdapter() {

    override fun getCount(): Int = mediaList.size
    override fun getItem(position: Int): Any = mediaList[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val holder: ViewHolder

        if (parent is GridView) {
            parent.post {
                try {
                    parent.numColumns = 3
                    parent.horizontalSpacing = 0
                    parent.verticalSpacing = 0
                    parent.setStretchMode(GridView.STRETCH_COLUMN_WIDTH)
                    parent.setPadding(0, 0, 0, 0)
                } catch (e: Exception) {}
            }
        }

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(
                context.resources.getIdentifier("gallery_item", "layout", context.packageName),
                parent,
                false
            )
            holder = ViewHolder()
            holder.thumbnail = view.findViewById(context.resources.getIdentifier("img_thumbnail", "id", context.packageName))
            holder.videoIcon = view.findViewById(context.resources.getIdentifier("img_video_icon", "id", context.packageName))
            view.tag = holder
        } else {
            view = convertView
            holder = convertView.tag as ViewHolder
        }

        try {
            val displayMetrics = context.resources.displayMetrics
            val size = displayMetrics.widthPixels / 3
            view.layoutParams = AbsListView.LayoutParams(size, size)
        } catch (e: Exception) {}

        val item = mediaList[position]

        val txtName = view.findViewById<TextView>(context.resources.getIdentifier("txt_name", "id", context.packageName))
        txtName?.visibility = View.GONE

        if (holder.videoIcon != null) {
            if (item.isVideo) {
                holder.videoIcon?.visibility = View.VISIBLE
                try {
                    val params = RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
                    holder.videoIcon?.layoutParams = params
                } catch (e: Exception) {}
            } else {
                holder.videoIcon?.visibility = View.GONE
            }
        }

        if (holder.thumbnail != null) {
            Glide.clear(holder.thumbnail)
            holder.thumbnail?.setImageResource(android.R.drawable.ic_menu_gallery)

            try {
                Glide.with(context.applicationContext)
                    .load(item.path)
                    .asBitmap()
                    .override(220, 220)
                    .centerCrop()
                    .dontAnimate()
                    .diskCacheStrategy(DiskCacheStrategy.RESULT)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(holder.thumbnail!!)
            } catch (e: Exception) {}
        }

        return view
    }

    private class ViewHolder {
        var thumbnail: ImageView? = null
        var videoIcon: ImageView? = null
    }
}
