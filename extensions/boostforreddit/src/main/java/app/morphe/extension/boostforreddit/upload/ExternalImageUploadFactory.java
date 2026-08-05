/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */
package app.morphe.extension.boostforreddit.upload;

import com.rubenmayayo.reddit.models.imgur.Upload;

import java.util.List;
import java.util.Locale;

import de.e;
import de.f;

/** Creates the configured Boost media uploader without changing post/gallery routing. */
public final class ExternalImageUploadFactory {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_EXTERNAL_IMAGE_HOST_FACTORY_ISSUE66_V1";

    private ExternalImageUploadFactory() {
    }

    public static de.b create(String provider) {
        String normalized = provider == null
                ? "reddit"
                : provider.trim().toLowerCase(Locale.US);

        if ("imgbb".equals(normalized)) {
            return new ImgBbUploader();
        }
        if ("imgur_free".equals(normalized)) {
            return instantiate(
                    "ee.a",
                    "Boost's legacy Imgur uploader is unavailable."
            );
        }
        if ("imgur_paid".equals(normalized)) {
            return instantiate(
                    "ee.b",
                    "Boost's paid Imgur uploader is unavailable."
            );
        }
        if ("vgy".equals(normalized)) {
            return instantiate(
                    "ge.b",
                    "Boost's legacy vgy uploader is unavailable."
            );
        }

        return instantiate(
                "fe.b",
                "Boost's native Reddit uploader is unavailable."
        );
    }

    private static de.b instantiate(String className, String failureMessage) {
        try {
            Object value = Class.forName(className)
                    .getDeclaredConstructor()
                    .newInstance();
            if (value instanceof de.b) {
                return (de.b) value;
            }
        } catch (Throwable ignored) {
        }

        return new UnavailableUploader(failureMessage);
    }

    private static final class UnavailableUploader implements de.b {
        private final String message;

        private UnavailableUploader(String message) {
            this.message = message;
        }

        @Override
        public void a(
                String title,
                List<Upload> uploads,
                f.b progress,
                e listener
        ) {
            fail(listener);
        }

        @Override
        public void b(Upload upload, f.b progress, e listener) {
            fail(listener);
        }

        @Override
        public void c(Upload upload, f.b progress, e listener) {
            fail(listener);
        }

        private void fail(e listener) {
            if (listener != null) {
                listener.c(null, message);
            }
        }
    }
}
