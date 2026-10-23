package com.example.dahamusic.ui

import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bumptech.glide.Glide
import com.example.dahamusic.R
import com.example.dahamusic.databinding.ActivityMusicNewBinding
import com.example.dahamusic.playback.PlaybackQueue
import com.example.dahamusic.playback.PlaybackService
import com.example.dahamusic.room.RoomAudioModel
import com.example.dahamusic.viewmodel.MediaViewModel
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.Serializable

@androidx.annotation.OptIn(UnstableApi::class)
class MusicActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMusicNewBinding
    private lateinit var viewModel: MediaViewModel
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var selectionJob: Job? = null
    private var pendingSelection = false
    private var started = false
    private var seeking = false
    private var favoriteSource: LiveData<RoomAudioModel?>? = null
    private var currentSong: RoomAudioModel? = null
    private var boundUri: String? = null
    private var favorite = false
    private var renderedUri: String? = null
    private var artwork: ByteArray? = null
    private var artworkUri: android.net.Uri? = null
    private val handler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            if (!started) return
            renderProgress()
            handler.postDelayed(this, 500)
        }
    }
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { render() }
        override fun onPlayerError(error: PlaybackException) {
            Log.e("Playback", "Unable to play local audio", error)
            Toast.makeText(this@MusicActivity, R.string.playback_failed, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMusicNewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        window.statusBarColor = getColor(R.color.musicActivity)
        window.navigationBarColor = getColor(R.color.musicActivity)
        viewModel = ViewModelProvider(this)[MediaViewModel::class.java]
        pendingSelection = savedInstanceState?.getBoolean("pendingSelection") ?: intent.hasExtra("folderName")
        binding.btnArrow.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnPlayPause.setOnClickListener {
            controller?.let { player ->
                if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) player.pause()
                else {
                    if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                    player.play()
                }
            }
        }
        binding.playNext.setOnClickListener { controller?.seekToNext() }
        binding.playPrevious.setOnClickListener { controller?.seekToPrevious() }
        binding.cardShuffle.setOnClickListener {
            controller?.let { player ->
                player.shuffleModeEnabled = !player.shuffleModeEnabled
                if (player.shuffleModeEnabled) player.repeatMode = Player.REPEAT_MODE_ALL
            }
        }
        binding.cardRepeat.setOnClickListener {
            controller?.let { player ->
                player.repeatMode = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_ONE
                if (player.repeatMode == Player.REPEAT_MODE_ONE) player.shuffleModeEnabled = false
            }
        }
        binding.cardBookmark.setOnClickListener {
            currentSong?.takeIf { it.audioUri == controller?.currentMediaItem?.mediaId }?.let { song ->
                favorite = !favorite
                renderFavorite()
                viewModel.setFavorite(if (favorite) 1 else 0, song.audioUri)
            }
        }
        binding.cardAddToList.setOnClickListener {
            currentSong?.takeIf { it.audioUri == controller?.currentMediaItem?.mediaId }?.let { song ->
                startActivity(Intent(this, FolderSelectionActivity::class.java)
                    .putExtra("data", arrayListOf(song) as Serializable))
            }
        }
        binding.tvSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(bar: SeekBar?) { seeking = true }
            override fun onStopTrackingTouch(bar: SeekBar?) {
                controller?.seekTo(bar?.progress?.toLong() ?: 0L)
                seeking = false
            }
        })
        setControlsEnabled(false)
    }

    override fun onStart() {
        super.onStart()
        started = true
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token)
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(mediaController: MediaController, extras: Bundle) {
                    if (started && controller === mediaController) selectWhenReady(mediaController)
                }
                override fun onDisconnected(mediaController: MediaController) {
                    if (started && controller === mediaController) {
                        controller = null
                        setControlsEnabled(false)
                        Toast.makeText(this@MusicActivity, R.string.playback_connection_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }).buildAsync()
        controllerFuture = future
        future.addListener({
            if (!started || controllerFuture !== future) return@addListener
            try {
                val connected = future.get()
                controller = connected
                connected.addListener(playerListener)
                render()
                selectWhenReady(connected)
            } catch (error: Exception) {
                Log.e("Playback", "Unable to connect controller", error)
                Toast.makeText(this, R.string.playback_connection_failed, Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
        handler.post(progressUpdater)
    }

    private fun selectWhenReady(player: MediaController) {
        if (pendingSelection && !player.sessionExtras.getBoolean(PlaybackService.EXTRA_RESTORING)) {
            loadSelection(player)
        }
    }

    private fun loadSelection(player: MediaController) {
        selectionJob?.cancel()
        selectionJob = lifecycleScope.launch {
            try {
                val folder = intent.getStringExtra("folderName") ?: return@launch
                val songs = viewModel.loadPlaybackQueue(folder)
                if (controller !== player || !started) return@launch
                val uri = intent.getStringExtra("audioUri") ?: songs.getOrNull(intent.getIntExtra("position", 0))?.audioUri
                pendingSelection = false
                if (uri == null || !PlaybackQueue.open(player, songs, uri)) {
                    Toast.makeText(this@MusicActivity, R.string.track_unavailable, Toast.LENGTH_LONG).show()
                }
                render()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("Playback", "Unable to load queue", error)
                pendingSelection = false
                Toast.makeText(this@MusicActivity, R.string.track_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selectionJob?.cancel()
        pendingSelection = intent.hasExtra("folderName")
        controller?.let { if (pendingSelection) selectWhenReady(it) else render() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pendingSelection", pendingSelection)
        super.onSaveInstanceState(outState)
    }

    private fun render() {
        val player = controller ?: return
        val item = player.currentMediaItem
        setControlsEnabled(item != null)
        binding.playNext.isEnabled = item != null && player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)
        binding.playPrevious.isEnabled = item != null && player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)
        binding.playPause.setImageResource(if (player.playWhenReady && player.playbackState != Player.STATE_ENDED)
            R.drawable.ic_pause else R.drawable.ic_play_button_arrowhead)
        val metadata = player.mediaMetadata
        binding.musicName.text = metadata.title ?: item?.mediaMetadata?.title ?: getString(R.string.no_current_track)
        binding.musicAuthor.text = metadata.artist ?: item?.mediaMetadata?.artist ?: ""
        if (renderedUri != item?.mediaId || artwork !== metadata.artworkData || artworkUri != metadata.artworkUri) {
            renderedUri = item?.mediaId
            artwork = metadata.artworkData
            artworkUri = metadata.artworkUri
            val localArt = metadata.artworkUri?.takeIf(PlaybackQueue::isLocal)
            Glide.with(this).load(artwork ?: localArt).placeholder(R.drawable.music_photo)
                .error(R.drawable.music_photo).into(binding.musicPhoto)
        }
        if (boundUri != item?.mediaId) {
            boundUri = item?.mediaId
            favoriteSource?.removeObservers(this)
            currentSong = null
            favorite = false
            favoriteSource = boundUri?.let(viewModel::observeMusic)
            favoriteSource?.observe(this) { song ->
                currentSong = song
                favorite = song?.isFavorite == 1
                renderFavorite()
            }
        }
        renderFavorite()
        renderMode(binding.cardShuffle, binding.shuffleIv, player.shuffleModeEnabled)
        renderMode(binding.cardRepeat, binding.repeatIv, player.repeatMode == Player.REPEAT_MODE_ONE)
        renderProgress()
    }

    private fun renderFavorite() {
        binding.bookmarkIv.setBackgroundResource(if (favorite) R.drawable.ic_heart else R.drawable.ic_heart__6_)
        binding.cardBookmark.isEnabled = currentSong != null && controller != null
        binding.cardAddToList.isEnabled = currentSong != null && controller != null
    }

    private fun renderMode(card: CardView, icon: View, active: Boolean) {
        card.isSelected = active
        card.backgroundTintList = ColorStateList.valueOf(getColor(if (active) R.color.shuffleColor else R.color.musicActivity))
        icon.background?.mutate()?.let { DrawableCompat.setTint(it, getColor(if (active) R.color.white else R.color.shuffleColor)) }
    }

    private fun renderProgress() {
        val player = controller ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it >= 0L }
            ?: currentSong?.audioDuration?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
        binding.musicDuration.text = DateUtils.formatElapsedTime(duration / 1000)
        if (!seeking) {
            val position = player.currentPosition.coerceAtLeast(0)
            binding.tvStartTime.text = DateUtils.formatElapsedTime(position / 1000)
            binding.tvSeekBar.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            binding.tvSeekBar.progress = position.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

    private fun setControlsEnabled(enabled: Boolean) {
        listOf(binding.btnPlayPause, binding.playNext, binding.playPrevious, binding.cardShuffle,
            binding.cardRepeat, binding.cardBookmark, binding.cardAddToList, binding.tvSeekBar)
            .forEach { it.isEnabled = enabled }
    }

    override fun onStop() {
        started = false
        selectionJob?.cancel()
        handler.removeCallbacks(progressUpdater)
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        setControlsEnabled(false)
        super.onStop()
    }
}
