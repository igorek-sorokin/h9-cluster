package net.adminrunet.h9cluster;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;

/** Minimal FileProvider for cached update APKs (no AndroidX dependency). */
public final class UpdateFileProvider extends ContentProvider {
    private static final String[] COLUMNS = {
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE
    };

    private String authority;
    private File updatesDir;

    public static Uri getUriForFile(Context context, File file) {
        String auth = context.getPackageName() + ".updatefiles";
        return new Uri.Builder()
                .scheme("content")
                .authority(auth)
                .appendPath(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            return false;
        }
        updatesDir = new File(context.getCacheDir(), "updates");
        return true;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        authority = info.authority;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        File file = fileForUri(uri);
        String[] columns = projection == null ? COLUMNS : projection;
        Object[] values = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            String column = columns[index];
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                values[index] = file.getName();
            } else if (OpenableColumns.SIZE.equals(column)) {
                values[index] = file.length();
            } else {
                values[index] = null;
            }
        }
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        cursor.addRow(values);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        String name = fileForUri(uri).getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String extension = name.substring(dot + 1);
            String mime = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }
        return "application/vnd.android.package-archive";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Read-only provider");
        }
        return ParcelFileDescriptor.open(
                fileForUri(uri),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(
            Uri uri,
            ContentValues values,
            String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    private File fileForUri(Uri uri) {
        if (authority != null && !authority.equals(uri.getAuthority())) {
            throw new IllegalArgumentException("Wrong authority: " + uri);
        }
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Invalid path: " + uri);
        }
        if (updatesDir == null) {
            throw new IllegalStateException("Provider not initialized");
        }
        File file = new File(updatesDir, name);
        if (!file.getAbsolutePath().startsWith(updatesDir.getAbsolutePath())) {
            throw new IllegalArgumentException("Path escape: " + uri);
        }
        return file;
    }
}
