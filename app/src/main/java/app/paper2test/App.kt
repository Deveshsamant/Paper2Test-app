package app.paper2test

import android.app.Application
import android.content.Context

class App : Application() {
    lateinit var session: Session
    lateinit var api: Api
    override fun onCreate() {
        super.onCreate()
        session = Session(this)
        api = Api(BuildConfig.SITE, session)
        Push.createChannel(this)
    }
    companion object { fun of(ctx: Context) = ctx.applicationContext as App }
}

/** Bearer session + cached profile, persisted in app-private preferences. */
class Session(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("p2t", Context.MODE_PRIVATE)
    var token: String?
        get() = prefs.getString("token", null)
        set(v) { prefs.edit().apply { if (v == null) remove("token") else putString("token", v) }.apply() }
    var userName: String?
        get() = prefs.getString("user_name", null)
        set(v) { prefs.edit().putString("user_name", v).apply() }
    var username: String?
        get() = prefs.getString("username", null)
        set(v) { prefs.edit().putString("username", v).apply() }
    /** This phone's push token as registered with the server (removed there on sign-out). */
    var pushToken: String?
        get() = prefs.getString("push_token", null)
        set(v) { prefs.edit().putString("push_token", v).apply() }
    /** Institute link (paper2test.app/i/<slug>) opened before signing in: joined after sign-in. */
    var refInstitute: String?
        get() = prefs.getString("ref_institute", null)
        set(v) { prefs.edit().apply { if (v == null) remove("ref_institute") else putString("ref_institute", v) }.apply() }
    /** The Play install referrer was read (only once per install). */
    var referrerChecked: Boolean
        get() = prefs.getBoolean("referrer_checked", false)
        set(v) { prefs.edit().putBoolean("referrer_checked", v).apply() }
    /** Android 13+ notification permission was asked once already. */
    var notifAsked: Boolean
        get() = prefs.getBoolean("notif_asked", false)
        set(v) { prefs.edit().putBoolean("notif_asked", v).apply() }
    val signedIn get() = token != null
    /** The welcome screen (profile + exams) was offered in this app run; not offered again until the next start. */
    var welcomeOffered = false
    fun clear() { val checked = referrerChecked; prefs.edit().clear().apply(); welcomeOffered = false; referrerChecked = checked }
}
