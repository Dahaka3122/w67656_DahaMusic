package com.example.dahamusic.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.lifecycle.ViewModelProvider
import com.example.dahamusic.R
import com.example.dahamusic.adapter.AdapterForFolderSelection
import com.example.dahamusic.databinding.ActivityFolderSelectionBinding
import com.example.dahamusic.interfaces.OnFolderForSelection
import com.example.dahamusic.room.RoomAudioModel
import com.example.dahamusic.room.RoomDbHelper
import com.example.dahamusic.room.RoomFolderModel
import com.example.dahamusic.viewmodel.MediaViewModel
import java.io.Serializable

class FolderSelectionActivity : AppCompatActivity(), OnFolderForSelection,Serializable {

    private lateinit var binding:ActivityFolderSelectionBinding
    private lateinit var dbHelper: RoomDbHelper
    private lateinit var adapter :AdapterForFolderSelection
    private lateinit var data : List<RoomAudioModel>
    private lateinit var viewModel :MediaViewModel

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFolderSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        viewModel = ViewModelProvider(this).get(MediaViewModel::class.java)

        dbHelper = RoomDbHelper.DatabaseBuilder.getInstance(this)
        viewModel.folders.observe(this){
            adapter = AdapterForFolderSelection(this,it)
        }
        data = intent.getSerializableExtra("data") as List<RoomAudioModel>

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.statusBarColor = getColor(R.color.main_light)
            window.navigationBarColor = getColor(R.color.white)
        }

        binding.btnArrow.elevation = 0F
        binding.addFolder.elevation = 0F

        binding.btnArrow.setOnClickListener {
            onBackPressed()
        }

        binding.addFolder.setOnClickListener {
            val dialog = AlertDialog.Builder(this).create()
            val dialogView = layoutInflater.inflate(R.layout.adding_folder_dialog_new, binding.root, false)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.setView(dialogView)

            val ok = dialogView.findViewById<CardView>(R.id.yes)
            ok.elevation = 0F
            val no = dialogView.findViewById<CardView>(R.id.no)
            no.elevation = 0F
            val et = dialogView.findViewById<EditText>(R.id.textInputEditText)
            et.requestFocus()
            et.isFocusableInTouchMode = true
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            dialog.show()

            no.setOnClickListener {
                dialog.dismiss()
            }
            ok.setOnClickListener {
                val folderName = et.text.toString()
                if (et.text.isNullOrEmpty()){
                    et.error = "Fill field"
                }else{
                    val newRoomFolder = RoomFolderModel(folderName = folderName,audioList = listOf<RoomAudioModel>())
                    insertFolderToDatabase(newRoomFolder)
                    adapter.notifyItemInserted(viewModel.getFoldersCount()-1)
                    dialog.dismiss()
                }
            }
        }
        viewModel.folders.observe(this){
            setAdapter(it)
        }

    }
    private fun insertFolderToDatabase(roomFolderModel: RoomFolderModel){
        viewModel.insertFolder(roomFolderModel)
    }

    private  fun setAdapter(folders:List<RoomFolderModel>){
        adapter.folders = folders
        binding.recyclerView.adapter=adapter
    }

    override fun onFolderForSelectionClick(model: RoomFolderModel, position: Int) {
        viewModel.getFolder(model.folderName).observe(this){
            val wholeData  = ArrayList<RoomAudioModel>()
            val nonDuplicatedMusic = ArrayList<RoomAudioModel>()

            for (i in data.indices){
                if (!it.audioList.contains(data[i])){
                    nonDuplicatedMusic.add(data[i])
                }else{
                    Toast.makeText(this, "found   ", Toast.LENGTH_SHORT).show()
                }
            }
            wholeData.addAll(it.audioList)
            wholeData.addAll(nonDuplicatedMusic)
            it.audioList = wholeData
            viewModel.updateFolder(it)
            onBackPressed()
        }
    }
}