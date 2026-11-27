package com.novelweaver.android;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.*;

public class KoboTransferTest {
    @Test public void servesOnlySelectedBooksAndCleansUp() throws Exception {
        File a=File.createTempFile("nw-kobo-a",".epub");
        File b=File.createTempFile("nw-kobo-b",".epub");
        byte[] first={80,75,3,4,1,2,3},second={80,75,3,4,5,6};
        Files.write(a.toPath(),first);Files.write(b.toPath(),second);
        KoboTransfer server=new KoboTransfer((Inet4Address)InetAddress.getByName("127.0.0.1"),
            Arrays.asList(new KoboTransfer.Book(a,"First & book.epub"),new KoboTransfer.Book(b,"Second.epub")));
        try {
            URL home=new URL(server.url());
            assertTrue(home.getPath().matches("/[0-9]{4}"));
            HttpURLConnection index=get(home,"GET");
            assertEquals(200,index.getResponseCode());
            String html=new String(index.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            index.disconnect();
            assertTrue(html.contains("First &amp; book.epub"));
            Matcher links=Pattern.compile("href=\"([^\"]+)\"").matcher(html);
            assertTrue(links.find()); URL firstUrl=new URL(home,links.group(1));
            assertTrue(links.find()); URL secondUrl=new URL(home,links.group(1));
            assertFalse(links.find());
            HttpURLConnection head=get(firstUrl,"HEAD");
            assertEquals(200,head.getResponseCode());assertEquals(first.length,head.getContentLength());
            assertEquals("application/epub+zip",head.getContentType());head.disconnect();
            HttpURLConnection download=get(firstUrl,"GET");
            assertTrue(download.getHeaderField("Content-Disposition").contains("attachment"));
            assertArrayEquals(first,download.getInputStream().readAllBytes());download.disconnect();
            HttpURLConnection other=get(secondUrl,"GET");
            assertArrayEquals(second,other.getInputStream().readAllBytes());other.disconnect();
            for(String target:new String[]{"/","/wrong/book/0/First.epub",home.getPath()+"/book/99/no.epub"}){
                HttpURLConnection denied=get(new URL(home,target),"GET");
                assertEquals(404,denied.getResponseCode());denied.disconnect();
            }
        } finally {server.close();}
        assertFalse(a.exists());assertFalse(b.exists());
    }
    @Test public void rejectsEmptyExport() throws Exception {
        File file=File.createTempFile("nw-kobo-empty",".epub");
        try{
            try{
                new KoboTransfer((Inet4Address)InetAddress.getByName("127.0.0.1"),
                    Collections.singletonList(new KoboTransfer.Book(file,"Empty.epub")));
                fail("Expected empty export to be rejected");
            }catch(IOException expected){assertTrue(expected.getMessage().contains("completed"));}
        }finally{file.delete();}
    }
    private static HttpURLConnection get(URL url,String method) throws Exception {
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setRequestMethod(method);c.setConnectTimeout(3000);c.setReadTimeout(3000);return c;
    }
}
