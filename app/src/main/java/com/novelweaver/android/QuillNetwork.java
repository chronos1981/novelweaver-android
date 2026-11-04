package com.novelweaver.android;

import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class QuillNetwork {
    static final class Request {
        volatile boolean cancelled;
        volatile HttpURLConnection connection;
        void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
        void check() throws IOException {if(cancelled)throw new IOException("Request stopped. The server may finish processing it.");}
    }
    static URL validate(String value) throws Exception {
        URI uri=new URI(value);
        if(!("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)
            throw new IOException("Enter an HTTP or HTTPS server address without credentials, query parameters or fragments.");
        if(!uri.getPath().endsWith("/api/models")&&!uri.getPath().endsWith("/api/chat/completions"))
            throw new IOException("Only Open WebUI model and chat endpoints are supported.");
        String host=uri.getHost().toLowerCase(java.util.Locale.ROOT).replace("[","").replace("]","");
        if("http".equals(uri.getScheme())&&!local(host))throw new IOException("Use HTTPS for remote servers. HTTP is allowed only for local/private network addresses.");
        return uri.toURL();
    }
    static boolean local(String host) {
        if(host.equals("localhost")||host.endsWith(".localhost")||host.endsWith(".local")||host.equals("::1")||host.matches("f[cd][0-9a-f]{2}:.*")||host.matches("fe[89ab][0-9a-f]:.*"))return true;
        String[] parts=host.split("\\.");if(parts.length!=4)return false;
        try{int a=Integer.parseInt(parts[0]),b=Integer.parseInt(parts[1]);for(String p:parts){int n=Integer.parseInt(p);if(n<0||n>255)return false;}
            return a==127||a==10||a==172&&b>=16&&b<=31||a==192&&b==168||a==169&&b==254||a==100&&b>=64&&b<=127;
        }catch(NumberFormatException e){return false;}
    }
    static JSONObject execute(JSONObject payload,Request task) throws Exception {
        URL url=validate(payload.getString("url"));task.check();
        HttpURLConnection c=(HttpURLConnection)url.openConnection();task.connection=c;
        try {
            task.check();c.setConnectTimeout(20_000);c.setReadTimeout(0);c.setInstanceFollowRedirects(false);
            String method=payload.optString("method","GET");if(!method.equals("GET")&&!method.equals("POST"))throw new IOException("Unsupported request method.");
            c.setRequestMethod(method);c.setRequestProperty("Authorization","Bearer "+payload.getString("key"));
            c.setRequestProperty("Accept","application/json");
            if(method.equals("POST")) {
                byte[] body=payload.optString("body","").getBytes(StandardCharsets.UTF_8);
                if(body.length>2_000_000)throw new IOException("The AI request is too large.");
                c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setFixedLengthStreamingMode(body.length);
                try(OutputStream out=c.getOutputStream()){out.write(body);}
            }
            task.check();int status=c.getResponseCode();
            InputStream input=status>=400?c.getErrorStream():c.getInputStream();
            byte[] bytes=input==null?new byte[0]:readLimited(input,8_000_000,task);
            task.check();return new JSONObject().put("status",status).put("body",new String(bytes,StandardCharsets.UTF_8));
        } finally {task.connection=null;c.disconnect();}
    }
    static byte[] readLimited(InputStream input,int max,Request task) throws Exception {
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] bytes=new byte[16384];int count;
            while((count=in.read(bytes))!=-1){if(task!=null)task.check();if(out.size()+count>max)throw new IOException("File or response exceeds its size limit.");out.write(bytes,0,count);}
            return out.toByteArray();
        }
    }
}
