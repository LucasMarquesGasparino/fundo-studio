package com.fundostudio;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Editor de fotos autocontido. O processamento é deliberadamente local: não há SDK,
 * servidor ou permissão de internet no aplicativo final.
 */
public final class MainActivity extends Activity {
    private static final int PICK_IMAGE = 41;
    private static final int PICK_BACKGROUND = 42;
    private static final int CREATE_IMAGE = 43;

    private static final int INK = Color.rgb(242, 240, 248);
    private static final int MUTED = Color.rgb(158, 157, 175);
    private static final int DIM = Color.rgb(103, 103, 123);
    private static final int BG = Color.rgb(16, 17, 22);
    private static final int PANEL = Color.rgb(24, 25, 33);
    private static final int PANEL_2 = Color.rgb(31, 32, 42);
    private static final int LINE = Color.rgb(57, 57, 70);
    private static final int PURPLE = Color.rgb(167, 139, 250);
    private static final int GREEN = Color.rgb(91, 214, 166);
    private static final int CASE_HUMAN = 1;
    private static final int CASE_ANIMAL = 2;
    private static final int CASE_OTHER = 3;

    private LinearLayout content;
    private TextView status;
    private TextView undoButton;
    private TextView redoButton;
    private EditorView editorView;
    private Bitmap cutoutBitmap;
    private Bitmap backgroundBitmap;
    private int backgroundColor = Color.rgb(236, 232, 224);
    private int removalCase = CASE_OTHER;
    private boolean traceMode;
    private boolean cropMode;
    private boolean processing;
    private final ArrayList<EditState> history = new ArrayList<>();
    private int historyIndex = -1;

    private final int[] templateIds = new int[]{
            R.drawable.template_montanhas,
            R.drawable.template_praia,
            R.drawable.template_floresta,
            R.drawable.template_cidade,
            R.drawable.template_por_do_sol,
            R.drawable.template_aurora,
            R.drawable.template_deserto,
            R.drawable.template_campo,
            R.drawable.template_neve,
            R.drawable.template_cafe
    };
    private final String[] templateNames = new String[]{
            "Montanhas", "Praia", "Floresta", "Cidade", "Pôr do sol",
            "Aurora", "Deserto", "Campo", "Neve", "Café"
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 23) {
            window.getDecorView().setSystemUiVisibility(0);
        }
        buildScreen();
    }

    private void buildScreen() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);

        shell.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(70)));

        EditorScrollView scroll = new EditorScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(12), dp(20), dp(30));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(shell);

        if (cutoutBitmap == null) buildWelcome();
        else buildEditor();
        updateHistoryButtons();
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), dp(10), dp(16), dp(4));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Fundo Studio", 21, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titles.addView(title);
        TextView subtitle = text("recorte · cenário · exportação", 11, MUTED);
        titles.addView(subtitle, lp(-1, -2, 1));
        bar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        undoButton = toolText("↶");
        undoButton.setContentDescription("Desfazer");
        undoButton.setOnClickListener(v -> undo());
        bar.addView(undoButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        redoButton = toolText("↷");
        redoButton.setContentDescription("Refazer");
        redoButton.setOnClickListener(v -> redo());
        bar.addView(redoButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return bar;
    }

    private void buildWelcome() {
        SpaceView space = new SpaceView(this);
        content.addView(space, new LinearLayout.LayoutParams(1, dp(28)));

        TextView eyebrow = text("EDIÇÃO PRIVADA · SEM NUVEM", 11, PURPLE);
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        eyebrow.setLetterSpacing(.13f);
        content.addView(eyebrow, lp(-1, -2, 12));

        TextView title = text("Dê um novo fundo\npara a sua foto.", 34, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLineSpacing(0, .96f);
        content.addView(title, lp(-1, -2, 13));

        TextView intro = text(
                "Remova o fundo automaticamente ou contorne o assunto com um traço livre. "
                        + "Tudo roda no aparelho, sem enviar imagens para lugar nenhum.", 16, MUTED);
        intro.setLineSpacing(dp(3), 1f);
        content.addView(intro, lp(-1, -2, 26));

        LinearLayout featureCard = card();
        featureCard.addView(featureLine("✦", "Recorte inteligente", "automático ou guiado"));
        featureCard.addView(featureLine("◈", "10 cenários prontos", "mais cor, hex ou upload"), lp(-1, -2, 15));
        featureCard.addView(featureLine("↗", "Salve em PNG", "transparência preservada"), lp(-1, -2, 15));
        content.addView(featureCard, lp(-1, -2, 22));

        Button choose = primaryButton("Escolher uma foto");
        choose.setOnClickListener(v -> openImagePicker());
        content.addView(choose, lp(-1, dp(56), 12));

        TextView hint = text("Funciona com fotos da galeria, arquivos e imagens compartilhadas.", 12, DIM);
        hint.setGravity(Gravity.CENTER);
        content.addView(hint, lp(-1, -2, 18));
    }

    private View featureLine(String icon, String headline, String detail) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView iconView = text(icon, 23, PURPLE);
        iconView.setGravity(Gravity.CENTER);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView first = text(headline, 15, INK);
        first.setTypeface(Typeface.DEFAULT_BOLD);
        words.addView(first);
        words.addView(text(detail, 12, MUTED), lp(-1, -2, 2));
        row.addView(words, lp(0, -2, 1, 12));
        return row;
    }

    private void buildEditor() {
        content.removeAllViews();
        content.addView(buildSteps(), lp(-1, dp(55), 0));

        editorView = new EditorView(this);
        editorView.setTraceListener(points -> {
            if (points.size() > 2) {
                setStatus("Traço fechado. Toque em “Aplicar traço” para recortar.", PURPLE);
            }
        });
        editorView.setTraceMode(traceMode);
        editorView.setCropMode(cropMode);
        content.addView(buildRotateBar(), lp(-1, dp(48), 9));
        FrameLayout canvasFrame = new FrameLayout(this);
        canvasFrame.setBackground(round(PANEL, 0, 0));
        canvasFrame.setPadding(dp(2), dp(2), dp(2), dp(2));
        canvasFrame.addView(editorView, new FrameLayout.LayoutParams(-1, -1));
        content.addView(canvasFrame, lp(-1, dp(330), 0));

        status = text(traceMode
                ? "Desenhe uma volta ampla ao redor do assunto. Não precisa ser exato."
                : "Escolha um modo para remover o fundo.", 12, MUTED);
        status.setGravity(Gravity.CENTER);
        content.addView(status, lp(-1, -2, 9));

        buildRemovalSection();
        buildBackgroundSection();
        buildCropSection();
        buildSaveSection();
    }

    private View buildRotateBar() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("Ajustar antes de começar", 12, MUTED);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(label, lp(0, -1, 1));
        Button left = secondaryButton("↺ 90°");
        left.setContentDescription("Girar 90 graus para a esquerda");
        left.setOnClickListener(v -> rotateImage(-90f));
        row.addView(left, lp(dp(92), dp(42), 0, 6));
        Button right = secondaryButton("↻ 90°");
        right.setContentDescription("Girar 90 graus para a direita");
        right.setOnClickListener(v -> rotateImage(90f));
        row.addView(right, lp(dp(92), dp(42), 0));
        return row;
    }

    private void rotateImage(float degrees) {
        if (cutoutBitmap == null || processing) return;
        cutoutBitmap = rotateBitmap(cutoutBitmap, degrees);
        if (backgroundBitmap != null) backgroundBitmap = rotateBitmap(backgroundBitmap, degrees);
        cropMode = false;
        traceMode = false;
        if (editorView != null) {
            editorView.setTraceMode(false);
            editorView.setCropMode(false);
            editorView.invalidate();
        }
        pushHistory();
        setStatus(degrees < 0 ? "Foto girada 90° para a esquerda." : "Foto girada 90° para a direita.", GREEN);
    }

    private Bitmap rotateBitmap(Bitmap source, float degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private View buildSteps() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        String[] labels = {"1  Remover", "2  Fundo", "3  Cortar", "4  Salvar"};
        for (int i = 0; i < labels.length; i++) {
            TextView step = text(labels[i], 11, i == 0 ? PURPLE : DIM);
            step.setTypeface(Typeface.DEFAULT_BOLD);
            step.setGravity(Gravity.CENTER);
            row.addView(step, lp(0, -1, 1, i == labels.length - 1 ? 0 : 5));
        }
        return row;
    }

    private void buildRemovalSection() {
        LinearLayout section = section("1", "Remover fundo", "Um único modo automático escolhe o melhor modelo local para a imagem.");
        TextView detail = text("Matting de retrato para bordas finas; segmentação geral como fallback.", 12, MUTED);
        detail.setLineSpacing(dp(2), 1f);
        section.addView(detail, lp(-1, -2, 15));
        Button apply = primaryButton("✦ Remover fundo automaticamente");
        apply.setOnClickListener(v -> removeAutomatically(apply));
        section.addView(apply, lp(-1, dp(50), 0));
        content.addView(section, lp(-1, -2, 15));
    }

    private void buildBackgroundSection() {
        LinearLayout section = section("2", "Escolha um novo fundo", "Use uma cor, um dos cenários embutidos ou uma imagem sua.");

        LinearLayout colorRow = new LinearLayout(this);
        int[] colors = new int[]{
                Color.rgb(246, 240, 230), Color.rgb(31, 38, 55), Color.rgb(212, 235, 228),
                Color.rgb(238, 193, 198), Color.rgb(247, 211, 139), Color.rgb(145, 135, 190)
        };
        for (int color : colors) {
            TextView swatch = new TextView(this);
            swatch.setBackground(round(color, 12, color == backgroundColor ? PURPLE : color));
            swatch.setOnClickListener(v -> {
                backgroundBitmap = null;
                backgroundColor = color;
                pushHistory();
                editorView.invalidate();
                setStatus("Cor aplicada: " + toHex(color), GREEN);
            });
            colorRow.addView(swatch, lp(0, dp(36), 1, 5));
        }
        TextView transparent = text("⊘", 20, INK);
        transparent.setGravity(Gravity.CENTER);
        transparent.setContentDescription("Fundo transparente");
        transparent.setBackground(round(PANEL_2, 12, backgroundColor == Color.TRANSPARENT ? PURPLE : LINE));
        transparent.setOnClickListener(v -> {
            backgroundBitmap = null;
            backgroundColor = Color.TRANSPARENT;
            pushHistory();
            editorView.invalidate();
            setStatus("Fundo transparente aplicado.", GREEN);
        });
        colorRow.addView(transparent, lp(dp(40), dp(36), 0));
        section.addView(colorRow, lp(-1, dp(36), 12));

        LinearLayout hexRow = new LinearLayout(this);
        EditText hex = new EditText(this);
        hex.setSingleLine(true);
        hex.setHint("#RRGGBB ou #AARRGGBB");
        hex.setText(toHex(backgroundColor));
        hex.setTextColor(INK);
        hex.setHintTextColor(DIM);
        hex.setTextSize(13);
        hex.setPadding(dp(13), 0, dp(10), 0);
        hex.setBackground(round(PANEL_2, 10, LINE));
        hexRow.addView(hex, lp(0, dp(44), 1, 8));
        Button applyHex = secondaryButton("Aplicar cor");
        applyHex.setOnClickListener(v -> {
            try {
                String value = hex.getText().toString().trim();
                if (!value.startsWith("#")) value = "#" + value;
                int parsed = Color.parseColor(value);
                backgroundBitmap = null;
                backgroundColor = parsed;
                pushHistory();
                editorView.invalidate();
                setStatus("Cor aplicada: " + toHex(parsed), GREEN);
            } catch (IllegalArgumentException error) {
                setStatus("Use #RRGGBB ou #AARRGGBB.", Color.rgb(255, 142, 142));
            }
        });
        hexRow.addView(applyHex, lp(dp(118), dp(44), 0));
        section.addView(hexRow, lp(-1, dp(44), 0));

        TextView templateTitle = text("Templates locais", 12, MUTED);
        templateTitle.setTypeface(Typeface.DEFAULT_BOLD);
        section.addView(templateTitle, lp(-1, -2, 17));

        HorizontalScrollView templateScroll = new HorizontalScrollView(this);
        templateScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout templates = new LinearLayout(this);
        templates.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < templateIds.length; i++) {
            final int position = i;
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView image = new ImageView(this);
            image.setImageResource(templateIds[i]);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(round(PANEL_2, 10, Color.TRANSPARENT));
            image.setClipToOutline(true);
            image.setContentDescription(templateNames[i]);
            image.setOnClickListener(v -> {
                backgroundBitmap = BitmapFactory.decodeResource(getResources(), templateIds[position]);
                pushHistory();
                editorView.invalidate();
                setStatus("Template aplicado: " + templateNames[position], GREEN);
            });
            tile.addView(image, new LinearLayout.LayoutParams(dp(82), dp(72)));
            TextView name = text(templateNames[i], 10, MUTED);
            name.setGravity(Gravity.CENTER);
            tile.addView(name, lp(-1, dp(24), 5));
            templates.addView(tile, new LinearLayout.LayoutParams(dp(88), dp(101)));
        }
        templateScroll.addView(templates, new HorizontalScrollView.LayoutParams(-2, -1));
        section.addView(templateScroll, lp(-1, dp(105), 0));

        Button upload = secondaryButton("＋  Usar imagem da galeria como fundo");
        upload.setOnClickListener(v -> openBackgroundPicker());
        section.addView(upload, lp(-1, dp(47), 15));
        content.addView(section, lp(-1, -2, 15));
    }

    private void buildCropSection() {
        LinearLayout section = section("3", "Recortar foto", "Escolha uma proporção; o enquadramento central é aplicado ao resultado.");
        LinearLayout row = new LinearLayout(this);
        String[] labels = {"Livre", "1 : 1", "4 : 5", "16 : 9"};
        final float[] ratios = {0f, 1f, .8f, 16f / 9f};
        Button freeApply = primaryButton("Aplicar corte livre");
        freeApply.setVisibility(cropMode ? View.VISIBLE : View.GONE);
        freeApply.setOnClickListener(v -> applyFreeCrop(freeApply));
        for (int i = 0; i < labels.length; i++) {
            final float ratio = ratios[i];
            Button crop = secondaryButton(labels[i]);
            crop.setOnClickListener(v -> {
                if (ratio == 0f) {
                    traceMode = false;
                    cropMode = true;
                    if (editorView != null) {
                        editorView.setTraceMode(false);
                        editorView.setCropMode(true);
                    }
                    freeApply.setVisibility(View.VISIBLE);
                    setStatus("Arraste na foto para marcar a área que deseja manter.", PURPLE);
                } else {
                    cropMode = false;
                    if (editorView != null) editorView.setCropMode(false);
                    freeApply.setVisibility(View.GONE);
                    applyCrop(ratio);
                }
            });
            row.addView(crop, lp(0, dp(44), 1, i == labels.length - 1 ? 0 : 5));
        }
        section.addView(row, lp(-1, dp(44), 0));
        section.addView(freeApply, lp(-1, dp(47), 10));
        content.addView(section, lp(-1, -2, 15));
    }

    private void buildSaveSection() {
        LinearLayout section = section("4", "Salvar resultado", "PNG mantém a transparência quando você deixar o fundo sem cor.");
        Button save = primaryButton("Salvar foto em PNG");
        save.setOnClickListener(v -> saveImage());
        section.addView(save, lp(-1, dp(52), 0));
        Button newPhoto = secondaryButton("Escolher outra foto");
        newPhoto.setOnClickListener(v -> openImagePicker());
        section.addView(newPhoto, lp(-1, dp(45), 10));
        TextView credit = text("Templates: Unsplash · disponíveis offline após a instalação.", 10, DIM);
        credit.setGravity(Gravity.CENTER);
        section.addView(credit, lp(-1, -2, 13));
        content.addView(section, lp(-1, -2, 0));
    }

    private LinearLayout section(String number, String title, String detail) {
        LinearLayout box = card();
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView numberView = text(number, 12, BG);
        numberView.setTypeface(Typeface.DEFAULT_BOLD);
        numberView.setGravity(Gravity.CENTER);
        numberView.setBackground(round(PURPLE, 14, 0));
        heading.addView(numberView, new LinearLayout.LayoutParams(dp(28), dp(28)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 17, INK);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        words.addView(titleView);
        words.addView(text(detail, 11, MUTED), lp(-1, -2, 2));
        heading.addView(words, lp(0, -2, 1, 11));
        box.addView(heading);
        return box;
    }

    private void removeAutomatically(Button button) {
        if (processing || cutoutBitmap == null) return;
        processing = true;
        button.setEnabled(false);
        setStatus("Executando matting automático local…", PURPLE);
        final Bitmap source = copyOf(cutoutBitmap);
        new Thread(() -> {
            Bitmap candidate = OfflineSegmenter.tryAutomatic(MainActivity.this, source);
            boolean usedModel = candidate != null;
            if (candidate == null) candidate = BackgroundRemover.removeAutomatic(source, CASE_OTHER);
            final Bitmap result = candidate;
            final boolean modelUsed = usedModel;
            runOnUiThread(() -> {
                cutoutBitmap = result;
                pushHistory();
                processing = false;
                button.setEnabled(true);
                editorView.invalidate();
                setStatus(modelUsed
                        ? "Modelo neural offline aplicado. Você pode trocar o cenário agora."
                        : "Recorte offline aplicado com o perfil selecionado.", GREEN);
            });
        }, "offline-cutout").start();
    }

    private void removeByTrace(Button button) {
        if (processing || cutoutBitmap == null) return;
        List<PointF> points = editorView.getTraceInBitmapCoordinates();
        if (points.size() < 3) {
            setStatus("Faça um traço fechado ao redor do assunto primeiro.", Color.rgb(255, 142, 142));
            return;
        }
        processing = true;
        button.setEnabled(false);
        setStatus("Refinando o traço com análise local…", PURPLE);
        final Bitmap source = copyOf(cutoutBitmap);
        new Thread(() -> {
            Bitmap base = OfflineSegmenter.tryAutomatic(MainActivity.this, source);
            if (base == null) base = BackgroundRemover.removeAutomatic(source, CASE_OTHER);
            final Bitmap result = BackgroundRemover.clipToGuide(base, points);
            runOnUiThread(() -> {
                cutoutBitmap = result;
                pushHistory();
                processing = false;
                button.setEnabled(true);
                editorView.clearTrace();
                traceMode = false;
                editorView.setTraceMode(false);
                buildScreen();
                setStatus("Traço aplicado. O fundo foi removido offline.", GREEN);
            });
        }, "offline-guide-cutout").start();
    }

    private void applyCrop(float ratio) {
        if (cutoutBitmap == null) return;
        int width = cutoutBitmap.getWidth();
        int height = cutoutBitmap.getHeight();
        applyCropRect(cropRect(width, height, ratio));
        setStatus(ratio == 0f ? "Enquadramento original restaurado." : "Recorte aplicado ao centro da foto.", GREEN);
    }

    private void applyFreeCrop(Button applyButton) {
        if (editorView == null) return;
        Rect crop = editorView.getCropInBitmapCoordinates();
        if (crop == null || crop.width() < 8 || crop.height() < 8) {
            setStatus("Arraste uma área maior sobre a foto antes de aplicar.", Color.rgb(255, 142, 142));
            return;
        }
        applyCropRect(crop);
        cropMode = false;
        editorView.setCropMode(false);
        applyButton.setVisibility(View.GONE);
        setStatus("Corte livre aplicado.", GREEN);
    }

    private void applyCropRect(Rect crop) {
        if (cutoutBitmap == null) return;
        cutoutBitmap = Bitmap.createBitmap(cutoutBitmap, crop.left, crop.top, crop.width(), crop.height());
        if (backgroundBitmap != null) {
            Rect bgCrop = cropRect(backgroundBitmap.getWidth(), backgroundBitmap.getHeight(),
                    cutoutBitmap.getWidth() / (float) cutoutBitmap.getHeight());
            backgroundBitmap = Bitmap.createBitmap(backgroundBitmap, bgCrop.left, bgCrop.top,
                    bgCrop.width(), bgCrop.height());
        }
        pushHistory();
        editorView.invalidate();
    }

    private Rect cropRect(int width, int height, float ratio) {
        if (ratio <= 0) return new Rect(0, 0, width, height);
        float current = width / (float) height;
        if (current > ratio) {
            int newWidth = Math.max(1, Math.round(height * ratio));
            int left = (width - newWidth) / 2;
            return new Rect(left, 0, left + newWidth, height);
        }
        int newHeight = Math.max(1, Math.round(width / ratio));
        int top = (height - newHeight) / 2;
        return new Rect(0, top, width, top + newHeight);
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE);
    }

    private void openBackgroundPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_BACKGROUND);
    }

    private void saveImage() {
        if (cutoutBitmap == null) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, "fundo-studio.png");
        startActivityForResult(intent, CREATE_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_IMAGE) {
            Bitmap bitmap = decodeUri(uri, 1400);
            if (bitmap == null) {
                Toast.makeText(this, "Não foi possível abrir essa imagem.", Toast.LENGTH_LONG).show();
                return;
            }
            cutoutBitmap = bitmap;
            backgroundBitmap = null;
            backgroundColor = Color.rgb(236, 232, 224);
            history.clear();
            historyIndex = -1;
            traceMode = false;
            pushHistory();
            buildScreen();
            setStatus("Foto carregada. Escolha automático ou traço guiado.", MUTED);
        } else if (requestCode == PICK_BACKGROUND) {
            Bitmap bitmap = decodeUri(uri, 1400);
            if (bitmap != null) {
                backgroundBitmap = bitmap;
                pushHistory();
                editorView.invalidate();
                setStatus("Imagem aplicada como fundo.", GREEN);
            }
        } else if (requestCode == CREATE_IMAGE) {
            writeImage(uri);
        }
    }

    private void writeImage(Uri uri) {
        OutputStream output = null;
        try {
            output = getContentResolver().openOutputStream(uri);
            if (output == null || !composeBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IllegalStateException("compressão falhou");
            }
            output.flush();
            Toast.makeText(this, "Foto salva com sucesso.", Toast.LENGTH_LONG).show();
            setStatus("Arquivo PNG salvo. Você já pode compartilhá-lo.", GREEN);
        } catch (Exception error) {
            Toast.makeText(this, "Não foi possível salvar a foto.", Toast.LENGTH_LONG).show();
        } finally {
            if (output != null) {
                try { output.close(); } catch (Exception ignored) { }
            }
        }
    }

    private Bitmap decodeUri(Uri uri, int maxSide) {
        ContentResolver resolver = getContentResolver();
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream first = resolver.openInputStream(uri);
            BitmapFactory.decodeStream(first, null, bounds);
            if (first != null) first.close();
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inSampleSize = sample;
            InputStream second = resolver.openInputStream(uri);
            Bitmap result = BitmapFactory.decodeStream(second, null, options);
            if (second != null) second.close();
            if (result == null) return null;
            if (Math.max(result.getWidth(), result.getHeight()) > maxSide) {
                float scale = maxSide / (float) Math.max(result.getWidth(), result.getHeight());
                Bitmap scaled = Bitmap.createScaledBitmap(result,
                        Math.max(1, Math.round(result.getWidth() * scale)),
                        Math.max(1, Math.round(result.getHeight() * scale)), true);
                if (scaled != result) result.recycle();
                return scaled;
            }
            return result;
        } catch (Exception error) {
            return null;
        }
    }

    private void pushHistory() {
        if (cutoutBitmap == null) return;
        while (history.size() > historyIndex + 1) history.remove(history.size() - 1);
        history.add(new EditState(copyOf(cutoutBitmap), copyOf(backgroundBitmap), backgroundColor));
        if (history.size() > 12) history.remove(0);
        historyIndex = history.size() - 1;
        updateHistoryButtons();
    }

    private void undo() {
        if (historyIndex <= 0 || processing) return;
        historyIndex--;
        restore(history.get(historyIndex));
        setStatus("Alteração desfeita.", MUTED);
    }

    private void redo() {
        if (historyIndex < 0 || historyIndex >= history.size() - 1 || processing) return;
        historyIndex++;
        restore(history.get(historyIndex));
        setStatus("Alteração refeita.", MUTED);
    }

    private void restore(EditState state) {
        cutoutBitmap = copyOf(state.cutout);
        backgroundBitmap = copyOf(state.background);
        backgroundColor = state.color;
        if (editorView == null) buildScreen();
        else editorView.invalidate();
        updateHistoryButtons();
    }

    private void updateHistoryButtons() {
        if (undoButton == null || redoButton == null) return;
        boolean canUndo = historyIndex > 0;
        boolean canRedo = historyIndex >= 0 && historyIndex < history.size() - 1;
        undoButton.setTextColor(canUndo ? INK : DIM);
        redoButton.setTextColor(canRedo ? INK : DIM);
    }

    private Bitmap composeBitmap() {
        if (cutoutBitmap == null) return null;
        Bitmap result = Bitmap.createBitmap(cutoutBitmap.getWidth(), cutoutBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        if (backgroundBitmap == null) canvas.drawColor(backgroundColor);
        else drawCover(canvas, backgroundBitmap, new RectF(0, 0, result.getWidth(), result.getHeight()));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(cutoutBitmap, 0, 0, paint);
        return result;
    }

    private void setStatus(String value, int color) {
        if (status != null) {
            status.setText(value);
            status.setTextColor(color);
        }
    }

    private static Bitmap copyOf(Bitmap source) {
        return source == null ? null : source.copy(Bitmap.Config.ARGB_8888, true);
    }

    private static String toHex(int color) {
        if (Color.alpha(color) != 255) return String.format(Locale.US, "#%08X", color);
        return String.format(Locale.US, "#%06X", 0xFFFFFF & color);
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(BG);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(round(PURPLE, 13, 0));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(INK);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(round(PANEL_2, 11, LINE));
        return button;
    }

    private TextView toolText(String value) {
        TextView view = text(value, 29, INK);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(PANEL_2, 12, 0));
        return view;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(15), dp(15), dp(15), dp(15));
        view.setBackground(round(PANEL, 16, LINE));
        return view;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams lp(int width, int height, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private LinearLayout.LayoutParams lp(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }

    private LinearLayout.LayoutParams lp(int width, int height, float weight, int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height, weight);
        params.rightMargin = dp(right);
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class EditState {
        final Bitmap cutout;
        final Bitmap background;
        final int color;

        EditState(Bitmap cutout, Bitmap background, int color) {
            this.cutout = cutout;
            this.background = background;
            this.color = color;
        }
    }

    private final class EditorView extends View {
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint tracePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cropPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArrayList<PointF> tracePoints = new ArrayList<>();
        private RectF imageRect = new RectF();
        private boolean drawingTrace;
        private boolean drawingCrop;
        private PointF cropStart;
        private PointF cropEnd;
        private TraceListener traceListener;

        EditorView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            tracePaint.setColor(PURPLE);
            tracePaint.setStyle(Paint.Style.STROKE);
            tracePaint.setStrokeWidth(dp(3));
            tracePaint.setStrokeCap(Paint.Cap.ROUND);
            tracePaint.setStrokeJoin(Paint.Join.ROUND);
            tracePaint.setPathEffect(new DashPathEffect(new float[]{dp(9), dp(6)}, 0));
            cropPaint.setColor(PURPLE);
            cropPaint.setStyle(Paint.Style.STROKE);
            cropPaint.setStrokeWidth(dp(2));
        }

        void setTraceMode(boolean enabled) {
            drawingTrace = enabled;
            if (enabled) drawingCrop = false;
            if (!enabled) tracePoints.clear();
            invalidate();
        }

        void setCropMode(boolean enabled) {
            drawingCrop = enabled;
            if (enabled) drawingTrace = false;
            if (!enabled) {
                cropStart = null;
                cropEnd = null;
            }
            invalidate();
        }

        void clearTrace() {
            tracePoints.clear();
            invalidate();
        }

        void setTraceListener(TraceListener listener) { traceListener = listener; }

        List<PointF> getTraceInBitmapCoordinates() {
            ArrayList<PointF> result = new ArrayList<>();
            if (cutoutBitmap == null || imageRect.width() <= 0) return result;
            float sx = cutoutBitmap.getWidth() / imageRect.width();
            float sy = cutoutBitmap.getHeight() / imageRect.height();
            for (PointF point : tracePoints) {
                result.add(new PointF(
                        clamp((point.x - imageRect.left) * sx, 0, cutoutBitmap.getWidth() - 1),
                        clamp((point.y - imageRect.top) * sy, 0, cutoutBitmap.getHeight() - 1)));
            }
            return result;
        }

        Rect getCropInBitmapCoordinates() {
            if (cutoutBitmap == null || cropStart == null || cropEnd == null
                    || imageRect.width() <= 0 || imageRect.height() <= 0) return null;
            RectF selected = normalizedCropRect();
            if (!selected.intersect(imageRect)) return null;
            int left = Math.round((selected.left - imageRect.left)
                    * cutoutBitmap.getWidth() / imageRect.width());
            int top = Math.round((selected.top - imageRect.top)
                    * cutoutBitmap.getHeight() / imageRect.height());
            int right = Math.round((selected.right - imageRect.left)
                    * cutoutBitmap.getWidth() / imageRect.width());
            int bottom = Math.round((selected.bottom - imageRect.top)
                    * cutoutBitmap.getHeight() / imageRect.height());
            left = Math.max(0, Math.min(cutoutBitmap.getWidth() - 1, left));
            top = Math.max(0, Math.min(cutoutBitmap.getHeight() - 1, top));
            right = Math.max(left + 1, Math.min(cutoutBitmap.getWidth(), right));
            bottom = Math.max(top + 1, Math.min(cutoutBitmap.getHeight(), bottom));
            return new Rect(left, top, right, bottom);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(PANEL_2);
            if (cutoutBitmap == null) return;
            imageRect = fitRect(cutoutBitmap.getWidth(), cutoutBitmap.getHeight(),
                    getWidth() - dp(10), getHeight() - dp(10));
            drawCheckerboard(canvas, imageRect);
            if (backgroundBitmap == null && Color.alpha(backgroundColor) > 0) canvas.drawColor(backgroundColor);
            else drawCover(canvas, backgroundBitmap, imageRect);
            canvas.drawBitmap(cutoutBitmap, null, imageRect, bitmapPaint);
            if (drawingTrace && tracePoints.size() > 0) {
                Path path = new Path();
                path.moveTo(tracePoints.get(0).x, tracePoints.get(0).y);
                for (int i = 1; i < tracePoints.size(); i++) {
                    path.lineTo(tracePoints.get(i).x, tracePoints.get(i).y);
                }
                canvas.drawPath(path, tracePaint);
                Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
                dot.setColor(PURPLE);
                dot.setStyle(Paint.Style.FILL);
                canvas.drawCircle(tracePoints.get(0).x, tracePoints.get(0).y, dp(5), dot);
            }
            if (drawingCrop && cropStart != null && cropEnd != null) {
                RectF selected = normalizedCropRect();
                selected.left = Math.max(selected.left, imageRect.left);
                selected.top = Math.max(selected.top, imageRect.top);
                selected.right = Math.min(selected.right, imageRect.right);
                selected.bottom = Math.min(selected.bottom, imageRect.bottom);
                Paint shade = new Paint();
                shade.setColor(Color.argb(150, 8, 9, 14));
                canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, selected.top, shade);
                canvas.drawRect(imageRect.left, selected.bottom, imageRect.right, imageRect.bottom, shade);
                canvas.drawRect(imageRect.left, selected.top, selected.left, selected.bottom, shade);
                canvas.drawRect(selected.right, selected.top, imageRect.right, selected.bottom, shade);
                canvas.drawRect(selected, cropPaint);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if ((!drawingTrace && !drawingCrop) || cutoutBitmap == null) return false;
            float x = event.getX();
            float y = event.getY();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (drawingTrace) {
                    tracePoints.clear();
                    tracePoints.add(new PointF(x, y));
                } else {
                    cropStart = clampToImage(x, y);
                    cropEnd = new PointF(cropStart.x, cropStart.y);
                }
                invalidate();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (drawingTrace) {
                    if (tracePoints.isEmpty() || distance(tracePoints.get(tracePoints.size() - 1), x, y) > dp(3)) {
                        tracePoints.add(new PointF(x, y));
                        invalidate();
                    }
                } else if (cropStart != null) {
                    cropEnd = clampToImage(x, y);
                    invalidate();
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (drawingTrace && tracePoints.size() > 2 && traceListener != null) {
                    traceListener.onTrace(tracePoints);
                }
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            return true;
        }

        private RectF normalizedCropRect() {
            return new RectF(Math.min(cropStart.x, cropEnd.x), Math.min(cropStart.y, cropEnd.y),
                    Math.max(cropStart.x, cropEnd.x), Math.max(cropStart.y, cropEnd.y));
        }

        private PointF clampToImage(float x, float y) {
            return new PointF(clamp(x, imageRect.left, imageRect.right),
                    clamp(y, imageRect.top, imageRect.bottom));
        }

        private float distance(PointF point, float x, float y) {
            return (float) Math.hypot(point.x - x, point.y - y);
        }
    }

    private interface TraceListener {
        void onTrace(List<PointF> points);
    }

    private static final class SpaceView extends View {
        SpaceView(Context context) { super(context); }
    }

    /** Keeps the photo canvas interactive while the surrounding editor remains scrollable. */
    private final class EditorScrollView extends ScrollView {
        private boolean gestureStartedOnCanvas;

        EditorScrollView(Context context) { super(context); }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if ((traceMode || cropMode) && editorView != null) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    gestureStartedOnCanvas = isInsideCanvas(event);
                }
                if (gestureStartedOnCanvas) {
                    if (event.getActionMasked() == MotionEvent.ACTION_UP
                            || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        gestureStartedOnCanvas = false;
                    }
                    return false;
                }
            }
            return super.onInterceptTouchEvent(event);
        }

        private boolean isInsideCanvas(MotionEvent event) {
            int[] location = new int[2];
            editorView.getLocationOnScreen(location);
            float x = event.getRawX();
            float y = event.getRawY();
            return x >= location[0] && x < location[0] + editorView.getWidth()
                    && y >= location[1] && y < location[1] + editorView.getHeight();
        }
    }

    private static final class BackgroundRemover {
        static Bitmap removeAutomatic(Bitmap source, int profile) {
            int width = source.getWidth();
            int height = source.getHeight();
            Bitmap output = source.copy(Bitmap.Config.ARGB_8888, true);
            int[] pixels = new int[width * height];
            source.getPixels(pixels, 0, width, 0, 0, width, height);
            int[] palette = edgePalette(pixels, width, height);
            Profile settings = Profile.forCase(profile);
            boolean[] removed = new boolean[pixels.length];
            boolean[] visited = new boolean[pixels.length];
            int[] queue = new int[pixels.length];
            int queueHead = 0;
            int queueTail = 0;
            for (int x = 0; x < width; x++) {
                queueTail = seedIfBackground(x, 0, pixels, width, palette, visited, queue, queueTail, settings);
                queueTail = seedIfBackground(x, height - 1, pixels, width, palette, visited, queue, queueTail, settings);
            }
            for (int y = 1; y < height - 1; y++) {
                queueTail = seedIfBackground(0, y, pixels, width, palette, visited, queue, queueTail, settings);
                queueTail = seedIfBackground(width - 1, y, pixels, width, palette, visited, queue, queueTail, settings);
            }
            while (queueHead < queueTail) {
                int index = queue[queueHead++];
                removed[index] = true;
                int x = index % width;
                int y = index / width;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                        int next = ny * width + nx;
                        if (!visited[next]
                                && !isProtectedSubject(pixels[next], nx, ny, width, height,
                                palette, settings)
                                && near(pixels[index], pixels[next], settings.floodThreshold)
                                && nearPalette(pixels[next], palette, settings.paletteThreshold)) {
                            visited[next] = true;
                            queue[queueTail++] = next;
                        }
                    }
                }
            }
            for (int i = 0; i < pixels.length; i++) {
                if (removed[i]) pixels[i] = pixels[i] & 0x00FFFFFF;
            }
            output.setPixels(pixels, 0, width, 0, 0, width, height);
            return output;
        }

        static Bitmap removeWithGuide(Bitmap source, List<PointF> guide, int profile) {
            Bitmap automatic = removeAutomatic(source, profile);
            return clipToGuide(automatic, guide);
        }

        static Bitmap clipToGuide(Bitmap automatic, List<PointF> guide) {
            Bitmap output = Bitmap.createBitmap(automatic.getWidth(), automatic.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            Path path = new Path();
            path.moveTo(guide.get(0).x, guide.get(0).y);
            for (int i = 1; i < guide.size(); i++) path.lineTo(guide.get(i).x, guide.get(i).y);
            path.close();
            canvas.save();
            canvas.clipPath(path);
            canvas.drawBitmap(automatic, 0, 0, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            canvas.restore();
            return output;
        }

        private static int seedIfBackground(int x, int y, int[] pixels, int width, int[] palette,
                                             boolean[] visited, int[] queue, int tail, Profile settings) {
            int index = y * width + x;
            if (!visited[index] && nearPalette(pixels[index], palette, settings.paletteThreshold)) {
                visited[index] = true;
                queue[tail++] = index;
            }
            return tail;
        }

        private static int[] edgePalette(int[] pixels, int width, int height) {
            ArrayList<Integer> colors = new ArrayList<>();
            int stride = Math.max(1, Math.min(width, height) / 32);
            for (int x = 0; x < width; x += stride) {
                colors.add(pixels[x]);
                colors.add(pixels[(height - 1) * width + x]);
            }
            for (int y = stride; y < height - 1; y += stride) {
                colors.add(pixels[y * width]);
                colors.add(pixels[y * width + width - 1]);
            }
            int paletteSize = Math.min(48, colors.size());
            int[] palette = new int[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                palette[i] = colors.get(i * colors.size() / paletteSize);
            }
            return palette;
        }

        private static boolean nearPalette(int color, int[] palette, int threshold) {
            int limit = threshold * threshold;
            for (int candidate : palette) {
                if (colorDistanceSquared(color, candidate) <= limit) return true;
            }
            return false;
        }

        private static int paletteDistance(int color, int[] palette) {
            int best = Integer.MAX_VALUE;
            for (int candidate : palette) {
                best = Math.min(best, colorDistanceSquared(color, candidate));
            }
            return (int) Math.sqrt(best);
        }

        private static boolean isProtectedSubject(int color, int x, int y, int width, int height,
                                                   int[] palette, Profile settings) {
            if (settings.centerBias <= 0) return false;
            float nx = (x - width * .5f) / (width * .5f);
            float ny = (y - height * settings.centerY) / (height * .5f);
            float ellipse = nx * nx + ny * ny;
            if (ellipse > settings.centerBias) return false;
            int contrast = paletteDistance(color, palette);
            return contrast >= settings.protectContrast
                    || (settings.humanSkinProtection && skinLike(color));
        }

        private static boolean skinLike(int color) {
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            return r > 72 && g > 30 && b > 18
                    && r > g + 12 && g > b + 8 && r - b > 28;
        }

        private static final class Profile {
            final int floodThreshold;
            final int paletteThreshold;
            final int protectContrast;
            final float centerBias;
            final float centerY;
            final boolean humanSkinProtection;

            Profile(int floodThreshold, int paletteThreshold, int protectContrast,
                    float centerBias, float centerY, boolean humanSkinProtection) {
                this.floodThreshold = floodThreshold;
                this.paletteThreshold = paletteThreshold;
                this.protectContrast = protectContrast;
                this.centerBias = centerBias;
                this.centerY = centerY;
                this.humanSkinProtection = humanSkinProtection;
            }

            static Profile forCase(int profile) {
                if (profile == CASE_HUMAN) {
                    return new Profile(72, 116, 54, .92f, .44f, true);
                }
                if (profile == CASE_ANIMAL) {
                    return new Profile(82, 126, 42, 1.10f, .50f, false);
                }
                return new Profile(48, 92, Integer.MAX_VALUE, 0f, .50f, false);
            }
        }

        private static boolean near(int a, int b, int threshold) {
            return colorDistanceSquared(a, b) <= threshold * threshold;
        }

        private static int colorDistanceSquared(int a, int b) {
            int dr = Color.red(a) - Color.red(b);
            int dg = Color.green(a) - Color.green(b);
            int db = Color.blue(a) - Color.blue(b);
            return dr * dr + dg * dg + db * db;
        }
    }

    private RectF fitRect(int sourceWidth, int sourceHeight, int maxWidth, int maxHeight) {
        float scale = Math.min(maxWidth / (float) sourceWidth, maxHeight / (float) sourceHeight);
        float width = sourceWidth * scale;
        float height = sourceHeight * scale;
        float containerWidth = editorView == null ? maxWidth : editorView.getWidth();
        float containerHeight = editorView == null ? maxHeight : editorView.getHeight();
        float left = (containerWidth - width) / 2f;
        float top = (containerHeight - height) / 2f;
        return new RectF(left, top, left + width, top + height);
    }

    private void drawCheckerboard(Canvas canvas, RectF rect) {
        int cell = dp(14);
        Paint paint = new Paint();
        for (int y = (int) rect.top; y < rect.bottom; y += cell) {
            for (int x = (int) rect.left; x < rect.right; x += cell) {
                boolean light = ((x / cell) + (y / cell)) % 2 == 0;
                paint.setColor(light ? Color.rgb(53, 54, 64) : Color.rgb(42, 43, 52));
                canvas.drawRect(x, y, Math.min(x + cell, rect.right), Math.min(y + cell, rect.bottom), paint);
            }
        }
    }

    private static void drawCover(Canvas canvas, Bitmap bitmap, RectF target) {
        if (bitmap == null) return;
        float scale = Math.max(target.width() / bitmap.getWidth(), target.height() / bitmap.getHeight());
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        float left = target.centerX() - width / 2f;
        float top = target.centerY() - height / 2f;
        RectF source = new RectF(left, top, left + width, top + height);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.save();
        canvas.clipRect(target);
        canvas.drawBitmap(bitmap, null, source, paint);
        canvas.restore();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
