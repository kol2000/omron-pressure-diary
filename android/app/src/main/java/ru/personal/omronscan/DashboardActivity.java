package ru.personal.omronscan;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.FileProvider;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintManager;
import android.view.Gravity;
import android.webkit.*;
import android.widget.*;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardActivity extends ComponentActivity {
    WebView web;
    TextView connectionLabel,connectionDetail;
    Button retry;
    SharedPreferences prefs;
    String base,secret;
    Uri origin;
    boolean loaded=false,checking=false,active=false;
    int automaticLogins=0;
    ValueCallback<Uri[]> fileCallback;
    ActivityResultLauncher<String> filePicker;
    ActivityResultLauncher<Uri> camera;
    final Handler handler=new Handler(Looper.getMainLooper());
    final ExecutorService executor=Executors.newSingleThreadExecutor();
    final Runnable heartbeat=new Runnable(){public void run(){if(active){checkConnection(false);handler.postDelayed(this,30000);}}};
    int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}

    @Override public void onCreate(Bundle state){
        Theme.apply(this);super.onCreate(state);prefs=getSharedPreferences("scanner",MODE_PRIVATE);
        try{base=ServerAddress.normalize(prefs.getString("server",""));}catch(Exception error){settings("Проверьте адрес сервера.");return;}
        secret=prefs.getString("token","");origin=Uri.parse(base);
        if(secret.isEmpty()){settings(null);return;}
        filePicker=registerForActivityResult(new ActivityResultContracts.GetContent(),uri->fileResult(uri));
        camera=registerForActivityResult(new ActivityResultContracts.TakePicture(),ok->fileResult(ok?photoUri():null));
        boolean dark=Theme.dark(this);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setBackgroundColor(Color.parseColor(dark?"#111b16":"#f5f7f6"));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(15),dp(5),dp(8),dp(5));bar.setBackgroundColor(Color.parseColor(dark?"#17231d":"#edf4ef"));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        connectionLabel=new TextView(this);connectionLabel.setTextSize(12);connectionLabel.setTextColor(Color.parseColor(dark?"#bfd6c5":"#3d6952"));labels.addView(connectionLabel);
        connectionDetail=new TextView(this);connectionDetail.setTextSize(10);connectionDetail.setTextColor(Color.parseColor(dark?"#9eb4a5":"#708578"));connectionDetail.setText(origin.getAuthority());labels.addView(connectionDetail);
        bar.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        retry=new Button(this);retry.setText("Повторить");retry.setAllCaps(false);retry.setTextSize(11);retry.setTextColor(Color.parseColor(dark?"#a9d6be":"#126b60"));retry.setBackgroundColor(Color.TRANSPARENT);retry.setOnClickListener(v->checkConnection(true));bar.addView(retry,new LinearLayout.LayoutParams(dp(87),dp(42)));
        Button menu=new Button(this);menu.setText("⋮");menu.setTextSize(22);menu.setTextColor(Color.parseColor(dark?"#a9d6be":"#126b60"));menu.setContentDescription("Настройки подключения и отчёт");menu.setBackgroundColor(Color.TRANSPARENT);menu.setOnClickListener(v->showMenu());bar.addView(menu,new LinearLayout.LayoutParams(dp(40),dp(42)));layout.addView(bar);
        web=new WebView(this);web.setBackgroundColor(Color.parseColor(dark?"#111b16":"#f5f7f6"));layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(layout);
        ViewCompat.setOnApplyWindowInsetsListener(layout,(v,insets)->{androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        WebSettings config=web.getSettings();config.setJavaScriptEnabled(true);config.setDomStorageEnabled(true);config.setAllowFileAccess(false);config.setAllowContentAccess(true);config.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);config.setUserAgentString(config.getUserAgentString()+" OmronDiaryAndroid");
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
                new AlertDialog.Builder(DashboardActivity.this).setTitle("Фото тонометра").setItems(new String[]{"Сфотографировать экран","Выбрать из галереи"},(dialog,which)->{
                    try{if(which==0)camera.launch(photoUri());else filePicker.launch("image/*");}
                    catch(Exception error){fileResult(null);Toast.makeText(DashboardActivity.this,"Не удалось открыть камеру или галерею",Toast.LENGTH_LONG).show();}
                }).setNegativeButton("Отмена",(d,w)->fileResult(null)).setOnCancelListener(d->fileResult(null)).show();return true;
            }
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();
                if("omron-action".equals(uri.getScheme())){
                    if("print".equals(uri.getHost()))printReport();
                    else if("settings".equals(uri.getHost()))settings(null);
                    else if("disconnect".equals(uri.getHost()))disconnect();
                    else if("reconnect".equals(uri.getHost())){
                        if(automaticLogins++<1)login();else settings("Сессия не открылась. Проверьте подключение и войдите снова.");
                    }
                    return true;
                }
                if(sameOrigin(uri))return false;
                if(request.hasGesture()&&"https".equals(uri.getScheme())&&"docs.google.com".equals(uri.getHost())){
                    try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}
                }
                return true;
            }
            @Override public void onPageFinished(WebView view,String url){
                Uri uri=Uri.parse(url);
                if(sameOrigin(uri)&&uri.getPath().equals(origin.getPath()==null||origin.getPath().isEmpty()?"/":origin.getPath()+"/")){
                    loaded=true;checkConnection(false);
                }else if(sameOrigin(uri)&&uri.getPath().endsWith("/auth/mobile"))settings("Не удалось войти в дневник. Проверьте токен и повторите подключение.");
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame()){loaded=false;setStatus("○ Нет связи", "Проверьте Wi-Fi или VPN и нажмите «Повторить»",false);}}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame()){loaded=false;setStatus("○ Ошибка сервера", "Ответ "+response.getStatusCode()+" · повторите подключение",false);}}
            @Override public void onReceivedSslError(WebView view,SslErrorHandler result,SslError error){result.cancel();settings("Сертификат сервера не подтверждён. Установите сертификат CA на телефон и проверьте адрес.");}
        });
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){@Override public void handleOnBackPressed(){finish();}});
        login();
    }
    boolean sameOrigin(Uri uri){return "https".equals(uri.getScheme())&&origin.getHost().equals(uri.getHost())&&port(origin)==port(uri);}
    int port(Uri uri){return uri.getPort()<0?443:uri.getPort();}
    void setStatus(String label,String detail,boolean connected){if(isDestroyed()||connectionLabel==null)return;boolean dark=Theme.dark(this);connectionLabel.setTextColor(Color.parseColor(connected?(dark?"#83d6ae":"#126b60"):label.startsWith("○")?(dark?"#e2cd9c":"#8a6b2a"):(dark?"#a4b6a7":"#78877e")));connectionLabel.setText(label);connectionDetail.setText(detail);retry.setVisibility(connected?android.view.View.GONE:android.view.View.VISIBLE);}
    void login(){loaded=false;setStatus("◌ Подключаемся",origin.getAuthority()+" · открываем дневник",false);try{web.postUrl(base+"/auth/mobile",("token="+URLEncoder.encode(secret,"UTF-8")).getBytes(StandardCharsets.UTF_8));}catch(Exception error){settings("Не удалось открыть дневник. Повторите подключение.");}}
    void checkConnection(boolean retryLogin){
        if(checking||isFinishing()||isDestroyed())return;checking=true;retry.setEnabled(false);
        if(retryLogin)setStatus("◌ Проверяем связь",origin.getAuthority(),false);
        executor.execute(()->{try{ServerConnection.check(base,secret);handler.post(()->{if(isFinishing()||isDestroyed())return;checking=false;retry.setEnabled(true);String time=java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                    if(!loaded&&retryLogin)login();else setStatus(loaded?"● Подключён":"◌ Открываем дневник",origin.getAuthority()+" · проверено в "+time,loaded);});}
            catch(ServerConnection.Failure error){handler.post(()->{if(isFinishing()||isDestroyed())return;checking=false;retry.setEnabled(true);setStatus(error.unauthorized?"○ Неверный токен":"○ Нет связи",error.getMessage(),false);});}});
    }
    void showMenu(){
        boolean draft=false;for(String key:new String[]{"frozen","sys","dia","pulse","note"})if(!prefs.getString(key,"").isEmpty())draft=true;
        String[] items=draft?new String[]{"Настройки подключения","Проверить связь","Печать / PDF","Запись из предыдущей версии"}:new String[]{"Настройки подключения","Проверить связь","Печать / PDF"};
        new AlertDialog.Builder(this).setItems(items,(d,which)->{if(which==0)settings(null);else if(which==1)checkConnection(true);else if(which==2)printReport();else startActivity(new Intent(this,LegacyMeasurementActivity.class));}).show();
    }
    void printReport(){if(!loaded){Toast.makeText(this,"Сначала откройте дневник",Toast.LENGTH_SHORT).show();return;}((PrintManager)getSystemService(PRINT_SERVICE)).print("Дневник давления",web.createPrintDocumentAdapter("Дневник давления"),null);}
    void settings(String error){Intent intent=new Intent(this,ConnectActivity.class).putExtra("edit",true);if(error!=null)intent.putExtra("connection_error",error);startActivity(intent);if(error!=null||web==null)finish();}
    void disconnect(){prefs.edit().remove("token").putBoolean("connected",false).commit();CookieManager.getInstance().setCookie(base,"session=; Max-Age=0; Path=/; Secure");startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();}
    Uri photoUri(){File folder=new File(getCacheDir(),"camera");folder.mkdirs();return FileProvider.getUriForFile(this,getPackageName()+".files",new File(folder,"dashboard.jpg"));}
    void fileResult(Uri uri){if(fileCallback!=null){fileCallback.onReceiveValue(uri==null?null:new Uri[]{uri});fileCallback=null;}}
    @Override protected void onResume(){super.onResume();if(isFinishing())return;if(prefs!=null&&prefs.getString("token","").isEmpty()){startActivity(new Intent(this,MainActivity.class));finish();return;}active=true;handler.post(heartbeat);}
    @Override protected void onPause(){active=false;handler.removeCallbacks(heartbeat);super.onPause();}
    @Override protected void onDestroy(){active=false;handler.removeCallbacksAndMessages(null);executor.shutdownNow();fileResult(null);if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
