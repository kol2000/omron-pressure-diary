package ru.personal.omronscan;

import androidx.activity.ComponentActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.os.Bundle;
import android.net.Uri;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.Toast;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class DashboardActivity extends ComponentActivity {
    WebView web;
    ValueCallback<Uri[]> fileCallback;
    ActivityResultLauncher<String> filePicker;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        filePicker=registerForActivityResult(new ActivityResultContracts.GetContent(),uri->{
            if(fileCallback!=null){fileCallback.onReceiveValue(uri==null?null:new Uri[]{uri});fileCallback=null;}
        });
        String base=getSharedPreferences("scanner",MODE_PRIVATE).getString("server","").replaceAll("/+$", "");
        String token=getSharedPreferences("scanner",MODE_PRIVATE).getString("token","");
        Uri origin=Uri.parse(base);
        if(!"https".equals(origin.getScheme()) || origin.getHost()==null) {
            Toast.makeText(this,"Укажите HTTPS-адрес сервера",Toast.LENGTH_LONG).show(); finish(); return;
        }
        LinearLayout layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout actions=new LinearLayout(this);
        Button back=new Button(this); back.setText("Назад"); back.setOnClickListener(v->finish()); actions.addView(back);
        Button print=new Button(this); print.setText("Печать / PDF"); print.setOnClickListener(v->{
            PrintManager manager=(PrintManager)getSystemService(PRINT_SERVICE);
            manager.print("Дневник давления",web.createPrintDocumentAdapter("Дневник давления"),null);
        }); actions.addView(print); layout.addView(actions);
        web=new WebView(this); layout.addView(web,new LinearLayout.LayoutParams(-1,0,1)); setContentView(layout);
        ViewCompat.setOnApplyWindowInsetsListener(layout,(v,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets;
        });
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(fileCallback!=null)fileCallback.onReceiveValue(null);
                fileCallback=callback;filePicker.launch("image/*");return true;
            }
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();
                boolean same="https".equals(uri.getScheme()) && origin.getHost().equals(uri.getHost()) && origin.getPort()==uri.getPort();
                if(!same && "https".equals(uri.getScheme()) && "docs.google.com".equals(uri.getHost())){
                    try{startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,uri));}
                    catch(android.content.ActivityNotFoundException ignored){}
                }
                return !same;
            }
        });
        String data;
        try { data="token="+URLEncoder.encode(token,"UTF-8"); }
        catch(java.io.UnsupportedEncodingException impossible){throw new IllegalStateException(impossible);}
        web.postUrl(base+"/auth/mobile",data.getBytes(StandardCharsets.UTF_8));
    }
    @Override protected void onDestroy(){if(fileCallback!=null){fileCallback.onReceiveValue(null);fileCallback=null;}if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
