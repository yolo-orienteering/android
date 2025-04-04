package com.example.yolo_android
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
            myWebView.loadUrl("https://o-mate.app")
        }
    }

    private fun handleUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        val internalDomains = resources.getStringArray(R.array.internal_domains).toList()

        if (internalDomains.contains(uri.host)) {
            return false
        } else {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
            return true
        }
    }
}