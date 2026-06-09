package com.example.batchimagerenamer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int REQUEST_IMAGES = 1001;

    private Button pickButton;
    private TextView statusText;
    private TextView resultText;
    private final SimpleDateFormat nameFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createView());
    }

    private LinearLayout createView() {
        int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(245, 246, 242));

        TextView title = new TextView(this);
        title.setText("批量图片改名");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(23, 33, 31));
        title.setGravity(Gravity.START);
        root.addView(title, fullWidth());

        TextView subtitle = new TextView(this);
        subtitle.setText("选择图片，直接把原文件名改成文件修改时间。");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.rgb(97, 109, 104));
        subtitle.setPadding(0, dp(6), 0, dp(14));
        root.addView(subtitle, fullWidth());

        pickButton = new Button(this);
        pickButton.setText("选择图片并改名");
        pickButton.setAllCaps(false);
        pickButton.setOnClickListener(v -> openImagePicker());
        root.addView(pickButton, fullWidth());

        statusText = new TextView(this);
        statusText.setText("等待选择图片。");
        statusText.setTextSize(15);
        statusText.setTextColor(Color.rgb(23, 33, 31));
        statusText.setPadding(0, dp(14), 0, dp(10));
        root.addView(statusText, fullWidth());

        ScrollView scroll = new ScrollView(this);
        resultText = new TextView(this);
        resultText.setText("还没有执行改名。");
        resultText.setTextSize(14);
        resultText.setTextColor(Color.rgb(23, 33, 31));
        resultText.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(resultText, fullWidth());

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        root.addView(scroll, scrollParams);

        return root;
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMAGES || resultCode != RESULT_OK || data == null) {
            setStatus("已取消。");
            return;
        }

        List<Uri> uris = collectUris(data);
        if (uris.isEmpty()) {
            setStatus("没有选择图片。");
            return;
        }

        List<RenamePlan> plans = buildPlans(uris);
        showPlan(plans, "等待确认");
        confirmAndRename(plans);
    }

    private List<Uri> collectUris(Intent data) {
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                uris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        return uris;
    }

    private List<RenamePlan> buildPlans(List<Uri> uris) {
        List<RenamePlan> plans = new ArrayList<>();
        Map<String, Integer> usedNames = new HashMap<>();
        for (Uri uri : uris) {
            FileInfo info = readFileInfo(uri);
            String newName = uniqueName(formatTimestamp(info.modifiedAt) + extensionOf(info.displayName), usedNames);
            plans.add(new RenamePlan(uri, info.displayName, newName));
        }
        return plans;
    }

    private FileInfo readFileInfo(Uri uri) {
        String displayName = "image.jpg";
        long modifiedAt = 0L;

        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    String value = cursor.getString(nameIndex);
                    if (value != null && !value.trim().isEmpty()) {
                        displayName = value;
                    }
                }

                int modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
                if (modifiedIndex >= 0) {
                    modifiedAt = cursor.getLong(modifiedIndex);
                }
            }
        } catch (Exception ignored) {
        }

        if (modifiedAt <= 0L) {
            modifiedAt = System.currentTimeMillis();
        }
        return new FileInfo(displayName, modifiedAt);
    }

    private void confirmAndRename(List<RenamePlan> plans) {
        StringBuilder preview = new StringBuilder();
        int limit = Math.min(5, plans.size());
        for (int i = 0; i < limit; i++) {
            RenamePlan plan = plans.get(i);
            preview.append(plan.oldName).append(" -> ").append(plan.newName).append("\n");
        }
        if (plans.size() > limit) {
            preview.append("...共 ").append(plans.size()).append(" 个文件\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("确认直接改名")
                .setMessage(preview + "\n继续吗？")
                .setNegativeButton("取消", (dialog, which) -> {
                    setStatus("已取消。");
                    showPlan(plans, "已取消");
                })
                .setPositiveButton("改名", (dialog, which) -> renamePlans(plans))
                .show();
    }

    private void renamePlans(List<RenamePlan> plans) {
        ContentResolver resolver = getContentResolver();
        int success = 0;

        for (RenamePlan plan : plans) {
            try {
                takePersistablePermission(plan.uri);
                if (plan.oldName.equals(plan.newName)) {
                    plan.result = "跳过：文件名已符合格式";
                    success++;
                    continue;
                }
                Uri renamed = DocumentsContract.renameDocument(resolver, plan.uri, plan.newName);
                if (renamed == null) {
                    plan.result = "失败：系统没有返回新文件";
                } else {
                    plan.result = "成功";
                    success++;
                }
            } catch (Exception error) {
                plan.result = "失败：" + readableError(error);
            }
        }

        showPlan(plans, null);
        setStatus("完成：成功 " + success + " 个，失败 " + (plans.size() - success) + " 个。");
    }

    private void takePersistablePermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        } catch (Exception ignored) {
        }
    }

    private String readableError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "系统拒绝改名";
        }
        return message;
    }

    private void showPlan(List<RenamePlan> plans, String overrideResult) {
        StringBuilder builder = new StringBuilder();
        for (RenamePlan plan : plans) {
            builder.append(plan.oldName)
                    .append("\n  -> ")
                    .append(plan.newName)
                    .append("\n  ")
                    .append(overrideResult != null ? overrideResult : plan.result)
                    .append("\n\n");
        }
        resultText.setText(builder.toString().trim());
    }

    private void setStatus(String text) {
        statusText.setText(text);
    }

    private String formatTimestamp(long millis) {
        return nameFormat.format(new Date(millis));
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            return name.substring(dot).toLowerCase(Locale.US);
        }
        return ".jpg";
    }

    private String uniqueName(String name, Map<String, Integer> usedNames) {
        String key = name.toLowerCase(Locale.US);
        int count = usedNames.containsKey(key) ? usedNames.get(key) : 0;
        usedNames.put(key, count + 1);
        if (count == 0) {
            return name;
        }

        String ext = extensionOf(name);
        String stem = name.substring(0, name.length() - ext.length());
        return stem + "_" + String.format(Locale.US, "%03d", count) + ext;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class FileInfo {
        final String displayName;
        final long modifiedAt;

        FileInfo(String displayName, long modifiedAt) {
            this.displayName = displayName;
            this.modifiedAt = modifiedAt;
        }
    }

    private static class RenamePlan {
        final Uri uri;
        final String oldName;
        final String newName;
        String result = "等待";

        RenamePlan(Uri uri, String oldName, String newName) {
            this.uri = uri;
            this.oldName = oldName;
            this.newName = newName;
        }
    }
}
