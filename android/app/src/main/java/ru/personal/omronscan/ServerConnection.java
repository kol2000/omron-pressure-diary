package ru.personal.omronscan;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class ServerConnection {
    static final class Failure extends Exception {
        final boolean unauthorized;
        Failure(String message,boolean unauthorized){super(message);this.unauthorized=unauthorized;}
    }
    static void check(String base,String token) throws Failure {
        HttpURLConnection connection=null;
        try {
            connection=(HttpURLConnection)new URL(ServerAddress.normalize(base)+"/health").openConnection();
            connection.setRequestMethod("GET");connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(7000);connection.setReadTimeout(7000);connection.setUseCaches(false);
            connection.setRequestProperty("Authorization","Bearer "+token);
            int code=connection.getResponseCode();
            if(code==401 || code==403) throw new Failure("Неверный токен доступа. Проверьте его и подключитесь снова.",true);
            if(code!=200) throw new Failure("Сервер ответил с ошибкой "+code+". Повторите подключение.",false);
            try(InputStream input=connection.getInputStream()){
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[512];int count;
                while((count=input.read(buffer))!=-1){bytes.write(buffer,0,count);if(bytes.size()>8192)throw new Failure("По этому адресу отвечает другой сервис.",false);}
                JSONObject response=new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
                if(!"ok".equals(response.optString("status")))throw new Failure("По этому адресу не найден дневник давления.",false);
            }
        }catch(Failure error){throw error;}
        catch(org.json.JSONException error){throw new Failure("По этому адресу отвечает другой сервис. Проверьте адрес дневника.",false);}
        catch(javax.net.ssl.SSLException error){throw new Failure("Сертификат сервера не подтверждён. Установите сертификат CA дневника на телефон и проверьте адрес.",false);}
        catch(java.net.SocketTimeoutException error){throw new Failure("Сервер не ответил. Проверьте домашний Wi-Fi или VPN и повторите попытку.",false);}
        catch(java.net.UnknownHostException error){throw new Failure("Адрес сервера не найден. Проверьте адрес, Wi-Fi или VPN.",false);}
        catch(Exception error){throw new Failure("Нет связи с сервером. Проверьте домашний Wi-Fi или VPN.",false);}
        finally{if(connection!=null)connection.disconnect();}
    }
}
