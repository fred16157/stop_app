package com.example.stop_app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.example.stop_app.ml.Deeplabv3Mnv2Test257;

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
    private Deeplabv3Mnv2Test257 model;
    public ImageProcessor imageProcessor = new ImageProcessor.Builder()
            .add(new ResizeOp(257, 257, ResizeOp.ResizeMethod.BILINEAR))
            .add(new NormalizeOp(127.5f, 127.5f))
            .add(new QuantizeOp(128.0f, 1/128.0f)).build();
    public DeeplabPredictionHelper(Context context) {
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
            model = Deeplabv3Mnv2Test257.newInstance(context, options);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public TensorBuffer predict(Bitmap bitmap) {
        TensorImage tImage = new TensorImage(DataType.FLOAT32);
        tImage.load(bitmap);
        tImage = imageProcessor.process(tImage);
        TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 257, 257, 3}, DataType.FLOAT32);
        inputFeature0.loadBuffer(tImage.getBuffer());

        Deeplabv3Mnv2Test257.Outputs outputs = model.process(inputFeature0);
        return outputs.getOutputFeature0AsTensorBuffer();
    }

    Bitmap fetchArgmax(ByteBuffer inputBuffer) {
        int outputBatchSize = 1;
        int outputHeight = 257;
        int outputWidth = 257;
        int outputChannels = 2;
        List<Integer> labelColors = new ArrayList<Integer>();
        labelColors.add(Color.argb(255, 0, 255 ,0));
        labelColors.add(Color.argb(255, 255, 0 ,0));

        Bitmap outputArgmax = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888);
        inputBuffer.rewind();
        for (int i = 0; i < outputHeight; i++) {
            for (int j = 0; j < outputWidth; j++) {
                int maxIndex = 0;
                float maxValue = 0.0f;
                for (int c = 0; c < outputChannels; ++c) {
                    float outputValue = inputBuffer.getFloat((i * outputWidth * 2 + j * 2 + c) * 4);
                    if (outputValue > maxValue) {
                        maxIndex = c;
                        maxValue = outputValue;
                    }
                }
                int labelColor = labelColors.get(maxIndex);
                outputArgmax.setPixel(j, i, labelColor);
            }
        }
        return outputArgmax;
    }

    public void close() {
        model.close();
    }
}
