package com.fundostudio;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;

import org.tensorflow.lite.Interpreter;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Automatic local matting pipeline: MODNet first, semantic segmentation second. */
final class OfflineSegmenter {
    private static Interpreter modnetInterpreter;
    private static ByteBuffer modnetBuffer;
    private static boolean modnetAttempted;
    private static Interpreter genericInterpreter;
    private static ByteBuffer genericModelBuffer;
    private static boolean genericAttempted;

    private OfflineSegmenter() { }

    static Bitmap tryAutomatic(Context context, Bitmap source) {
        Bitmap portrait = tryModNet(context, source);
        if (portrait != null) return portrait;
        return tryGeneric(context, source);
    }

    /** MODNet produces a soft alpha matte at 512x512, which is better for hair and gaps. */
    private static Bitmap tryModNet(Context context, Bitmap source) {
        try {
            Interpreter model = getModNetInterpreter(context);
            if (model == null) return null;
            int[] inputShape = model.getInputTensor(0).shape();
            int inputHeight = inputShape[2];
            int inputWidth = inputShape[3];
            Bitmap inputBitmap = Bitmap.createScaledBitmap(source, inputWidth, inputHeight, true);
            float[][][][] input = new float[1][3][inputHeight][inputWidth];
            for (int y = 0; y < inputHeight; y++) {
                for (int x = 0; x < inputWidth; x++) {
                    int pixel = inputBitmap.getPixel(x, y);
                    input[0][0][y][x] = Color.red(pixel) / 127.5f - 1f;
                    input[0][1][y][x] = Color.green(pixel) / 127.5f - 1f;
                    input[0][2][y][x] = Color.blue(pixel) / 127.5f - 1f;
                }
            }
            if (inputBitmap != source) inputBitmap.recycle();

            int[] outputShape = model.getOutputTensor(0).shape();
            int outputHeight = outputShape[2];
            int outputWidth = outputShape[3];
            float[][][][] output = new float[1][1][outputHeight][outputWidth];
            model.run(input, output);

            float[][] alpha = new float[outputHeight][outputWidth];
            int confident = 0;
            for (int y = 0; y < outputHeight; y++) {
                for (int x = 0; x < outputWidth; x++) {
                    alpha[y][x] = clamp(output[0][0][y][x], 0f, 1f);
                    if (alpha[y][x] > .45f) confident++;
                }
            }
            float coverage = confident / (float) (outputHeight * outputWidth);
            if (confident < 100 || coverage > .985f) return null;
            // A slightly firmer transition suppresses low-confidence halos between limbs.
            return applyMask(source, alpha, .28f, .68f);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** DeepLab MobileNet/PASCAL-VOC fallback for common objects and non-portrait images. */
    private static Bitmap tryGeneric(Context context, Bitmap source) {
        try {
            Interpreter model = getGenericInterpreter(context);
            if (model == null) return null;
            int[] inputShape = model.getInputTensor(0).shape();
            int inputHeight = inputShape[1];
            int inputWidth = inputShape[2];
            Bitmap inputBitmap = Bitmap.createScaledBitmap(source, inputWidth, inputHeight, true);
            float[][][][] input = new float[1][inputHeight][inputWidth][3];
            for (int y = 0; y < inputHeight; y++) {
                for (int x = 0; x < inputWidth; x++) {
                    int pixel = inputBitmap.getPixel(x, y);
                    input[0][y][x][0] = Color.red(pixel) / 127.5f - 1f;
                    input[0][y][x][1] = Color.green(pixel) / 127.5f - 1f;
                    input[0][y][x][2] = Color.blue(pixel) / 127.5f - 1f;
                }
            }
            if (inputBitmap != source) inputBitmap.recycle();

            int[] outputShape = model.getOutputTensor(0).shape();
            int outputHeight = outputShape[1];
            int outputWidth = outputShape[2];
            int outputClasses = outputShape.length > 3 ? outputShape[3] : 1;
            float[][][][] scores = new float[1][outputHeight][outputWidth][outputClasses];
            model.run(input, scores);

            float[][] mask = new float[outputHeight][outputWidth];
            int confident = 0;
            for (int y = 0; y < outputHeight; y++) {
                for (int x = 0; x < outputWidth; x++) {
                    mask[y][x] = foregroundProbability(scores[0][y][x]);
                    if (mask[y][x] > .45f) confident++;
                }
            }
            if (confident < 80) return null;
            return applyMask(source, mask, .26f, .64f);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static float foregroundProbability(float[] values) {
        if (values.length == 1) return values[0];
        float rawSum = 0f;
        float rawMax = -Float.MAX_VALUE;
        for (float value : values) {
            rawSum += value;
            rawMax = Math.max(rawMax, value);
        }
        boolean probabilities = rawMax <= 1.01f && rawSum > .8f && rawSum < 1.2f;
        if (probabilities) {
            float foreground = 0f;
            for (int i = 1; i < values.length; i++) foreground += values[i];
            return foreground;
        }
        float denominator = 0f;
        float foreground = 0f;
        for (int i = 0; i < values.length; i++) {
            float weight = (float) Math.exp(values[i] - rawMax);
            denominator += weight;
            if (i > 0) foreground += weight;
        }
        return denominator == 0f ? 0f : foreground / denominator;
    }

    private static Bitmap applyMask(Bitmap source, float[][] mask, float low, float high) {
        int width = source.getWidth();
        int height = source.getHeight();
        int maskHeight = mask.length;
        int maskWidth = mask[0].length;
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int y = 0; y < height; y++) {
            float gy = y * (maskHeight - 1f) / Math.max(1, height - 1);
            int y0 = Math.min(maskHeight - 1, (int) gy);
            int y1 = Math.min(maskHeight - 1, y0 + 1);
            float fy = gy - y0;
            for (int x = 0; x < width; x++) {
                float gx = x * (maskWidth - 1f) / Math.max(1, width - 1);
                int x0 = Math.min(maskWidth - 1, (int) gx);
                int x1 = Math.min(maskWidth - 1, x0 + 1);
                float fx = gx - x0;
                float top = lerp(mask[y0][x0], mask[y0][x1], fx);
                float bottom = lerp(mask[y1][x0], mask[y1][x1], fx);
                float alpha = smoothstep(low, high, lerp(top, bottom, fy));
                int original = pixels[y * width + x];
                int finalAlpha = Math.round(Color.alpha(original) * alpha);
                pixels[y * width + x] = (finalAlpha << 24) | (original & 0x00FFFFFF);
            }
        }
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }

    private static synchronized Interpreter getModNetInterpreter(Context context) {
        if (modnetInterpreter != null) return modnetInterpreter;
        if (modnetAttempted) return null;
        modnetAttempted = true;
        modnetBuffer = loadModel(context, R.raw.modnet);
        if (modnetBuffer == null) return null;
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2);
            options.setUseXNNPACK(true);
            modnetInterpreter = new Interpreter(modnetBuffer, options);
            return modnetInterpreter;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static synchronized Interpreter getGenericInterpreter(Context context) {
        if (genericInterpreter != null) return genericInterpreter;
        if (genericAttempted) return null;
        genericAttempted = true;
        genericModelBuffer = loadModel(context, R.raw.deeplabv3);
        if (genericModelBuffer == null) return null;
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2);
            options.setUseXNNPACK(true);
            genericInterpreter = new Interpreter(genericModelBuffer, options);
            return genericInterpreter;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ByteBuffer loadModel(Context context, int resourceId) {
        try {
            Resources resources = context.getResources();
            InputStream stream = resources.openRawResource(resourceId);
            byte[] bytes = new byte[stream.available()];
            int offset = 0;
            while (offset < bytes.length) {
                int count = stream.read(bytes, offset, bytes.length - offset);
                if (count < 0) break;
                offset += count;
            }
            stream.close();
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            buffer.put(bytes);
            buffer.rewind();
            return buffer;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static float lerp(float a, float b, float amount) {
        return a + (b - a) * amount;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Math.max(0f, Math.min(1f, (value - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
