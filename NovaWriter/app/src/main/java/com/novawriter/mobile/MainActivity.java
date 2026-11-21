package com.novawriter.mobile;

import android.app.Activity;
import android.print.PrintManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.print.PrintAttributes;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class MainActivity extends Activity {
    private static final int OPEN=101, SAVE=102, IMAGE=103;
    private static final int MAX=15*1024*1024;
    private WebView web;
    private byte[] staged;
    private ValueCallback<Uri[]> imageCallback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(24,90,189));
        getWindow().setNavigationBarColor(Color.rgb(249,250,252));
        web=new WebView(this);
        web.setBackgroundColor(Color.rgb(240,242,245));
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBlockNetworkLoads(true);
        settings.setDefaultTextEncodingName("UTF-8");
        web.addJavascriptInterface(new Bridge(),"AndroidBridge");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();
                return !("file".equals(uri.getScheme())&&uri.toString().startsWith("file:///android_asset/"));
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(imageCallback!=null)imageCallback.onReceiveValue(null);
                imageCallback=callback;
                try {
                    Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    startActivityForResult(intent,IMAGE);
                    return true;
                }catch(Exception e){
                    imageCallback.onReceiveValue(null);
                    imageCallback=null;
                    return false;
                }
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private void script(String command){runOnUiThread(()->{
        if(web!=null)web.evaluateJavascript(command,null);
    });}
    private void error(String message){runOnUiThread(()->Toast.makeText(this,message,Toast.LENGTH_LONG).show());}
    private String fileName(Uri uri){
        try(Cursor c=getContentResolver().query(uri,null,null,null,null)){
            if(c!=null&&c.moveToFirst()){
                int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if(n>=0)return c.getString(n);
            }
        }catch(Exception ignored){}
        return "Документ.docx";
    }
    private byte[] read(Uri uri)throws Exception{
        try(InputStream in=getContentResolver().openInputStream(uri);
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)throw new IllegalStateException("Нет доступа к файлу");
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1){
                if(out.size()+n>MAX)throw new IllegalStateException("DOCX больше 15 МБ");
                out.write(buf,0,n);
            }
            return out.toByteArray();
        }
    }
    public final class Bridge{
        @JavascriptInterface public void openDocument(){runOnUiThread(()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            try{startActivityForResult(i,OPEN);}catch(Exception e){error("Не удалось выбрать документ");}
        });}
        @JavascriptInterface public void saveDocument(String b64,String name,String mime){
            byte[] bytes;
            try{
                bytes=Base64.decode(b64,Base64.DEFAULT);
                if(bytes.length>MAX)throw new IllegalArgumentException("Файл больше 15 МБ");
            }catch(Exception e){error("Ошибка подготовки документа: "+e.getMessage());script("onNativeSaveResult(false)");return;}
            runOnUiThread(()->{
                staged=bytes;
                Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(("text/plain".equals(mime)||"text/html".equals(mime))?mime:"application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                i.putExtra(Intent.EXTRA_TITLE,name.replaceAll("[\\\\/:*?\"<>|]","_"));
                try{startActivityForResult(i,SAVE);}catch(Exception e){staged=null;script("onNativeSaveResult(false)");}
            });
        }
        @JavascriptInterface public void printDocument(){runOnUiThread(()->{
            try{
                PrintManager pm=(PrintManager)getSystemService(Context.PRINT_SERVICE);
                pm.print("Nova Writer",web.createPrintDocumentAdapter("Документ"),new PrintAttributes.Builder().build());
            }catch(Exception e){error("Ошибка печати");}
        });}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==IMAGE){
            if(imageCallback!=null){
                Uri[] uris=(result==RESULT_OK&&data!=null&&data.getData()!=null)?new Uri[]{data.getData()}:null;
                imageCallback.onReceiveValue(uris);
                imageCallback=null;
            }
            return;
        }
        if(request==OPEN){
            if(result==RESULT_OK&&data!=null&&data.getData()!=null){
                Uri uri=data.getData();
                new Thread(()->{
                    try{
                        byte[] bytes=read(uri);
                        String encoded=Base64.encodeToString(bytes,Base64.NO_WRAP);
                        script("onNativeDocxReady("+JSONObject.quote(encoded)+","+JSONObject.quote(fileName(uri))+")");
                    }catch(Exception e){error("Не удалось открыть DOCX: "+e.getMessage());}
                },"nova-open").start();
            }
            return;
        }
        if(request==SAVE){
            byte[] bytes=staged;staged=null;
            if(result!=RESULT_OK||data==null||data.getData()==null||bytes==null){
                script("onNativeSaveResult(false)");
                return;
            }
            Uri uri=data.getData();
            new Thread(()->{
                boolean ok=false;
                try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                    if(out==null)throw new IllegalStateException("Нет доступа на запись");
                    out.write(bytes);out.flush();ok=true;
                }catch(Exception e){error("Ошибка сохранения: "+e.getMessage());}
                script("onNativeSaveResult("+ok+")");
            },"nova-save").start();
        }
    }
    @Override protected void onDestroy(){
        if(web!=null){web.removeJavascriptInterface("AndroidBridge");web.destroy();web=null;}
        super.onDestroy();
    }
}