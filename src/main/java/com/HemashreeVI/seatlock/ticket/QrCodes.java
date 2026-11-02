package com.HemashreeVI.seatlock.ticket;

import com.HemashreeVI.seatlock.common.BadRequestException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Draws QR codes (PNG) and reads them back from photos, using ZXing. */
@Component
public class QrCodes {

    private static final int SIZE = 360;
    private static final int MAX_SCAN_EDGE = 1600;   // phone photos are huge; QR codes don't need that

    public byte[] png(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not draw QR code", e);
        }
    }

    /** Reads the QR code in an uploaded photo or screenshot. */
    public String read(byte[] image) {
        BufferedImage picture;
        try {
            picture = ImageIO.read(new ByteArrayInputStream(image));
        } catch (IOException e) {
            picture = null;
        }
        if (picture == null) {
            throw new BadRequestException("That file isn't an image we can read (use JPG or PNG)");
        }
        try {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(shrink(picture))));
            return new MultiFormatReader().decode(bitmap, Map.of(
                    DecodeHintType.TRY_HARDER, Boolean.TRUE,
                    DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE))).getText();
        } catch (NotFoundException e) {
            throw new BadRequestException("No QR code found in the photo. Hold the ticket flat and closer, then try again");
        }
    }

    private static BufferedImage shrink(BufferedImage image) {
        int edge = Math.max(image.getWidth(), image.getHeight());
        if (edge <= MAX_SCAN_EDGE) {
            return image;
        }
        double scale = (double) MAX_SCAN_EDGE / edge;
        int w = (int) Math.round(image.getWidth() * scale);
        int h = (int) Math.round(image.getHeight() * scale);
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        return small;
    }
}
