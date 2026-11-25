package com.novelweaver.android;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only, temporary, single-interface HTTP server. Owns staged files after start. */
final class KoboTransfer implements Closeable {
    static final int MAX_BOOKS=32;
    static final long MAX_BYTES=128_000_000L;
    static final long DURATION_MS=15*60*1000L;
    static final class Book {
        final File file;
        final String name;
        Book(File file,String name){this.file=file;this.name=name;}
    }
    private final ServerSocket listener;
    private final List<Book> books;
    private final String path;
    private final AtomicBoolean closed=new AtomicBoolean();
    private volatile Socket active;
    private final Thread worker;
    KoboTransfer(Inet4Address address,List<Book> books) throws IOException {
        if(books.isEmpty()||books.size()>MAX_BOOKS)throw new IOException("Choose 1–32 books.");
        long size=0;
        for(Book b:books){
            if(!b.file.isFile()||b.file.length()==0||!b.name.toLowerCase(Locale.ROOT).endsWith(".epub"))
                throw new IOException("Only completed EPUB exports can be shared.");
            size+=b.file.length();
            if(size>MAX_BYTES)throw new IOException("Selected ebooks exceed 128 MB.");
        }
        this.books=new ArrayList<>(books);
        byte[] secret=new byte[6];new SecureRandom().nextBytes(secret);
        StringBuilder key=new StringBuilder("/");
        for(byte b:secret)key.append(String.format(Locale.ROOT,"%02x",b&255));
        path=key.toString();
        ServerSocket socket=new ServerSocket();
        try{
            try{socket.bind(new InetSocketAddress(address,8765),8);}
            catch(BindException occupied){socket.close();socket=new ServerSocket();socket.bind(new InetSocketAddress(address,0),8);}
            listener=socket;
        }catch(IOException|RuntimeException e){try{socket.close();}catch(IOException ignored){}throw e;}
        worker=new Thread(this::serve,"NovelWeaver-Kobo");worker.setDaemon(true);worker.start();
    }
    String url(){return "http://"+listener.getInetAddress().getHostAddress()+":"+listener.getLocalPort()+path;}
    private void serve(){
        try{
            while(!closed.get()){
                try(Socket socket=listener.accept()){
                    active=socket;
                    if(closed.get())break;
                    socket.setSoTimeout(5000);
                    handle(socket);
                }catch(IOException ignored){if(closed.get())break;}
                finally{active=null;}
            }
        }finally{close();}
    }
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        for(int i=0;i<8192;i++){
            int b=in.read();
            if(b<0)throw new EOFException();
            if(b=='\n')return bytes.toString("US-ASCII").replaceFirst("\\r$","");
            bytes.write(b);
        }
        throw new IOException("HTTP line too long");
    }
    private void handle(Socket socket) throws IOException {
        InputStream in=new BufferedInputStream(socket.getInputStream());
        OutputStream out=new BufferedOutputStream(socket.getOutputStream());
        String[] request=line(in).split(" ");
        int headerBytes=0;
        for(int i=0;;i++){
            String header=line(in);
            headerBytes+=header.length();
            if(i>64||headerBytes>16384)throw new IOException("HTTP headers too large");
            if(header.isEmpty())break;
        }
        if(request.length!=3){text(out,400,"Bad request",false,"Bad request");return;}
        boolean head=request[0].equals("HEAD");
        if(!head&&!request[0].equals("GET")){text(out,405,"Method not allowed",false,"GET or HEAD only");return;}
        String target=request[1];
        if(target.equals(path)||target.equals(path+"/")){
            StringBuilder html=new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>NovelWeaver</title></head><body style=\"font:20px sans-serif;background:white;color:black;margin:18px\"><h1>NovelWeaver</h1><p>Tap an ebook to download it.</p>");
            for(int i=0;i<books.size();i++)html.append("<p><a href=\"").append(path).append("/book/").append(i).append(".epub\">").append(escape(books.get(i).name)).append("</a></p>");
            html.append("<p>Keep NovelWeaver open until all downloads finish.</p></body></html>");
            text(out,200,"OK",head,html.toString());return;
        }
        for(int i=0;i<books.size();i++){
            if(!target.equals(path+"/book/"+i+".epub"))continue;
            Book b=books.get(i);
            String fallback=b.name.replaceAll("[^A-Za-z0-9._ -]","_");
            String encoded=URLEncoder.encode(b.name,"UTF-8").replace("+","%20").replace("*","%2A");
            headers(out,200,"OK","application/epub+zip",b.file.length(),"Content-Disposition: attachment; filename=\""+fallback+"\"; filename*=UTF-8''"+encoded+"\r\n");
            if(!head)try(InputStream file=new FileInputStream(b.file)){
                byte[] buffer=new byte[32768];int n;
                while((n=file.read(buffer))!=-1){out.write(buffer,0,n);}
            }
            out.flush();return;
        }
        text(out,404,"Not found",head,"Not found");
    }
    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    private static void text(OutputStream out,int code,String reason,boolean head,String body) throws IOException {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        headers(out,code,reason,"text/html; charset=utf-8",bytes.length,"");
        if(!head)out.write(bytes);out.flush();
    }
    private static void headers(OutputStream out,int code,String reason,String type,long length,String extra) throws IOException {
        String value="HTTP/1.1 "+code+" "+reason+"\r\nContent-Type: "+type+"\r\nContent-Length: "+length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n"+extra+"\r\n";
        out.write(value.getBytes(StandardCharsets.US_ASCII));
    }
    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;
        try{listener.close();}catch(IOException ignored){}
        Socket socket=active;if(socket!=null)try{socket.close();}catch(IOException ignored){}
        for(Book b:books)b.file.delete();
    }
}
