package com.allconvert.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;
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
import android.widget.ScrollView;
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
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST=1001, SAVE_DOCUMENT_REQUEST=1002;
    private static final String APP_ID="ca-app-pub-9940728659432865~2706136459";
    private static final String BANNER_ID="ca-app-pub-9940728659432865/6234492932";
    private static final String INTERSTITIAL_ID="ca-app-pub-9940728659432865/4102978608";
    private static final String REWARDED_ID="ca-app-pub-9940728659432865/6537570257";
    private static final String NATIVE_ID="ca-app-pub-9940728659432865/5224488586";
    private static final String APP_OPEN_ID="ca-app-pub-9940728659432865/1006961841";
    private static final long APP_OPEN_COOLDOWN_MS=300000L;
    private static final String PREFS_ADS="all_convert_ads";
    private static final String PREF_LAST_APP_OPEN="last_app_open_shown";
    private static final String PREF_CONVERSION_COUNT="conversion_count";

    private WebView webView; private AdView adView; private NativeAd nativeAd; private ConsentInformation consentInformation; private TextView menuEntry, settingsEntry, historyEntry; private BillingClient billingClient; private final Map<String,ProductDetails> proProducts=new HashMap<>(); private volatile boolean isPro=false; private boolean adsInitialized=false; private android.widget.FrameLayout bannerContainer; private FrameLayout nativeContainer; private Button rewardButton; private boolean bannerLoaded=false; private volatile String currentAdPage="home";
    private ValueCallback<Uri[]> filePathCallback; private byte[] pendingBytes;
    private InterstitialAd interstitialAd; private RewardedAd rewardedAd;
    private int conversionCount=0; private long lastInterstitialShown=0L; private long lastAppOpenShown=0L; private boolean firstSession=true; private boolean appOpenShowing=false; private boolean skipNextInterstitial=false;

    @Override public void onCreate(Bundle b){
        super.onCreate(b); setContentView(R.layout.activity_main);
        lastAppOpenShown=getSharedPreferences(PREFS_ADS,MODE_PRIVATE).getLong(PREF_LAST_APP_OPEN,0L);conversionCount=getSharedPreferences(PREFS_ADS,MODE_PRIVATE).getInt(PREF_CONVERSION_COUNT,0);
        webView=findViewById(R.id.webView); bannerContainer=findViewById(R.id.bannerContainer); nativeContainer=findViewById(R.id.nativeAdContainer); menuEntry=findViewById(R.id.menuEntry); settingsEntry=findViewById(R.id.settingsEntry); historyEntry=findViewById(R.id.historyEntry); rewardButton=null;
        menuEntry.setOnClickListener(v->showMainMenuDialog());
        settingsEntry.setOnClickListener(v->showSettingsDialog());
        historyEntry.setOnClickListener(v->showHistoryDialog());
        initializeCloudSession();
        initializeBilling();
        WebSettings s=webView.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true); s.setAllowContentAccess(true);
        webView.addJavascriptInterface(new AndroidBridge(),"AndroidBridge");
        webView.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){handleIncoming(getIntent());v.evaluateJavascript("window.__allConvertSetPro&&window.__allConvertSetPro("+isPro+")",null);}});
        webView.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){
            if(filePathCallback!=null)filePathCallback.onReceiveValue(null); filePathCallback=cb;
            try{startActivityForResult(p.createIntent(),FILE_CHOOSER_REQUEST);return true;}catch(Exception e){filePathCallback=null;cb.onReceiveValue(null);return false;}
        }});
        webView.loadUrl("file:///android_asset/index.html");
        setupConsentManagement();
    }


    private static final String[] PRO_SUBS={"allconvert_pro_monthly","allconvert_pro_yearly"};
    private static final String PRO_LIFETIME="allconvert_pro_lifetime";
    private static final String HISTORY_PREFS="all_convert_history";
    private static final String HISTORY_KEY="records";
    private static final int MAX_HISTORY_ITEMS=50;
    private static final long MAX_HISTORY_BYTES=200L*1024L*1024L;
    private static final String SUPABASE_URL="https://hckvehpkzwyerkwpdvlz.supabase.co";
    private static final String SUPABASE_PUBLISHABLE_KEY="sb_publishable_W1nZcXZSwu3oeKA35iQeJA_AU579wju";
    private static final String CLOUD_PREFS="allconvert_secure_cloud_session";
    private volatile String cloudAccessToken="", cloudRefreshToken="", cloudUserId="";
    private SharedPreferences cloudPrefs;

    private void initializeBilling(){
        billingClient=BillingClient.newBuilder(this).setListener(this::onPurchasesUpdated).enablePendingPurchases().build();
        billingClient.startConnection(new com.android.billingclient.api.BillingClientStateListener(){
            @Override public void onBillingSetupFinished(BillingResult result){if(result.getResponseCode()==BillingClient.BillingResponseCode.OK){queryProPurchases();queryProProducts();}}
            @Override public void onBillingServiceDisconnected(){}
        });
    }
    private void queryProProducts(){
        if(billingClient==null||!billingClient.isReady())return;
        List<QueryProductDetailsParams.Product> subs=new ArrayList<>();
        for(String id:PRO_SUBS)subs.add(QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(BillingClient.ProductType.SUBS).build());
        billingClient.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(subs).build(),(result,details)->{if(result.getResponseCode()==BillingClient.BillingResponseCode.OK)for(ProductDetails d:details)proProducts.put(d.getProductId(),d);});
        List<QueryProductDetailsParams.Product> inapp=new ArrayList<>();
        inapp.add(QueryProductDetailsParams.Product.newBuilder().setProductId(PRO_LIFETIME).setProductType(BillingClient.ProductType.INAPP).build());
        billingClient.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(inapp).build(),(result,details)->{if(result.getResponseCode()==BillingClient.BillingResponseCode.OK)for(ProductDetails d:details)proProducts.put(d.getProductId(),d);});
    }
    private void queryProPurchases(){
        if(billingClient==null||!billingClient.isReady())return;
        final boolean[] active={false,false};
        billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),(result,purchases)->{active[0]=hasValidProPurchase(result,purchases);setProState(active[0]||active[1]);});
        billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),(result,purchases)->{active[1]=hasValidProPurchase(result,purchases);setProState(active[0]||active[1]);});
    }
    private boolean hasValidProPurchase(BillingResult result,List<Purchase> purchases){
        if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK||purchases==null)return false;
        boolean found=false;
        for(Purchase purchase:purchases){
            if(purchase.getPurchaseState()!=Purchase.PurchaseState.PURCHASED)continue;
            boolean matches=false;
            for(String product:purchase.getProducts())if(product.equals(PRO_LIFETIME)||product.equals(PRO_SUBS[0])||product.equals(PRO_SUBS[1]))matches=true;
            if(!matches)continue;
            found=true;
            if(!purchase.isAcknowledged())billingClient.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build(),ack->{});
        }
        return found;
    }
    private void onPurchasesUpdated(BillingResult result,List<Purchase> purchases){
        if(result.getResponseCode()==BillingClient.BillingResponseCode.OK){if(purchases!=null)hasValidProPurchase(result,purchases);queryProPurchases();}
        else if(result.getResponseCode()!=BillingClient.BillingResponseCode.USER_CANCELED)Toast.makeText(this,"Pembelian tidak berjaya. Cuba lagi.",Toast.LENGTH_LONG).show();
    }
    private void setProState(boolean enabled){runOnUiThread(()->{
        isPro=enabled;
        if(historyEntry!=null)historyEntry.setVisibility(enabled?View.VISIBLE:View.GONE);
        if(webView!=null)webView.evaluateJavascript("window.__allConvertSetPro&&window.__allConvertSetPro("+enabled+")",null);
        if(enabled){
            adsInitialized=false;
            if(bannerContainer!=null){bannerContainer.setVisibility(View.GONE);bannerContainer.removeAllViews();if(bannerContainer.getLayoutParams()!=null){bannerContainer.getLayoutParams().height=0;bannerContainer.requestLayout();}}
            if(adView!=null){adView.destroy();adView=null;}bannerLoaded=false;
            if(nativeContainer!=null){nativeContainer.setVisibility(View.GONE);nativeContainer.removeAllViews();}
            if(nativeAd!=null){nativeAd.destroy();nativeAd=null;}
            interstitialAd=null;rewardedAd=null;if(rewardButton!=null)rewardButton.setVisibility(View.GONE);
            if(appOpenShowing)appOpenShowing=false;
            if(!cloudAccessToken.isEmpty())syncLocalHistoryToCloud();
        }else if(consentInformation!=null&&consentInformation.canRequestAds()){
            initializeAds();
        }
    });}
    private boolean billingClientReady(){return billingClient!=null&&billingClient.isReady();}
    private android.app.Dialog proDialog;
    private ProductDetails selectedProProduct;

    private String proPrice(ProductDetails product){
        if(product==null)return "Harga dipaparkan oleh Google Play";
        if(product.getOneTimePurchaseOfferDetails()!=null)return product.getOneTimePurchaseOfferDetails().getFormattedPrice();
        if(product.getSubscriptionOfferDetails()!=null&&!product.getSubscriptionOfferDetails().isEmpty()){
            List<ProductDetails.PricingPhase> phases=product.getSubscriptionOfferDetails().get(0).getPricingPhases().getPricingPhaseList();
            if(!phases.isEmpty())return phases.get(phases.size()-1).getFormattedPrice();
        }
        return "Harga dipaparkan oleh Google Play";
    }
    private android.graphics.drawable.GradientDrawable proBg(int color,int stroke){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
        int r=Color.red(color),g=Color.green(color),b=Color.blue(color);
        int light=Color.rgb(Math.min(255,r+Math.round((255-r)*0.34f)),Math.min(255,g+Math.round((255-g)*0.34f)),Math.min(255,b+Math.round((255-b)*0.34f)));
        int deep=Color.rgb(Math.max(0,r-10),Math.max(0,g-10),Math.max(0,b-10));
        d.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TL_BR);
        d.setColors(new int[]{light,color,deep});d.setCornerRadius(dp(20));d.setStroke(dp(1),stroke);return d;
    }
    private TextView proText(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private Button proButton(String label,boolean primary){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);
        b.setTextColor(primary?Color.WHITE:Color.rgb(221,94,0));
        b.setBackground(proBg(primary?Color.rgb(255,126,0):Color.WHITE,primary?Color.rgb(255,126,0):Color.rgb(255,170,90)));
        b.setMinHeight(dp(48));return b;
    }
    private void addProFeature(LinearLayout parent,String value){
        TextView t=proText("✓  "+value,13,Color.rgb(55,65,82),false);
        t.setPadding(dp(4),dp(5),dp(4),dp(5));parent.addView(t,new LinearLayout.LayoutParams(-1,-2));
    }
    private void showProPlansDialog(){
        proDialog=new android.app.Dialog(this);
        proDialog.setContentView(new LinearLayout(this));
        showProScreen(0,null);
        android.view.Window window=proDialog.getWindow();
        if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setLayout(-1,-1);}
        proDialog.show();
        if(proDialog.getWindow()!=null)proDialog.getWindow().setLayout(-1,-1);
    }
    private void showProScreen(int screen,ProductDetails selected){
        if(proDialog==null)return;
        selectedProProduct=selected;
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(12),dp(18),dp(16));page.setBackground(proBg(Color.rgb(250,250,253),Color.rgb(255,255,255)));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=proText(screen==0?"✕":"‹  Kembali",15,Color.rgb(48,55,72),true);
        back.setPadding(dp(8),dp(10),dp(14),dp(10));back.setOnClickListener(v->{if(screen==0)proDialog.dismiss();else showProScreen(screen==1?0:screen==2?1:2,selectedProProduct);});
        top.addView(back,new LinearLayout.LayoutParams(-2,dp(44)));
        TextView title=proText(screen==0?"Langganan Pro":screen==1?"Pembayaran":screen==2?"Status Langganan":screen==3?"Pembayaran Berjaya!":"Pengurusan Langganan",19,Color.rgb(24,30,45),true);
        title.setGravity(Gravity.CENTER);top.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));
        top.addView(new View(this),new LinearLayout.LayoutParams(dp(42),dp(1)));page.addView(top);
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,dp(8),0,dp(10));
        TextView hero=proText("♛  All Convert Pro\nLebih banyak fungsi. Lebih mudah.\nAkses premium untuk kerja harian anda.",18,Color.WHITE,true);
        hero.setGravity(Gravity.CENTER);hero.setPadding(dp(18),dp(20),dp(18),dp(20));hero.setBackground(proBg(Color.rgb(255,139,0),Color.rgb(255,210,150)));hero.setElevation(dp(7));
        body.addView(hero,new LinearLayout.LayoutParams(-1,dp(132)));
        if(screen==0){
            TextView intro=proText("Pilih pelan yang sesuai",15,Color.rgb(35,43,60),true);intro.setPadding(dp(4),dp(16),0,dp(8));body.addView(intro);
            for(String id:new String[]{PRO_SUBS[0],PRO_SUBS[1],PRO_LIFETIME}){
                ProductDetails product=proProducts.get(id);
                boolean lifetime=id.equals(PRO_LIFETIME);
                String name=lifetime?"Seumur Hidup":id.equals(PRO_SUBS[0])?"Bulanan":"Tahunan";
                String suffix=lifetime?" • sekali bayar":id.equals(PRO_SUBS[0])?" / bulan":" / tahun";
                LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(14),dp(16),dp(14));card.setBackground(proBg(Color.WHITE,Color.rgb(239,174,110)));card.setElevation(dp(4));
                TextView h=proText("All Convert Pro • "+name,lifetime?17:16,Color.rgb(25,32,48),true);card.addView(h);
                TextView price=proText(proPrice(product)+suffix,20,Color.rgb(225,100,0),true);price.setPadding(0,dp(7),0,dp(6));card.addView(price);
                addProFeature(card,"Pengalaman tanpa iklan semasa Pro aktif");
                addProFeature(card,"History sehingga 50 fail / 200 MB keseluruhan (maksimum 50 MB setiap fail)");
                addProFeature(card,"Pulihkan pembelian melalui Google Play");
                Button choose=proButton(lifetime?"Beli Akses Seumur Hidup":"Pilih Pelan "+name,true);choose.setOnClickListener(v->{if(product==null){queryProProducts();Toast.makeText(this,"Pelan belum tersedia di Google Play. Pastikan produk dan negara jualan telah diaktifkan.",Toast.LENGTH_LONG).show();}else showProScreen(1,product);});
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(10);body.addView(card,cp);card.addView(choose);
                LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-1,dp(12));body.addView(new View(this),gap);
            }
            TextView note=proText("Semua pembelian diproses oleh Google Play. Harga dan mata wang dipaparkan mengikut negara akaun Google Play anda. Langganan boleh diurus atau dibatalkan melalui Google Play.",12,Color.rgb(105,112,128),false);note.setPadding(dp(4),dp(6),dp(4),dp(8));body.addView(note);
            Button status=proButton("Lihat Status Langganan",false);status.setOnClickListener(v->showProScreen(2,null));body.addView(status);
        }else if(screen==1){
            TextView detail=proText("Pelan dipilih: "+(selected==null?"All Convert Pro":selected.getName())+"\nHarga: "+proPrice(selected),15,Color.rgb(35,43,60),true);
            detail.setPadding(dp(16),dp(16),dp(16),dp(16));detail.setBackground(proBg(Color.WHITE,Color.rgb(225,231,240)));body.addView(detail);
            TextView methods=proText("Pembayaran melalui Google Play",16,Color.rgb(30,38,55),true);methods.setPadding(dp(4),dp(18),0,dp(8));body.addView(methods);
            TextView google=proText("Google Play\nPembelian selamat menggunakan kaedah pembayaran yang tersedia untuk negara akaun anda. Harga dipaparkan dalam mata wang tempatan oleh Google Play.",14,Color.rgb(35,43,60),true);
            google.setPadding(dp(16),dp(16),dp(16),dp(16));google.setBackground(proBg(Color.WHITE,Color.rgb(239,174,110)));google.setElevation(dp(3));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2);gp.topMargin=dp(4);body.addView(google,gp);
            Button pay=proButton("Teruskan dengan Google Play",true);pay.setOnClickListener(v->{if(selected!=null){proDialog.dismiss();launchProPurchase(selected);}else Toast.makeText(this,"Sila pilih pelan dahulu.",Toast.LENGTH_SHORT).show();});LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=dp(16);body.addView(pay,pp);
            TextView legal=proText("Akses Pro hanya diaktifkan selepas Google Play mengesahkan pembelian. Langganan bulanan dan tahunan diperbaharui secara automatik mengikut terma Google Play; pelan seumur hidup ialah bayaran sekali sahaja.",12,Color.rgb(105,112,128),false);legal.setPadding(dp(4),dp(12),dp(4),0);body.addView(legal);
        }else if(screen==2||screen==3||screen==4){
            if(screen==3){
                TextView success=proText("✓\nPembayaran Berjaya!\nAll Convert Pro telah diaktifkan selepas pengesahan Google Play.",18,Color.rgb(25,130,75),true);success.setGravity(Gravity.CENTER);success.setPadding(dp(12),dp(24),dp(12),dp(24));success.setBackground(proBg(Color.rgb(235,251,242),Color.rgb(160,225,185)));body.addView(success);
                Button done=proButton("Selesai",true);done.setOnClickListener(v->proDialog.dismiss());body.addView(done);
            }else{
                TextView status=proText(isPro?"●  All Convert Pro — AKTIF":"○  All Convert Pro — BELUM AKTIF",17,isPro?Color.rgb(20,145,80):Color.rgb(105,112,128),true);
                status.setPadding(dp(16),dp(18),dp(16),dp(18));status.setBackground(proBg(Color.WHITE,Color.rgb(225,231,240)));body.addView(status);
                TextView details=proText(isPro?"Akses Pro tersedia pada peranti ini.\nStatus akan disemak semula melalui Google Play.":"Tiada pembelian Pro aktif ditemui.\nPulihkan pembelian untuk menyemak semula transaksi terdahulu.",14,Color.rgb(55,65,82),false);details.setPadding(dp(8),dp(14),dp(8),dp(14));body.addView(details);
                Button restore=proButton("Pulihkan Pembelian",false);restore.setOnClickListener(v->{queryProPurchases();Toast.makeText(this,"Sedang menyemak pembelian Google Play…",Toast.LENGTH_SHORT).show();});body.addView(restore);
                Button manage=proButton("Pengurusan / Batal Langganan",false);manage.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/account/subscriptions?package=com.allconvert.app")));}catch(Exception e){Toast.makeText(this,"Halaman langganan Google Play tidak dapat dibuka.",Toast.LENGTH_SHORT).show();}});LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=dp(8);body.addView(manage,mp);
            }
        }
        scroll.addView(body);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button close=proButton("Tutup",false);close.setOnClickListener(v->proDialog.dismiss());page.addView(close,new LinearLayout.LayoutParams(-1,dp(48)));
        proDialog.setContentView(page);
    }
    private void launchProPurchase(ProductDetails product){
        BillingFlowParams.ProductDetailsParams.Builder detail=BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product);
        if(BillingClient.ProductType.SUBS.equals(product.getProductType())){
            if(product.getSubscriptionOfferDetails()==null||product.getSubscriptionOfferDetails().isEmpty()){Toast.makeText(this,"Tawaran langganan tidak tersedia.",Toast.LENGTH_LONG).show();return;}
            detail.setOfferToken(product.getSubscriptionOfferDetails().get(0).getOfferToken());
        }
        BillingFlowParams params=BillingFlowParams.newBuilder().setProductDetailsParamsList(java.util.Collections.singletonList(detail.build())).build();
        billingClient.launchBillingFlow(this,params);
    }
    private JSONArray readHistory(){try{return new JSONArray(getSharedPreferences(HISTORY_PREFS,MODE_PRIVATE).getString(HISTORY_KEY,"[]"));}catch(Exception e){return new JSONArray();}}
    private void recordHistory(String name,String type,byte[] data){
        if(!isPro||data==null||data.length==0)return;
        try{
            File dir=new File(getFilesDir(),"history");if(!dir.exists()&&!dir.mkdirs())return;
            String stored=System.currentTimeMillis()+"_"+UUID.randomUUID().toString()+".bin";
            try(FileOutputStream out=new FileOutputStream(new File(dir,stored))){out.write(data);}
            JSONArray old=readHistory(),next=new JSONArray();
            long totalBytes=data.length;
            JSONObject item=new JSONObject();item.put("name",name);item.put("mime",type);item.put("file",stored);item.put("time",System.currentTimeMillis());item.put("size",data.length);item.put("cloud_synced",false);next.put(item);
            for(int i=0;i<old.length();i++){
                JSONObject previous=old.optJSONObject(i);if(previous==null)continue;
                long previousSize=previous.optLong("size",0L);
                if(next.length()<MAX_HISTORY_ITEMS && totalBytes+previousSize<=MAX_HISTORY_BYTES){next.put(previous);totalBytes+=previousSize;}
                else new File(dir,previous.optString("file","")).delete();
            }
            getSharedPreferences(HISTORY_PREFS,MODE_PRIVATE).edit().putString(HISTORY_KEY,next.toString()).apply();
            if(isPro&&cloudAccessToken!=null&&!cloudAccessToken.isEmpty())syncLocalHistoryToCloud();
        }catch(Exception ignored){}
    }

    private SecretKey getCloudEncryptionKey()throws Exception{
        KeyStore keyStore=KeyStore.getInstance("AndroidKeyStore");keyStore.load(null);
        String alias="allconvert_cloud_session_key";
        if(!keyStore.containsAlias(alias)){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
            generator.generateKey();
        }
        return (SecretKey)keyStore.getKey(alias,null);
    }
    private String encryptCloudValue(String value)throws Exception{
        if(value==null||value.isEmpty())return "";
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,getCloudEncryptionKey());
        byte[] iv=cipher.getIV(),encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),combined=new byte[iv.length+encrypted.length];
        System.arraycopy(iv,0,combined,0,iv.length);System.arraycopy(encrypted,0,combined,iv.length,encrypted.length);
        return android.util.Base64.encodeToString(combined,android.util.Base64.NO_WRAP);
    }
    private String decryptCloudValue(String value){
        if(value==null||value.isEmpty())return "";
        try{byte[] combined=android.util.Base64.decode(value,android.util.Base64.NO_WRAP);byte[] iv=java.util.Arrays.copyOfRange(combined,0,12),encrypted=java.util.Arrays.copyOfRange(combined,12,combined.length);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,getCloudEncryptionKey(),new GCMParameterSpec(128,iv));return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);}catch(Exception e){return "";}
    }
    private void initializeCloudSession(){
        cloudPrefs=getSharedPreferences(CLOUD_PREFS,MODE_PRIVATE);
        cloudAccessToken=decryptCloudValue(cloudPrefs.getString("access_token",""));
        cloudRefreshToken=decryptCloudValue(cloudPrefs.getString("refresh_token",""));
        cloudUserId=cloudPrefs.getString("user_id","");
    }
    private void storeCloudSession(JSONObject session)throws Exception{
        JSONObject user=session.optJSONObject("user");
        String access=session.optString("access_token",""),refresh=session.optString("refresh_token","");
        String uid=user==null?"":user.optString("id","");
        if(access.isEmpty()||uid.isEmpty())throw new Exception("Sesi akaun belum disahkan. Semak e-mel pengesahan jika diminta.");
        cloudAccessToken=access;cloudRefreshToken=refresh;cloudUserId=uid;
        if(cloudPrefs!=null)cloudPrefs.edit().putString("access_token",encryptCloudValue(access)).putString("refresh_token",encryptCloudValue(refresh)).putString("user_id",uid).apply();
    }
    private byte[] cloudRequest(String method,String path,byte[] body,String contentType,boolean retry)throws Exception{
        HttpURLConnection conn=null;
        try{
            conn=(HttpURLConnection)new URL(SUPABASE_URL+path).openConnection();
            conn.setRequestMethod(method);conn.setConnectTimeout(20000);conn.setReadTimeout(30000);
            conn.setRequestProperty("apikey",SUPABASE_PUBLISHABLE_KEY);
            String token=(path.startsWith("/auth/v1/signup")||path.startsWith("/auth/v1/token")||cloudAccessToken==null||cloudAccessToken.isEmpty())?SUPABASE_PUBLISHABLE_KEY:cloudAccessToken;
            conn.setRequestProperty("Authorization","Bearer "+token);
            conn.setRequestProperty("Accept","application/json");
            if(contentType!=null)conn.setRequestProperty("Content-Type",contentType);
            if("POST".equals(method)||"PUT".equals(method)||"PATCH".equals(method)){conn.setDoOutput(true);if(body!=null)try(OutputStream out=conn.getOutputStream()){out.write(body);}}
            int code=conn.getResponseCode();
            InputStream stream=code>=200&&code<300?conn.getInputStream():conn.getErrorStream();
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            if(stream!=null)try(InputStream in=stream){byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);}
            byte[] result=out.toByteArray();
            if(code==401&&retry&&!cloudRefreshToken.isEmpty()&&!path.startsWith("/auth/v1/token")){
                refreshCloudSession();return cloudRequest(method,path,body,contentType,false);
            }
            if(code<200||code>=300)throw new Exception("Supabase "+code+": "+new String(result,StandardCharsets.UTF_8));
            return result;
        }finally{if(conn!=null)conn.disconnect();}
    }
    private byte[] cloudRequest(String method,String path,byte[] body,String contentType)throws Exception{return cloudRequest(method,path,body,contentType,true);}
    private String cloudText(String method,String path,String body)throws Exception{
        byte[] result=cloudRequest(method,path,body==null?null:body.getBytes(StandardCharsets.UTF_8),"application/json");
        return new String(result,StandardCharsets.UTF_8);
    }
    private void refreshCloudSession()throws Exception{
        if(cloudRefreshToken.isEmpty())throw new Exception("Sesi tamat. Log masuk semula.");
        JSONObject payload=new JSONObject();payload.put("refresh_token",cloudRefreshToken);
        byte[] result=cloudRequest("POST","/auth/v1/token?grant_type=refresh_token",payload.toString().getBytes(StandardCharsets.UTF_8),"application/json",false);
        storeCloudSession(new JSONObject(new String(result,StandardCharsets.UTF_8)));
    }
    private void logoutCloud(){
        new Thread(()->{
            try{if(!cloudAccessToken.isEmpty())cloudRequest("POST","/auth/v1/logout",null,null);}
            catch(Exception ignored){}
            cloudAccessToken="";cloudRefreshToken="";cloudUserId="";
            if(cloudPrefs!=null)cloudPrefs.edit().clear().apply();
            runOnUiThread(()->Toast.makeText(this,"Akaun Cloud telah dilog keluar. Fail setempat kekal pada telefon.",Toast.LENGTH_LONG).show());
        }).start();
    }
    private void showCloudAccountDialog(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(20),dp(8),dp(20),dp(4));
        EditText email=new EditText(this);email.setSingleLine(true);email.setHint("E-mel");email.setInputType(33);panel.addView(email);
        EditText password=new EditText(this);password.setSingleLine(true);password.setHint("Kata laluan");password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);panel.addView(password);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Akaun All Convert Cloud").setMessage("Log masuk atau daftar akaun untuk menyimpan History Pro dalam cloud dan memulihkannya pada telefon lain.").setView(panel).setPositiveButton("Log Masuk",null).setNeutralButton("Daftar Akaun",null).setNegativeButton("History Peranti",null).create();
        dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{String e=email.getText().toString().trim(),p=password.getText().toString();if(e.isEmpty()||p.length()<8){Toast.makeText(this,"Masukkan e-mel dan kata laluan sekurang-kurangnya 8 aksara.",Toast.LENGTH_LONG).show();return;}dialog.dismiss();authenticateCloud(e,p,false);});
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x->{String e=email.getText().toString().trim(),p=password.getText().toString();if(e.isEmpty()||p.length()<6){Toast.makeText(this,"Masukkan e-mel dan kata laluan sekurang-kurangnya 6 aksara.",Toast.LENGTH_LONG).show();return;}dialog.dismiss();authenticateCloud(e,p,true);});
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(x->{dialog.dismiss();showLocalHistoryDialog();});
        });dialog.show();
    }
    private void authenticateCloud(String email,String password,boolean register){
        Toast.makeText(this,register?"Mendaftar akaun…":"Log masuk…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                JSONObject body=new JSONObject();body.put("email",email);body.put("password",password);
                String endpoint=register?"/auth/v1/signup":"/auth/v1/token?grant_type=password";
                String response=cloudText("POST",endpoint,body.toString());
                JSONObject session=new JSONObject(response);
                if(session.has("access_token")){storeCloudSession(session);if(isPro)syncLocalHistoryToCloud();runOnUiThread(()->{Toast.makeText(this,"Akaun cloud berjaya disambungkan.",Toast.LENGTH_LONG).show();showHistoryDialog();});}
                else runOnUiThread(()->Toast.makeText(this,"Pendaftaran diterima. Sahkan e-mel dahulu, kemudian log masuk.",Toast.LENGTH_LONG).show());
            }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Sambungan akaun gagal: "+e.getMessage(),Toast.LENGTH_LONG).show());}
        }).start();
    }
    private String encodePath(String value){try{return java.net.URLEncoder.encode(value,"UTF-8").replace("+","%20");}catch(Exception e){return value;}}
    private String encodeObjectPath(String value){String[] parts=value.split("/");StringBuilder out=new StringBuilder();for(String part:parts){if(out.length()>0)out.append("/");out.append(encodePath(part));}return out.toString();}
    private void syncLocalHistoryToCloud(){
        if(!isPro||cloudAccessToken.isEmpty()||cloudUserId.isEmpty())return;
        new Thread(()->{
            JSONArray records=readHistory();boolean changed=false;
            for(int i=0;i<records.length();i++){
                JSONObject item=records.optJSONObject(i);if(item==null||item.optBoolean("cloud_synced",false))continue;
                try{
                    File local=new File(new File(getFilesDir(),"history"),item.optString("file",""));
                    if(!local.exists())continue;
                    byte[] bytes=readFileBytes(local);String name=item.optString("name","converted_file");
                    String objectPath=cloudUserId+"/"+UUID.randomUUID().toString()+"_"+safe(name);
                    if(bytes.length>104857600L||!pruneCloudHistory(bytes.length))continue;
                    cloudRequest("POST","/storage/v1/object/allconvert-history/"+encodeObjectPath(objectPath),bytes,item.optString("mime","application/octet-stream"));
                    try{
                        JSONObject row=new JSONObject();row.put("user_id",cloudUserId);row.put("file_name",name);row.put("mime_type",item.optString("mime","application/octet-stream"));row.put("object_path",objectPath);row.put("file_size",bytes.length);
                        cloudText("POST","/rest/v1/allconvert_history",row.toString());
                        item.put("cloud_synced",true);item.put("cloud_path",objectPath);changed=true;
                    }catch(Exception metadataError){
                        try{cloudRequest("DELETE","/storage/v1/object/allconvert-history/"+encodeObjectPath(objectPath),null,null);}catch(Exception cleanupError){android.util.Log.w("AllConvertCloud","Orphan cleanup failed",cleanupError);}
                        throw metadataError;
                    }
                }catch(Exception e){android.util.Log.w("AllConvertCloud","History sync failed",e);}
            }
            if(changed)getSharedPreferences(HISTORY_PREFS,MODE_PRIVATE).edit().putString(HISTORY_KEY,records.toString()).apply();
        }).start();
    }
    private boolean pruneCloudHistory(long incomingBytes)throws Exception{
        if(incomingBytes<=0||incomingBytes>104857600L)return false;
        JSONArray existing=new JSONArray(cloudText("GET","/rest/v1/allconvert_history?select=id,object_path,file_size&order=created_at.asc&limit=50",null));
        long total=incomingBytes;for(int i=0;i<existing.length();i++)total+=existing.optJSONObject(i).optLong("file_size",0L);
        int index=0;
        while((existing.length()-index>=50||total>MAX_HISTORY_BYTES)&&index<existing.length()){
            JSONObject oldest=existing.optJSONObject(index++);if(oldest==null)continue;
            String objectPath=oldest.optString("object_path",""),id=oldest.optString("id","");
            cloudRequest("DELETE","/storage/v1/object/allconvert-history/"+encodeObjectPath(objectPath),null,null);
            cloudText("DELETE","/rest/v1/allconvert_history?id=eq."+encodePath(id),null);
            total-=oldest.optLong("file_size",0L);
        }
        return total<=MAX_HISTORY_BYTES&&existing.length()-index<50;
    }
    private void loadCloudHistory(){
        Toast.makeText(this,"Memuatkan History cloud…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                String json=cloudText("GET","/rest/v1/allconvert_history?select=id,file_name,mime_type,object_path,file_size,created_at&order=created_at.desc&limit=50",null);
                JSONArray records=new JSONArray(json);
                runOnUiThread(()->showCloudHistoryList(records));
            }catch(Exception e){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("History Cloud tidak tersedia").setMessage("Sambungan cloud gagal. Anda masih boleh buka History yang tersimpan pada telefon.\n\n"+e.getMessage()).setPositiveButton("History Peranti",(d,w)->showLocalHistoryDialog()).setNegativeButton("Tutup",null).show());}
        }).start();
    }
    private void showCloudHistoryList(JSONArray records){
        if(records.length()==0){new AlertDialog.Builder(this).setTitle("History Pro • Cloud").setMessage("Belum ada fail dalam History cloud. Fail baharu akan disimpan selepas penukaran apabila akaun Pro dan sambungan internet tersedia.").setPositiveButton("Tutup",null).setNeutralButton("History Peranti",(d,w)->showLocalHistoryDialog()).show();return;}
        String[] labels=new String[records.length()];
        for(int i=0;i<records.length();i++){JSONObject item=records.optJSONObject(i);String stamp=item==null?"":item.optString("created_at","");if(stamp.length()>16)stamp=stamp.substring(0,16).replace('T',' ');labels[i]=(item==null?"Fail":item.optString("file_name","Fail"))+"  •  "+stamp;}
        new AlertDialog.Builder(this).setTitle("History Pro • Cloud").setItems(labels,(d,which)->showCloudHistoryItem(records.optJSONObject(which))).setNegativeButton("Tutup",null).setNeutralButton("History Peranti",(d,w)->showLocalHistoryDialog()).show();
    }
    private void showCloudHistoryItem(JSONObject item){
        if(item==null)return;
        String[] actions={"Download semula","Share","Padam daripada Cloud"};
        new AlertDialog.Builder(this).setTitle(item.optString("file_name","Fail")).setItems(actions,(d,which)->{
            String id=item.optString("id",""),path=item.optString("object_path",""),name=item.optString("file_name","converted_file"),type=item.optString("mime_type","application/octet-stream");
            new Thread(()->{
                try{
                    if(which==2){cloudRequest("DELETE","/storage/v1/object/allconvert-history/"+encodeObjectPath(path),null,null);cloudText("DELETE","/rest/v1/allconvert_history?id=eq."+encodePath(id),null);runOnUiThread(()->{Toast.makeText(this,"Rekod cloud dipadam.",Toast.LENGTH_SHORT).show();loadCloudHistory();});}
                    else{
                        byte[] bytes=cloudRequest("GET","/storage/v1/object/authenticated/allconvert-history/"+encodeObjectPath(path),null,null);
                        runOnUiThread(()->{if(which==0)saveDownload(name,type,bytes);else shareOrOpen(name,type,bytes);});
                    }
                }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Operasi History gagal: "+e.getMessage(),Toast.LENGTH_LONG).show());}
            }).start();
        }).setNegativeButton("Kembali",null).show();
    }
    private void showLocalHistoryDialog(){
        JSONArray records=readHistory();
        if(records.length()==0){new AlertDialog.Builder(this).setTitle("History Peranti").setMessage("Tiada rekod setempat.").setPositiveButton("Tutup",null).show();return;}
        String[] labels=new String[records.length()];
        for(int i=0;i<records.length();i++){JSONObject item=records.optJSONObject(i);labels[i]=(item==null?"Fail":item.optString("name","Fail"));}
        new AlertDialog.Builder(this).setTitle("History Peranti").setItems(labels,(d,w)->showHistoryItem(w)).setNegativeButton("Tutup",null).show();
    }

    private void showHistoryDialog(){
        if(!isPro){showProPlansDialog();return;}
        showLocalHistoryDialog();
        if(cloudAccessToken!=null&&!cloudAccessToken.isEmpty())syncLocalHistoryToCloud();
    }
    private void showHistoryItem(int index){
        JSONArray records=readHistory();JSONObject item=records.optJSONObject(index);if(item==null)return;
        String[] actions={"Maklumat fail","Download semula","Share","Padam daripada History"};
        new AlertDialog.Builder(this).setTitle(item.optString("name","Fail")).setItems(actions,(dialog,choice)->{
            File file=new File(new File(getFilesDir(),"history"),item.optString("file",""));
            if(choice==3){file.delete();JSONArray kept=new JSONArray();for(int i=0;i<records.length();i++)if(i!=index)kept.put(records.optJSONObject(i));getSharedPreferences(HISTORY_PREFS,MODE_PRIVATE).edit().putString(HISTORY_KEY,kept.toString()).apply();showLocalHistoryDialog();return;}
            try{
                byte[] bytes=readFileBytes(file);String name=item.optString("name","converted_file"),type=item.optString("mime","application/octet-stream");
                if(choice==0)new AlertDialog.Builder(this).setTitle("Maklumat fail").setMessage(name+"\n"+type+"\n"+android.text.format.Formatter.formatShortFileSize(this,bytes.length)).setPositiveButton("Tutup",null).show();
                else if(choice==1)saveDownload(name,type,bytes);
                else shareOrOpen(name,type,bytes);
            }catch(Exception e){Toast.makeText(this,"Fail History tidak dapat dibaca.",Toast.LENGTH_LONG).show();}
        }).setNegativeButton("Kembali",null).show();
    }
    private byte[] readFileBytes(File file)throws Exception{try(InputStream in=new java.io.FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))>0)out.write(buffer,0,n);return out.toByteArray();}}

    private void setupConsentManagement(){
        consentInformation=UserMessagingPlatform.getConsentInformation(this);
        ConsentRequestParameters params=new ConsentRequestParameters.Builder().build();
        consentInformation.requestConsentInfoUpdate(this,params,()->{
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(this,error->{
                updatePrivacyOptionsEntry();
                if(consentInformation.canRequestAds()) initializeAds();
            });
        },error->{
            updatePrivacyOptionsEntry();
            if(consentInformation.canRequestAds()) initializeAds();
        });
    }

    private void initializeAds(){
        if(isPro||adsInitialized)return;
        adsInitialized=true;
        MobileAds.initialize(this,status->runOnUiThread(()->{
            showBanner(); loadInterstitial(); loadRewarded(); loadNative();
            new android.os.Handler().postDelayed(()->showAppOpenIfReady(),900);
        }));
    }

    private void updatePrivacyOptionsEntry(){ /* The privacy action is displayed inside the app information panel when required. */ }

    private void showMainMenuDialog(){
        final String[] labels=isPro
            ? new String[]{"Mula / Penukar Fail","Pelan All Convert Pro","History Pro","Tetapan & Maklumat"}
            : new String[]{"Mula / Penukar Fail","Pelan All Convert Pro","History & Muat Turun (Pro)","Tetapan & Maklumat"};
        new AlertDialog.Builder(this)
            .setTitle("Menu All Convert")
            .setItems(labels,(dialog,which)->{
                if(which==0){webView.evaluateJavascript("window.scrollTo({top:0,behavior:'smooth'})",null);}
                else if(which==1)showProPlansDialog();
                else if(which==2)showHistoryDialog();
                else showSettingsDialog();
            }).setNegativeButton("Tutup",null).show();
    }

    private void showSettingsDialog(){
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18),dp(14),dp(18),dp(8));
        panel.setBackground(proBg(Color.rgb(255,253,249),Color.rgb(255,255,255)));
        TextView heading=new TextView(this);
        heading.setText("Tetapan All Convert");
        heading.setTextSize(21);
        heading.setTypeface(null,android.graphics.Typeface.BOLD);
        heading.setTextColor(Color.rgb(25,38,62));
        heading.setPadding(dp(4),dp(4),dp(4),dp(2));
        panel.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        TextView version=new TextView(this);
        version.setText("Maklumat & undang-undang serta kawalan privasi iklan");
        version.setTextSize(12);
        version.setTextColor(Color.rgb(105,117,138));
        version.setPadding(dp(4),0,dp(4),dp(12));
        panel.addView(version,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Tetapan").setView(panel).setNegativeButton("Tutup",null).create();
        addInfoRow(panel,"Langganan Pro","Buka ciri premium dan History",()->{dialog.dismiss();showProPlansDialog();});
        if(!isPro)addInfoRow(panel,"Tonton Iklan Rewarded","Tonton video untuk langkau iklan sela seterusnya",()->{dialog.dismiss();showRewardedIfReady();});
        addInfoRow(panel,"Pulihkan Pembelian","Semak semula langganan atau pembelian terdahulu",()->{dialog.dismiss();queryProPurchases();Toast.makeText(this,"Sedang menyemak pembelian Google Play…",Toast.LENGTH_SHORT).show();});
        if(isPro||!cloudAccessToken.isEmpty())addInfoRow(panel,"Akaun Cloud",cloudAccessToken.isEmpty()?"Sambung akaun untuk History cloud":"Akaun cloud disambungkan",()->{dialog.dismiss();if(cloudAccessToken.isEmpty())showCloudAccountDialog();else new AlertDialog.Builder(this).setTitle("Akaun Cloud").setMessage("Akaun All Convert Cloud sedang disambungkan.").setPositiveButton("Log keluar",(d,w)->logoutCloud()).setNegativeButton("Kembali",null).show();});
        addInfoRow(panel,"Dasar Privasi","Cara maklumat dan data dikendalikan",()->{dialog.dismiss();showLegalPage("privacy_policy.html","Dasar Privasi");});
        addInfoRow(panel,"Terma Penggunaan","Syarat dan peraturan penggunaan aplikasi",()->{dialog.dismiss();showLegalPage("terms.html","Terma Penggunaan");});
        if(consentInformation!=null && consentInformation.getPrivacyOptionsRequirementStatus()==ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED){
            addInfoRow(panel,"Tetapan Privasi Iklan","Urus persetujuan dan pilihan iklan Google",()->{dialog.dismiss();showPrivacyOptions();});
        }
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-2);
    }

    private void addInfoRow(LinearLayout parent,String title,String subtitle,Runnable action){
        TextView row=new TextView(this);
        row.setText(title+"\n"+subtitle+"     ›");
        row.setTextSize(14);
        row.setTextColor(Color.rgb(30,45,70));
        row.setLineSpacing(dp(2),1f);
        row.setPadding(dp(15),dp(13),dp(15),dp(13));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TL_BR);
        bg.setColors(new int[]{Color.WHITE,Color.rgb(255,248,240)});
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1),Color.rgb(245,230,214));
        row.setBackground(bg);
        row.setElevation(dp(3));
        row.setClickable(true);
        row.setFocusable(true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(9);
        parent.addView(row,lp);
        row.setOnClickListener(v->action.run());
    }

    private void showPrivacyOptions(){
        if(consentInformation==null)return;
        UserMessagingPlatform.showPrivacyOptionsForm(this,error->{
            if(error!=null)Toast.makeText(this,"Tetapan privasi tidak dapat dibuka. Cuba lagi.",Toast.LENGTH_SHORT).show();
            updatePrivacyOptionsEntry();
        });
    }

    private void showLegalPage(String assetName,String title){
        WebView legalView=new WebView(this);
        legalView.setBackgroundColor(Color.WHITE);
        WebSettings settings=legalView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        legalView.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url){
                if(url!=null && (url.startsWith("https://")||url.startsWith("http://"))){
                    try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception ignored){}
                    return true;
                }
                return false;
            }
        });
        legalView.loadUrl("file:///android_asset/"+assetName);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(legalView)
            .setPositiveButton("Tutup",(d,w)->{})
            .create();
        dialog.setOnDismissListener(d->legalView.destroy());
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,dp(560));
    }

    private class AndroidBridge{
        @JavascriptInterface public void recordConverted(String n,String m,String b){String name=safe(n),type=mime(m);byte[] data=dec(b);if(data.length>0)recordHistory(name,type,data);}
        @JavascriptInterface public void conversionCompleted(){if(!isPro)runOnUiThread(()->{conversionCount++;getSharedPreferences(PREFS_ADS,MODE_PRIVATE).edit().putInt(PREF_CONVERSION_COUNT,conversionCount).apply();if(conversionCount%5==0)showInterstitialIfReady();});}
        @JavascriptInterface public void download(String n,String m,String b){
            String name=safe(n); String type=mime(m); byte[] data=dec(b);
            if(isHtmlFile(name,type)){runOnUiThread(()->showHtmlPreview(name,type,data));}else{saveDownload(name,type,data);}
        }
        @JavascriptInterface public void saveAs(String n,String m,String b){String name=safe(n);byte[] data=dec(b);runOnUiThread(()->{pendingBytes=data;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime(m));i.putExtra(Intent.EXTRA_TITLE,name);try{startActivityForResult(i,SAVE_DOCUMENT_REQUEST);}catch(Exception e){pendingBytes=null;Toast.makeText(MainActivity.this,"Tidak dapat membuka lokasi simpan.",Toast.LENGTH_LONG).show();}});}
        @JavascriptInterface public void share(String n,String m,String b){String name=safe(n),type=mime(m);byte[] data=dec(b);shareOrOpen(name,type,data);}
        @JavascriptInterface public void openWith(String n,String m,String b){String name=safe(n),type=mime(m);byte[] data=dec(b);openFile(name,type,data);}
        @JavascriptInterface public void showRewarded(){runOnUiThread(MainActivity.this::showRewardedIfReady);}
        @JavascriptInterface public void openPro(){runOnUiThread(MainActivity.this::showProPlansDialog);}
        @JavascriptInterface public void openSettings(){runOnUiThread(MainActivity.this::showSettingsDialog);}
        @JavascriptInterface public void openHistory(){runOnUiThread(MainActivity.this::showHistoryDialog);}
        @JavascriptInterface public void setAdPage(String page){runOnUiThread(()->MainActivity.this.setAdPage(page));}
    }
    private byte[] dec(String s){try{return android.util.Base64.decode(s,android.util.Base64.DEFAULT);}catch(Exception e){return new byte[0];}}
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
        if(data==null||data.length==0)return;
        new Thread(()->{try{
            ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,n);v.put(MediaStore.Downloads.MIME_TYPE,m);v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/All Convert");
            Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)throw new Exception("Tidak dapat menyediakan fail Download.");
            try(OutputStream o=getContentResolver().openOutputStream(u)){if(o==null)throw new Exception("Lokasi Download tidak boleh dibuka.");o.write(data);o.flush();}
            runOnUiThread(()->{if(webView!=null)webView.evaluateJavascript("window.__allConvertNativeDone&&window.__allConvertNativeDone('download')",null);});
        }catch(Exception e){runOnUiThread(()->{if(webView!=null)webView.evaluateJavascript("window.__allConvertNativeError&&window.__allConvertNativeError('download')",null);Toast.makeText(this,"Download gagal. Semak ruang storan dan cuba lagi.",Toast.LENGTH_LONG).show();});}}).start();
    }
    private void shareOrOpen(String n,String m,byte[] data){
        new Thread(()->{try{
            File d=new File(getCacheDir(),"shared");if(!d.exists()&&!d.mkdirs())throw new Exception("Folder sementara tidak dapat dibuat.");
            File f=new File(d,n);try(FileOutputStream o=new FileOutputStream(f)){o.write(data);o.flush();}
            Uri u=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);
            Intent i=new Intent(Intent.ACTION_SEND);i.setType(m);i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            runOnUiThread(()->{try{startActivity(Intent.createChooser(i,"Share fail"));}catch(Exception e){if(webView!=null)webView.evaluateJavascript("window.__allConvertNativeError&&window.__allConvertNativeError('share')",null);}});
        }catch(Exception e){runOnUiThread(()->{if(webView!=null)webView.evaluateJavascript("window.__allConvertNativeError&&window.__allConvertNativeError('share')",null);Toast.makeText(this,"Fail tidak dapat dikongsi.",Toast.LENGTH_LONG).show();});}}).start();
    }
    private void openFile(String n,String m,byte[] data){
        new Thread(()->{try{
            File d=new File(getCacheDir(),"shared");if(!d.exists()&&!d.mkdirs())throw new Exception("Folder sementara tidak dapat dibuat.");
            File f=new File(d,n);try(FileOutputStream o=new FileOutputStream(f)){o.write(data);o.flush();}
            Uri u=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);
            Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(u,m);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            runOnUiThread(()->{try{startActivity(i);}catch(Exception e){shareOrOpen(n,m,data);}});
        }catch(Exception e){runOnUiThread(()->{if(webView!=null)webView.evaluateJavascript("window.__allConvertNativeError&&window.__allConvertNativeError('openWith')",null);Toast.makeText(this,"Tiada aplikasi yang boleh membuka fail ini.",Toast.LENGTH_LONG).show();});}}).start();
    }
    private void handleIncoming(Intent x){
        if(x==null)return;String a=x.getAction();Uri u=x.getData();
        if(Intent.ACTION_SEND.equals(a)){if(x.getClipData()!=null&&x.getClipData().getItemCount()>0)u=x.getClipData().getItemAt(0).getUri();else if(x.getParcelableExtra(Intent.EXTRA_STREAM)!=null)u=(Uri)x.getParcelableExtra(Intent.EXTRA_STREAM);}
        if(u==null||!(Intent.ACTION_VIEW.equals(a)||Intent.ACTION_SEND.equals(a)))return;
        try{byte[] d=read(u);String n=u.getLastPathSegment();if(n==null||n.isEmpty())n="shared_file";String m=getContentResolver().getType(u);String b=android.util.Base64.encodeToString(d,android.util.Base64.NO_WRAP);
            String js="window.__allConvertIncoming&&window.__allConvertIncoming("+q(n)+","+q(mime(m))+",'"+b+"')";webView.evaluateJavascript(js,null);
        }catch(Exception ignored){}
    }
    private String q(String s){return JSONObject.quote(String.valueOf(s));}
    private byte[] read(Uri u)throws Exception{try(InputStream i=getContentResolver().openInputStream(u);ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=i.read(b))>0)o.write(b,0,n);return o.toByteArray();}}

    private void setAdPage(String page){
        currentAdPage="home".equals(page)?"home":"convert";
        if(isPro)return;
        if(nativeContainer!=null)nativeContainer.setVisibility(nativeAd!=null?View.VISIBLE:View.GONE);
        if(bannerContainer!=null)bannerContainer.setVisibility(bannerLoaded?View.VISIBLE:View.GONE);
        if(nativeAd==null&&adsInitialized)loadNative();
    }

    private void showBanner(){
        if(isPro||bannerContainer==null)return;
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
                if(isPro){bannerContainer.removeAllViews();bannerContainer.setVisibility(View.GONE);return;}
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

    private void loadInterstitial(){if(isPro)return;InterstitialAd.load(this,INTERSTITIAL_ID,new AdRequest.Builder().build(),new InterstitialAdLoadCallback(){
        @Override public void onAdLoaded(InterstitialAd ad){if(isPro){interstitialAd=null;return;}interstitialAd=ad;ad.setFullScreenContentCallback(new com.google.android.gms.ads.FullScreenContentCallback(){@Override public void onAdDismissedFullScreenContent(){interstitialAd=null;loadInterstitial();}});}
        @Override public void onAdFailedToLoad(LoadAdError e){interstitialAd=null;}
    });}
    private void showInterstitialIfReady(){if(isPro)return;long now=System.currentTimeMillis();if(now-lastInterstitialShown<180000L)return;if(skipNextInterstitial){skipNextInterstitial=false;webView.evaluateJavascript("window.__allConvertRewardedUsed&&window.__allConvertRewardedUsed()",null);loadInterstitial();return;}if(interstitialAd!=null && !appOpenShowing){lastInterstitialShown=now;interstitialAd.show(this);}else loadInterstitial();}

    private void loadRewarded(){if(isPro)return;RewardedAd.load(this,REWARDED_ID,new AdRequest.Builder().build(),new RewardedAdLoadCallback(){
        @Override public void onAdLoaded(RewardedAd ad){if(isPro){rewardedAd=null;return;}rewardedAd=ad;runOnUiThread(()->{if(rewardButton!=null&&!isPro)rewardButton.setVisibility(View.VISIBLE);});}
        @Override public void onAdFailedToLoad(LoadAdError e){rewardedAd=null;runOnUiThread(()->{if(rewardButton!=null)rewardButton.setVisibility(View.GONE);});}
    });}
    private void showRewardedIfReady(){
        if(isPro)return;
        if(rewardedAd==null){loadRewarded();Toast.makeText(this,"Rewarded ad belum tersedia.",Toast.LENGTH_SHORT).show();return;}
        RewardedAd ad=rewardedAd; rewardedAd=null; if(rewardButton!=null)rewardButton.setVisibility(View.GONE);
        ad.setFullScreenContentCallback(new com.google.android.gms.ads.FullScreenContentCallback(){@Override public void onAdDismissedFullScreenContent(){loadRewarded();}});
        ad.show(this,rewardItem->{skipNextInterstitial=true;webView.evaluateJavascript("window.__allConvertRewarded&&window.__allConvertRewarded("+rewardItem.getAmount()+")",null);});
    }

    private void loadNative(){
        if(isPro)return;
        AdLoader loader=new AdLoader.Builder(this,NATIVE_ID).forNativeAd(ad->{if(nativeAd!=null)nativeAd.destroy();nativeAd=ad;renderNative(ad);})
            .withNativeAdOptions(new NativeAdOptions.Builder().build()).withAdListener(new AdListener(){
                @Override public void onAdFailedToLoad(LoadAdError error){runOnUiThread(()->{if(nativeAd==null&&nativeContainer!=null){nativeContainer.removeAllViews();nativeContainer.setVisibility(View.GONE);}});}
            }).build();
        loader.loadAd(new AdRequest.Builder().build());
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void renderNative(NativeAd ad){
        if(isPro||nativeContainer==null){ad.destroy();return;}
        nativeContainer.removeAllViews();
        LinearLayout wrap=new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(8),dp(6),dp(8),dp(5)); wrap.setBackgroundColor(Color.rgb(247,250,255));
        NativeAdView nv=new NativeAdView(this);
        nv.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(88)));
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(4),dp(3),dp(4),dp(3));
        com.google.android.gms.ads.nativead.MediaView media=new com.google.android.gms.ads.nativead.MediaView(this); media.setLayoutParams(new LinearLayout.LayoutParams(dp(76),dp(76)));
        row.addView(media);
        LinearLayout text=new LinearLayout(this); text.setOrientation(LinearLayout.VERTICAL); text.setPadding(dp(9),0,dp(4),0); text.setLayoutParams(new LinearLayout.LayoutParams(0,-1,1));
        TextView headline=new TextView(this); headline.setTextSize(13); headline.setTextColor(Color.rgb(20,28,45)); headline.setMaxLines(2); headline.setTypeface(null,android.graphics.Typeface.BOLD);
        TextView body=new TextView(this); body.setTextSize(10); body.setTextColor(Color.rgb(90,100,120)); body.setMaxLines(2); body.setPadding(0,dp(2),0,dp(2));
        Button cta=new Button(this); cta.setTextSize(10); cta.setMinHeight(dp(28)); cta.setAllCaps(false); cta.setTextColor(Color.rgb(40,60,120));
        text.addView(headline,new LinearLayout.LayoutParams(-1,0,1)); text.addView(body,new LinearLayout.LayoutParams(-1,0,1)); text.addView(cta,new LinearLayout.LayoutParams(-1,dp(28)));
        row.addView(text);
        nv.addView(row);
        nv.setMediaView(media); nv.setHeadlineView(headline); nv.setBodyView(body); nv.setCallToActionView(cta); nv.setNativeAd(ad);
        TextView hide=new TextView(this);
        hide.setText("Sembunyikan iklan"); hide.setTextSize(11); hide.setTypeface(null,android.graphics.Typeface.BOLD);
        hide.setTextColor(Color.rgb(35,91,180)); hide.setGravity(Gravity.CENTER);
        hide.setPadding(dp(10),0,dp(10),0); hide.setMinHeight(dp(24));
        android.graphics.drawable.GradientDrawable pill=new android.graphics.drawable.GradientDrawable();
        pill.setColor(Color.rgb(232,241,255)); pill.setCornerRadius(dp(18)); pill.setStroke(dp(1),Color.rgb(205,222,248));
        hide.setBackground(pill); hide.setContentDescription("Sembunyikan iklan native");
        wrap.addView(nv);
        LinearLayout.LayoutParams hideParams=new LinearLayout.LayoutParams(-2,dp(24));
        hideParams.gravity=Gravity.RIGHT; hideParams.topMargin=dp(2);
        wrap.addView(hide,hideParams);
        final boolean[] nativeHidden={false};
        hide.setOnClickListener(v->{
            nativeHidden[0]=!nativeHidden[0];
            nv.setVisibility(nativeHidden[0]?View.GONE:View.VISIBLE);
            hide.setText(nativeHidden[0]?"Tunjukkan iklan":"Sembunyikan iklan");
            hide.setContentDescription(nativeHidden[0]?"Tunjukkan iklan native":"Sembunyikan iklan native");
        });
        nativeContainer.addView(wrap); nativeContainer.setVisibility(View.VISIBLE);
    }
    private void showAppOpenIfReady(){
        if(isPro||!adsInitialized || consentInformation==null || !consentInformation.canRequestAds())return;
        if(appOpenShowing || System.currentTimeMillis()-lastAppOpenShown<APP_OPEN_COOLDOWN_MS)return;
        com.google.android.gms.ads.appopen.AppOpenAd.load(this,APP_OPEN_ID,new AdRequest.Builder().build(),new com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback(){
            @Override public void onAdLoaded(com.google.android.gms.ads.appopen.AppOpenAd ad){
                if(isPro||System.currentTimeMillis()-lastAppOpenShown<APP_OPEN_COOLDOWN_MS)return;
                appOpenShowing=true;
                if(bannerContainer!=null)bannerContainer.setVisibility(View.GONE);
                ad.setFullScreenContentCallback(new com.google.android.gms.ads.FullScreenContentCallback(){
                    @Override public void onAdDismissedFullScreenContent(){lastAppOpenShown=System.currentTimeMillis();getSharedPreferences(PREFS_ADS,MODE_PRIVATE).edit().putLong(PREF_LAST_APP_OPEN,lastAppOpenShown).apply();appOpenShowing=false;if(bannerContainer!=null && bannerLoaded)bannerContainer.setVisibility(View.VISIBLE);}
                    @Override public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError e){appOpenShowing=false;if(bannerContainer!=null && bannerLoaded)bannerContainer.setVisibility("convert".equals(currentAdPage)?View.VISIBLE:View.GONE);}
                });
                ad.show(MainActivity.this);
            }
        });
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==FILE_CHOOSER_REQUEST){if(filePathCallback==null)return;Uri[] z=null;if(c==RESULT_OK&&d!=null){if(d.getClipData()!=null){int n=d.getClipData().getItemCount();z=new Uri[n];for(int i=0;i<n;i++)z[i]=d.getClipData().getItemAt(i).getUri();}else if(d.getData()!=null)z=new Uri[]{d.getData()};}filePathCallback.onReceiveValue(z);filePathCallback=null;}
        else if(r==SAVE_DOCUMENT_REQUEST){if(c==RESULT_OK&&d!=null&&d.getData()!=null&&pendingBytes!=null){byte[] bytes=pendingBytes;Uri target=d.getData();pendingBytes=null;new Thread(()->{try(OutputStream o=getContentResolver().openOutputStream(target)){if(o==null)throw new Exception("Lokasi simpan tidak tersedia.");o.write(bytes);o.flush();runOnUiThread(()->Toast.makeText(this,"Fail berjaya disimpan.",Toast.LENGTH_SHORT).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Gagal menyimpan fail. Cuba lokasi lain.",Toast.LENGTH_LONG).show());}}).start();}else pendingBytes=null;}
    }
    @Override protected void onStart(){super.onStart();if(billingClientReady())queryProPurchases();if(firstSession){firstSession=false;return;}new android.os.Handler().postDelayed(()->showAppOpenIfReady(),800);}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleIncoming(i);}
    @Override protected void onDestroy(){if(filePathCallback!=null){filePathCallback.onReceiveValue(null);filePathCallback=null;}if(billingClient!=null&&billingClient.isReady())billingClient.endConnection();if(adView!=null)adView.destroy();if(nativeAd!=null)nativeAd.destroy();if(webView!=null)webView.destroy();super.onDestroy();}
}