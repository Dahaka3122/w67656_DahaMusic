package com.example.dahamusic.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dahamusic.interfaces.OnFolderForSelection
import com.example.dahamusic.databinding.FolderItemViewBinding
import com.example.dahamusic.room.RoomFolderModel
import java.io.Serializable

class AdapterForFolderSelection(val listener: OnFolderForSelection,var folders:List<RoomFolderModel>): RecyclerView.Adapter<AdapterForFolderSelection.ViewHolderHomeFragment>() , Serializable {

    inner class ViewHolderHomeFragment(private  var binding: FolderItemViewBinding): RecyclerView.ViewHolder(binding.root){
        fun onBind(model: RoomFolderModel,position: Int){
            binding.folderName.text = model.folderName

            binding.folderSongs.text = "${model.audioList?.size} tracks"
            binding.cardMenu.visibility = View.GONE

            binding.root.setOnClickListener {
                listener.onFolderForSelectionClick(model,position)
            }
            if (model.folderName == "Your musics"||model.folderName=="Favorites"){
                binding.cardMenu.visibility = View.GONE
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
        holder.onBind(folders[position],position)
    }

}