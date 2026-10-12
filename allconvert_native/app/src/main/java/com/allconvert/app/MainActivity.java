package com.allconvert.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.core.content.FileProvider;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST=1001, SAVE_DOCUMENT_REQUEST=1002;
    private WebView webView; private AdView adView; private ValueCallback<Uri[]> filePathCallback;
    private byte[] pendingBytes;

    @Override public void onCreate(Bundle b){
        super.onCreate(b); setContentView(R.layout.activity_main);
        webView=findViewById(R.id.webView);
        WebSettings s=webView.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true); s.setAllowContentAccess(true);
        webView.addJavascriptInterface(new AndroidBridge(),"AndroidBridge");
        webView.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){handleIncoming(getIntent());}});
        webView.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){
            if(filePathCallback!=null)filePathCallback.onReceiveValue(null); filePathCallback=cb;
            try{startActivityForResult(p.createIntent(),FILE_CHOOSER_REQUEST);return true;}catch(Exception e){filePathCallback=null;cb.onReceiveValue(null);return false;}
        }});
        webView.loadUrl("file:///android_asset/index.html");
        MobileAds.initialize(this,status->runOnUiThread(this::showBanner));
    }

    private class AndroidBridge{
        @JavascriptInterface public void download(String n,String m,String b){saveDownload(safe(n),mime(m),dec(b));}
        @JavascriptInterface public void saveAs(String n,String m,String b){pendingBytes=dec(b);Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime(m));i.putExtra(Intent.EXTRA_TITLE,safe(n));startActivityForResult(i,SAVE_DOCUMENT_REQUEST);}
        @JavascriptInterface public void share(String n,String m,String b){shareOrOpen(safe(n),mime(m),dec(b));}
        @JavascriptInterface public void openWith(String n,String m,String b){shareOrOpen(safe(n),mime(m),dec(b));}
    }
    private byte[] dec(String s){try{return Base64.getDecoder().decode(s);}catch(Exception e){return new byte[0];}}
    private String safe(String s){return s==null||s.isEmpty()?"converted_file":s.replaceAll("[\\r\\n\\\\/:*?\"<>|]","_");}
    private String mime(String s){return s==null||s.isEmpty()?"application/octet-stream":s;}

    private void saveDownload(String n,String m,byte[] data){
        if(data.length==0)return; try{
            ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,n);v.put(MediaStore.Downloads.MIME_TYPE,m);v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/All Convert");
            Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)throw new Exception();
            try(OutputStream o=getContentResolver().openOutputStream(u)){o.write(data);}
            webView.evaluateJavascript("window.__allConvertNativeDone&&window.__allConvertNativeDone('download')",null);
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
    private void handleIncoming(Intent x){
        if(x==null)return;String a=x.getAction();Uri u=x.getData();
        if(Intent.ACTION_SEND.equals(a)){if(x.getClipData()!=null&&x.getClipData().getItemCount()>0)u=x.getClipData().getItemAt(0).getUri();else if(x.getParcelableExtra(Intent.EXTRA_STREAM)!=null)u=(Uri)x.getParcelableExtra(Intent.EXTRA_STREAM);}
        if(u==null||!(Intent.ACTION_VIEW.equals(a)||Intent.ACTION_SEND.equals(a)))return;
        try{byte[] d=read(u);String n=u.getLastPathSegment();if(n==null||n.isEmpty())n="shared_file";String m=getContentResolver().getType(u);String b=Base64.getEncoder().encodeToString(d);
            String js="window.__allConvertIncoming&&window.__allConvertIncoming("+q(n)+","+q(mime(m))+",'"+b+"')";webView.evaluateJavascript(js,null);
        }catch(Exception ignored){}
    }
    private String q(String s){return "'"+String.valueOf(s).replace("\\","\\\\").replace("'","\\'").replace("\n","\\n").replace("\r","\\r")+"'";}
    private byte[] read(Uri u)throws Exception{try(InputStream i=getContentResolver().openInputStream(u);ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=i.read(b))>0)o.write(b,0,n);return o.toByteArray();}}

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==FILE_CHOOSER_REQUEST){if(filePathCallback==null)return;Uri[] z=null;if(c==RESULT_OK&&d!=null){if(d.getClipData()!=null){int n=d.getClipData().getItemCount();z=new Uri[n];for(int i=0;i<n;i++)z[i]=d.getClipData().getItemAt(i).getUri();}else if(d.getData()!=null)z=new Uri[]{d.getData()};}filePathCallback.onReceiveValue(z);filePathCallback=null;}
        else if(r==SAVE_DOCUMENT_REQUEST){if(c==RESULT_OK&&d!=null&&d.getData()!=null&&pendingBytes!=null)try(OutputStream o=getContentResolver().openOutputStream(d.getData())){o.write(pendingBytes);}catch(Exception ignored){}pendingBytes=null;}
    }
    private void showBanner(){adView=new AdView(this);adView.setAdUnitId("ca-app-pub-9940728659432865/7572128943");adView.setAdSize(com.google.android.gms.ads.AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this,360));addContentView(adView,new android.view.ViewGroup.LayoutParams(-1,-2));adView.loadAd(new AdRequest.Builder().build());}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleIncoming(i);}
    @Override protected void onDestroy(){if(filePathCallback!=null){filePathCallback.onReceiveValue(null);filePathCallback=null;}if(adView!=null)adView.destroy();if(webView!=null)webView.destroy();super.onDestroy();}
}