package com.example.dahamusic.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentUris
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
import java.io.Serializable
import java.util.*

class MainActivity : AppCompatActivity(), OnFolderListener, Serializable {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FolderAdapter
    private val STORAGE_PERMISSION_CODE = 1
    private lateinit var viewModel: MediaViewModel

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
            window.statusBarColor = getColor(R.color.main_light)
            window.navigationBarColor = getColor(R.color.white)
        }

        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this@MainActivity, audioPermission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(audioPermission),
                STORAGE_PERMISSION_CODE
            )
        } else {
            viewModel.folders.observe(this) {
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
                if (et.text.isNullOrEmpty()) {
                    et.error = "Fill field"
                } else {
                    val newRoomFolder = RoomFolderModel(folderName = folderName, audioList = emptyList())
                    insertFolderToDatabase(newRoomFolder)
                    dialog.dismiss()
                }
            }
        }
    }

    private fun insertMusicsToDatabase(musics: List<RoomAudioModel>) {
        viewModel.insertMusics(musics)
    }

    private fun insertFolderToDatabase(roomFolderModel: RoomFolderModel) {
        viewModel.insertFolder(roomFolderModel)
    }

    private fun setAdapter(folders: List<RoomFolderModel>) {
        adapter = FolderAdapter(this, folders)
        adapter.folders = folders
        binding.recyclerView.adapter = adapter
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                val contentResolver = this.contentResolver
                val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
                val musics = mutableListOf<RoomAudioModel>()

                when {
                    cursor == null -> {
                        Toast.makeText(this, "Cannot read music !", Toast.LENGTH_SHORT).show()
                    }
                    !cursor.moveToFirst() -> {
                        Toast.makeText(this, "No music found on this phone", Toast.LENGTH_SHORT).show()

                        Log.d("INSERTING_FOLDER", "Creating playlists...")
                        insertFolderToDatabase(RoomFolderModel(folderName = "Your musics", audioList = emptyList()))
                        insertFolderToDatabase(RoomFolderModel(folderName = "Favorites", audioList = emptyList()))
                    }
                    else -> {
                        Toast.makeText(this, "adding started", Toast.LENGTH_SHORT).show()
                        do {
                            val title: String = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                            val artist: String = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))

                            // --- NOWY SPOSÓB POBIERANIA ŚCIEŻKI (SCOPED STORAGE) ---
                            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                            val id = cursor.getLong(idColumn)
                            val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                            val url: String = contentUri.toString()
                            // --------------------------------------------------------

                            val roomAudio = RoomAudioModel(
                                audioTitle = title,
                                audioDuration = "null",
                                audioArtist = artist,
                                audioUri = url,
                                isFavorite = 0,
                                isSelected = false
                            )
                            musics.add(roomAudio)
                            Log.d("INSERTING", roomAudio.audioTitle)

                        } while (cursor.moveToNext())

                        insertMusicsToDatabase(musics)
                        Log.d("INSERTING_FOLDER", "1")
                        insertFolderToDatabase(RoomFolderModel(folderName = "Your musics", audioList = musics))
                        insertFolderToDatabase(RoomFolderModel(folderName = "Favorites", audioList = emptyList()))
                    }
                }
                cursor?.close()
                viewModel.folders.observe(this) {
                    Log.d("INSERTING_FOLDER", "2")
                    setAdapter(it)
                }
                Toast.makeText(this@MainActivity, "Storage Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Storage Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onFolderItemClick(view: View, folder: RoomFolderModel, position: Int) {
        val popupMenu = androidx.appcompat.widget.PopupMenu(this@MainActivity, view)

        try {
            val fieldMPopup = androidx.appcompat.widget.PopupMenu::class.java.getDeclaredField("mPopup")
            fieldMPopup.isAccessible = true
            val mPopup = fieldMPopup.get(popupMenu)
            mPopup.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                .invoke(mPopup, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val color = ContextCompat.getColor(this@MainActivity, R.color.folderActivity)

        val title1 = android.text.SpannableString("Rename")
        title1.setSpan(android.text.style.ForegroundColorSpan(color), 0, title1.length, 0)
        val item1 = popupMenu.menu.add(android.view.Menu.NONE, 1, android.view.Menu.NONE, title1)
        item1.setIcon(R.drawable.ic_edit__2_)
        item1.icon?.setTint(color)

        val title2 = android.text.SpannableString(getString(R.string.remove))
        title2.setSpan(android.text.style.ForegroundColorSpan(color), 0, title2.length, 0)
        val item2 = popupMenu.menu.add(android.view.Menu.NONE, 2, android.view.Menu.NONE, title2)
        item2.setIcon(R.drawable.ic_trash)
        item2.icon?.setTint(color)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
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
                        val newFolderName = et.text.toString()
                        if (et.text.isNullOrEmpty()) {
                            et.error = "Fill field"
                        } else if (viewModel.checkForExist(newFolderName)) {
                            et.error = "Folder name exists !"
                        } else {
                            viewModel.setNewFolderName(newFolderName, folder.folderName)
                            folder.folderName = newFolderName
                            adapter.notifyDataSetChanged()
                            dialog.dismiss()
                        }
                    }
                    true
                }
                2 -> {
                    viewModel.deleteFolder(folder)
                    adapter.notifyDataSetChanged()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    override fun onFolderClick(folder: RoomFolderModel) {
        val intent = Intent(this, FolderActivity::class.java)
        intent.putExtra("folderName", folder.folderName)
        startActivity(intent)
    }
}