package ru.personal.omronscan;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConnectActivity extends ComponentActivity {
    SharedPreferences prefs;
    EditText address,token;
    TextView status,detail;
    Button connect,themeButton;
    boolean busy,dark;
    final ExecutorService executor=Executors.newSingleThreadExecutor();
    int ink,muted,card,border,accent;

    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),border);return d;}
    LinearLayout.LayoutParams space(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);return p;}
    TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}

    @Override public void onCreate(Bundle state){
        Theme.apply(this);super.onCreate(state);prefs=getSharedPreferences("scanner",MODE_PRIVATE);dark=Theme.dark(this);
        ink=Color.parseColor(dark?"#e0e9e1":"#20332f");muted=Color.parseColor(dark?"#a4b6a7":"#78877e");
        card=Color.parseColor(dark?"#1b2821":"#ffffff");border=Color.parseColor(dark?"#334238":"#e2e9e3");accent=Color.parseColor(dark?"#62c7b5":"#126b60");
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.parseColor(dark?"#111b16":"#f5f7f6"));
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setGravity(Gravity.CENTER);outer.setPadding(dp(22),dp(28),dp(22),dp(28));scroll.addView(outer);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(25),dp(25),dp(25),dp(25));panel.setBackground(shape(card,22));
        LinearLayout.LayoutParams panelSize=new LinearLayout.LayoutParams(-1,-2);outer.addView(panel,panelSize);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView heart=text("♥",27,Color.WHITE,true);heart.setGravity(Gravity.CENTER);heart.setBackground(shape(Color.parseColor("#126b60"),12));top.addView(heart,new LinearLayout.LayoutParams(dp(44),dp(44)));
        Space filler=new Space(this);top.addView(filler,new LinearLayout.LayoutParams(0,1,1));
        themeButton=new Button(this);themeButton.setText(dark?"☀ Светлая":"☾ Тёмная");themeButton.setAllCaps(false);themeButton.setTextSize(12);themeButton.setTextColor(muted);themeButton.setBackground(shape(card,9));top.addView(themeButton,new LinearLayout.LayoutParams(dp(110),dp(40)));panel.addView(top);
        themeButton.setOnClickListener(v->{prefs.edit().putBoolean("dark_theme",!dark).commit();recreate();});
        TextView eyebrow=text("ДНЕВНИК ДАВЛЕНИЯ",10,accent,true);eyebrow.setLetterSpacing(.15f);panel.addView(eyebrow,space(27));
        panel.addView(text("Подключение\nк серверу",29,ink,true),space(9));
        panel.addView(text("Один раз укажите адрес и токен. После проверки откроются измерения и графики.",14,muted,false),space(12));
        panel.addView(text("Адрес сервера",12,muted,false),space(25));
        address=new EditText(this);address.setSingleLine(true);address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);address.setTextSize(16);address.setTextColor(ink);address.setHintTextColor(muted);address.setPadding(dp(13),dp(12),dp(13),dp(12));address.setBackground(shape(card,10));address.setHint("https://адрес-сервера");
        address.setText(state!=null?state.getString("form_address",""):prefs.getString("server",""));panel.addView(address,space(7));
        panel.addView(text("Токен доступа",12,muted,false),space(17));
        token=new EditText(this);token.setSingleLine(true);token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setTextSize(16);token.setTextColor(ink);token.setPadding(dp(13),dp(12),dp(13),dp(12));token.setBackground(shape(card,10));token.setText(state!=null?state.getString("form_token",""):prefs.getString("token",""));panel.addView(token,space(7));
        status=text("○ Не подключён",14,muted,true);panel.addView(status,space(22));
        detail=text("Нажмите «Подключиться» — приложение проверит связь и токен.",12,muted,false);panel.addView(detail,space(5));
        connect=new Button(this);connect.setText("Подключиться →");connect.setAllCaps(false);connect.setTextSize(15);connect.setTextColor(Color.WHITE);connect.setBackground(shape(Color.parseColor("#126b60"),11));LinearLayout.LayoutParams buttonSize=space(20);buttonSize.height=dp(51);panel.addView(connect,buttonSize);connect.setOnClickListener(v->check());
        TextView help=text("Телефон должен быть в домашнем Wi-Fi или VPN. Для HTTPS нужен установленный сертификат сервера.",11,muted,false);panel.addView(help,space(18));
        Button certificate=new Button(this);certificate.setAllCaps(false);certificate.setText("Как установить сертификат");certificate.setTextSize(11);certificate.setTextColor(accent);certificate.setBackgroundColor(Color.TRANSPARENT);panel.addView(certificate,space(0));
        certificate.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("Сертификат сервера").setMessage("Установите файл omron-server-ca.crt:\n\nНастройки телефона → Безопасность → Шифрование и учётные данные → Установить сертификат → Сертификат CA.\n\nПункты могут называться немного иначе. После установки вернитесь и нажмите «Подключиться».").setPositiveButton("Понятно",null).show());
        if(getIntent().getBooleanExtra("edit",false)){
            Button disconnect=new Button(this);disconnect.setText("Отключиться от сервера");disconnect.setAllCaps(false);disconnect.setTextColor(muted);disconnect.setTextSize(12);disconnect.setBackgroundColor(Color.TRANSPARENT);panel.addView(disconnect,space(7));
            disconnect.setOnClickListener(v->{prefs.edit().remove("token").putBoolean("connected",false).commit();android.webkit.CookieManager.getInstance().setCookie(prefs.getString("server",""),"session=; Max-Age=0; Path=/; Secure");token.setText("");status.setText("○ Не подключён");detail.setText("Для входа введите токен и подключитесь снова. Измерения на сервере сохранены.");});
        }
        setContentView(scroll);ViewCompat.setOnApplyWindowInsetsListener(scroll,(v,insets)->{androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        String failure=getIntent().getStringExtra("connection_error");if(failure!=null){status.setText("○ Не подключён");detail.setText(failure);}
        else if(getIntent().getBooleanExtra("edit",false)){status.setText("Настройки подключения");detail.setText("Чтобы применить изменения, нажмите «Подключиться». Сначала проверим сервер и токен.");}
        else if(state==null&&!getIntent().getBooleanExtra("edit",false)&&!prefs.getString("server","").isEmpty()&&!prefs.getString("token","").isEmpty())check();
    }
    void check(){
        if(busy)return;String base;
        try{base=ServerAddress.normalize(address.getText().toString());}catch(IllegalArgumentException e){detail.setText(e.getMessage());status.setText("○ Проверьте адрес");return;}
        String secret=token.getText().toString().trim();if(secret.length()<32){status.setText("○ Введите токен");detail.setText("Скопируйте токен целиком из инструкции доступа.");return;}
        busy=true;connect.setEnabled(false);address.setEnabled(false);token.setEnabled(false);themeButton.setEnabled(false);status.setText("◌ Подключаемся…");status.setTextColor(accent);detail.setText("Проверяем сервер и токен доступа");connect.setText("Проверяем подключение…");
        executor.execute(()->{try{ServerConnection.check(base,secret);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;prefs.edit().putString("server",base).putString("token",secret).putBoolean("connected",true).commit();status.setText("● Подключён");detail.setText("Сервер доступен. Открываем дневник…");
                    Intent next=new Intent(this,DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);startActivity(next);finish();});}
            catch(ServerConnection.Failure error){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;busy=false;connect.setEnabled(true);address.setEnabled(true);token.setEnabled(true);themeButton.setEnabled(true);connect.setText("Подключиться →");status.setText(error.unauthorized?"○ Неверный токен":"○ Не подключён");status.setTextColor(Color.parseColor(dark?"#e2cd9c":"#8a6b2a"));detail.setText(error.getMessage());});}});
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putString("form_address",address.getText().toString());state.putString("form_token",token.getText().toString());super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
