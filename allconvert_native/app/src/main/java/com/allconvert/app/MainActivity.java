package com.allconvert.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST=1001, SAVE_DOCUMENT_REQUEST=1002;
    private static final String APP_ID="ca-app-pub-9940728659432865~2706136459";
    private static final String BANNER_ID="ca-app-pub-9940728659432865/6234492932";
    private static final String INTERSTITIAL_ID="ca-app-pub-9940728659432865/4102978608";
    private static final String REWARDED_ID="ca-app-pub-9940728659432865/6537570257";
    private static final String NATIVE_ID="ca-app-pub-9940728659432865/5224488586";
    private static final String APP_OPEN_ID="ca-app-pub-9940728659432865/1006961841";

    private WebView webView; private AdView adView; private NativeAd nativeAd; private android.widget.FrameLayout bannerContainer; private FrameLayout nativeContainer; private Button rewardButton; private boolean bannerLoaded=false;
    private ValueCallback<Uri[]> filePathCallback; private byte[] pendingBytes;
    private InterstitialAd interstitialAd; private RewardedAd rewardedAd;
    private int conversionCount=0; private long lastInterstitialShown=0L; private long lastAppOpenShown=0L; private boolean firstSession=true; private boolean appOpenShowing=false; private boolean skipNextInterstitial=false;

    @Override public void onCreate(Bundle b){
        super.onCreate(b); setContentView(R.layout.activity_main);
        webView=findViewById(R.id.webView); bannerContainer=findViewById(R.id.bannerContainer); nativeContainer=findViewById(R.id.nativeAdContainer); rewardButton=null;
        WebSettings s=webView.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true); s.setAllowContentAccess(true);
        webView.addJavascriptInterface(new AndroidBridge(),"AndroidBridge");
        webView.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){handleIncoming(getIntent());}});
        webView.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){
            if(filePathCallback!=null)filePathCallback.onReceiveValue(null); filePathCallback=cb;
            try{startActivityForResult(p.createIntent(),FILE_CHOOSER_REQUEST);return true;}catch(Exception e){filePathCallback=null;cb.onReceiveValue(null);return false;}
        }});
        webView.loadUrl("file:///android_asset/index.html");
        MobileAds.initialize(this,status->runOnUiThread(()->{
            showBanner(); loadInterstitial(); loadRewarded(); loadNative();
            new android.os.Handler().postDelayed(()->showAppOpenIfReady(),900);
        }));
    }

    private class AndroidBridge{
        @JavascriptInterface public void download(String n,String m,String b){
            String name=safe(n); String type=mime(m); byte[] data=dec(b);
            if(isHtmlFile(name,type)){runOnUiThread(()->showHtmlPreview(name,type,data));}else{saveDownload(name,type,data);}
        }
        @JavascriptInterface public void saveAs(String n,String m,String b){pendingBytes=dec(b);Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime(m));i.putExtra(Intent.EXTRA_TITLE,safe(n));startActivityForResult(i,SAVE_DOCUMENT_REQUEST);}
        @JavascriptInterface public void share(String n,String m,String b){shareOrOpen(safe(n),mime(m),dec(b));}
        @JavascriptInterface public void openWith(String n,String m,String b){openFile(safe(n),mime(m),dec(b));}
        @JavascriptInterface public void showRewarded(){runOnUiThread(MainActivity.this::showRewardedIfReady);}
    }
    private byte[] dec(String s){try{return Base64.getDecoder().decode(s);}catch(Exception e){return new byte[0];}}
    private String safe(String s){return s==null||s.isEmpty()?"converted_file":s.replaceAll("[\r\n\\/:*?\"<>|]","_");}
    private String mime(String s){return s==null||s.isEmpty()?"application/octet-stream":s;}

    private boolean isHtmlFile(String n,String m){
        String name=n==null?"":n.toLowerCase(); String type=m==null?"":m.toLowerCase();
        return type.contains("text/html") || type.contains("application/xhtml+xml") || name.endsWith(".html") || name.endsWith(".htm");
    }

    private void showHtmlPreview(String n,String m,byte[] data){
        if(data==null||data.length==0)return;
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(10),dp(4),dp(10),dp(8));
        TextView info=new TextView(this); info.setText("Preview • "+n); info.setTextSize(14); info.setTextColor(Color.rgb(20,28,45)); info.setTypeface(null,android.graphics.Typeface.BOLD); info.setPadding(dp(4),dp(4),dp(4),dp(8));
        root.addView(info,new LinearLayout.LayoutParams(-1,dp(38)));
        WebView preview=new WebView(this); preview.setBackgroundColor(Color.WHITE); WebSettings ps=preview.getSettings(); ps.setJavaScriptEnabled(true); ps.setDomStorageEnabled(true); ps.setAllowFileAccess(false); ps.setAllowContentAccess(false); ps.setBuiltInZoomControls(false); ps.setDisplayZoomControls(false);
        String html=prepareHtmlPreview(data);
        preview.loadDataWithBaseURL("https://allconvert.local/",html,"text/html","UTF-8",null);
        root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); actions.setGravity(Gravity.CENTER); actions.setPadding(0,dp(8),0,0);
        Button download=new Button(this); download.setText("Download"); download.setAllCaps(false);
        Button save=new Button(this); save.setText("Save As"); save.setAllCaps(false);
        Button share=new Button(this); share.setText("Share"); share.setAllCaps(false);
        actions.addView(download,new LinearLayout.LayoutParams(0,dp(44),1)); actions.addView(save,new LinearLayout.LayoutParams(0,dp(44),1)); actions.addView(share,new LinearLayout.LayoutParams(0,dp(44),1));
        root.addView(actions,new LinearLayout.LayoutParams(-1,dp(52)));
        final AlertDialog dialog=new AlertDialog.Builder(this).setTitle("HTML Preview").setView(root).setNegativeButton("Tutup",null).create();
        download.setOnClickListener(v->{dialog.dismiss();saveDownload(n,m,data);});
        save.setOnClickListener(v->{dialog.dismiss();pendingBytes=data;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(m);i.putExtra(Intent.EXTRA_TITLE,n);startActivityForResult(i,SAVE_DOCUMENT_REQUEST);});
        share.setOnClickListener(v->shareOrOpen(n,m,data));
        dialog.setOnDismissListener(v->preview.destroy());
        dialog.show();
    }

    private String prepareHtmlPreview(byte[] data){
        String html=new String(data,java.nio.charset.StandardCharsets.UTF_8).replace("\uFEFF","");
        html=html.trim();
        if(html.startsWith("\u0060\u0060\u0060")){
            int first=html.indexOf('\n');
            if(first>=0)html=html.substring(first+1);
            if(html.endsWith("\u0060\u0060\u0060"))html=html.substring(0,html.length()-3).trim();
        }
        if(html.contains("&lt;html") || html.contains("&lt;!doctype") || html.contains("&lt;div") || html.contains("&lt;body")){
            html=android.text.Html.fromHtml(html,android.text.Html.FROM_HTML_MODE_LEGACY).toString();
        }
        if(!html.matches("(?s).*<\\s*(html|!doctype|head|body|div|main|section|table|p|h[1-6])\\b.*")){
            html="<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head><body><pre style=\"white-space:pre-wrap;word-wrap:break-word;font-family:sans-serif;padding:16px\">"+android.text.TextUtils.htmlEncode(html)+"</pre></body></html>";
        }
        return html;
    }

    private void saveDownload(String n,String m,byte[] data){
        if(data.length==0)return; try{
            ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,n);v.put(MediaStore.Downloads.MIME_TYPE,m);v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/All Convert");
            Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)throw new Exception();
            try(OutputStream o=getContentResolver().openOutputStream(u)){o.write(data);}
            webView.evaluateJavascript("window.__allConvertNativeDone&&window.__allConvertNativeDone('download')",null);
            conversionCount++;
            if(conversionCount%5==0) showInterstitialIfReady();
        }catch(Exception e){webView.evaluateJavascript("window.__allConvertNativeError&&window.__allConvertNativeError('download')",null);}
    }
    private void shareOrOpen(String n,String m,byte[] data){
        try{
            File d=new File(getCacheDir(),"shared");if(!d.exists())d.mkdirs();File f=new File(d,n);
            try(FileOutputStream o=new FileOutputStream(f)){o.write(data);}
            Uri u=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);
            Intent i=new Intent(Intent.ACTION_SEND);i.setType(m);i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i,"Share / Open With"));
        }catch(Exception e){webView.evaluateJavascript("window.__allConvertNativeError&&window.__allConvertNativeError('share')",null);}
    }
    private void openFile(String n,String m,byte[] data){
        try{
            File d=new File(getCacheDir(),"shared");if(!d.exists())d.mkdirs();File f=new File(d,n);
            try(FileOutputStream o=new FileOutputStream(f)){o.write(data);}
            Uri u=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);
            Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(u,m);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        }catch(Exception e){shareOrOpen(n,m,data);}
    }
    private void handleIncoming(Intent x){
        if(x==null)return;String a=x.getAction();Uri u=x.getData();
        if(Intent.ACTION_SEND.equals(a)){if(x.getClipData()!=null&&x.getClipData().getItemCount()>0)u=x.getClipData().getItemAt(0).getUri();else if(x.getParcelableExtra(Intent.EXTRA_STREAM)!=null)u=(Uri)x.getParcelableExtra(Intent.EXTRA_STREAM);}
        if(u==null||!(Intent.ACTION_VIEW.equals(a)||Intent.ACTION_SEND.equals(a)))return;
        try{byte[] d=read(u);String n=u.getLastPathSegment();if(n==null||n.isEmpty())n="shared_file";String m=getContentResolver().getType(u);String b=Base64.getEncoder().encodeToString(d);
            String js="window.__allConvertIncoming&&window.__allConvertIncoming("+q(n)+","+q(mime(m))+",'"+b+"')";webView.evaluateJavascript(js,null);
        }catch(Exception ignored){}
    }
    private String q(String s){return JSONObject.quote(String.valueOf(s));}
    private byte[] read(Uri u)throws Exception{try(InputStream i=getContentResolver().openInputStream(u);ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=i.read(b))>0)o.write(b,0,n);return o.toByteArray();}}

    private void showBanner(){
        if(bannerContainer==null)return;
        bannerLoaded=false;
        bannerContainer.removeAllViews();
        bannerContainer.setVisibility(View.GONE);
        bannerContainer.getLayoutParams().height=0;
        bannerContainer.requestLayout();
        adView=new AdView(this);adView.setAdUnitId(BANNER_ID);
        int width=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
        adView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this,Math.max(320,width)));
        bannerContainer.removeAllViews();
        bannerContainer.addView(adView,new android.widget.FrameLayout.LayoutParams(-1,-2));
        adView.setAdListener(new AdListener(){
            @Override public void onAdLoaded(){
                bannerLoaded=true;
                bannerContainer.getLayoutParams().height=android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                bannerContainer.requestLayout();
                bannerContainer.setVisibility(View.VISIBLE);
            }
            @Override public void onAdFailedToLoad(LoadAdError error){
                bannerLoaded=false;
                bannerContainer.removeAllViews();
                bannerContainer.getLayoutParams().height=0;
                bannerContainer.requestLayout();
                bannerContainer.setVisibility(View.GONE);
            }
        });
        adView.loadAd(new AdRequest.Builder().build());
    }

    private void loadInterstitial(){InterstitialAd.load(this,INTERSTITIAL_ID,new AdRequest.Builder().build(),new InterstitialAdLoadCallback(){
        @Override public void onAdLoaded(InterstitialAd ad){interstitialAd=ad;ad.setFullScreenContentCallback(new com.google.android.gms.ads.FullScreenContentCallback(){@Override public void onAdDismissedFullScreenContent(){interstitialAd=null;loadInterstitial();}});}
        @Override public void onAdFailedToLoad(LoadAdError e){interstitialAd=null;}
    });}
    private void showInterstitialIfReady(){long now=System.currentTimeMillis();if(now-lastInterstitialShown<180000L)return;if(skipNextInterstitial){skipNextInterstitial=false;webView.evaluateJavascript("window.__allConvertRewardedUsed&&window.__allConvertRewardedUsed()",null);loadInterstitial();return;}if(interstitialAd!=null && !appOpenShowing){lastInterstitialShown=now;interstitialAd.show(this);}else loadInterstitial();}

    private void loadRewarded(){RewardedAd.load(this,REWARDED_ID,new AdRequest.Builder().build(),new RewardedAdLoadCallback(){
        @Override public void onAdLoaded(RewardedAd ad){rewardedAd=ad;runOnUiThread(()->{if(rewardButton!=null)rewardButton.setVisibility(View.VISIBLE);});}
        @Override public void onAdFailedToLoad(LoadAdError e){rewardedAd=null;runOnUiThread(()->{if(rewardButton!=null)rewardButton.setVisibility(View.GONE);});}
    });}
    private void showRewardedIfReady(){
        if(rewardedAd==null){loadRewarded();Toast.makeText(this,"Rewarded ad belum tersedia.",Toast.LENGTH_SHORT).show();return;}
        RewardedAd ad=rewardedAd; rewardedAd=null; if(rewardButton!=null)rewardButton.setVisibility(View.GONE);
        ad.setFullScreenContentCallback(new com.google.android.gms.ads.FullScreenContentCallback(){@Override public void onAdDismissedFullScreenContent(){loadRewarded();}});
        ad.show(this,rewardItem->{skipNextInterstitial=true;webView.evaluateJavascript("window.__allConvertRewarded&&window.__allConvertRewarded("+rewardItem.getAmount()+")",null);});
    }

    private void loadNative(){
        AdLoader loader=new AdLoader.Builder(this,NATIVE_ID).forNativeAd(ad->{if(nativeAd!=null)nativeAd.destroy();nativeAd=ad;renderNative(ad);})
            .withNativeAdOptions(new NativeAdOptions.Builder().build()).withAdListener(new AdListener(){}).build();
        loader.loadAd(new AdRequest.Builder().build());
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void renderNative(NativeAd ad){
        if(nativeContainer==null)return;
        nativeContainer.removeAllViews();
        LinearLayout wrap=new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL); wrap.setBackgroundColor(Color.WHITE);
        NativeAdView nv=new NativeAdView(this);
        nv.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(128)));
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(8),dp(4),dp(8),dp(4));
        com.google.android.gms.ads.nativead.MediaView media=new com.google.android.gms.ads.nativead.MediaView(this); media.setLayoutParams(new LinearLayout.LayoutParams(dp(120),dp(120)));
        row.addView(media);
        LinearLayout text=new LinearLayout(this); text.setOrientation(LinearLayout.VERTICAL); text.setPadding(dp(8),0,dp(4),0); text.setLayoutParams(new LinearLayout.LayoutParams(0,-1,1));
        TextView headline=new TextView(this); headline.setTextSize(13); headline.setTextColor(Color.rgb(20,28,45)); headline.setMaxLines(2); headline.setTypeface(null,android.graphics.Typeface.BOLD);
        TextView body=new TextView(this); body.setTextSize(10); body.setTextColor(Color.rgb(90,100,120)); body.setMaxLines(2); body.setPadding(0,dp(2),0,dp(2));
        Button cta=new Button(this); cta.setTextSize(10); cta.setMinHeight(dp(32)); cta.setAllCaps(false); cta.setTextColor(Color.rgb(40,60,120));
        text.addView(headline,new LinearLayout.LayoutParams(-1,0,1)); text.addView(body,new LinearLayout.LayoutParams(-1,0,1)); text.addView(cta,new LinearLayout.LayoutParams(-1,dp(34)));
        row.addView(text);
        nv.addView(row);
        nv.setMediaView(media); nv.setHeadlineView(headline); nv.setBodyView(body); nv.setCallToActionView(cta); nv.setNativeAd(ad);
        android.widget.ImageButton hide=new android.widget.ImageButton(this);
        hide.setImageResource(android.R.drawable.arrow_up_float);
        hide.setContentDescription("Sembunyikan iklan");
        hide.setBackgroundColor(Color.TRANSPARENT);
        hide.setPadding(dp(8),dp(2),dp(8),dp(2));
        wrap.addView(nv);
        LinearLayout.LayoutParams hideParams=new LinearLayout.LayoutParams(-1,dp(32));
        hideParams.gravity=Gravity.CENTER_HORIZONTAL;
        wrap.addView(hide,hideParams);
        final boolean[] nativeHidden={false};
        hide.setOnClickListener(v->{
            nativeHidden[0]=!nativeHidden[0];
            nv.setVisibility(nativeHidden[0]?View.GONE:View.VISIBLE);
            hide.setImageResource(nativeHidden[0]?android.R.drawable.arrow_down_float:android.R.drawable.arrow_up_float);
            hide.setContentDescription(nativeHidden[0]?"Tunjukkan iklan":"Sembunyikan iklan");
        });
        nativeContainer.addView(wrap); nativeContainer.setVisibility(View.VISIBLE);
    }
    private void showAppOpenIfReady(){
        if(appOpenShowing || System.currentTimeMillis()-lastAppOpenShown<180000L)return;
        com.google.android.gms.ads.appopen.AppOpenAd.load(this,APP_OPEN_ID,new AdRequest.Builder().build(),new com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback(){
            @Override public void onAdLoaded(com.google.android.gms.ads.appopen.AppOpenAd ad){
                if(System.currentTimeMillis()-lastAppOpenShown<180000L)return;
                appOpenShowing=true;
                if(bannerContainer!=null)bannerContainer.setVisibility(View.GONE);
                ad.setFullScreenContentCallback(new com.google.android.gms.ads.FullScreenContentCallback(){
                    @Override public void onAdDismissedFullScreenContent(){lastAppOpenShown=System.currentTimeMillis();appOpenShowing=false;if(bannerContainer!=null && bannerLoaded)bannerContainer.setVisibility(View.VISIBLE);}
                    @Override public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError e){appOpenShowing=false;if(bannerContainer!=null && bannerLoaded)bannerContainer.setVisibility(View.VISIBLE);}
                });
                ad.show(MainActivity.this);
            }
        });
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==FILE_CHOOSER_REQUEST){if(filePathCallback==null)return;Uri[] z=null;if(c==RESULT_OK&&d!=null){if(d.getClipData()!=null){int n=d.getClipData().getItemCount();z=new Uri[n];for(int i=0;i<n;i++)z[i]=d.getClipData().getItemAt(i).getUri();}else if(d.getData()!=null)z=new Uri[]{d.getData()};}filePathCallback.onReceiveValue(z);filePathCallback=null;}
        else if(r==SAVE_DOCUMENT_REQUEST){if(c==RESULT_OK&&d!=null&&d.getData()!=null&&pendingBytes!=null)try(OutputStream o=getContentResolver().openOutputStream(d.getData())){o.write(pendingBytes);}catch(Exception ignored){}pendingBytes=null;}
    }
    @Override protected void onStart(){super.onStart(); if(firstSession){firstSession=false;return;} new android.os.Handler().postDelayed(()->showAppOpenIfReady(),800);}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleIncoming(i);}
    @Override protected void onDestroy(){if(filePathCallback!=null){filePathCallback.onReceiveValue(null);filePathCallback=null;}if(adView!=null)adView.destroy();if(nativeAd!=null)nativeAd.destroy();if(webView!=null)webView.destroy();super.onDestroy();}
}