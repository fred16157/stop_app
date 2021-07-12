package com.example.stop_app;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Pair;

import com.example.stop_app.ml.Deeplabv3Mnv2Test257;
import com.example.stop_app.ml.Deeplabv3Mnv2Test513;
import com.example.stop_app.ml.Yolov4Tiny416;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.common.ops.QuantizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.model.Model;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Vector;

public class YoloPredictionHelper {
    private Yolov4Tiny416 model;
    public ImageProcessor imageProcessor = new ImageProcessor.Builder()
            .add(new ResizeOp(416, 416, ResizeOp.ResizeMethod.BILINEAR))
            .add(new NormalizeOp(127.5f, 127.5f))
            .add(new QuantizeOp(128.0f, 1/128.0f)).build();

    private static final int[] OUTPUT_WIDTH_TINY = new int[]{2535, 2535};
    private Vector<String> labels = new Vector<>();
    protected float mNmsThresh = 0.6f;

    protected float overlap(float x1, float w1, float x2, float w2) {
        float l1 = x1 - w1 / 2;
        float l2 = x2 - w2 / 2;
        float left = l1 > l2 ? l1 : l2;
        float r1 = x1 + w1 / 2;
        float r2 = x2 + w2 / 2;
        float right = r1 < r2 ? r1 : r2;
        return right - left;
    }

    protected float box_iou(RectF a, RectF b) {
        return box_intersection(a, b) / box_union(a, b);
    }

    protected float box_intersection(RectF a, RectF b) {
        float w = overlap((a.left + a.right) / 2, a.right - a.left,
                (b.left + b.right) / 2, b.right - b.left);
        float h = overlap((a.top + a.bottom) / 2, a.bottom - a.top,
                (b.top + b.bottom) / 2, b.bottom - b.top);
        if (w < 0 || h < 0) return 0;
        float area = w * h;
        return area;
    }

    protected float box_union(RectF a, RectF b) {
        float i = box_intersection(a, b);
        float u = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - i;
        return u;
    }

    public YoloPredictionHelper(Context context) {
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
            InputStream labelInput = context.getAssets().open("coco.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(labelInput));
            String line;
            while ((line = br.readLine()) != null) {
                labels.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ArrayList<Recognition> predict(Bitmap bitmap) {
        ArrayList<Recognition> detections = new ArrayList<>();
        TensorImage tImage = new TensorImage(DataType.FLOAT32);
        tImage.load(bitmap);
        tImage = imageProcessor.process(tImage);
        TensorBuffer inputFeature = TensorBuffer.createFixedSize(new int[]{1, 416, 416, 3}, DataType.FLOAT32);
        inputFeature.loadBuffer(tImage.getBuffer());
        Yolov4Tiny416.Outputs outputs = model.process(inputFeature);

        int gridWidth = OUTPUT_WIDTH_TINY[0];
        ByteBuffer bboxBuffer = outputs.getOutputFeature0AsTensorBuffer().getBuffer();
        float[][][] bboxes = new float[1][OUTPUT_WIDTH_TINY[0]][4];
        for(int i = 0; i < OUTPUT_WIDTH_TINY[0]; i++) {
            for(int j = 0; j < 4; j++) {
                bboxes[0][i][j] = bboxBuffer.getFloat();
            }

        }
        ByteBuffer scoreBuffer = outputs.getOutputFeature1AsTensorBuffer().getBuffer();
        float[][][] out_score = new float[1][OUTPUT_WIDTH_TINY[1]][labels.size()];
        for(int i = 0; i < OUTPUT_WIDTH_TINY[1]; i++) {
            for(int j = 0; j < labels.size(); j++) {
                out_score[0][i][j] = scoreBuffer.getFloat();
            }
        }
        for (int i = 0; i < gridWidth;i++){
            float maxClass = 0;
            int detectedClass = -1;
            final float[] classes = new float[labels.size()];
            for (int c = 0;c< labels.size();c++){
                classes [c] = out_score[0][i][c];
            }
            for (int c = 0;c<labels.size();++c){
                if (classes[c] > maxClass){
                    detectedClass = c;
                    maxClass = classes[c];
                }
            }
            final float score = maxClass;
            if (score > 0.5f){  // 정확도 50% 이상인 결과만 추가
                final float xPos = bboxes[0][i][0];
                final float yPos = bboxes[0][i][1];
                final float w = bboxes[0][i][2];
                final float h = bboxes[0][i][3];
                final RectF rectF = new RectF(
                        Math.max(0, xPos - w / 2),
                        Math.max(0, yPos - h / 2),
                        Math.min(bitmap.getWidth() - 1, xPos + w / 2),
                        Math.min(bitmap.getHeight() - 1, yPos + h / 2));
                detections.add(new Recognition("" + i, labels.get(detectedClass),score,rectF,detectedClass ));
            }
        }

        return detections;
    }

    public Bitmap getResultOverlay(ArrayList<Recognition> detections) {
        Bitmap overlay = Bitmap.createBitmap(416, 416, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(overlay);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        for(Recognition detection : detections) {
            canvas.drawRect(detection.location, paint);
        }
        return overlay;
    }

    protected ArrayList<Recognition> nms(ArrayList<Recognition> list) {
        ArrayList<Recognition> nmsList = new ArrayList<Recognition>();

        for (int k = 0; k < labels.size(); k++) {
            //1.find max confidence per class
            PriorityQueue<Recognition> pq =
                    new PriorityQueue<Recognition>(
                            50,
                            new Comparator<Recognition>() {
                                @Override
                                public int compare(final Recognition lhs, final Recognition rhs) {
                                    // Intentionally reversed to put high confidence at the head of the queue.
                                    return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                                }
                            });

            for (int i = 0; i < list.size(); ++i) {
                if (list.get(i).getDetectedClass() == k) {
                    pq.add(list.get(i));
                }
            }

            //2.do non maximum suppression
            while (pq.size() > 0) {
                //insert detection with max confidence
                Recognition[] a = new Recognition[pq.size()];
                Recognition[] detections = pq.toArray(a);
                Recognition max = detections[0];
                nmsList.add(max);
                pq.clear();

                for (int j = 1; j < detections.length; j++) {
                    Recognition detection = detections[j];
                    RectF b = detection.getLocation();
                    if (box_iou(max.getLocation(), b) < mNmsThresh) {
                        pq.add(detection);
                    }
                }
            }
        }
        return nmsList;
    }

    public class Recognition {
        /**
         * A unique identifier for what has been recognized. Specific to the class, not the instance of
         * the object.
         */
        private final String id;

        /**
         * Display name for the recognition.
         */
        private final String title;

        /**
         * A sortable score for how good the recognition is relative to others. Higher should be better.
         */
        private final Float confidence;

        /**
         * Optional location within the source image for the location of the recognized object.
         */
        private RectF location;

        private int detectedClass;

        public Recognition(
                final String id, final String title, final Float confidence, final RectF location) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
        }

        public Recognition(final String id, final String title, final Float confidence, final RectF location, int detectedClass) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
            this.detectedClass = detectedClass;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return new RectF(location);
        }

        public void setLocation(RectF location) {
            this.location = location;
        }

        public int getDetectedClass() {
            return detectedClass;
        }

        public void setDetectedClass(int detectedClass) {
            this.detectedClass = detectedClass;
        }

        @Override
        public String toString() {
            String resultString = "";
            if (id != null) {
                resultString += "[" + id + "] ";
            }

            if (title != null) {
                resultString += title + " ";
            }

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f);
            }

            if (location != null) {
                resultString += location + " ";
            }

            return resultString.trim();
        }
    }

    public void close() {
        model.close();
    }
}