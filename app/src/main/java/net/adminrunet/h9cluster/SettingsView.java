package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.SkinSettings;
import net.adminrunet.h9cluster.skins.SkinSettingsProvider;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Touch settings surface kept dependency-free for the Android 9 head unit. */
@SuppressLint("ViewConstructor")
public final class SettingsView extends View {
    private static final float LOGICAL_WIDTH = 960.0f;
    private static final float LOGICAL_HEIGHT = 540.0f;
    private static final int COLOR_BACKGROUND = 0xFF071014;
    private static final int COLOR_CARD_SELECTED = 0xFF17343A;
    private static final int COLOR_TEXT = 0xFFF2F5F7;
    private static final int COLOR_MUTED = 0xFF98A7AE;
    private static final int COLOR_ACCENT = 0xFF31D7C5;
    private static final SkinRegistry.Definition[] SKINS =
            SkinRegistry.getDefinitions();
    private static final CharSequence[] SKIN_TITLES = createSkinTitles();
    private static final float CONFIGURE_TOP = 288.0f;
    private static final float CONFIGURE_BOTTOM = 330.0f;
    private static final float SAVE_TOP_DEFAULT = 300.0f;
    private static final float SAVE_BOTTOM_DEFAULT = 350.0f;
    private static final float SAVE_TOP_WITH_SETTINGS = 340.0f;
    private static final float SAVE_BOTTOM_WITH_SETTINGS = 390.0f;
    private static final float UPDATE_HEIGHT = 44.0f;
    private static final float UPDATE_GAP = 10.0f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final SkinSettingsSession session;
    private final Listener listener;
    private String status = "";
    private float contentScale = 1.0f;
    private float contentOffsetX;
    private float contentOffsetY;

    interface Listener {
        void onDraftChanged(SkinSettingsSession.Snapshot draft);

        void onSaveRequested(SkinSettingsSession.Snapshot draft);

        void onCheckUpdateRequested();
    }

    SettingsView(
            Context context,
            SkinSettingsSession session,
            Listener listener) {
        super(context);
        this.session = session;
        this.listener = listener;
        setBackgroundColor(COLOR_BACKGROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(COLOR_BACKGROUND);
        contentScale = Math.min(
                getWidth() / LOGICAL_WIDTH,
                getHeight() / LOGICAL_HEIGHT);
        contentOffsetX = (getWidth() - LOGICAL_WIDTH * contentScale) * 0.5f;
        contentOffsetY = (getHeight() - LOGICAL_HEIGHT * contentScale) * 0.5f;

        int save = canvas.save();
        canvas.translate(contentOffsetX, contentOffsetY);
        canvas.scale(contentScale, contentScale);

        drawCenteredText(canvas, "H9 Cluster", 480.0f, 48.0f, 32.0f, COLOR_TEXT, true);
        drawCenteredText(
                canvas,
                "Разработчик: admin.ru.net",
                480.0f,
                76.0f,
                16.0f,
                COLOR_ACCENT,
                false);
        drawCenteredText(
                canvas,
                "Версия " + BuildConfig.VERSION_NAME
                        + "  ·  GitHub "
                        + BuildConfig.UPDATE_GITHUB_REPO,
                480.0f,
                98.0f,
                14.0f,
                COLOR_MUTED,
                false);
        drawCenteredText(
                canvas,
                BuildConfig.DEMO_MODE
                        ? "Выберите тему для автономного Demo"
                        : "Выберите тему для дисплея 2 или заводскую штатную панель",
                480.0f,
                122.0f,
                16.0f,
                COLOR_MUTED,
                false);

        drawSkinSelector(canvas);

        boolean factorySelected = !selectedDefinition().overlaysCluster();
        boolean configurable = selectedDefinition().hasSettings();
        if (configurable) {
            drawConfigureButton(canvas);
        }

        float saveTop = configurable
                ? SAVE_TOP_WITH_SETTINGS
                : SAVE_TOP_DEFAULT;
        float saveBottom = configurable
                ? SAVE_BOTTOM_WITH_SETTINGS
                : SAVE_BOTTOM_DEFAULT;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_ACCENT);
        canvas.drawRoundRect(
                255.0f,
                saveTop,
                705.0f,
                saveBottom,
                14.0f,
                14.0f,
                paint);
        drawCenteredText(
                canvas,
                saveButtonLabel(factorySelected),
                480.0f,
                saveTop + 32.0f,
                18.0f,
                Color.BLACK,
                true);

        float updateTop = saveBottom + UPDATE_GAP;
        float updateBottom = updateTop + UPDATE_HEIGHT;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_CARD_SELECTED);
        canvas.drawRoundRect(
                255.0f,
                updateTop,
                705.0f,
                updateBottom,
                12.0f,
                12.0f,
                paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        paint.setColor(COLOR_ACCENT);
        canvas.drawRoundRect(
                255.0f,
                updateTop,
                705.0f,
                updateBottom,
                12.0f,
                12.0f,
                paint);
        drawCenteredText(
                canvas,
                "Проверить обновление",
                480.0f,
                updateTop + 29.0f,
                17.0f,
                COLOR_TEXT,
                true);

        float autoTop = updateBottom + UPDATE_GAP;
        float autoBottom = autoTop + UPDATE_HEIGHT;
        drawAutostartToggle(canvas, autoTop, autoBottom);

        drawCenteredText(
                canvas,
                status.length() == 0
                        ? defaultHint(factorySelected)
                        : status,
                480.0f,
                510.0f,
                15.0f,
                status.length() == 0 ? COLOR_MUTED : COLOR_ACCENT,
                false);

        canvas.restoreToCount(save);
    }

    private void drawConfigureButton(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_CARD_SELECTED);
        canvas.drawRoundRect(
                300.0f,
                CONFIGURE_TOP,
                660.0f,
                CONFIGURE_BOTTOM,
                12.0f,
                12.0f,
                paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        paint.setColor(COLOR_ACCENT);
        canvas.drawRoundRect(
                300.0f,
                CONFIGURE_TOP,
                660.0f,
                CONFIGURE_BOTTOM,
                12.0f,
                12.0f,
                paint);
        drawCenteredText(
                canvas,
                "Настроить выбранную тему",
                480.0f,
                CONFIGURE_TOP + 28.0f,
                16.0f,
                COLOR_TEXT,
                true);
    }

    private void drawSkinSelector(Canvas canvas) {
        SkinRegistry.Definition option = selectedDefinition();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_CARD_SELECTED);
        canvas.drawRoundRect(110.0f, 140.0f, 850.0f, 268.0f, 16.0f, 16.0f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        paint.setColor(COLOR_ACCENT);
        canvas.drawRoundRect(110.0f, 140.0f, 850.0f, 268.0f, 16.0f, 16.0f, paint);

        drawLeftText(canvas, "Тема приборной панели", 140.0f, 172.0f, 14.0f, COLOR_MUTED, false);
        drawLeftText(canvas, option.title, 140.0f, 206.0f, 20.0f, COLOR_TEXT, true);
        drawLeftText(
                canvas,
                option.description,
                140.0f,
                236.0f,
                14.0f,
                COLOR_MUTED,
                false);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        paint.setColor(COLOR_ACCENT);
        canvas.drawLine(794.0f, 195.0f, 808.0f, 209.0f, paint);
        canvas.drawLine(808.0f, 209.0f, 822.0f, 195.0f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        performClick();
        float x = (event.getX() - contentOffsetX) / contentScale;
        float y = (event.getY() - contentOffsetY) / contentScale;
        if (x >= 110.0f && x <= 850.0f && y >= 140.0f && y <= 268.0f) {
            showSkinPicker();
            return true;
        }
        boolean configurable = selectedDefinition().hasSettings();
        if (configurable
                && x >= 300.0f
                && x <= 660.0f
                && y >= CONFIGURE_TOP
                && y <= CONFIGURE_BOTTOM) {
            showSettingsEditor();
            return true;
        }
        float saveTop = configurable
                ? SAVE_TOP_WITH_SETTINGS
                : SAVE_TOP_DEFAULT;
        float saveBottom = configurable
                ? SAVE_BOTTOM_WITH_SETTINGS
                : SAVE_BOTTOM_DEFAULT;
        if (x >= 255.0f
                && x <= 705.0f
                && y >= saveTop
                && y <= saveBottom) {
            listener.onSaveRequested(session.snapshot());
            return true;
        }
        float updateTop = saveBottom + UPDATE_GAP;
        float updateBottom = updateTop + UPDATE_HEIGHT;
        if (x >= 255.0f
                && x <= 705.0f
                && y >= updateTop
                && y <= updateBottom) {
            listener.onCheckUpdateRequested();
            return true;
        }
        float autoTop = updateBottom + UPDATE_GAP;
        float autoBottom = autoTop + UPDATE_HEIGHT;
        if (x >= 255.0f
                && x <= 705.0f
                && y >= autoTop
                && y <= autoBottom) {
            boolean enabled = !SkinPreferences.isAutostartEnabled(getContext());
            SkinPreferences.setAutostartEnabled(getContext(), enabled);
            status = enabled
                    ? "Автозапуск включён"
                    : "Автозапуск выключен — панель сама не поднимется";
            if (!enabled) {
                // Drop pending ACC-resume work; manual Save still works.
                ClusterPowerController.clearSuspendForUserLaunch();
            }
            invalidate();
            return true;
        }
        return true;
    }

    private void drawAutostartToggle(Canvas canvas, float top, float bottom) {
        boolean enabled = SkinPreferences.isAutostartEnabled(getContext());
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_CARD_SELECTED);
        canvas.drawRoundRect(255.0f, top, 705.0f, bottom, 12.0f, 12.0f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        paint.setColor(enabled ? COLOR_ACCENT : COLOR_MUTED);
        canvas.drawRoundRect(255.0f, top, 705.0f, bottom, 12.0f, 12.0f, paint);

        float boxLeft = 280.0f;
        float boxTop = top + 10.0f;
        float boxSize = 24.0f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(enabled ? COLOR_ACCENT : COLOR_MUTED);
        canvas.drawRoundRect(
                boxLeft,
                boxTop,
                boxLeft + boxSize,
                boxTop + boxSize,
                4.0f,
                4.0f,
                paint);
        if (enabled) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3.0f);
            paint.setColor(COLOR_ACCENT);
            canvas.drawLine(
                    boxLeft + 5.0f,
                    boxTop + 12.0f,
                    boxLeft + 10.0f,
                    boxTop + 18.0f,
                    paint);
            canvas.drawLine(
                    boxLeft + 10.0f,
                    boxTop + 18.0f,
                    boxLeft + 19.0f,
                    boxTop + 6.0f,
                    paint);
        }
        drawLeftText(
                canvas,
                enabled ? "Автозапуск панели включён" : "Автозапуск панели выключен",
                320.0f,
                top + 29.0f,
                16.0f,
                COLOR_TEXT,
                true);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void showSkinPicker() {
        new AlertDialog.Builder(getContext())
                .setTitle("Выберите тему")
                .setSingleChoiceItems(
                        SKIN_TITLES,
                        getSelectedSkinIndex(),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                session.selectSkin(SKINS[which].id);
                                status = "Предпросмотр темы изменён";
                                dialog.dismiss();
                                listener.onDraftChanged(session.snapshot());
                                invalidate();
                            }
                        })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private int getSelectedSkinIndex() {
        for (int index = 0; index < SKINS.length; index++) {
            if (SKINS[index].id.equals(session.snapshot().skinId)) {
                return index;
            }
        }
        return 0;
    }

    private SkinRegistry.Definition selectedDefinition() {
        return SkinRegistry.getDefinition(session.snapshot().skinId);
    }

    private void showSettingsEditor() {
        SkinRegistry.Definition definition = selectedDefinition();
        if (!definition.hasSettings()) {
            return;
        }
        SkinSettingsSession.Snapshot snapshot = session.snapshot();
        View editor = definition.createSettingsEditor(
                getContext(),
                snapshot.settings,
                new SkinSettingsProvider.Listener() {
                    @Override
                    public void onSettingsChanged(SkinSettings settings) {
                        session.updateSettings(settings);
                        status = "Предпросмотр настроек изменён";
                        listener.onDraftChanged(session.snapshot());
                        invalidate();
                    }
                });
        new AlertDialog.Builder(getContext())
                .setTitle("Настройки: " + definition.title)
                .setView(editor)
                .setPositiveButton("Готово", null)
                .show();
    }

    void showSaveResult(boolean launched) {
        boolean factorySelected = !selectedDefinition().overlaysCluster();
        if (factorySelected) {
            status = "Сохранено: на дисплее 2 остаётся заводская панель";
        } else if (launched) {
            status = BuildConfig.DEMO_MODE
                    ? "Настройки сохранены и запущены"
                    : "Настройки сохранены и запущены на дисплее 2";
        } else {
            status = BuildConfig.DEMO_MODE
                    ? "Настройки сохранены. Не удалось запустить Demo"
                    : "Настройки сохранены. Дисплей 2 сейчас недоступен";
        }
        invalidate();
    }

    void setStatusMessage(String message) {
        status = message == null ? "" : message;
        invalidate();
    }

    private static String saveButtonLabel(boolean factorySelected) {
        if (factorySelected) {
            return "Сохранить заводскую панель";
        }
        return BuildConfig.DEMO_MODE
                ? "Сохранить и запустить"
                : "Сохранить и запустить на дисплее 2";
    }

    private String defaultHint(boolean factorySelected) {
        if (!SkinPreferences.isAutostartEnabled(getContext())) {
            return "Без автозапуска: панель только после «Сохранить и запустить»";
        }
        if (factorySelected) {
            return "При автозапуске кастомная панель не будет перекрывать дисплей 2";
        }
        return BuildConfig.DEMO_MODE
                ? "Demo использует только тестовые данные"
                : "При автозапуске основной дисплей остаётся свободным";
    }

    private static CharSequence[] createSkinTitles() {
        CharSequence[] titles = new CharSequence[SKINS.length];
        for (int index = 0; index < SKINS.length; index++) {
            titles[index] = SKINS[index].title;
        }
        return titles;
    }

    private void drawCenteredText(
            Canvas canvas,
            String text,
            float centerX,
            float baseline,
            float size,
            int color,
            boolean bold) {
        configureText(size, color, Paint.Align.CENTER, bold);
        canvas.drawText(text, centerX, baseline, paint);
    }

    private void drawLeftText(
            Canvas canvas,
            String text,
            float left,
            float baseline,
            float size,
            int color,
            boolean bold) {
        configureText(size, color, Paint.Align.LEFT, bold);
        canvas.drawText(text, left, baseline, paint);
    }

    private void configureText(float size, int color, Paint.Align align, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTextAlign(align);
        paint.setFakeBoldText(bold);
    }
}
