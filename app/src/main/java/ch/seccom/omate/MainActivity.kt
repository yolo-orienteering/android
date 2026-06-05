package ch.seccom.omate
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback

class MainActivity : AppCompatActivity() {
    private lateinit var myWebView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val intentAction: String? = intent?.action
        val intentData: Uri? = intent?.data

        CookieManager.getInstance().setAcceptCookie(true)

        myWebView = findViewById(R.id.webview)
        myWebView.settings.javaScriptEnabled = true
        myWebView.settings.domStorageEnabled = true

        // Advertise the native wrapper + its version to the web app so the frontend can
        // gate features that only work in a recent enough app (e.g. webcal calendar links).
        myWebView.settings.userAgentString =
            "${myWebView.settings.userAgentString} o-mate-app/${appVersionCode()}"

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (myWebView.canGoBack()) {
                    myWebView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Open external links in browser/mail app/dialler
        myWebView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return url?.let { handleUrl(it) } ?: false
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return request?.url?.toString()?.let { handleUrl(it) } ?: false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }

        // The page to load on a fresh start. Set per build type (debug = localhost dev,
        // release = production) via BuildConfig.START_URL — see app/build.gradle.kts.
        val startUrl = BuildConfig.START_URL

        // A deep link (an https://o-mate.app/... link opened from mail or another browser)
        // arrives as an ACTION_VIEW intent — open that exact URL inside the app.
        val deepLinkUrl =
            if (intentAction == Intent.ACTION_VIEW && intentData != null) intentData.toString()
            else null

        // On recreation, restore the WebView so we never show a blank screen: rotation is
        // kept alive via android:configChanges, but process death (e.g. after a long time in
        // the background) still recreates the Activity. Fall back to a fresh load when there
        // is no saved state to restore.
        if (savedInstanceState == null || myWebView.restoreState(savedInstanceState) == null) {
            myWebView.loadUrl(deepLinkUrl ?: startUrl)
        }
    }

    // Preserve the WebView (history + current page) across process death so re-opening the
    // app after a long time in the background doesn't show a blank screen.
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::myWebView.isInitialized) {
            myWebView.saveState(outState)
        }
    }

    // A deep link that arrives while the app is already running (singleTop) is delivered here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW && ::myWebView.isInitialized) {
            intent.data?.let { myWebView.loadUrl(it.toString()) }
        }
    }

    private fun handleUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()

        // Calendar subscription links: hand webcal(s):// and any .ics feed to the system
        // (calendar app / browser). The WebView can't render these, and a calendar link
        // must never open or reload the o-mate app.
        if (scheme == "webcal" || scheme == "webcals") {
            openCalendarSubscription(uri)
            return true
        }

        val internalDomains = resources.getStringArray(R.array.internal_domains).toList()
        if (internalDomains.contains(uri.host)) {
            return false
        }

        // Any other non-internal link (browser, mail, dialler, …) goes to the system.
        openExternally(uri)
        return true
    }

    /** Open a URI with an external app, ignoring (rather than crashing on) a missing handler. */
    private fun openExternally(uri: Uri) {
        tryStart(Intent(Intent.ACTION_VIEW, uri))
    }

    /**
     * Open a calendar feed (webcal:// or .ics) in a calendar app. Android has no native
     * webcal:// handler (unlike iOS), so a plain ACTION_VIEW usually fails and a browser just
     * downloads the file.
     */
    private fun openCalendarSubscription(uri: Uri) {
        val scheme = uri.scheme?.lowercase()
        val isWebcal = scheme == "webcal" || scheme == "webcals"

        // 1) A calendar app that registers the webcal scheme, if one is installed.
        if (isWebcal && tryStart(Intent(Intent.ACTION_VIEW, uri))) return
    }

    /** Start [intent], returning false instead of crashing if nothing can handle it. */
    private fun tryStart(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun appVersionCode(): Long {
        return try {
            PackageInfoCompat.getLongVersionCode(
                packageManager.getPackageInfo(packageName, 0)
            )
        } catch (e: Exception) {
            0L
        }
    }
}