package com.scope.camera;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** A pending file is published only after its stream has closed successfully. */
final class MediaOutput implements AutoCloseable {
    enum Phase { ALLOCATED, WRITING, WRITTEN, PUBLISHED, DISCARDED }
    static final class Lifecycle {
        private Phase phase=Phase.ALLOCATED;
        synchronized void open() { if(phase!=Phase.ALLOCATED) throw new IllegalStateException("Output already opened");phase=Phase.WRITING; }
        synchronized void written() { if(phase==Phase.WRITING) phase=Phase.WRITTEN; }
        synchronized void publish() { if(phase!=Phase.WRITTEN) throw new IllegalStateException("Output is incomplete");phase=Phase.PUBLISHED; }
        synchronized void discard() { if(phase!=Phase.PUBLISHED) phase=Phase.DISCARDED; }
        synchronized Phase phase() { return phase; }
    }
    private final Context context;
    final Uri uri;
    private final File file, temporary;
    private final Lifecycle lifecycle=new Lifecycle();
    MediaOutput(Context context,Uri uri,File file) {
        this.context=context.getApplicationContext();this.uri=uri;this.file=file;
        temporary=file==null?null:new File(file.getPath()+".part");
    }
    static MediaOutput allocate(Context context,String name,String mime,String directory,boolean publicFile) throws IOException {
        if(Build.VERSION.SDK_INT>=29 && publicFile) {
            ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
            values.put(MediaStore.MediaColumns.MIME_TYPE,mime);values.put(MediaStore.MediaColumns.RELATIVE_PATH,directory);
            values.put(MediaStore.MediaColumns.IS_PENDING,1);
            Uri collection=mime.startsWith("video/")?MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    :MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri uri=context.getContentResolver().insert(collection,values);
            if(uri==null) throw new IOException("Could not allocate media output");
            return new MediaOutput(context,uri,null);
        }
        File parent=publicFile && mime.startsWith("video/")
                ?new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),"Lika Lens")
                :new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),"Lika Lens");
        if(!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create output directory");
        return new MediaOutput(context,null,new File(parent,name));
    }
    OutputStream open() throws IOException {
        lifecycle.open();
        OutputStream stream=uri!=null?context.getContentResolver().openOutputStream(uri,"w"):new FileOutputStream(temporary);
        if(stream==null) throw new IOException("Could not open output stream");
        return new FilterOutputStream(stream) {
            @Override public void write(byte[] bytes,int offset,int length) throws IOException { out.write(bytes,offset,length); }
            @Override public void close() throws IOException { super.close();lifecycle.written(); }
        };
    }
    void publish() throws IOException {
        if(lifecycle.phase()!=Phase.WRITTEN) throw new IllegalStateException("Output is incomplete");
        if(uri!=null && Build.VERSION.SDK_INT>=29) {
            ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.IS_PENDING,0);
            if(context.getContentResolver().update(uri,values,null,null)<=0) throw new IOException("Could not publish output");
        } else if(temporary!=null && !temporary.renameTo(file)) throw new IOException("Could not finish output file");
        lifecycle.publish();
        if(uri==null && file!=null && file.getName().endsWith(".mp4")) {
            try { android.media.MediaScannerConnection.scanFile(context,new String[]{file.getAbsolutePath()},new String[]{"video/mp4"},null); }
            catch(RuntimeException error) { android.util.Log.w("MediaOutput","Published video could not be indexed",error); }
        }
    }
    Uri result() { if(lifecycle.phase()!=Phase.PUBLISHED) throw new IllegalStateException("Output not published");return uri!=null?uri:Uri.fromFile(file); }
    void discard() {
        if(lifecycle.phase()==Phase.PUBLISHED || lifecycle.phase()==Phase.DISCARDED) return;
        try { if(uri!=null) context.getContentResolver().delete(uri,null,null);else if(temporary!=null) temporary.delete(); }
        finally { lifecycle.discard(); }
    }
    @Override public void close() { discard(); }
}
