package com.allconvert.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

public class MainActivity extends Activity {
    private WebView webView;
    private AdView adView;
    @Override public void onCreate(Bundle b){ super.onCreate(b); setContentView(R.layout.activity_main); webView=findViewById(R.id.webView); WebSettings s=webView.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true); s.setAllowContentAccess(true); webView.setWebViewClient(new WebViewClient()); webView.loadUrl("file:///android_asset/index.html"); MobileAds.initialize(this, status -> runOnUiThread(this::showBanner)); }
    private void showBanner(){ adView=new AdView(this); adView.setAdUnitId("ca-app-pub-9940728659432865/7572128943"); adView.setAdSize(com.google.android.gms.ads.AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, 360)); addContentView(adView,new android.view.ViewGroup.LayoutParams(-1,-2)); adView.loadAd(new AdRequest.Builder().build()); }
    @Override protected void onDestroy(){ if(adView!=null) adView.destroy(); if(webView!=null) webView.destroy(); super.onDestroy(); }
}
