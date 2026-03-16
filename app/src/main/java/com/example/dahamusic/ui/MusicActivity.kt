package com.example.dahamusic.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.dahamusic.utils.CreateNotification
import com.example.dahamusic.R
import com.example.dahamusic.databinding.ActivityMusicNewBinding
import com.example.dahamusic.room.RoomAudioModel
import com.example.dahamusic.services.OnClearFromRecentService
import com.example.dahamusic.viewmodel.MediaViewModel
import java.io.Serializable

open class MusicActivity : AppCompatActivity(), Serializable {

    private lateinit var binding: ActivityMusicNewBinding
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var audioArrayList: List<RoomAudioModel>
    var current_pos = 0.0
    private var total_duration: Double = 0.0
    private var audio_index = 0
    private var notificationManager: NotificationManager? = null
    private lateinit var viewModel: MediaViewModel

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    private lateinit var mediaSession: MediaSessionCompat

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMusicNewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        mediaSession = MediaSessionCompat(this, "PlayerService")
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                if (this@MusicActivity::audioArrayList.isInitialized) setPause(audioArrayList)
            }
            override fun onPause() {
                if (this@MusicActivity::audioArrayList.isInitialized) setPause(audioArrayList)
            }
            override fun onSkipToNext() {
                if (this@MusicActivity::audioArrayList.isInitialized) nextAudio(audioArrayList)
            }
            override fun onSkipToPrevious() {
                if (this@MusicActivity::audioArrayList.isInitialized) prevAudio(audioArrayList)
            }
        })
        mediaSession.isActive = true

        viewModel = ViewModelProvider(this).get(MediaViewModel::class.java)

        binding.cardBookmark.elevation = 0F
        binding.cardAddToList.elevation = 0F
        binding.cardRepeat.elevation = 0F
        binding.cardShuffle.elevation = 0F

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.statusBarColor = getColor(R.color.musicActivity)
            window.navigationBarColor = getColor(R.color.musicActivity)
        }

        val position = intent.getIntExtra("position", 0)
        val folderName = intent.getStringExtra("folderName") as String
        viewModel.getFolder(folderName).observe(this) {
            audioArrayList = it.audioList
            setAudio(position, audioArrayList)
        }

        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.getStringExtra("actionname")
                when (action) {
                    CreateNotification().ACTION_NEXT -> {
                        if (this@MusicActivity::audioArrayList.isInitialized) nextAudio(audioArrayList)
                    }
                    CreateNotification().ACTION_PLAY -> {
                        if (this@MusicActivity::audioArrayList.isInitialized) setPause(audioArrayList)
                    }
                    CreateNotification().ACTION_PREVIOUS -> {
                        if (this@MusicActivity::audioArrayList.isInitialized) prevAudio(audioArrayList)
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel()
            val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Context.RECEIVER_EXPORTED
            } else 0
            registerReceiver(broadcastReceiver, IntentFilter("TRACKS_TRACKS"), receiverFlags)
            startService(Intent(baseContext, OnClearFromRecentService::class.java))
        }

        binding.playNext.setOnClickListener {
            if (this::audioArrayList.isInitialized) nextAudio(audioArrayList)
        }
        binding.playPrevious.setOnClickListener {
            if (this::audioArrayList.isInitialized) prevAudio(audioArrayList)
        }
        binding.btnPlayPause.setOnClickListener {
            if (this::audioArrayList.isInitialized) setPause(audioArrayList)
        }
        binding.btnArrow.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CreateNotification().CHANNEL_ID,
                "AppNameHasan",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun getAlbumArt(uriString: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = Uri.parse(uriString)
            retriever.setDataSource(applicationContext, uri)
            val art = retriever.embeddedPicture
            retriever.release()
            art
        } catch (e: Exception) {
            Log.e("TEST_OKLADKI", "Nie udało się pobrać okładki dla: $uriString", e)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setAudio(pos: Int, audioArrayList: List<RoomAudioModel>) {

        var isRepeatActivated = false
        var isRandomPlayingActivated = false

        audio_index = pos

        if (this::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }

        mediaPlayer = MediaPlayer()
        mediaPlayer.apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
        }

        viewModel.getMusic(pos + 1).observe(this) {

            var cardDrawableBookmark: Drawable = binding.cardBookmark.background
            cardDrawableBookmark = DrawableCompat.wrap(cardDrawableBookmark)

            val state = it.isFavorite
            var stateBoolean = false
            if (state == 1) {
                stateBoolean = true
                binding.bookmarkIv.setImageResource(R.drawable.ic_heart)
            } else {
                stateBoolean = false
                DrawableCompat.setTint(cardDrawableBookmark, resources.getColor(R.color.musicActivity, null))
                binding.cardBookmark.background = cardDrawableBookmark
                binding.bookmarkIv.setImageResource(R.drawable.ic_heart__6_)
            }

            binding.cardShuffle.setOnClickListener {
                isRandomPlayingActivated = !isRandomPlayingActivated

                var cardDrawable: Drawable = binding.cardShuffle.background
                cardDrawable = DrawableCompat.wrap(cardDrawable)
                var ivDrawable = binding.shuffleIv.background
                ivDrawable = DrawableCompat.wrap(ivDrawable)

                if (isRandomPlayingActivated) {
                    DrawableCompat.setTint(cardDrawable, resources.getColor(R.color.shuffleColor, null))
                    binding.cardShuffle.background = cardDrawable
                    DrawableCompat.setTint(ivDrawable, resources.getColor(R.color.white, null))
                    binding.shuffleIv.background = ivDrawable
                } else {
                    DrawableCompat.setTint(cardDrawable, resources.getColor(R.color.musicActivity, null))
                    binding.cardShuffle.background = cardDrawable
                    DrawableCompat.setTint(ivDrawable, resources.getColor(R.color.shuffleColor, null))
                    binding.shuffleIv.background = ivDrawable
                }

                Toast.makeText(this, "shuffle", Toast.LENGTH_SHORT).show()
                if (isRepeatActivated) {
                    isRepeatActivated = false
                }
            }

            binding.cardBookmark.setOnClickListener {
                stateBoolean = !stateBoolean
                if (stateBoolean) {
                    viewModel.setFavorite(1, pos + 1)
                    binding.bookmarkIv.setImageResource(R.drawable.ic_heart)
                } else {
                    viewModel.setFavorite(0, pos + 1)
                    DrawableCompat.setTint(cardDrawableBookmark, resources.getColor(R.color.musicActivity, null))
                    binding.cardBookmark.background = cardDrawableBookmark
                    binding.bookmarkIv.setImageResource(R.drawable.ic_heart__6_)
                }
            }

            binding.cardRepeat.setOnClickListener {
                isRepeatActivated = !isRepeatActivated

                var cardDrawable: Drawable = binding.cardRepeat.background
                cardDrawable = DrawableCompat.wrap(cardDrawable)
                var ivDrawable = binding.repeatIv.background
                ivDrawable = DrawableCompat.wrap(ivDrawable)

                if (isRepeatActivated) {
                    DrawableCompat.setTint(cardDrawable, resources.getColor(R.color.shuffleColor, null))
                    binding.cardRepeat.background = cardDrawable
                    DrawableCompat.setTint(ivDrawable, resources.getColor(R.color.white, null))
                    binding.repeatIv.background = ivDrawable
                } else {
                    DrawableCompat.setTint(cardDrawable, resources.getColor(R.color.musicActivity, null))
                    binding.cardRepeat.background = cardDrawable
                    DrawableCompat.setTint(ivDrawable, resources.getColor(R.color.shuffleColor, null))
                    binding.repeatIv.background = ivDrawable
                }

                Toast.makeText(this, "repeat", Toast.LENGTH_SHORT).show()

                if (isRandomPlayingActivated) {
                    isRandomPlayingActivated = false
                }
            }

            binding.cardAddToList.setOnClickListener {
                Toast.makeText(this, "addToList", Toast.LENGTH_SHORT).show()
            }
        }

        playAudio(pos, audioArrayList)

        binding.tvSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (this@MusicActivity::mediaPlayer.isInitialized) {
                    current_pos = seekBar!!.progress.toDouble()
                    mediaPlayer.seekTo(current_pos.toInt())

                    val playIcon = if (mediaPlayer.isPlaying) {
                        R.drawable.ic_pause
                    } else {
                        R.drawable.ic_play_button_arrowhead
                    }
                    CreateNotification().createNotification(this@MusicActivity, audioArrayList[audio_index], playIcon, current_pos.toLong(), mediaSession)
                }
            }
        })

        mediaPlayer.setOnCompletionListener {
            if (isRandomPlayingActivated) {
                val random = (0 until audioArrayList.size).random()
                audio_index = random
            }
            if (!isRandomPlayingActivated && !isRepeatActivated) {
                audio_index++
            }
            if (audio_index < (audioArrayList.size)) {
                playAudio(audio_index, audioArrayList)
            } else {
                audio_index = 0
                playAudio(audio_index, audioArrayList)
            }
        }
    }

    private fun playAudio(pos: Int, audioArrayList: List<RoomAudioModel>) {
        try {
            val image = audioArrayList[pos].audioUri?.let { getAlbumArt(it) }
            if (image != null) {
                Glide.with(this).asBitmap().load(image).into(binding.musicPhoto)
            } else {
                binding.musicPhoto.setImageDrawable(null)
            }

            binding.playPause.setImageResource(R.drawable.ic_pause)
            binding.musicName.text = audioArrayList[pos].audioTitle
            binding.musicAuthor.text = audioArrayList[pos].audioArtist

            if (this::mediaPlayer.isInitialized) {
                mediaPlayer.stop()
                mediaPlayer.reset()
            }

            CreateNotification().createNotification(this, audioArrayList[pos], R.drawable.ic_pause, 0L, mediaSession)

            val uriString = audioArrayList[pos].audioUri!!
            Log.d("TEST_PLAYERA", "Próbuję odtworzyć link: $uriString")

            mediaPlayer.setDataSource(applicationContext, Uri.parse(uriString))

            mediaPlayer.setOnPreparedListener { mp ->
                mp.start()
                setAudioProgress()
            }

            mediaPlayer.prepareAsync()

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("TEST_PLAYERA", "Wyjątek podczas ładowania piosenki: ", e)
            Toast.makeText(this, "Błąd odtwarzania pliku", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setAudioProgress() {
        if (!this::mediaPlayer.isInitialized) return

        current_pos = mediaPlayer.currentPosition.toDouble()
        total_duration = mediaPlayer.duration.toDouble()

        binding.musicDuration.text = timerConversion(total_duration.toLong())
        binding.tvStartTime.text = timerConversion(current_pos.toLong())
        binding.tvSeekBar.max = total_duration.toInt()

        runnable = object : Runnable {
            override fun run() {
                try {
                    if (this@MusicActivity::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
                        current_pos = mediaPlayer.currentPosition.toDouble()
                        binding.tvStartTime.text = timerConversion(current_pos.toLong())
                        binding.tvSeekBar.progress = current_pos.toInt()
                    }
                    handler.postDelayed(this, 1000)
                } catch (ed: IllegalStateException) {
                    ed.printStackTrace()
                }
            }
        }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(runnable, 1000)
    }

    private fun prevAudio(audioArrayList: List<RoomAudioModel>) {
        if (audio_index > 0) {
            audio_index--
            playAudio(audio_index, audioArrayList)
        } else {
            audio_index = audioArrayList.size - 1
            playAudio(audio_index, audioArrayList)
        }
    }

    private fun nextAudio(audioArrayList: List<RoomAudioModel>) {
        if (audio_index < audioArrayList.size - 1) {
            audio_index++
            playAudio(audio_index, audioArrayList)
        } else {
            audio_index = 0
            playAudio(audio_index, audioArrayList)
        }
    }

    private fun setPause(audioArrayList: List<RoomAudioModel>) {
        if (!this::mediaPlayer.isInitialized) return

        try {
            val currentPos = mediaPlayer.currentPosition.toLong()

            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                binding.playPause.setImageResource(R.drawable.ic_play_button_arrowhead)
                CreateNotification().createNotification(this, audioArrayList[audio_index], R.drawable.ic_play_button_arrowhead, currentPos, mediaSession)
            } else {
                mediaPlayer.start()
                binding.playPause.setImageResource(R.drawable.ic_pause)
                CreateNotification().createNotification(this, audioArrayList[audio_index], R.drawable.ic_pause, currentPos, mediaSession)
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    fun timerConversion(value: Long): String {
        val dur = value.toInt()
        val hrs = dur / 3600000
        val mns = dur / 60000 % 60000
        val scs = dur % 60000 / 1000
        return if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mns, scs)
        } else {
            String.format("%02d:%02d", mns, scs)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (this::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }
        mediaSession.isActive = false
        mediaSession.release()
    }
}