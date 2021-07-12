package com.example.stop_app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Pair;

import com.example.stop_app.ml.Deeplabv3Mnv2Test257;
import com.example.stop_app.ml.Deeplabv3Mnv2Test513;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.common.ops.QuantizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.model.Model;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DeeplabPredictionHelper {
    private Deeplabv3Mnv2Test257 model257;
    private Deeplabv3Mnv2Test513 model513;
    public ImageProcessor imageProcessor257 = new ImageProcessor.Builder()
            .add(new ResizeOp(257, 257, ResizeOp.ResizeMethod.BILINEAR))
            .add(new NormalizeOp(127.5f, 127.5f))
            .add(new QuantizeOp(128.0f, 1/128.0f)).build();
    public ImageProcessor imageProcessor513 = new ImageProcessor.Builder()
            .add(new ResizeOp(513, 513, ResizeOp.ResizeMethod.BILINEAR))
            .add(new NormalizeOp(127.5f, 127.5f))
            .add(new QuantizeOp(128.0f, 1/128.0f)).build();

    enum PredictionSize {
        SIZE_257, SIZE_513
    }

    private PredictionSize size;

    public DeeplabPredictionHelper(Context context, PredictionSize size) {
        try {
            Model.Options options;
            CompatibilityList compatList = new CompatibilityList();

            if(compatList.isDelegateSupportedOnThisDevice()){
                // if the device has a supported GPU, add the GPU delegate
                options = new Model.Options.Builder().setDevice(Model.Device.GPU).build();
            } else {
                // if the GPU is not supported, run on 4 threads
                options = new Model.Options.Builder().setNumThreads(4).build();
            }
            this.size = size;

            switch(size) {
                case SIZE_257:
                    model257 = Deeplabv3Mnv2Test257.newInstance(context, options);
                    break;
                case SIZE_513:
                    model513 = Deeplabv3Mnv2Test513.newInstance(context, options);
                    break;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public TensorBuffer predict(Bitmap bitmap) {
        TensorImage tImage = new TensorImage(DataType.FLOAT32);
        tImage.load(bitmap);
        switch(size) {
            case SIZE_257:
                tImage = imageProcessor257.process(tImage);
                TensorBuffer inputFeature257 = TensorBuffer.createFixedSize(new int[]{1, 257, 257, 3}, DataType.FLOAT32);
                inputFeature257.loadBuffer(tImage.getBuffer());
                Deeplabv3Mnv2Test257.Outputs outputs257 = model257.process(inputFeature257);
                return outputs257.getOutputFeature0AsTensorBuffer();
            case SIZE_513:
                tImage = imageProcessor513.process(tImage);
                TensorBuffer inputFeature513 = TensorBuffer.createFixedSize(new int[]{1, 513, 513, 3}, DataType.FLOAT32);
                inputFeature513.loadBuffer(tImage.getBuffer());
                Deeplabv3Mnv2Test513.Outputs outputs513 = model513.process(inputFeature513);
                return outputs513.getOutputFeature0AsTensorBuffer();
        }
        return null;
    }

    public Pair<Bitmap, Boolean> fetchArgmax(ByteBuffer inputBuffer, PredictionSize size) {
        int outputHeight;
        int outputWidth;
        switch(size) {
            case SIZE_257:
                outputHeight = 257;
                outputWidth = 257;
                break;
            case SIZE_513:
                outputHeight = 513;
                outputWidth = 513;
                break;
            default:
                outputHeight = 0;
                outputWidth = 0;
                break;
        }
        int outputBatchSize = 1;
        int outputChannels = 2;
        List<Integer> labelColors = new ArrayList<>();
        labelColors.add(Color.argb(255, 0, 0 ,0));
        labelColors.add(Color.argb(255, 255, 0 ,0));

        Bitmap outputArgmax = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888);
        inputBuffer.rewind();
        boolean isDetected = false;
        for (int i = 0; i < outputHeight; i++) {
            for (int j = 0; j < outputWidth; j++) {
                int maxIndex = 0;
                float maxValue = 0.0f;
                for (int c = 0; c < outputChannels; ++c) {
                    float outputValue = inputBuffer.getFloat((i * outputWidth * 2 + j * 2 + c) * 4);
                    if (outputValue > maxValue) {
                        if(c == 1) isDetected = true;
                        maxIndex = c;
                        maxValue = outputValue;
                    }
                }
                int labelColor = labelColors.get(maxIndex);
                outputArgmax.setPixel(j, i, labelColor);
            }
        }
        return new Pair<>(outputArgmax, isDetected);
    }

    public void close() {
        switch(size) {
            case SIZE_257:
                model257.close();
                break;
            case SIZE_513:
                model513.close();
                break;
        }


    }
}
