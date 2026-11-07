package com.fsosuke.personamusic.playback

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import com.fsosuke.personamusic.MainActivity
import com.fsosuke.personamusic.R
import com.fsosuke.personamusic.data.Song

/** Home-screen widget in P5 style: cover, title, artist and chunky controls. */
class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = render(context)

    companion object {
        @Volatile private var song: Song? = null
        @Volatile private var playing = false
        @Volatile private var art: Pair<String, Bitmap>? = null

        fun push(context: Context, current: Song?, isPlaying: Boolean) {
            song = current
            playing = isPlaying
            render(context)
        }

        fun hasArtFor(songId: String) = art?.first == songId

        fun setArt(context: Context, songId: String, bitmap: Bitmap) {
            art = songId to bitmap
            if (song?.id == songId) render(context)
        }

        @OptIn(UnstableApi::class)
        private fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
            if (ids.isEmpty()) return

            val current = song
            val views = RemoteViews(context.packageName, R.layout.widget_player)
            views.setTextViewText(R.id.widget_title, current?.title ?: "Persona Music")
            views.setTextViewText(R.id.widget_artist, current?.artist ?: "Take your time.")
            val bitmap = art?.takeIf { it.first == current?.id }?.second
            if (bitmap != null) views.setImageViewBitmap(R.id.widget_art, bitmap)
            else views.setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)
            views.setImageViewResource(R.id.widget_play, if (playing) R.drawable.widget_pause else R.drawable.widget_play)

            val openApp = PendingIntent.getActivity(
                context, 10, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)

            fun mediaButton(code: Int, keyCode: Int): PendingIntent = PendingIntent.getBroadcast(
                context,
                code,
                Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setClass(context, MediaButtonReceiver::class.java)
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_prev, mediaButton(11, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            views.setOnClickPendingIntent(R.id.widget_play, mediaButton(12, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_next, mediaButton(13, KeyEvent.KEYCODE_MEDIA_NEXT))

            manager.updateAppWidget(ids, views)
        }
    }
}
