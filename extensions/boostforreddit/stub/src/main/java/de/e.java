package de;

import com.rubenmayayo.reddit.models.imgur.ImageResponse;

import java.util.List;

public interface e {
    void a(List<?> items);

    void b(ImageResponse.UploadedImage uploadedImage);

    void c(Exception exception, String message);

    void onSuccess(String url);
}
