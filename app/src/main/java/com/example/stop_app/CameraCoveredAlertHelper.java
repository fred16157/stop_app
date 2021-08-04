package com.example.stop_app;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

public class CameraCoveredAlertHelper {
    public static float getSharpnessFromBitmap(Bitmap origin) {
        try {
            Mat sourceMatImage = new Mat();
            Mat destination = new Mat();
            Mat matGray = new Mat();


            Utils.bitmapToMat(origin, sourceMatImage);
            Mat kernel = new Mat(3, 3, CvType.CV_32F);
            kernel.put(0, 0, 0.0);
            kernel.put(0, 1, -1.0);
            kernel.put(0, 2, 0.0);

            kernel.put(1, 0, -1.0);
            kernel.put(1, 1, 4.0);
            kernel.put(1, 2, -1.0);

            kernel.put(2, 0, 0.0);
            kernel.put(2, 1, -1.0);
            kernel.put(2, 2, 0.0);

            Imgproc.cvtColor(sourceMatImage, matGray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.filter2D(matGray, destination, -1, kernel, new Point());
            MatOfDouble median = new MatOfDouble();
            MatOfDouble std = new MatOfDouble();
            Core.meanStdDev(destination, median, std);
            System.out.println((float) Math.pow(std.get(0, 0)[0], 2.0));

            return (float) Math.pow(std.get(0, 0)[0], 2.0);
        }catch (CvException e) {
            e.printStackTrace();
        }
        return 50; //default sharpness value
    }
}
