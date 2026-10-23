package com.example.dahamusic.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.dahamusic.R
import com.example.dahamusic.adapter.FolderAdapter
import com.example.dahamusic.databinding.ActivityMainBinding
import com.example.dahamusic.interfaces.OnFolderListener
import com.example.dahamusic.room.RoomFolderModel
import com.example.dahamusic.viewmodel.MediaViewModel
import java.io.Serializable
import java.util.*
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import com.example.dahamusic.library.LibraryMerger

class MainActivity : AppCompatActivity(), OnFolderListener, Serializable {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FolderAdapter
    private val STORAGE_PERMISSION_CODE = 1
    private lateinit var viewModel: MediaViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        viewModel = ViewModelProvider(this).get(MediaViewModel::class.java)
        binding.cardMenu.elevation = 0F
        binding.musicalNote.apply {
            contentDescription = getString(R.string.open_saved_player)
            isFocusable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, MusicActivity::class.java)) }
        }
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.musicalNote, getString(R.string.open_saved_player))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.statusBarColor = getColor(R.color.main_light)
            window.navigationBarColor = getColor(R.color.white)
        }

        viewModel.folders.observe(this) { setAdapter(it) }
        viewModel.refreshState.observe(this) { state ->
            binding.refreshLibrary.isEnabled = !state.isRefreshing
            binding.refreshLibrary.setText(if (state.isRefreshing) R.string.refreshing_library else R.string.refresh_library)
            if (state.failed) Toast.makeText(this, R.string.library_refresh_failed, Toast.LENGTH_LONG).show()
        }
        binding.refreshLibrary.setOnClickListener {
            if (hasAudioPermission()) viewModel.refreshLibrary() else requestAudioPermission()
        }
        if (!hasAudioPermission()) requestAudioPermission()

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
                val folderName = et.text.toString().trim()
                if (et.text.isNullOrEmpty()) {
                    et.error = "Fill field"
                } else if (folderName in listOf(LibraryMerger.ALL_MUSIC, LibraryMerger.FAVORITES)) {
                    et.error = getString(R.string.reserved_folder_name)
                } else {
                    val newRoomFolder = RoomFolderModel(folderName = folderName, audioList = emptyList())
                    insertFolderToDatabase(newRoomFolder)
                    dialog.dismiss()
                }
            }
        }
    }

    private fun insertFolderToDatabase(roomFolderModel: RoomFolderModel) {
        viewModel.insertFolder(roomFolderModel)
    }

    private fun setAdapter(folders: List<RoomFolderModel>) {
        adapter = FolderAdapter(this, folders)
        adapter.folders = folders
        binding.recyclerView.adapter = adapter
    }

    private val observerHandler = Handler(Looper.getMainLooper())
    private val refreshAfterChange = Runnable { if (hasAudioPermission()) viewModel.refreshLibrary() }
    private var observingMedia = false
    private val mediaObserver = object : ContentObserver(observerHandler) {
        override fun onChange(selfChange: Boolean) {
            observerHandler.removeCallbacks(refreshAfterChange)
            observerHandler.postDelayed(refreshAfterChange, 600)
        }
    }

    private fun audioPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasAudioPermission() = ContextCompat.checkSelfPermission(this, audioPermission()) ==
        PackageManager.PERMISSION_GRANTED

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(audioPermission()), STORAGE_PERMISSION_CODE)
    }

    private fun startLibraryRefresh() {
        if (!hasAudioPermission()) return
        if (!observingMedia) {
            try {
                contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
                observingMedia = true
            } catch (denied: SecurityException) {
                Toast.makeText(this, R.string.library_permission_required, Toast.LENGTH_LONG).show()
                return
            }
        }
        viewModel.refreshLibrary()
    }

    override fun onStart() {
        super.onStart()
        startLibraryRefresh()
    }

    override fun onStop() {
        observerHandler.removeCallbacks(refreshAfterChange)
        if (observingMedia) {
            contentResolver.unregisterContentObserver(mediaObserver)
            observingMedia = false
        }
        super.onStop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (hasAudioPermission()) startLibraryRefresh()
            else Toast.makeText(this, R.string.library_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onFolderItemClick(view: View, folder: RoomFolderModel, position: Int) {
        if (folder.folderName in listOf(LibraryMerger.ALL_MUSIC, LibraryMerger.FAVORITES)) return
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