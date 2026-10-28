package ru.personal.omronscan;

import java.net.URI;

final class ServerAddress {
    static String normalize(String input) {
        String value=input.trim();
        if(!value.contains("://")) value="https://"+value;
        try {
            URI uri=new URI(value);
            if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null ||
               uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null ||
               uri.getPort()>65535 || uri.getPort()==0) throw new IllegalArgumentException();
            String path=uri.getRawPath()==null?"":uri.getRawPath().replaceAll("/+$","");
            if(path.contains("..") || path.contains("%")) throw new IllegalArgumentException();
            return "https://"+uri.getRawAuthority().toLowerCase(java.util.Locale.ROOT)+path;
        } catch(Exception error) {
            throw new IllegalArgumentException("Укажите HTTPS-адрес вашего сервера без токена и дополнительных параметров.");
        }
    }
}
