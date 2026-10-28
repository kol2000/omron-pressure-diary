package ru.personal.omronscan;
import org.junit.Test;
import static org.junit.Assert.*;

public class ServerAddressTest {
    @Test public void normalizesAddressWithoutScheme(){assertEquals("https://192.0.2.10",ServerAddress.normalize(" 192.0.2.10/ "));}
    @Test public void preservesSecurePortAndBasePath(){assertEquals("https://diary.example:8443/api",ServerAddress.normalize("https://DIARY.EXAMPLE:8443/api/"));}
    @Test public void rejectsUnsafeOrAmbiguousAddresses(){for(String value:new String[]{"http://diary.example","https://token@diary.example","https://diary.example?token=secret","https://diary.example/#token","", "https://diary.example:70000", "https://diary.example/../api"}){try{ServerAddress.normalize(value);fail(value);}catch(IllegalArgumentException expected){}}}
}
