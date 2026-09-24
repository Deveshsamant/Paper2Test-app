package app.paper2test

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** Receives push notifications. In the background Android shows them itself; while the app is open we show them here. */
class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val app = App.of(this)
        if (app.session.signedIn) CoroutineScope(Dispatchers.IO).launch { Push.upload(app, token) }
    }

    override fun onMessageReceived(m: RemoteMessage) {
        val n = m.notification ?: return
        Push.show(this, n.title ?: "Paper2Test", n.body ?: "", m.data["url"])
    }
}

object Push {
    const val CHANNEL = "general"

    /** Firebase is set up only when the build had google-services.json; otherwise notifications stay off. */
    private fun ready(ctx: Context) = FirebaseApp.getApps(ctx).isNotEmpty()

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) ctx.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Paper2Test", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Exam dates, results and news" })
    }

    /** After sign-in: tell the server this phone's token (it changes rarely; sending it again is harmless). */
    suspend fun register(ctx: Context) {
        if (!ready(ctx)) return
        val app = App.of(ctx)
        if (!app.session.signedIn) return
        runCatching { upload(app, FirebaseMessaging.getInstance().token.await()) }
    }

    suspend fun upload(app: App, token: String) {
        runCatching { app.api.post("/me/push-token", JSONObject().put("token", token).put("platform", "android")) }
            .onSuccess { app.session.pushToken = token }
    }

    /** Sign-out: stop notifications for this account on this phone. */
    suspend fun unregister(ctx: Context) {
        val app = App.of(ctx)
        val t = app.session.pushToken ?: return
        runCatching { app.api.post("/me/push-token/remove", JSONObject().put("token", t)) }
        app.session.pushToken = null
    }

    fun show(ctx: Context, title: String, body: String, url: String?) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).apply { if (url != null) putExtra("url", url) }
        val id = (title + body).hashCode()
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }
}
