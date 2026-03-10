package com.mishiranu.dashchan.chan.e444.enhance.tasks;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.E444RequestPerformer;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceTask;
import java.io.InputStream;

public final class TaskReadIcon implements EnhanceTask<Bitmap> {
    private final String iconName;

    public TaskReadIcon(String iconName) {
        this.iconName = iconName;
    }

    @Override
    public Bitmap run(E444ChanLocator locator, HttpRequest.Preset preset) throws Exception {
        HttpResponse response = E444RequestPerformer.request(preset, "static", "img", "reactions", iconName)
                .configure(HttpRequest::setGetMethod)
                .perform();
        try (InputStream input = response.open()) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                throw new HttpException(0, "Failed to decode reaction icon");
            }
            return bitmap;
        }
    }
}
