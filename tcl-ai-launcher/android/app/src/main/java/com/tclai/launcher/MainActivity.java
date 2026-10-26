package com.tclai.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import java.io.InputStream;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Independent TV launcher prototype. Not based on Projectivy source or resources. */
public final class MainActivity extends Activity {
    private static final int WHITE = 0xfff5f7ff, SUB = 0xffa6b7d1;
    private static final int CYAN = 0xff67e3ed, VIOLET = 0xff957dff;
    private static final int PICK_WALLPAPER = 801;
    private static final int[] BUILT_IN_WALLPAPERS = {
        R.drawable.wallpaper_neon_moon,
        R.drawable.wallpaper_penthouse,
        R.drawable.wallpaper_cyberpunk,
        R.drawable.wallpaper_rooftop
    };
    private static final String[] WALLPAPER_NAMES = {
        "Неоновый мегаполис под луной", "Фиолетовый пентхаус",
        "Киберпанк над городом", "Вечер на крыше"
    };
    private final List<String> categories = new ArrayList<>();
    private final Map<String, String> assignments = new LinkedHashMap<>();
    private final Set<String> hidden = new LinkedHashSet<>();
    private final Map<String, ResolveInfo> apps = new LinkedHashMap<>();
    private final List<String> watchShows = new ArrayList<>();
    private final Map<String, String> showPosters = new LinkedHashMap<>();
    private static final int PICK_SERIES_POSTER = 802;
    private String pendingPosterTitle;
    private LinearLayout body;
    private ImageView wallpaperView;
    private SharedPreferences prefs;
    private PackageManager pm;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        pm = getPackageManager();
        loadSettings();
        findApps();
        loadShows();
        draw();
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs != null) { findApps(); draw(); }
    }

    private int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int fill, float radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(dp(radius));
        if (strokeColor != 0) d.setStroke(dp(1), strokeColor);
        return d;
    }
    private TextView text(String label, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(label); t.setTextSize(sp); t.setTextColor(color);
        t.setFontFeatureSettings("kern");
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h));
    }
    private void loadSettings() {
        categories.clear(); assignments.clear(); hidden.clear();
        try {
            JSONObject o = new JSONObject(prefs.getString("layout", "{}"));
            JSONArray a = o.optJSONArray("categories");
            if (a != null) for (int i=0; i<a.length(); i++) {
                String s = a.optString(i, "").trim();
                if (!s.isEmpty() && !categories.contains(s)) categories.add(s);
            }
            JSONObject ass = o.optJSONObject("assignments");
            if (ass != null) {
                java.util.Iterator<String> it = ass.keys();
                while (it.hasNext()) { String k = it.next(); assignments.put(k, ass.optString(k)); }
            }
            JSONArray h = o.optJSONArray("hidden");
            if (h != null) for (int i=0; i<h.length(); i++) hidden.add(h.optString(i));
        } catch (Exception ignored) { }
        if (categories.isEmpty()) {
            categories.add("Кино"); categories.add("Приложения");
        }
    }
    private void saveSettings() {
        JSONObject o = new JSONObject();
        try {
            o.put("categories", new JSONArray(categories));
            JSONObject a = new JSONObject();
            for (Map.Entry<String, String> e: assignments.entrySet()) a.put(e.getKey(), e.getValue());
            o.put("assignments", a);
            o.put("hidden", new JSONArray(hidden));
            prefs.edit().putString("layout", o.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void findApps() {
        apps.clear();
        scanCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        scanCategory(Intent.CATEGORY_LAUNCHER);
    }
    private void scanCategory(String category) {
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> matches = pm.queryIntentActivities(probe, 0);
        if (matches == null) return;
        Collections.sort(matches, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return String.CASE_INSENSITIVE_ORDER.compare(
                    String.valueOf(a.loadLabel(pm)), String.valueOf(b.loadLabel(pm)));
            }
        });
        for (ResolveInfo r: matches) {
            if (r.activityInfo == null) continue;
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            if (!apps.containsKey(pkg)) apps.put(pkg, r);
        }
    }
    private String categoryOf(String pkg) {
        String chosen = assignments.get(pkg);
        if (chosen != null && categories.contains(chosen)) return chosen;
        String id = pkg.toLowerCase(Locale.ROOT);
        if (id.contains("youtube") || id.contains("kino") || id.contains("netflix") ||
            id.contains("video") || id.contains("ivi") || id.contains("okko") ||
            id.contains("film") || id.contains("primevideo")) {
            return categories.contains("Кино") ? "Кино" : categories.get(categories.size()-1);
        }
        return categories.contains("Приложения") ? "Приложения" : categories.get(categories.size()-1);
    }

    private void draw() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xff080f20);
        ImageView wallpaper = new ImageView(this);
        wallpaperView = wallpaper;
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        loadWallpaper(wallpaper);
        applyImageAdjustments();
        frame.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));

        // Readable menus without obscuring the character on the right.
        View leftShade = new View(this);
        leftShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xe9060b1b, 0xb2091022, 0x44060a19, 0x18000000}));
        frame.addView(leftShade, new FrameLayout.LayoutParams(-1, -1));
        View bottomShade = new View(this);
        bottomShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00020916, 0x24020916, 0xd9060b19}));
        frame.addView(bottomShade, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true); scroller.setClipToPadding(false);
        scroller.setVerticalScrollBarEnabled(false);
        frame.addView(scroller, new FrameLayout.LayoutParams(-1,-1));
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(34), dp(22), dp(32), dp(40));
        scroller.addView(body);
        drawHeader();
        // Give the wallpaper room to breathe on a 1080p TV.
        TextView welcome = text("Твой экран. Твои правила.", 25, WHITE, true);
        welcome.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        welcome.setShadowLayer(dp(5), 0, dp(2), 0xff050712);
        LinearLayout.LayoutParams welcomeLayout = lp(-1, 125);
        welcomeLayout.bottomMargin = dp(10);
        body.addView(welcome, welcomeLayout);
        drawKinoWatchlist();
        for (String name: categories) drawCategory(name);
        setContentView(frame);
    }

    private void loadWallpaper(ImageView view) {
        String uriString = prefs.getString("custom_wallpaper", "");
        if (!uriString.isEmpty()) {
            try {
                Uri uri = Uri.parse(uriString);
                // Decode with downsampling; full camera photos can exhaust a 2 GB TV.
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, options);
                }
                int scale = 1;
                while (options.outWidth / scale > 1920 || options.outHeight / scale > 1080) scale *= 2;
                options.inJustDecodeBounds = false;
                options.inSampleSize = scale;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
                    if (bitmap != null) {
                        view.setImageDrawable(new BitmapDrawable(getResources(), bitmap));
                        return;
                    }
                }
            } catch (Exception | OutOfMemoryError e) {
                prefs.edit().remove("custom_wallpaper").apply();
            }
        }
        int selected = prefs.getInt("wallpaper_index", 0);
        if (selected < 0 || selected >= BUILT_IN_WALLPAPERS.length) selected = 0;
        view.setImageResource(BUILT_IN_WALLPAPERS[selected]);
    }

    /** Changes the launcher wallpaper only, never the TCL panel or Android-wide color calibration. */
    private void applyImageAdjustments() {
        if (wallpaperView == null || prefs == null) return;
        int saturation = prefs.getInt("wallpaper_saturation", 100);
        int brightness = prefs.getInt("wallpaper_brightness", 100);
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(Math.max(0, Math.min(200, saturation)) / 100f);
        float b = Math.max(40, Math.min(160, brightness)) / 100f;
        ColorMatrix multiplier = new ColorMatrix(new float[]{
            b,0,0,0,0,
            0,b,0,0,0,
            0,0,b,0,0,
            0,0,0,1,0
        });
        matrix.postConcat(multiplier);
        wallpaperView.setColorFilter(new ColorMatrixColorFilter(matrix));
    }

    /** D-pad OK long press on the on-screen gear opens TV-safe wallpaper controls. */
    private void showWallpaperControls() {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(25), dp(12), dp(25), dp(12));
        TextView note = text("Меняется только фон лаунчера. Яркость матрицы TCL и настройки цвета Android не затрагиваются.", 13, SUB, false);
        controls.addView(note, lp(-1, -2));
        int beforeBrightness = prefs.getInt("wallpaper_brightness", 100);
        int beforeSaturation = prefs.getInt("wallpaper_saturation", 100);
        TextView brightnessLabel = text("Яркость фона: " + beforeBrightness + "%", 17, WHITE, true);
        LinearLayout.LayoutParams bp = lp(-1, -2);bp.topMargin=dp(15);controls.addView(brightnessLabel, bp);
        SeekBar brightnessBar = new SeekBar(this);
        brightnessBar.setMax(120);
        brightnessBar.setProgress(beforeBrightness - 40);
        controls.addView(brightnessBar, lp(-1, -2));
        TextView saturationLabel = text("Насыщенность: " + beforeSaturation + "%", 17, WHITE, true);
        LinearLayout.LayoutParams sp = lp(-1, -2);sp.topMargin=dp(10);controls.addView(saturationLabel, sp);
        SeekBar saturationBar = new SeekBar(this);
        saturationBar.setMax(200);
        saturationBar.setProgress(beforeSaturation);
        controls.addView(saturationBar, lp(-1, -2));
        brightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int val, boolean fromUser) {
                prefs.edit().putInt("wallpaper_brightness", val + 40).apply();
                brightnessLabel.setText("Яркость фона: " + (val + 40) + "%");
                applyImageAdjustments();
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        saturationBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int val, boolean fromUser) {
                prefs.edit().putInt("wallpaper_saturation", val).apply();
                saturationLabel.setText("Насыщенность: " + val + "%");
                applyImageAdjustments();
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        new AlertDialog.Builder(this)
            .setTitle("Изображение · TCL AI")
            .setView(controls)
            .setPositiveButton("Сохранить", (d,w) -> { })
            .setNeutralButton("Сбросить", (d,w) -> {
                prefs.edit().putInt("wallpaper_brightness", 100)
                     .putInt("wallpaper_saturation", 100).apply();
                applyImageAdjustments();
            })
            .setNegativeButton("Отмена", (d,w) -> {
                prefs.edit().putInt("wallpaper_brightness", beforeBrightness)
                     .putInt("wallpaper_saturation", beforeSaturation).apply();
                applyImageAdjustments();
            })
            .setOnCancelListener(d -> {
                prefs.edit().putInt("wallpaper_brightness", beforeBrightness)
                     .putInt("wallpaper_saturation", beforeSaturation).apply();
                applyImageAdjustments();
            }).show();
    }

    private void openAndroidSettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
        } catch (Exception e) {
            try {
                Intent fallback = pm.getLaunchIntentForPackage("com.android.tv.settings");
                if (fallback != null) { startActivity(fallback); return; }
            } catch (Exception ignored) { }
            toast("Не удалось открыть настройки Android TV");
        }
    }

    private void chooseWallpaper() {
        String[] options = new String[WALLPAPER_NAMES.length + 1];
        System.arraycopy(WALLPAPER_NAMES, 0, options, 0, WALLPAPER_NAMES.length);
        options[WALLPAPER_NAMES.length] = "Выбрать собственное изображение…";
        new AlertDialog.Builder(this).setTitle("Обои главного экрана")
            .setItems(options, (dialog, index) -> {
                if (index < WALLPAPER_NAMES.length) {
                    prefs.edit().putInt("wallpaper_index", index).remove("custom_wallpaper").apply();
                    draw();
                } else {
                    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    pick.setType("image/*");
                    pick.addCategory(Intent.CATEGORY_OPENABLE);
                    pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    try { startActivityForResult(pick, PICK_WALLPAPER); }
                    catch (Exception e) { toast("Файловый менеджер для выбора обоев не найден"); }
                }
            }).setNegativeButton("Отмена", null).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_SERIES_POSTER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (pendingPosterTitle != null && watchShows.contains(pendingPosterTitle)) {
                    showPosters.put(pendingPosterTitle, uri.toString()); saveShows(); draw();
                }
            } catch (Exception e) { toast("Не удалось сохранить обложку"); }
            pendingPosterTitle = null;
            return;
        }
        if (requestCode == PICK_WALLPAPER && resultCode == RESULT_OK &&
                data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                prefs.edit().putString("custom_wallpaper", uri.toString()).apply();
                draw();
            } catch (Exception e) { toast("Не удалось сохранить доступ к изображению"); }
        }
    }

    private void drawHeader() {
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("◈  TCL AI", 23, WHITE, true); bar.addView(logo, lp(0,-2));
        LinearLayout.LayoutParams expand=(LinearLayout.LayoutParams)logo.getLayoutParams(); expand.weight=1; logo.setLayoutParams(expand);
        TextView time = text(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), 17, SUB, false);
        LinearLayout.LayoutParams timep=lp(-2,-2); timep.rightMargin=dp(22); bar.addView(time,timep);
        TextView vpn = pill(vpnBadge(), false);
        vpn.setContentDescription("Состояние системного VPN. Нажми, чтобы открыть Happ.");
        vpn.setOnClickListener(v -> openHapp());
        LinearLayout.LayoutParams vp=lp(-2,39); vp.rightMargin=dp(8); bar.addView(vpn, vp);
        TextView menu = pill("☷  TCL AI", false);
        menu.setOnClickListener(v -> showSettings());
        LinearLayout.LayoutParams mp=lp(-2,39);mp.rightMargin=dp(8);bar.addView(menu,mp);
        TextView settings = pill("⚙  Android", false);
        settings.setContentDescription("Нажми OK: настройки Android. Удерживай OK: яркость и насыщенность обоев.");
        settings.setOnClickListener(v -> openAndroidSettings());
        settings.setOnLongClickListener(v -> { showWallpaperControls(); return true; });
        LinearLayout.LayoutParams sp=lp(-2,39); sp.rightMargin=dp(8); bar.addView(settings,sp);
        TextView ai = pill("✦  ИИ", true); ai.setOnClickListener(v -> openAssistant());
        bar.addView(ai,lp(-2,39));
        body.addView(bar,lp(-1,45));
    }
    private TextView pill(String label, boolean primary) {
        TextView v = text(label, 14, primary ? 0xff101729 : WHITE, true);
        v.setGravity(Gravity.CENTER); v.setPadding(dp(17),0,dp(17),0);
        v.setBackground(shape(primary ? CYAN : 0xff253047, 13, primary ? 0 : 0xff42516c));
        makeFocusable(v, primary ? CYAN : 0xff253047);
        return v;
    }
    private void makeFocusable(View v, int base) {
        v.setFocusable(true); v.setFocusableInTouchMode(false);
        v.setOnFocusChangeListener((view, focused) -> {
            view.setScaleX(focused ? 1.045f : 1f);
            view.setScaleY(focused ? 1.045f : 1f);
            view.setElevation(focused ? dp(10) : 0);
            view.setBackground(shape(focused ? 0xff43567b : base, 14, focused ? CYAN : 0x55495d7b));
        });
    }

    /** Privacy-preserving indicator for Android's system VPN transport; not Happ's internal status. */
    private String vpnBadge() {
        boolean happ = installed("com.happproxy");
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "VPN · ?";
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return happ ? "Happ · VPN ✓" : "VPN ✓";
                }
            }
            return happ ? "Happ · VPN —" : "VPN —";
        } catch (SecurityException e) { return "VPN · ?"; }
    }
    private boolean installed(String pkg) {
        try { pm.getPackageInfo(pkg, 0); return true; }
        catch (PackageManager.NameNotFoundException ignored) { return false; }
    }
    private void openHapp() {
        try {
            Intent intent = pm.getLeanbackLaunchIntentForPackage("com.happproxy");
            if (intent == null) intent = pm.getLaunchIntentForPackage("com.happproxy");
            if (intent != null) { startActivity(intent); return; }
        } catch (Exception ignored) { }
        toast("Happ не найден или не имеет экрана запуска");
    }

    private void loadShows() {
        watchShows.clear(); showPosters.clear();
        try {
            JSONObject saved = new JSONObject(prefs.getString("kino_watchlist", "{}"));
            JSONArray titles = saved.optJSONArray("titles");
            if (titles != null) for (int i=0; i<titles.length() && i<12; i++) {
                String title = titles.optString(i, "").trim();
                if (!title.isEmpty() && !watchShows.contains(title)) watchShows.add(title);
            }
            JSONObject posters = saved.optJSONObject("posters");
            if (posters != null) for (String title : watchShows) {
                String value = posters.optString(title, "");
                if (!value.isEmpty()) showPosters.put(title, value);
            }
        } catch (Exception ignored) { }
    }
    private void saveShows() {
        try {
            JSONObject saved = new JSONObject();
            saved.put("titles", new JSONArray(watchShows));
            JSONObject posters = new JSONObject();
            for (String title : watchShows) {
                String uri = showPosters.get(title);
                if (uri != null) posters.put(title, uri);
            }
            saved.put("posters", posters);
            prefs.edit().putString("kino_watchlist", saved.toString()).apply();
        } catch (Exception ignored) { }
    }
    private void drawKinoWatchlist() {
        if (!installed("watch.kino") && watchShows.isEmpty()) return;
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("▶  Продолжить просмотр · Kino", 20, WHITE, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,-2,1));
        TextView manage = pill("+ Сериал", false);
        manage.setOnClickListener(v -> addKinoShow());
        titleRow.addView(manage, lp(-2,35));
        section.addView(titleRow, lp(-1,40));
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setGravity(Gravity.CENTER_VERTICAL);
        list.setPadding(dp(6),dp(8),dp(6),dp(10));
        if (watchShows.isEmpty()) {
            TextView empty = text("Добавь сериал вручную — позже можно выбрать красивую обложку.", 14, SUB, false);
            list.addView(empty, lp(-2,70));
        } else {
            for (String show : watchShows) list.addView(kinoCard(show));
        }
        scroll.addView(list, new HorizontalScrollView.LayoutParams(-2,-2));
        section.addView(scroll, lp(-1,-2));
        LinearLayout.LayoutParams sec = lp(-1,-2); sec.bottomMargin=dp(12);
        body.addView(section, sec);
    }
    private View kinoCard(String show) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(5),dp(5),dp(5),dp(7));
        tile.setBackground(shape(0xd9222c49,13,0x995978b2));
        LinearLayout.LayoutParams outer = lp(185,153); outer.rightMargin=dp(13); tile.setLayoutParams(outer);
        FrameLayout artwork = new FrameLayout(this);
        artwork.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] { 0xff452978, 0xff171b36, 0xff12566d }));
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        String uri = showPosters.get(show);
        if (uri != null) {
            try (InputStream in=getContentResolver().openInputStream(Uri.parse(uri))) {
                BitmapFactory.Options opt = new BitmapFactory.Options(); opt.inSampleSize = 4;
                Bitmap bitmap=BitmapFactory.decodeStream(in, null, opt);
                if (bitmap != null) poster.setImageBitmap(bitmap);
            } catch (Exception ignored) { }
        }
        artwork.addView(poster, new FrameLayout.LayoutParams(-1,-1));
        TextView play = text("▶",29, WHITE, true);
        play.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(48),dp(46),Gravity.CENTER);
        play.setBackground(shape(0x995a467b,40,0)); artwork.addView(play,pp);
        tile.addView(artwork, lp(-1,102));
        TextView label = text(show,13,WHITE,true);
        label.setSingleLine(true); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(dp(5),dp(3),dp(2),0); tile.addView(label, lp(-1,34));
        tile.setContentDescription("Сериал "+show+". OK открыть Kino. Долгое OK — редактировать карточку.");
        makeFocusable(tile, 0xd9222c49);
        tile.setOnClickListener(v->launchKino());
        tile.setOnLongClickListener(v->{ editKinoShow(show); return true; });
        return tile;
    }
    private void addKinoShow() {
        if (watchShows.size() >= 12) { toast("Максимум 12 карточек"); return; }
        editName("Название сериала", "", name -> {
            if (watchShows.contains(name)) { toast("Уже есть в списке"); return; }
            watchShows.add(name); saveShows(); draw();
        });
    }
    private void editKinoShow(String title) {
        String[] choices={"Выбрать обложку", "Переименовать", "Убрать из списка", "Открыть Kino"};
        new AlertDialog.Builder(this).setTitle(title).setItems(choices,(d,which)->{
            switch(which) {
                case 0: selectKinoPoster(title);break;
                case 1: editName("Название сериала",title,name->{
                    if (watchShows.contains(name) && !name.equals(title)) { toast("Уже есть в списке");return; }
                    int i=watchShows.indexOf(title); if(i>=0){watchShows.set(i,name);
                    String uri=showPosters.remove(title);if(uri!=null) showPosters.put(name,uri);
                    saveShows();draw();}
                });break;
                case 2: watchShows.remove(title);showPosters.remove(title);saveShows();draw();break;
                case 3: launchKino();break;
            }
        }).show();
    }
    private void selectKinoPoster(String title) {
        pendingPosterTitle=title;
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(pick, PICK_SERIES_POSTER); }
        catch (Exception ignored) { toast("Файловый менеджер недоступен"); }
    }
    private void launchKino() {
        try {
            Intent intent=pm.getLeanbackLaunchIntentForPackage("watch.kino");
            if(intent==null)intent=pm.getLaunchIntentForPackage("watch.kino");
            if(intent!=null) { startActivity(intent);return; }
        } catch(Exception ignored) { }
        toast("Приложение watch.kino не найдено");
    }

    private void drawCategory(String category) {
        LinearLayout section = new LinearLayout(this); section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = new LinearLayout(this); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(category, 20, WHITE, true);
        titleRow.addView(label, new LinearLayout.LayoutParams(0,-2,1));
        TextView edit = pill("Изменить",false);
        edit.setOnClickListener(v -> chooseAppForCategory(category));
        titleRow.addView(edit,lp(-2,35));
        section.addView(titleRow,lp(-1,37));
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false); scroll.setClipChildren(false);
        LinearLayout line = new LinearLayout(this); line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(dp(8), dp(12), dp(12), dp(12));
        int count=0;
        for (Map.Entry<String,ResolveInfo> e: apps.entrySet()) {
            if (hidden.contains(e.getKey()) || !category.equals(categoryOf(e.getKey()))) continue;
            line.addView(appTile(e.getKey(), e.getValue(), category)); count++;
        }
        if (count==0) {
            TextView empty = text("Пока пусто · нажми «Изменить», чтобы добавить приложения", 13, SUB, false);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(empty,lp(-1,80));
        }
        scroll.addView(line, new HorizontalScrollView.LayoutParams(-2,-2));
        section.addView(scroll,lp(-1,-2));
        LinearLayout.LayoutParams p = lp(-1,-2);p.topMargin=dp(13);body.addView(section,p);
    }
    private View appTile(String pkg, ResolveInfo info, String category) {
        LinearLayout tile = new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(10),dp(13),dp(10),dp(9));
        tile.setBackground(shape(0xff223047,15,0xff34435f));
        LinearLayout.LayoutParams outer=lp(150,123); outer.rightMargin=dp(13);tile.setLayoutParams(outer);
        ImageView icon = new ImageView(this);
        try { icon.setImageDrawable(info.loadIcon(pm)); } catch (Exception ignored) {}
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(icon,lp(63,64));
        TextView name = text(String.valueOf(info.loadLabel(pm)),13,WHITE,true);
        name.setGravity(Gravity.CENTER);name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams np=lp(-1,35);np.topMargin=dp(5);tile.addView(name,np);
        makeFocusable(tile,0xff223047);
        tile.setContentDescription(name.getText().toString()+". Удерживай OK для категорий");
        tile.setOnClickListener(v -> launch(info));
        tile.setOnLongClickListener(v -> { chooseCategory(pkg);return true; });
        return tile;
    }
    private void launch(ResolveInfo info) {
        try {
            // Prefer the exact intent Android's PackageManager creates for TV apps.
            String pkg = info.activityInfo.packageName;
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
            if (intent == null) {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.setComponent(new ComponentName(pkg, info.activityInfo.name));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this,"Не удалось запустить приложение",Toast.LENGTH_LONG).show();
        }
    }
    private void chooseCategory(String pkg) {
        String[] names=new String[categories.size()+1];
        for(int i=0;i<categories.size();i++) names[i]=categories.get(i);
        names[names.length-1]="Скрыть с главного экрана";
        new AlertDialog.Builder(this).setTitle("Куда поместить приложение?")
            .setItems(names,(d,which)->{
                if(which==categories.size()) hidden.add(pkg);
                else {hidden.remove(pkg); assignments.put(pkg,categories.get(which));}
                saveSettings();draw();
            }).setNegativeButton("Отмена",null).show();
    }
    private void chooseAppForCategory(String category) {
        List<String> keys=new ArrayList<>(apps.keySet());
        String[] names=new String[keys.size()];
        for(int i=0;i<keys.size();i++) names[i]=String.valueOf(apps.get(keys.get(i)).loadLabel(pm));
        new AlertDialog.Builder(this).setTitle("Добавить в «"+category+"»")
           .setItems(names,(d,index)->{
               String pkg=keys.get(index);
               hidden.remove(pkg);assignments.put(pkg,category);saveSettings();draw();
           }).setNegativeButton("Отмена",null).show();
    }
    private void showSettings() {
        String[] options={"Добавить категорию","Изменить / переставить категории", "Вернуть скрытые приложения",
                          "Настроить ИИ", "Добавить сериал Kino", "Обои главного экрана", "Яркость и насыщенность фона", "Настройки Android", "Обновить список приложений", "О лаунчере"};
        new AlertDialog.Builder(this).setTitle("Настройки TCL AI")
            .setItems(options,(d,n)->{
                switch(n){
                    case 0: addCategory();break;
                    case 1: manageCategories();break;
                    case 2: restoreHidden();break;
                    case 3: openAssistant();break;
                    case 4: addKinoShow();break;
                    case 5: chooseWallpaper();break;
                    case 6: showWallpaperControls();break;
                    case 7: openAndroidSettings();break;
                    case 8: findApps();draw();break;
                    case 9: new AlertDialog.Builder(this).setTitle("TCL AI Launcher 0.4")
                            .setMessage("Независимый прототип для Android TV. Projectivy и Google Assistant не меняются. Настройки фона влияют только на этот экран. Платные функции Projectivy не используются.")
                            .setPositiveButton("OK",null).show();break;
                }
            }).setNegativeButton("Закрыть",null).show();
    }
    private void editName(String title,String initial,java.util.function.Consumer<String> callback) {
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(initial);
        input.setSelectAllOnFocus(true);
        LinearLayout wrapper=new LinearLayout(this);wrapper.setPadding(dp(24),dp(10),dp(24),0);wrapper.addView(input,lp(-1,-2));
        new AlertDialog.Builder(this).setTitle(title).setView(wrapper)
           .setPositiveButton("Сохранить",(d,w)->{
               String name=input.getText().toString().trim();
               if(name.isEmpty() || name.length()>25) {toast("Название: от 1 до 25 символов");return;}
               callback.accept(name);
           }).setNegativeButton("Отмена",null).show();
    }
    private void addCategory() {
        editName("Новая категория","",name->{
            if(categories.contains(name)) {toast("Такая категория уже есть");return;}
            categories.add(name);saveSettings();draw();
        });
    }
    private void manageCategories() {
        new AlertDialog.Builder(this).setTitle("Категории")
            .setItems(categories.toArray(new String[0]),(d,i)->manageOneCategory(i))
            .setNegativeButton("Готово",null).show();
    }
    private void manageOneCategory(int index) {
        String item=categories.get(index);
        String[] actions={"Переименовать", "Поднять выше", "Опустить ниже", "Удалить категорию"};
        new AlertDialog.Builder(this).setTitle(item).setItems(actions,(d,which)->{
            switch(which){
                case 0: editName("Переименовать категорию",item,name->{
                    if(categories.contains(name) && !name.equals(item)){toast("Название занято");return;}
                    categories.set(index,name);
                    for(Map.Entry<String,String> e: assignments.entrySet())
                        if(item.equals(e.getValue())) e.setValue(name);
                    saveSettings();draw();
                });break;
                case 1: if(index>0){Collections.swap(categories,index,index-1);saveSettings();draw();}break;
                case 2: if(index<categories.size()-1){Collections.swap(categories,index,index+1);saveSettings();draw();}break;
                case 3: if(categories.size()==1){toast("Нужна хотя бы одна категория");return;}
                    new AlertDialog.Builder(this).setTitle("Удалить «"+item+"»?")
                        .setMessage("Приложения останутся установленными. Они появятся в другой категории.")
                        .setPositiveButton("Удалить",(dd,w)->{
                            categories.remove(index);assignments.values().removeAll(Collections.singleton(item));
                            saveSettings();draw();
                        }).setNegativeButton("Отмена",null).show();break;
            }
        }).show();
    }
    private void restoreHidden() {
        if(hidden.isEmpty()){toast("Скрытых приложений нет");return;}
        List<String> keys=new ArrayList<>(hidden);
        String[] labels=new String[keys.size()];
        for(int i=0;i<keys.size();i++){
            ResolveInfo r=apps.get(keys.get(i));
            labels[i]=r==null ? keys.get(i) : String.valueOf(r.loadLabel(pm));
        }
        new AlertDialog.Builder(this).setTitle("Вернуть приложение")
            .setItems(labels,(d,n)->{hidden.remove(keys.get(n));saveSettings();draw();})
            .setNegativeButton("Отмена",null).show();
    }
    private void openAssistant(){startActivity(new Intent(this, AssistantActivity.class));}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
}
