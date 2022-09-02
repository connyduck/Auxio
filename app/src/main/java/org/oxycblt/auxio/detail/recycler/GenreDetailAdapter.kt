/*
 * Copyright (c) 2021 Auxio Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.detail.recycler

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.oxycblt.auxio.IntegerTable
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.ItemDetailBinding
import org.oxycblt.auxio.music.Genre
import org.oxycblt.auxio.music.Song
import org.oxycblt.auxio.ui.recycler.Item
import org.oxycblt.auxio.ui.recycler.SimpleItemCallback
import org.oxycblt.auxio.ui.recycler.SongViewHolder
import org.oxycblt.auxio.util.context
import org.oxycblt.auxio.util.formatDurationMs
import org.oxycblt.auxio.util.getPlural
import org.oxycblt.auxio.util.inflater

/**
 * An adapter for displaying genre information and it's children.
 * @author OxygenCobalt
 */
class GenreDetailAdapter(private val listener: Listener) :
    DetailAdapter<DetailAdapter.Listener>(listener, DIFFER) {
    private var currentSong: Song? = null

    override fun getItemViewType(position: Int) =
        when (differ.currentList[position]) {
            is Genre -> GenreDetailViewHolder.VIEW_TYPE
            is Song -> SongViewHolder.VIEW_TYPE
            else -> super.getItemViewType(position)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        when (viewType) {
            GenreDetailViewHolder.VIEW_TYPE -> GenreDetailViewHolder.new(parent)
            SongViewHolder.VIEW_TYPE -> SongViewHolder.new(parent)
            else -> super.onCreateViewHolder(parent, viewType)
        }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payload: List<Any>
    ) {
        if (payload.isEmpty()) {
            when (val item = differ.currentList[position]) {
                is Genre -> (holder as GenreDetailViewHolder).bind(item, listener)
                is Song -> (holder as SongViewHolder).bind(item, listener)
            }
        }

        super.onBindViewHolder(holder, position, payload)
    }

    override fun shouldHighlightViewHolder(item: Item) = item is Song && item.id == currentSong?.id

    /** Update the [song] that this adapter should highlight */
    fun highlightSong(song: Song?) {
        if (song == currentSong) return
        highlightImpl(currentSong, song)
        currentSong = song
    }

    companion object {
        val DIFFER =
            object : SimpleItemCallback<Item>() {
                override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
                    return when {
                        oldItem is Genre && newItem is Genre ->
                            GenreDetailViewHolder.DIFFER.areItemsTheSame(oldItem, newItem)
                        oldItem is Song && newItem is Song ->
                            SongViewHolder.DIFFER.areItemsTheSame(oldItem, newItem)
                        else -> DetailAdapter.DIFFER.areContentsTheSame(oldItem, newItem)
                    }
                }
            }
    }
}

private class GenreDetailViewHolder private constructor(private val binding: ItemDetailBinding) :
    RecyclerView.ViewHolder(binding.root) {
    fun bind(item: Genre, listener: DetailAdapter.Listener) {
        binding.detailCover.bind(item)
        binding.detailType.text = binding.context.getString(R.string.lbl_genre)
        binding.detailName.text = item.resolveName(binding.context)
        binding.detailSubhead.text =
            binding.context.getPlural(R.plurals.fmt_song_count, item.songs.size)
        binding.detailInfo.text = item.durationMs.formatDurationMs(false)
        binding.detailPlayButton.setOnClickListener { listener.onPlayParent() }
        binding.detailShuffleButton.setOnClickListener { listener.onShuffleParent() }
    }

    companion object {
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_GENRE_DETAIL

        fun new(parent: View) =
            GenreDetailViewHolder(ItemDetailBinding.inflate(parent.context.inflater))

        val DIFFER =
            object : SimpleItemCallback<Genre>() {
                override fun areItemsTheSame(oldItem: Genre, newItem: Genre) =
                    oldItem.rawName == newItem.rawName &&
                        oldItem.songs.size == newItem.songs.size &&
                        oldItem.durationMs == newItem.durationMs
            }
    }
}
