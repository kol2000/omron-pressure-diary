package ru.personal.omronscan;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    EditText server, token, sys, dia, pulse, note, measured;
    TextView status;
    Button camera, gallery, save, reset, historyButton, dashboardButton;
    SharedPreferences prefs;
    String id, frozen;
    boolean busy;
    ActivityResultLauncher<Uri> takePhoto;
    ActivityResultLauncher<String> pickPhoto;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("scanner", MODE_PRIVATE);
        id = prefs.getString("id", UUID.randomUUID().toString());
        frozen = prefs.getString("frozen", "");
        takePhoto = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (ok) recognize(photoUri()); else message("Съёмка отменена");
        });
        pickPhoto = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> { if (uri != null) recognize(uri); });
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(32,24,32,32);
        scroll.addView(box); setContentView(scroll);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        TextView title = new TextView(this); title.setText("Дневник давления"); title.setTextSize(28); box.addView(title);
        TextView hint = new TextView(this); hint.setText("Сфотографируйте экран целиком без бликов. Перед сохранением сравните все три числа с прибором."); box.addView(hint);
        server = field(box,"Адрес сервера: https://…", "server", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        token = field(box,"Токен доступа", "token", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        camera = button(box,"Сфотографировать экран", v -> newScan(() -> takePhoto.launch(photoUri())));
        gallery = button(box,"Выбрать фото", v -> newScan(() -> pickPhoto.launch("image/*")));
        sys = field(box,"Верхнее", "sys", "", InputType.TYPE_CLASS_NUMBER);
        dia = field(box,"Нижнее", "dia", "", InputType.TYPE_CLASS_NUMBER);
        pulse = field(box,"Пульс", "pulse", "", InputType.TYPE_CLASS_NUMBER);
        measured = field(box,"Время измерения (с часовым поясом)", "measured", OffsetDateTime.now().withNano(0).toString(), InputType.TYPE_CLASS_TEXT);
        note = field(box,"Примечание", "note", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        save = button(box,"Проверил — сохранить в таблицу", v -> save());
        historyButton = button(box,"История и средние за 30 дней", v -> history());
        dashboardButton = button(box,"Графики и отчёт для врача", v -> {persist(); startActivity(new android.content.Intent(this,DashboardActivity.class));});
        reset = button(box,"Новое измерение", v -> new AlertDialog.Builder(this).setMessage("Очистить текущую запись? Если отправка завершилась ошибкой, сначала проверьте таблицу.").setPositiveButton("Очистить", (d,w) -> clear()).setNegativeButton("Отмена", null).show());
        status = new TextView(this); status.setTextSize(16); box.addView(status);
        if (!frozen.isEmpty()) { lockFields(true); message("Есть отправленная запись. Повторите отправку с тем же ID или проверьте таблицу перед новым измерением."); }
    }

    EditText field(LinearLayout box, String label, String key, String fallback, int type) {
        TextView caption = new TextView(this); caption.setText(label); box.addView(caption);
        EditText field = new EditText(this); field.setInputType(type); field.setText(prefs.getString(key, fallback));
        box.addView(field); return field;
    }
    Button button(LinearLayout box, String label, View.OnClickListener action) {
        Button b = new Button(this); b.setText(label); b.setOnClickListener(action); box.addView(b); return b;
    }
    Uri photoUri() {
        File folder = new File(getCacheDir(), "camera"); folder.mkdirs();
        return FileProvider.getUriForFile(this, getPackageName()+".files", new File(folder,"screen.jpg"));
    }
    String text(EditText field) { return field.getText().toString().trim(); }
    void message(String value) { status.setText(value); }
    void persist() {
        prefs.edit().putString("id",id).putString("frozen", frozen)
            .putString("server",text(server)).putString("token",text(token))
            .putString("sys",text(sys)).putString("dia",text(dia)).putString("pulse",text(pulse))
            .putString("note",text(note)).putString("measured",text(measured)).commit();
    }
    @Override public void onPause() { super.onPause(); persist(); }
    void lockFields(boolean locked) { for (EditText f : new EditText[]{sys,dia,pulse,note,measured}) f.setEnabled(!locked); }
    void working(boolean value) {
        busy=value; camera.setEnabled(!value); gallery.setEnabled(!value); save.setEnabled(!value); reset.setEnabled(!value); historyButton.setEnabled(!value); dashboardButton.setEnabled(!value);
        server.setEnabled(!value); token.setEnabled(!value); lockFields(value || !frozen.isEmpty());
    }
    void clear() {
        id=UUID.randomUUID().toString(); frozen="";
        sys.setText(""); dia.setText(""); pulse.setText(""); note.setText("");
        measured.setText(OffsetDateTime.now().withNano(0).toString()); lockFields(false); persist(); message("Готово к новому измерению");
    }
    void newScan(Runnable action) {
        if (!frozen.isEmpty() || !text(sys).isEmpty()) {
            new AlertDialog.Builder(this).setMessage("Начать новое измерение? Текущие поля будут очищены. После ошибки отправки сначала проверьте таблицу.")
                .setPositiveButton("Начать",(d,w) -> {clear(); action.run();}).setNegativeButton("Отмена",null).show();
        } else { clear(); action.run(); }
    }
    JSONObject call(String path, byte[] bytes, String contentType, String base, String secret) throws Exception {
        URL url = new URL(base.replaceAll("/+$", "")+path);
        if (!url.getProtocol().equals("https")) throw new IOException("Укажите HTTPS-адрес сервера с доверенным сертификатом");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setInstanceFollowRedirects(false); c.setRequestMethod(bytes == null ? "GET" : "POST"); c.setConnectTimeout(15000); c.setReadTimeout(200000);
        c.setRequestProperty("Authorization","Bearer "+secret); c.setRequestProperty("Content-Type",contentType);
        if (bytes != null) { c.setDoOutput(true); c.setFixedLengthStreamingMode(bytes.length); }
        try {
            if (bytes != null) { try(OutputStream stream=c.getOutputStream()) { stream.write(bytes); } }
            int code=c.getResponseCode();
            InputStream input = code>=400 ? c.getErrorStream() : c.getInputStream();
            if(input==null) throw new IOException("Сервер ответил: "+code);
            String body; try(input) { body=new String(readLimited(input,1024*1024),StandardCharsets.UTF_8); }
            JSONObject result;
            try { result=new JSONObject(body); } catch(Exception e) { throw new IOException("Некорректный ответ сервера: "+code); }
            if(code<200 || code>=300) throw new IOException(result.optString("error","Ошибка сервера: "+code));
            return result;
        } finally { c.disconnect(); }
    }
    interface Job { JSONObject run() throws Exception; }
    interface Done { void accept(JSONObject result) throws Exception; }
    void background(Job job, Done done) {
        working(true);
        java.util.concurrent.ExecutorService executor=Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try { JSONObject result=job.run(); runOnUiThread(() -> { working(false); try {done.accept(result);} catch(Exception e){message("Ошибка ответа сервера");} }); }
            catch(Exception e) { runOnUiThread(() -> {working(false); message("Не завершено: "+e.getMessage());}); }
            finally { executor.shutdown(); }
        });
    }
    void recognize(Uri uri) {
        String base=text(server), secret=text(token); message("Распознаю фото…"); persist();
        background(() -> {
            byte[] bytes;
            try(InputStream in=getContentResolver().openInputStream(uri)) {
                if(in==null) throw new IOException("Не удалось открыть фото");
                bytes=readLimited(in,10*1024*1024+1);
            }
            if(bytes.length>10*1024*1024) throw new IOException("Фото больше 10 МБ. Уменьшите размер снимка.");
            return call("/recognize",bytes,"application/octet-stream",base,secret);
        }, result -> {
            sys.setText(String.valueOf(result.getInt("systolic"))); dia.setText(String.valueOf(result.getInt("diastolic"))); pulse.setText(String.valueOf(result.getInt("pulse")));
            persist(); message("Сравните числа с экраном прибора, затем нажмите «Проверил — сохранить».");
        });
    }
    byte[] readLimited(InputStream stream, int max) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int count;
        while(out.size()<max && (count=stream.read(buffer,0,Math.min(buffer.length,max-out.size())))!=-1) out.write(buffer,0,count);
        return out.toByteArray();
    }
    void save() {
        try {
            if(frozen.isEmpty()) {
                int s=Integer.parseInt(text(sys)), d=Integer.parseInt(text(dia)), p=Integer.parseInt(text(pulse));
                if(s<40 || s>300 || d<20 || d>200 || p<20 || p>250 || s<=d) throw new IllegalArgumentException();
                OffsetDateTime.parse(text(measured));
                JSONObject data=new JSONObject().put("id",id).put("systolic",s).put("diastolic",d).put("pulse",p)
                    .put("measured_at",text(measured)).put("note",text(note)).put("confirmed",true);
                frozen=data.toString(); persist();
            }
            final byte[] body=frozen.getBytes(StandardCharsets.UTF_8);
            String base=text(server), secret=text(token); message("Сохраняю…");
            background(() -> call("/measurements",body,"application/json",base,secret), result -> {
                if(!result.optBoolean("saved")) throw new IOException();
                // Keep the same request after success: repeated presses cannot duplicate a row.
                message(result.optBoolean("synced") ? "Сохранено на сервере и в Google Таблице." : "Сохранено на сервере. Отправка в Google Таблицу выполняется автоматически; статус смотрите в истории.");
            });
        } catch(Exception e) { message("Проверьте три числа и дату с часовым поясом. Пример: 2026-10-28T12:18:05+03:00"); }
    }

    void history() {
        String base=text(server), secret=text(token);
        message("Загружаю историю…");
        background(() -> {
            JSONObject result=call("/measurements?limit=50",null,"application/json",base,secret);
            result.put("statistics",call("/statistics?days=30",null,"application/json",base,secret));
            return result;
        }, result -> {
            JSONObject stats=result.getJSONObject("statistics"), avg=stats.getJSONObject("averages");
            StringBuilder text=new StringBuilder("За последние 30 дней: ").append(stats.getInt("count")).append(" измерений\n");
            if(stats.getInt("count")>0) text.append("Средние: ").append(avg.get("systolic")).append(" / ").append(avg.get("diastolic")).append(", пульс ").append(avg.get("pulse")).append("\n");
            text.append("Ожидают Google: ").append(stats.getInt("pending_sync")).append("\n\nПоследние 50 записей:\n");
            org.json.JSONArray rows=result.getJSONArray("measurements");
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i);
                text.append("\n").append(row.getString("measured_at")).append("\n")
                    .append(row.getInt("systolic")).append(" / ").append(row.getInt("diastolic"))
                    .append(", пульс ").append(row.getInt("pulse"))
                    .append(row.getBoolean("synced") ? " · Google ✓" : " · ожидает Google").append("\n");
                if(!row.optString("note").isEmpty()) text.append(row.getString("note")).append("\n");
            }
            TextView content=new TextView(this); content.setText(text.toString()); content.setTextIsSelectable(true); content.setPadding(24,16,24,16);
            ScrollView view=new ScrollView(this); view.addView(content);
            new AlertDialog.Builder(this).setTitle("История измерений").setView(view).setPositiveButton("Закрыть",null).show();
            message("История обновлена");
        });
    }
}
