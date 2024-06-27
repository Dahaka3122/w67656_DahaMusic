package com.example.dahamusic.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.dahamusic.R
import com.example.dahamusic.adapter.FolderAdapter
import com.example.dahamusic.databinding.ActivityMainBinding
import com.example.dahamusic.interfaces.OnFolderListener
import com.example.dahamusic.room.RoomAudioModel
import com.example.dahamusic.room.RoomFolderModel
import com.example.dahamusic.viewmodel.MediaViewModel
import com.github.zawadz88.materialpopupmenu.popupMenu
import java.io.Serializable
import java.util.*

class MainActivity : AppCompatActivity(), OnFolderListener,Serializable {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FolderAdapter
    private val STORAGE_PERMISSION_CODE = 1
    private lateinit var viewModel:MediaViewModel

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("Recycle")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        viewModel = ViewModelProvider(this).get(MediaViewModel::class.java)
        binding.cardMenu.elevation = 0F

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or  View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.statusBarColor = getColor(R.color.main_light)
            window.navigationBarColor = getColor(R.color.white)
        }

        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
        }else{
            viewModel.folders.observe(this){
                setAdapter(it)
            }
        }

        binding.cardMenu.setOnClickListener {
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
                    val newRoomFolder = RoomFolderModel(folderName = folderName,audioList = emptyList() )
                    insertFolderToDatabase(newRoomFolder)
                    adapter.notifyItemInserted(viewModel.getFoldersCount()-1)
                    dialog.dismiss()
                }
            }
        }
    }

    private fun insertMusicsToDatabase(musics: List<RoomAudioModel>){
        viewModel.insertMusics(musics)
    }

    private fun insertFolderToDatabase(roomFolderModel: RoomFolderModel){
        viewModel.insertFolder(roomFolderModel)
    }

    private  fun setAdapter(folders: List<RoomFolderModel>){
        adapter = FolderAdapter(this,folders)
        adapter.folders = folders
        binding.recyclerView.adapter=adapter
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    val contentResolver = this.contentResolver
                    val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
                    val musics = mutableListOf<RoomAudioModel>()

                    when{
                        cursor == null -> {
                            Toast.makeText(this, "Cannot read music !", Toast.LENGTH_SHORT).show()

                        }
                        !cursor.moveToFirst() -> {
                            Toast.makeText(this, "No music found on this phone", Toast.LENGTH_SHORT).show()

                        }
                        else ->{
                            Toast.makeText(this, "adding started", Toast.LENGTH_SHORT).show()
                            do {
                                val title: String = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                                val artist: String = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))
                                val url: String = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                                val roomAudio= RoomAudioModel(audioTitle = title,audioDuration = "null",audioArtist = artist,audioUri = url,isFavorite = 0,isSelected = false)
                                musics.add(roomAudio)
                                Log.d("INSERTING",roomAudio.audioTitle)
                                
                            } while (cursor.moveToNext())
                            insertMusicsToDatabase(musics)
                                Log.d("INSERTING_FOLDER","1")
                                insertFolderToDatabase(RoomFolderModel(folderName = "Your musics",audioList = musics))
                                insertFolderToDatabase(RoomFolderModel(folderName = "Favorites",audioList = listOf()))
                            cursor.close()
                        }
                    }
                viewModel.folders.observe(this){
                    Log.d("INSERTING_FOLDER","2")
                    setAdapter(it)
                }
                Toast.makeText(this@MainActivity, "Storage Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Storage Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onFolderItemClick(view:View,folder:RoomFolderModel,position: Int) {
        val popupMenu = popupMenu {
            style = R.style.Widget_MPM_Menu_Dark_CustomBackground
            section {
                item {
                    label = "Rename"
                    labelColor = ContextCompat.getColor(this@MainActivity, R.color.folderActivity)
                    icon = R.drawable.ic_edit__2_
                    iconColor = ContextCompat.getColor(this@MainActivity, R.color.folderActivity)
                    callback = {
                        val dialog = AlertDialog.Builder(this@MainActivity).create()
                        val dialogView = layoutInflater.inflate(R.layout.change_folder_dialog, binding.root, false)
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
                            }
                            if (viewModel.checkForExist(folderName)){
                                et.error = "Folder name exists !"
                            }
                            else{
                                viewModel.setNewFolderName(folderName,folder.folderName)
                                folder.folderName = folderName
                                adapter.notifyDataSetChanged()
                                dialog.dismiss()
                            }
                        }
                    }
                }
                item {
                    labelRes = R.string.remove
                    labelColor = ContextCompat.getColor(this@MainActivity, R.color.folderActivity)
                    iconDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_trash) //optional
                    iconColor =ContextCompat.getColor(this@MainActivity, R.color.folderActivity)
                    callback = {

                        viewModel.deleteFolder(folder)
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
        popupMenu.show(this, view)
    }

    override fun onFolderClick(folder: RoomFolderModel) {
            val intent = Intent(this, FolderActivity::class.java)
            intent.putExtra("folderName",folder.folderName)
            startActivity(intent)
    }
}