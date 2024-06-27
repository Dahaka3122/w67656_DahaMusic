package com.example.dahamusic.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dahamusic.interfaces.OnFolderListener
import com.example.dahamusic.databinding.FolderItemViewBinding
import com.example.dahamusic.room.RoomFolderModel
import java.io.Serializable


class FolderAdapter(val listener: OnFolderListener,var folders : List<RoomFolderModel>): RecyclerView.Adapter<FolderAdapter.ViewHolderHomeFragment>() ,Serializable{

    inner class ViewHolderHomeFragment(private  var binding: FolderItemViewBinding): RecyclerView.ViewHolder(binding.root){
        fun onBind(model: RoomFolderModel){
            binding.folderName.text = model.folderName

            binding.folderSongs.text = "${model.audioList?.size} tracks"
            binding.cardMenu.elevation = 0F

            binding.root.setOnClickListener {
                listener.onFolderClick(model)
            }
            if (model.folderName == "Your musics"||model.folderName=="Favorites"){
                binding.cardMenu.visibility = View.GONE
            }

            binding.cardMenu.setOnClickListener {
                listener.onFolderItemClick(binding.cardMenu,model,adapterPosition)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderHomeFragment {
        return  ViewHolderHomeFragment(
                FolderItemViewBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        )
    }

    override fun getItemCount(): Int = folders.size

    override fun onBindViewHolder(holder: ViewHolderHomeFragment, position: Int) {
        holder.onBind(folders[position])
    }
}