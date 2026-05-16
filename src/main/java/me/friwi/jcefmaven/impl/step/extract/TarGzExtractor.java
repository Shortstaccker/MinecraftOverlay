package me.friwi.jcefmaven.impl.step.extract;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TarGzExtractor {
    private static final int BUFFER_SIZE = 4096;
    private static final Logger LOGGER = Logger.getLogger(TarGzExtractor.class.getName());

    public static void extractTarGZ(File installDir, InputStream in) throws IOException {
        Objects.requireNonNull(installDir, "installDir cannot be null");
        Objects.requireNonNull(in, "in cannot be null");

        try (GzipCompressorInputStream gzipIn = new GzipCompressorInputStream(in);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextTarEntry()) != null) {
                File target = new File(installDir, entry.getName());
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) {
                        LOGGER.log(Level.SEVERE,
                                "Unable to create directory '{0}', during extraction of archive contents.",
                                target.getAbsolutePath());
                    }
                    markExecutableIfNeeded(target, entry, "directory");
                    continue;
                }

                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Unable to create parent directory " + parent.getAbsolutePath());
                }

                byte[] buffer = new byte[BUFFER_SIZE];
                try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target, false),
                        BUFFER_SIZE)) {
                    int length;
                    while ((length = tarIn.read(buffer, 0, BUFFER_SIZE)) != -1) {
                        out.write(buffer, 0, length);
                    }
                }
                markExecutableIfNeeded(target, entry, "file");
            }
        }
    }

    private static void markExecutableIfNeeded(File target, TarArchiveEntry entry, String type) {
        if ((entry.getMode() & 73) == 0)
            return;
        if (!target.setExecutable(true, false)) {
            LOGGER.log(Level.SEVERE, "Unable to mark {0} '{1}' executable, during extraction of archive contents.",
                    new Object[]{type, target.getAbsolutePath()});
        }
    }
}
