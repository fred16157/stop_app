package com.example.stop_app;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.stop_app.ml.FrozenInferenceGraph;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.model.Model;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.nio.ByteBuffer;

public class QuantizedDeeplabPredictionHelper {
    private FrozenInferenceGraph model;
    private ImageProcessor imageProcessor = new ImageProcessor.Builder()
            .add(new ResizeOp(257, 257, ResizeOp.ResizeMethod.BILINEAR)).build();
    public QuantizedDeeplabPredictionHelper(Context context) {
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
            model = FrozenInferenceGraph.newInstance(context, options);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public TensorBuffer predict(Bitmap bitmap) {
        TensorImage tImage = new TensorImage(DataType.UINT8);
        tImage.load(bitmap);
        tImage = imageProcessor.process(tImage);
        TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 257, 257, 3}, DataType.UINT8);
        inputFeature0.loadBuffer(tImage.getBuffer());

        FrozenInferenceGraph.Outputs outputs = model.process(inputFeature0);
        return outputs.getOutputFeature0AsTensorBuffer();
    }

    public void close() {
        model.close();
    }

}
