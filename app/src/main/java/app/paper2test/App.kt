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
    val signedIn get() = token != null
    fun clear() { prefs.edit().clear().apply() }
}
