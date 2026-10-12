package com.allconvert.app;

import android.app.Activity;
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

    private WebView webView; private AdView adView; private NativeAd nativeAd; private android.widget.FrameLayout bannerContainer;
    private ValueCallback<Uri[]> filePathCallback; private byte[] pendingBytes;
    private InterstitialAd interstitialAd; private RewardedAd rewardedAd;
    private int conversionCount=0; private long lastInterstitialShown=0L; private long lastAppOpenShown=0L; private boolean firstSession=true; private boolean appOpenShowing=false;

    @Override public void onCreate(Bundle b){
        super.onCreate(b); setContentView(R.layout.activity_main);
        webView=findViewById(R.id.webView); bannerContainer=findViewById(R.id.bannerContainer);
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
        @JavascriptInterface public void download(String n,String m,String b){saveDownload(safe(n),mime(m),dec(b));}
        @JavascriptInterface public void saveAs(String n,String m,String b){pendingBytes=dec(b);Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime(m));i.putExtra(Intent.EXTRA_TITLE,safe(n));startActivityForResult(i,SAVE_DOCUMENT_REQUEST);}
        @JavascriptInterface public void share(String n,String m,String b){shareOrOpen(safe(n),mime(m),dec(b));}
        @JavascriptInterface public void openWith(String n,String m,String b){openFile(safe(n),mime(m),dec(b));}
        @JavascriptInterface public void showRewarded(){runOnUiThread(MainActivity.this::showRewardedIfReady);}
    }
    private byte[] dec(String s){try{return Base64.getDecoder().decode(s);}catch(Exception e){return new byte[0];}}
    private String safe(String s){return s==null||s.isEmpty()?"converted_file":s.replaceAll("[\r\n\\/:*?\"<>|]","_");}
    private String mime(String s){return s==null||s.isEmpty()?"application/octet-stream":s;}

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
        }catch(Exception e){webView.evaluateJavascript("window.__allConvertNativeError&&window.__allConvertNativeNativeError('share')",null);}
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
        adView=new AdView(this);adView.setAdUnitId(BANNER_ID);
        int width=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
        adView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this,Math.max(320,width)));
        bannerContainer.removeAllViews();bannerContainer.addView(adView,new android.widget.FrameLayout.LayoutParams(-1,-2));
        adView.loadAd(new AdRequest.Builder().build());
    }

    private void loadInterstitial(){InterstitialAd.load(this,INTERSTITIAL_ID,new AdRequest.Builder().build(),new InterstitialAdLoadCallback(){
        @Override public void onAdLoaded(InterstitialAd ad){interstitialAd=ad;ad.setFullScreenContentCallback(new com.google.android.gms.ads.FullScreenContentCallback(){@Override public void onAdDismissedFullScreenContent(){interstitialAd=null;loadInterstitial();}});}
        @Override public void onAdFailedToLoad(LoadAdError e){interstitialAd=null;}
    });}
    private void showInterstitialIfReady(){if(interstitialAd!=null && !appOpenShowing){lastInterstitialShown=System.currentTimeMillis();interstitialAd.show(this);}else loadInterstitial();}

    private void loadRewarded(){RewardedAd.load(this,REWARDED_ID,new AdRequest.Builder().build(),new RewardedAdLoadCallback(){
        @Override public void onAdLoaded(RewardedAd ad){rewardedAd=ad;}
        @Override public void onAdFailedToLoad(LoadAdError e){rewardedAd=null;}
    });}
    private void showRewardedIfReady(){
        if(rewardedAd==null){loadRewarded();Toast.makeText(this,"Rewarded ad belum tersedia.",Toast.LENGTH_SHORT).show();return;}
        RewardedAd ad=rewardedAd; rewardedAd=null;
        ad.setFullScreenContentCallback(new com.google.android.gms.ads.FullScreenContentCallback(){@Override public void onAdDismissedFullScreenContent(){loadRewarded();}});
        ad.show(this,rewardItem->webView.evaluateJavascript("window.__allConvertRewarded&&window.__allConvertRewarded("+rewardItem.getAmount()+")",null));
    }

    private void loadNative(){
        AdLoader loader=new AdLoader.Builder(this,NATIVE_ID).forNativeAd(ad->{if(nativeAd!=null)nativeAd.destroy();nativeAd=ad;renderNative(ad);})
            .withNativeAdOptions(new NativeAdOptions.Builder().build()).withAdListener(new AdListener(){}).build();
        loader.loadAd(new AdRequest.Builder().build());
    }
    private void renderNative(NativeAd ad){
        NativeAdView v=findViewById(R.id.nativeAdView);
        if(v==null)return;
        v.setVisibility(View.VISIBLE); v.setHeadlineView(v.findViewById(R.id.nativeHeadline)); v.setBodyView(v.findViewById(R.id.nativeBody));
        v.setAdvertiserView(v.findViewById(R.id.nativeAdvertiser)); v.setCallToActionView(v.findViewById(R.id.nativeCta));
        ((android.widget.TextView)v.getHeadlineView()).setText(ad.getHeadline());
        if(ad.getBody()==null)v.getBodyView().setVisibility(View.GONE);else{v.getBodyView().setVisibility(View.VISIBLE);((android.widget.TextView)v.getBodyView()).setText(ad.getBody());}
        if(ad.getAdvertiser()==null)v.getAdvertiserView().setVisibility(View.GONE);else{v.getAdvertiserView().setVisibility(View.VISIBLE);((android.widget.TextView)v.getAdvertiserView()).setText(ad.getAdvertiser());}
        if(ad.getCallToAction()==null)v.getCallToActionView().setVisibility(View.GONE);else{v.getCallToActionView().setVisibility(View.VISIBLE);((android.widget.Button)v.getCallToActionView()).setText(ad.getCallToAction());}
        v.setNativeAd(ad);
    }
    private void showAppOpenIfReady(){
        if(!startedOnce){startedOnce=true;return;}
        if(System.currentTimeMillis()-lastAppOpenShown<30000)return;
        com.google.android.gms.ads.appopen.AppOpenAd.load(this,APP_OPEN_ID,new AdRequest.Builder().build(),new com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback(){
            @Override public void onAdLoaded(com.google.android.gms.ads.appopen.AppOpenAd ad){
                if(System.currentTimeMillis()-lastAppOpenShown<14400000L)return;
                appOpenShowing=true;
                if(bannerContainer!=null)bannerContainer.setVisibility(View.GONE);
                ad.setFullScreenContentCallback(new com.google.android.gms.ads.FullScreenContentCallback(){
                    @Override public void onAdDismissedFullScreenContent(){lastAppOpenShown=System.currentTimeMillis();appOpenShowing=false;if(bannerContainer!=null)bannerContainer.setVisibility(View.VISIBLE);}
                    @Override public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError e){appOpenShowing=false;if(bannerContainer!=null)bannerContainer.setVisibility(View.VISIBLE);}
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
    @Override protected void onStart(){super.onStart(); if(!firstSession) new android.os.Handler().postDelayed(()->showAppOpenIfReady(),800);}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleIncoming(i);}
    @Override protected void onDestroy(){if(filePathCallback!=null){filePathCallback.onReceiveValue(null);filePathCallback=null;}if(adView!=null)adView.destroy();if(nativeAd!=null)nativeAd.destroy();if(webView!=null)webView.destroy();super.onDestroy();}
}