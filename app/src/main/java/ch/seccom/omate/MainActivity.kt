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

        var url = resources.getString(R.string.start_page)
        if (intentAction == Intent.ACTION_VIEW && intentData != null) {
            url = intentData.toString()
        }

        // Only reload page if app is newly opened
        if (savedInstanceState == null) {
            // myWebView.loadUrl("https://o-mate.app")
            myWebView.loadUrl("http://10.0.2.2:3000/")
        }
    }

    private fun handleUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()

        // Calendar subscription links: hand webcal(s):// to the system / calendar app.
        // The WebView itself cannot load these, so opening them in-place would crash the app.
        if (scheme == "webcal" || scheme == "webcals") {
            openExternally(uri)
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

    /** Open a URI with an external app, falling back gracefully instead of crashing. */
    private fun openExternally(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            // No app handles webcal:// — open the underlying https feed in a browser instead.
            val scheme = uri.scheme?.lowercase()
            if (scheme == "webcal" || scheme == "webcals") {
                val httpsUri = uri.buildUpon()
                    .scheme(if (scheme == "webcals") "https" else "http")
                    .build()
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, httpsUri))
                } catch (_: ActivityNotFoundException) {
                    // Nothing can handle it — swallow rather than crash.
                }
            }
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