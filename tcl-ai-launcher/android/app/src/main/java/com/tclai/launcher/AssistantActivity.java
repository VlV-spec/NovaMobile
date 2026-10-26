package com.tclai.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

/** HTTPS-only client; the OpenAI API key stays on Cloudflare Worker, never in this APK. */
public final class AssistantActivity extends Activity {
    private static final int REQUEST_SPEECH = 15;
    private EditText endpoint, deviceToken, message;
    private TextView result;
    private Button send;
    private CheckBox speak;
    private TextToSpeech tts;
    private SharedPreferences prefs;
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        prefs=getSharedPreferences("ai_settings",MODE_PRIVATE);
        ScrollView sc=new ScrollView(this); sc.setBackgroundColor(0xff0c1528);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(35),dp(27),dp(35),dp(35));sc.addView(root);
        TextView title=label("✦  TCL AI · Ассистент",27,true);root.addView(title);
        TextView hint=label("Введите адрес Cloudflare Worker и токен устройства. Ключ OpenAI должен находиться только на сервере.",14,false);
        hint.setTextColor(0xffacc3da);hint.setPadding(0,dp(9),0,dp(14));root.addView(hint);
        endpoint=entry("https://имя.workers.dev",prefs.getString("endpoint",""));root.addView(endpoint);
        deviceToken=entry("Токен устройства (не ключ OpenAI)",prefs.getString("token",""));
        deviceToken.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD );
        root.addView(deviceToken);
        message=entry("Что хочешь узнать?","");root.addView(message);
        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button mic=button("🎙 Сказать");mic.setOnClickListener(v->startSpeech());buttons.addView(mic);
        send=button("➤ Отправить");send.setOnClickListener(v->send());buttons.addView(send);
        Button ping=button("Проверить сервер");ping.setOnClickListener(v->checkServer());buttons.addView(ping);
        root.addView(buttons);
        speak=new CheckBox(this);speak.setText("Произносить ответы вслух");speak.setTextColor(0xffdee9ff);
        speak.setChecked(prefs.getBoolean("speak",false));root.addView(speak);
        result=label("Готово. Адрес и токен сохраняются только в настройках этого приложения.",17,false);
        result.setTextColor(0xffd6e2ff);result.setPadding(0,dp(18),0,dp(12));root.addView(result);
        setContentView(sc);
        tts=new TextToSpeech(this,status->{
            if(status==TextToSpeech.SUCCESS && tts!=null) tts.setLanguage(new Locale("ru","RU"));
        });
    }
    private TextView label(String s,int size,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xfff3f8ff);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private EditText entry(String hint,String value){
        EditText t=new EditText(this);t.setSingleLine(true);t.setTextSize(17);t.setText(value);
        t.setHint(hint);t.setTextColor(0xfff3f8ff);t.setHintTextColor(0xff8da2c1);
        t.setPadding(dp(12),dp(10),dp(12),dp(10));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff1e2b45);bg.setCornerRadius(dp(9));
        bg.setStroke(dp(1),0xff50627a);t.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(51));p.bottomMargin=dp(11);t.setLayoutParams(p);
        return t;
    }
    private Button button(String text){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setFocusable(true);return b;}
    private void startSpeech(){
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU");
        try{startActivityForResult(i,REQUEST_SPEECH);}
        catch(ActivityNotFoundException e){result.setText("На TCL не найдено совместимое распознавание речи. Вводи текст пультом.");}
    }
    @Override protected void onActivityResult(int request,int code,Intent data){
        super.onActivityResult(request,code,data);
        if(request==REQUEST_SPEECH && code==RESULT_OK && data!=null){
            ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if(words!=null && !words.isEmpty()) {message.setText(words.get(0));send();}
        }
    }
    /** Network health check; does not claim the OpenAI secret is configured. */
    private void checkServer(){
        String url = endpoint.getText().toString().trim().replaceAll("/+$", "");
        if (!url.matches("https://[^/\\s]+(?:/[^\\s]*)?")) {
            result.setText("Введите HTTPS-адрес вашего Cloudflare Worker."); return;
        }
        result.setText("Проверяю соединение с сервером…");
        new Thread(() -> {
            HttpURLConnection c = null;
            String status;
            try {
                c = (HttpURLConnection)new URL(url + "/health").openConnection();
                c.setConnectTimeout(6000);c.setReadTimeout(8000);
                c.setRequestMethod("GET");
                status = c.getResponseCode() == 200
                    ? "Cloudflare Worker отвечает. Чтобы проверить OpenAI, отправь тестовое сообщение с токеном."
                    : "Worker отвечает с HTTP " + c.getResponseCode();
            } catch (Exception e) { status = "Сервер недоступен: " + e.getMessage(); }
            finally { if(c != null)c.disconnect(); }
            final String shown = status;
            runOnUiThread(() -> result.setText(shown));
        }).start();
    }

    private void send(){
        String url=endpoint.getText().toString().trim().replaceAll("/+$","");
        String token=deviceToken.getText().toString().trim();
        String prompt=message.getText().toString().trim();
        if(!url.matches("https://[^/\\s]+(?:/[^\\s]*)?") || token.length()<16 || prompt.isEmpty() || prompt.length()>700){
            result.setText("Нужны HTTPS-адрес Worker, токен (не менее 16 символов) и сообщение до 700 символов.");return;
        }
        prefs.edit().putString("endpoint",url).putString("token",token).putBoolean("speak",speak.isChecked()).apply();
        send.setEnabled(false);result.setText("Спрашиваю ИИ…");
        new Thread(()->{
            HttpURLConnection c=null;
            try {
                c=(HttpURLConnection)new URL(url+"/chat").openConnection();
                c.setConnectTimeout(10000);c.setReadTimeout(28000);
                c.setRequestMethod("POST");c.setDoOutput(true);
                c.setRequestProperty("Content-Type","application/json; charset=utf-8");
                c.setRequestProperty("Authorization","Bearer "+token);
                byte[] bytes=new JSONObject().put("message",prompt).toString().getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=c.getOutputStream()){out.write(bytes);}
                int status=c.getResponseCode();
                if(status!=200)throw new IllegalStateException("Сервер вернул HTTP "+status);
                StringBuilder response=new StringBuilder();
                try(BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){
                    String line;while((line=in.readLine())!=null && response.length()<32768)response.append(line);
                }
                JSONObject json=new JSONObject(response.toString());
                String answer=json.optString("reply","Ответ не получен");
                String action=json.optString("action","");
                runOnUiThread(()->{
                    result.setText(answer);send.setEnabled(true);
                    if(speak.isChecked() && tts!=null) tts.speak(answer,TextToSpeech.QUEUE_FLUSH,null,"tclai-answer");
                    execute(action);
                });
            }catch(Exception e){
                String error=e.getMessage();runOnUiThread(()->{result.setText("Ошибка: "+error);send.setEnabled(true);});
            }finally{if(c!=null)c.disconnect();}
        }).start();
    }
    private void execute(String action){
        switch(action){
            case "open_youtube": openPackage("com.google.android.youtube.tv","app.revanced.android.youtube");break;
            case "open_kinopub": openPackage("watch.kino","com.kinopub");break;
            case "volume_up": changeVolume(1);break;
            case "volume_down": changeVolume(-1);break;
            case "pause": media(KeyEvent.KEYCODE_MEDIA_PAUSE);break;
            case "play": media(KeyEvent.KEYCODE_MEDIA_PLAY);break;
            default: break;
        }
    }
    private void openPackage(String... packages){
        for(String pkg:packages){
            Intent launch=getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if(launch==null)launch=getPackageManager().getLaunchIntentForPackage(pkg);
            if(launch!=null){try{startActivity(launch);return;}catch(Exception ignored){}}
        }
        result.append("\nНе нашла установленное приложение для запуска.");
    }
    private void changeVolume(int dir){
        try{AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
            if(audio!=null)audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,dir,AudioManager.FLAG_SHOW_UI);
        }catch(Exception e){result.append("\nТелевизор не разрешил изменить громкость.");}
    }
    private void media(int key){
        try{AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
            if(audio!=null){audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));
                audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));}
        }catch(Exception e){result.append("\nНе удалось управлять воспроизведением.");}
    }
    @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
