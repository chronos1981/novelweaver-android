package com.novelweaver.android;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.util.Base64;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.webkit.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Android owns documents, secrets, narration and networking; the shared editor
 * owns manuscripts and all writing features. No arbitrary native bridge access. */
public final class MainActivity extends Activity {
    private static final String ORIGIN="https://appassets.androidplatform.net";
    private static final String APP_URL=ORIGIN+"/assets/www/index.html";
    private static final int OPEN=101,SAVE=102,IMPORT=103,EXPORT=104;
    private final ExecutorService recoveryIO=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService documentIO=Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService networkIO=Executors.newCachedThreadPool();
    private final Map<String,QuillNetwork.Request> requests=new ConcurrentHashMap<>();
    private final Map<String,Export> exports=new ConcurrentHashMap<>();
    private ScheduledFuture<?> autoSave;
    private WebView web;
    private FrameLayout root;
    private TextView loading;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private ValueCallback<Uri[]> upload;
    private ProjectSession session;
    private BookcaseStore bookcase;
    private KeyVault vault;
    private PlaybackClient narrator;
    private Pending pending;
    private boolean initialized,destroyed;
    private static final class Pending {
        String id,action;JSONObject payload;
        Pending(String id,String action,JSONObject p){this.id=id;this.action=action;payload=p;}
    }
    private static final class Export {
        File file;String name,mime;
        Export(File file,String name,String mime){this.file=file;this.name=name;this.mime=mime;}
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(19,35,48));
        web=new WebView(this);root.addView(web,new FrameLayout.LayoutParams(-1,-1));
        loading=new TextView(this);loading.setText("Opening NovelWeaver…");loading.setTextColor(Color.WHITE);loading.setGravity(Gravity.CENTER);loading.setBackgroundColor(Color.rgb(19,35,48));
        root.addView(loading,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::handleBack);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        try{bookcase=new BookcaseStore(new File(getFilesDir(),"bookcase"),this::bookcaseThumbnail);}catch(IOException e){throw new IllegalStateException(e);}
        session=new ProjectSession(getFilesDir(),new ProjectSession.Documents(){
            public String read(String uri) throws Exception {return readDocument(Uri.parse(uri));}
            public void write(String uri,String json) throws Exception {
                Uri parsed=Uri.parse(uri);String shelf="file".equals(parsed.getScheme())?bookcase.idForPath(parsed.getPath()):"";
                if(!shelf.isEmpty()){ProjectSession.atomic(bookcase.file(shelf),json);return;}
                try(OutputStream out=getContentResolver().openOutputStream(Uri.parse(uri),"wt")){
                    if(out==null)throw new IOException("This document provider does not allow writing. Use Save as to choose another location.");
                    out.write(json.getBytes(StandardCharsets.UTF_8));out.flush();
                }
            }
            public String name(String uri){return documentName(Uri.parse(uri));}
            public long modified(String uri){
                try(Cursor c=getContentResolver().query(Uri.parse(uri),new String[]{DocumentsContract.Document.COLUMN_LAST_MODIFIED},null,null,null)){
                    if(c!=null&&c.moveToFirst())return c.getLong(0);
                }catch(Exception ignored){}return 0;
            }
        });
        vault=new KeyVault(this);narrator=new PlaybackClient(this,this::post);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);settings.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        WebViewAssetLoader assets=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                WebResourceResponse response=assets.shouldInterceptRequest(request.getUrl());
                return response!=null?response:new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(APP_URL.equals(request.getUrl().toString()))return initialized;
                if(request.isForMainFrame()&&request.hasGesture()&&("http".equals(request.getUrl().getScheme())||"https".equals(request.getUrl().getScheme())))
                    try{startActivity(new Intent(Intent.ACTION_VIEW,request.getUrl()));}catch(Exception ignored){}
                return true;
            }
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,android.net.http.SslError error){handler.cancel();}
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
                new android.app.AlertDialog.Builder(MainActivity.this).setTitle("Editor stopped").setMessage("Reopen NovelWeaver to restore its saved recovery copy.").setPositiveButton("Close",(d,w)->finish()).show();return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(upload!=null)upload.onReceiveValue(null);
                if(pending!=null){callback.onReceiveValue(null);return true;}
                upload=callback;
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE);
                ArrayList<String> types=new ArrayList<>();for(String type:params.getAcceptTypes())if(type.contains("/"))types.add(type);
                if(!types.isEmpty())i.putExtra(Intent.EXTRA_MIME_TYPES,types.toArray(new String[0]));
                try{startActivityForResult(i,IMPORT);}catch(Exception ex){upload.onReceiveValue(null);upload=null;toast("No document picker is available.");}
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request){request.deny();}
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(fullscreen!=null){callback.onCustomViewHidden();return;}
                fullscreen=view;fullscreenCallback=callback;root.addView(view,new FrameLayout.LayoutParams(-1,-1));web.setVisibility(View.GONE);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            @Override public void onHideCustomView(){exitFullscreen();}
        });
        if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)){
            WebViewCompat.addWebMessageListener(web,"AndroidBridge",Collections.singleton(ORIGIN),(view,message,origin,mainFrame,reply)->{
                if(!mainFrame||!ORIGIN.equals(origin.toString()))return;
                try{JSONObject m=new JSONObject(message.getData());handle(m.getString("id"),m.getString("action"),m.optJSONObject("payload")==null?new JSONObject():m.getJSONObject("payload"));}
                catch(Exception e){toast("Invalid app command.");}
            });
            web.loadUrl(APP_URL);
        }else loading.setText("Update Android System WebView or Chrome in the Play Store, then reopen NovelWeaver.");
    }

    private String readDocument(Uri uri) throws Exception {
        String shelf="file".equals(uri.getScheme())?bookcase.idForPath(uri.getPath()):"";
        if(!shelf.isEmpty())return ProjectSession.readFile(bookcase.file(shelf));
        InputStream stream=getContentResolver().openInputStream(uri);
        if(stream==null)throw new IOException("The selected file could not be opened.");
        return new String(QuillNetwork.readLimited(stream,ProjectSession.MAX_BYTES,null),StandardCharsets.UTF_8);
    }
    private String documentName(Uri uri){
        if("file".equals(uri.getScheme())){String name=bookcase.displayName(uri.getPath());if(name!=null)return name;}
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}return "Selected project file";
    }
    private String currentBookcaseId(){String value=session.currentUri();if(value==null)return "";Uri uri=Uri.parse(value);return "file".equals(uri.getScheme())?bookcase.idForPath(uri.getPath()):"";}
    private String bookcaseThumbnail(JSONObject cover){
        android.graphics.Bitmap image=null;
        try{
            String data=cover.optString("DataUrl");if(data.length()>2_000_000||!data.matches("^data:image/(png|jpeg);base64,[A-Za-z0-9+/=]+$"))return "";
            byte[] bytes=Base64.decode(data.substring(data.indexOf(',')+1),Base64.DEFAULT);
            android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inJustDecodeBounds=true;
            android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
            if(opts.outWidth<=0||opts.outHeight<=0||(long)opts.outWidth*opts.outHeight>50_000_000)return "";
            opts.inJustDecodeBounds=false;opts.inSampleSize=1;while(opts.outWidth/opts.inSampleSize>240||opts.outHeight/opts.inSampleSize>360)opts.inSampleSize*=2;
            image=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);if(image==null)return "";
            ByteArrayOutputStream out=new ByteArrayOutputStream();image.compress(android.graphics.Bitmap.CompressFormat.JPEG,82,out);
            return "data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
        }catch(Exception e){return "";}finally{if(image!=null)image.recycle();}
    }
    private void rememberPermission(Uri uri,Intent data){
        boolean read=(data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0;
        boolean write=(data.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0;
        try{
            if(read&&write)getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            else if(read)getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            else if(write)getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }catch(SecurityException ignored){toast("This provider may require choosing the file again after restarting.");}
    }
    private void post(JSONObject message){runOnUiThread(()->{
        if(destroyed||web==null||!APP_URL.equals(web.getUrl()))return;
        web.evaluateJavascript("window.AndroidHost&&window.AndroidHost.receive("+message+")",null);
    });}
    private void success(String id,Object result){try{if(result instanceof JSONObject&&Arrays.asList("opened","recovery").contains(((JSONObject)result).optString("status"))&&((JSONObject)result).optJSONObject("project")!=null)publishLibrary(((JSONObject)result).getJSONObject("project"));post(new JSONObject().put("id",id).put("ok",true).put("result",result==null?JSONObject.NULL:result));}catch(Exception ignored){}}
    private void publishLibrary(JSONObject project){String uri=session.currentUri();runOnUiThread(()->narrator.project(project,uri));}
    private void publishLibrary() throws Exception {String value=session.currentJson();if(value!=null)publishLibrary(new JSONObject(value));}
    private void failure(String id,Exception e){try{post(new JSONObject().put("id",id).put("ok",false).put("error",e.getMessage()==null?"Operation failed.":e.getMessage()));}catch(Exception ignored){}}
    interface Job{Object run() throws Exception;}
    private void work(Executor executor,String id,Job job){executor.execute(()->{try{success(id,job.run());}catch(Exception e){failure(id,e);}});}
    private void toast(String message){runOnUiThread(()->Toast.makeText(this,message,Toast.LENGTH_LONG).show());}
    private void announce(JSONObject result){try{post(new JSONObject().put("type","fileStatus").put("result",result));}catch(Exception ignored){}}
    private synchronized void cancelAuto(){if(autoSave!=null){autoSave.cancel(false);autoSave=null;}}
    private synchronized void queueAuto(){
        cancelAuto();if(session.currentUri()==null||session.paused())return;
        autoSave=documentIO.schedule(()->{
            try {if(!session.paused()&&session.currentUri()!=null)announce(session.save(session.currentJson(),null,null));}
            catch(Exception e){try{announce(session.result("error").put("message",e.getMessage()));}catch(Exception ignored){}}
        },3,TimeUnit.SECONDS);
    }

    private void handle(String id,String action,JSONObject p){
        try{switch(action){
            case "initialize":
                work(documentIO,id,()->{
                    JSONObject startup;
                    try{startup=session.startupInfo();}catch(Exception e){startup=new JSONObject().put("hasLast",false).put("recoveries",new JSONArray()).put("recoveryError","Startup metadata could not be read. Choose an empty project or open a file.");}
                    String key="",keyError="";try{key=vault.load();}catch(Exception e){keyError="The saved API key could not be unlocked. Enter it again in Quill settings.";}
                    JSONObject result=new JSONObject().put("startup",startup).put("key",key).put("keyError",keyError).put("voices",narrator.voices()).put("speechAvailable",true);
                    runOnUiThread(()->{initialized=true;loading.setVisibility(View.GONE);});return result;
                });break;
            case "autosave":
                work(recoveryIO,id,()->{JSONObject r=session.recover(p.getString("json"));publishLibrary();queueAuto();return r;});break;
            case "save":
                recoveryIO.execute(()->{try{
                    session.recover(p.getString("json"));publishLibrary();cancelAuto();
                    if(session.currentUri()==null||p.optBoolean("saveAs"))runOnUiThread(()->pick(id,"save",p,SAVE));
                    else work(documentIO,id,()->session.save(p.getString("json"),null,p.optString("revision",null)));
                }catch(Exception e){failure(id,e);}});break;
            case "open":cancelAuto();pick(id,"open",p,OPEN);break;
            case "openLast":cancelAuto();work(documentIO,id,session::openLast);break;
            case "bookcaseList":work(documentIO,id,()->bookcase.list(currentBookcaseId()));break;
            case "bookcaseRead":work(documentIO,id,()->bookcase.read(p.getString("id")));break;
            case "bookcasePut":work(documentIO,id,()->bookcase.put(p.optString("id",""),p.getString("json"),p.optString("revision",null)));break;
            case "bookcaseOpen":cancelAuto();work(documentIO,id,()->{
                String shelf=p.getString("id");bookcase.read(shelf);
                return session.open(Uri.fromFile(bookcase.file(shelf)).toString()).put("bookcaseId",shelf);
            });break;
            case "openRecovery":cancelAuto();work(documentIO,id,()->session.openRecovery(p.getString("id")));break;
            case "reloadDisk":cancelAuto();work(documentIO,id,()->{String uri=session.currentUri();if(uri==null)throw new IOException("No project file is selected.");return session.open(uri);});break;
            case "new":cancelAuto();work(documentIO,id,()->{JSONObject r=session.startNew(p.getString("json"));publishLibrary();return r;});break;
            case "key":work(recoveryIO,id,()->{vault.save(p.optString("key",""),p.optBoolean("remember"));return new JSONObject().put("saved",true);});break;
            case "http":
                QuillNetwork.Request request=new QuillNetwork.Request();requests.put(id,request);
                work(networkIO,id,()->{try{return QuillNetwork.execute(p,request);}finally{requests.remove(id);}});break;
            case "cancel":QuillNetwork.Request r=requests.get(p.optString("requestId"));if(r!=null)networkIO.execute(r::cancel);success(id,null);break;
            case "mediaPlay":
                // A native speech queue snapshots this project before WebView can be suspended.
                JSONObject project=ProjectSession.validate(p.getString("json"));
                narrator.project(project,session.currentUri());
                narrator.command(action,p,()->success(id,null),e->failure(id,e));break;
            case "speak":case "speechStop":case "speechPause":case "speechResume":
            case "mediaState":case "mediaParagraph":case "mediaSettings":
                narrator.command(action,p,()->success(id,null),e->failure(id,e));break;
            case "exportStart":work(recoveryIO,id,()->{
                String token=UUID.randomUUID().toString();File file=File.createTempFile("nw-export-",".tmp",getCacheDir());
                exports.put(token,new Export(file,p.getString("name"),p.optString("mime","application/octet-stream").split(";")[0]));return new JSONObject().put("token",token);
            });break;
            case "exportChunk":work(recoveryIO,id,()->{
                Export out=exports.get(p.getString("token"));if(out==null)throw new IOException("Export session expired.");
                byte[] bytes=Base64.decode(p.getString("data"),Base64.DEFAULT);
                if(bytes.length>100_000||out.file.length()+bytes.length>64_000_000)throw new IOException("Export exceeds its size limit.");
                try(FileOutputStream stream=new FileOutputStream(out.file,true)){stream.write(bytes);}return null;
            });break;
            case "exportFinish":pick(id,"export",p,EXPORT);break;
            case "exportCancel":Export discard=exports.remove(p.optString("token"));if(discard!=null)discard.file.delete();success(id,null);break;
            case "previousBackup":
                work(recoveryIO,id,()->{if(!session.previousFile().exists())throw new IOException("No previous file backup is available yet.");String token=UUID.randomUUID().toString();File file=File.createTempFile("nw-export-",".tmp",getCacheDir());java.nio.file.Files.copy(session.previousFile().toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);exports.put(token,new Export(file,"NovelWeaver-previous-file.novel.json","application/json"));return new JSONObject().put("token",token);});break;
            case "close":success(id,null);finish();break;
            default:throw new IOException("Unknown app command.");
        }}catch(Exception e){failure(id,e);}
    }

    private void pick(String id,String action,JSONObject p,int request){
        if(pending!=null||upload!=null){failure(id,new IOException("Finish the current file selection first."));return;}
        try{
            Intent intent=new Intent(request==OPEN?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
            if(request==OPEN)intent.setType("*/*"); // Providers may report JSON as text or binary.
            else if(request==SAVE)intent.setType("application/json").putExtra(Intent.EXTRA_TITLE,p.optString("name","NovelWeaver")+".novel.json");
            else {Export out=exports.get(p.getString("token"));if(out==null)throw new IOException("Export session expired.");intent.setType(out.mime).putExtra(Intent.EXTRA_TITLE,out.name);}
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            pending=new Pending(id,action,p);startActivityForResult(intent,request);
        }catch(Exception e){pending=null;failure(id,e);}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==IMPORT){
            ValueCallback<Uri[]> callback=upload;upload=null;if(callback==null)return;
            if(result!=RESULT_OK||data==null){callback.onReceiveValue(null);return;}
            List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());
            callback.onReceiveValue(uris.toArray(new Uri[0]));return;
        }
        Pending item=pending;pending=null;if(item==null)return;
        if(result!=RESULT_OK||data==null||data.getData()==null){
            try{success(item.id,new JSONObject().put("status","cancelled"));}catch(Exception ignored){}
            if(item.action.equals("export")){Export out=exports.remove(item.payload.optString("token"));if(out!=null)out.file.delete();}
            return;
        }
        Uri uri=data.getData();if(!item.action.equals("export"))rememberPermission(uri,data);
        if(item.action.equals("open"))work(documentIO,item.id,()->session.open(uri.toString()));
        else if(item.action.equals("save"))work(documentIO,item.id,()->{JSONObject r=session.save(item.payload.getString("json"),uri.toString(),null);publishLibrary();return r;});
        else work(documentIO,item.id,()->{
            Export out=exports.remove(item.payload.getString("token"));if(out==null)throw new IOException("Export session expired.");
            try(InputStream in=new FileInputStream(out.file);OutputStream target=getContentResolver().openOutputStream(uri,"wt")){
                if(target==null)throw new IOException("The selected location is not writable.");byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)target.write(bytes,0,n);target.flush();
            }finally{out.file.delete();}
            return new JSONObject().put("status","saved");
        });
    }

    private void exitFullscreen(){
        if(fullscreen==null)return;root.removeView(fullscreen);fullscreen=null;web.setVisibility(View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        if(fullscreenCallback!=null)fullscreenCallback.onCustomViewHidden();fullscreenCallback=null;
    }
    @Override public void onBackPressed(){
        handleBack();
    }
    private void handleBack(){
        if(fullscreen!=null){exitFullscreen();return;}
        if(initialized)web.evaluateJavascript("window.NWAndroid&&window.NWAndroid.back()",null);else super.onBackPressed();
    }
    @Override protected void onPause(){
        if(initialized)web.evaluateJavascript("window.NWAndroid&&window.NWAndroid.pause()",null);
        super.onPause();
    }
    @Override protected void onDestroy(){
        destroyed=true;cancelAuto();narrator.destroy();
        for(QuillNetwork.Request r:requests.values())networkIO.execute(r::cancel);
        // Existing saves may finish; shutting down must not truncate their writes.
        recoveryIO.shutdown();documentIO.shutdown();networkIO.shutdown();
        if(upload!=null)upload.onReceiveValue(null);
        for(Export out:exports.values())out.file.delete();exports.clear();
        root.removeAllViews();web.destroy();super.onDestroy();
    }
}
