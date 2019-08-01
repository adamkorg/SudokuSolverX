package org.adamk.sudokusolverx;

import android.annotation.SuppressLint;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.core.Scalar;
import org.opencv.core.CvType;
import org.opencv.android.Utils;
import android.view.SurfaceView;
import android.util.Log;
import android.widget.Toast;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.graphics.Bitmap;
import com.googlecode.tesseract.android.TessBaseAPI;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    public static final String TAG = "SUDOKUSOLVERX";

    private enum DrawColour {
        RED,
        GREEN,
        BLUE,
        WHITE,
        BLACK
    }

    Rect mRectCurrentGrid;

    int[][] mScanBoardVals = new int[9][9];
    int[][] mCalcBoardVals = new int[9][9];
    boolean mIsGridValid = true;
    boolean mPauseScreen = false;
    Mat mSolvedImg;

    TessBaseAPI mTessBaseAPI;

    private CameraBridgeViewBase mCameraBridgeViewBase;

    //camera listener callback
    private BaseLoaderCallback mBaseLoaderCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.cameraViewer);  //findViewById(R.id.fullscreen_content);

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);

        isReadStoragePermissionGranted();
        isCameraPermissionGranted();

        initOcrLib();

        mCameraBridgeViewBase = (JavaCameraView) findViewById(R.id.cameraViewer);
        mCameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        mCameraBridgeViewBase.setCvCameraViewListener(this);

        mBaseLoaderCallback = new BaseLoaderCallback(this) {
            @Override
            public void onManagerConnected(int status) {
                switch (status) {
                    case LoaderCallbackInterface.SUCCESS:
                        Log.v(TAG, "Camera Loader interface success");
                        mCameraBridgeViewBase.enableView();
                        break;
                    default:
                        super.onManagerConnected(status);
                        break;
                }
            }
        };

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
    }

    @Override
    public void onCameraViewStopped() {
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat gray = inputFrame.gray();
        Mat dst = inputFrame.rgba();
        Mat processedImg = null;

        Mat bwIMG = new Mat();
        Imgproc.pyrDown(gray, bwIMG, new Size(gray.cols() / 2, gray.rows() / 2));
        Imgproc.pyrUp(bwIMG, bwIMG, gray.size());
        Imgproc.Canny(bwIMG, bwIMG, 0, 100);
        Imgproc.dilate(bwIMG, bwIMG, new Mat(), new Point(-1, 1), 1);
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();

        Mat hierMat = new Mat();
        Imgproc.findContours(bwIMG, contours, hierMat, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        for (MatOfPoint cnt : contours) {
            MatOfPoint2f curve = new MatOfPoint2f(cnt.toArray());
            MatOfPoint2f approxCurve = new MatOfPoint2f();
            Imgproc.approxPolyDP(curve, approxCurve, 0.02 * Imgproc.arcLength(curve, true), true);

            int numberVertices = (int) approxCurve.total();
            double contourArea = Imgproc.contourArea(cnt);

            if (Math.abs(contourArea) < 500000) {
                continue;
            }

            //Rectangle detected
            if (numberVertices >= 4 && numberVertices <= 6) {
                List<Double> cos = new ArrayList<>();

                for (int j = 2; j < numberVertices + 1; j++) {
                    cos.add(angle(approxCurve.toArray()[j % numberVertices], approxCurve.toArray()[j - 2], approxCurve.toArray()[j - 1]));
                }
                Collections.sort(cos);
                double mincos = cos.get(0);
                double maxcos = cos.get(cos.size() - 1);

                if (numberVertices == 4 && mincos >= -0.1 && maxcos <= 0.3) {
//                    setLabel(dst, "X", cnt);
//                    drawRect(dst, cnt, DrawColour.BLUE);
                    processedImg = doOcrOnGrid(dst, cnt);
                    DrawColour rectcol = mIsGridValid ? DrawColour.BLUE : DrawColour.RED;
                    drawRect(dst, cnt, rectcol);
                }
            }
        }

        return dst;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mCameraBridgeViewBase != null)
            mCameraBridgeViewBase.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(getApplicationContext(), "Cannot initialise OpenCV", Toast.LENGTH_SHORT).show();
        }
        else {
            mBaseLoaderCallback.onManagerConnected(BaseLoaderCallback.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mCameraBridgeViewBase != null)
            mCameraBridgeViewBase.disableView();
        closeOcrLib();
    }

    public  boolean isReadStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Permission is granted1");
                return true;
            } else {

                Log.v(TAG,"Permission is revoked1");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 3);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG,"Permission is granted1");
            return true;
        }
    }

    public boolean isCameraPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Camera Permission is granted1");
                return true;
            } else {
                Log.v(TAG,"Camera Permission is revoked1");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 3);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG,"Camera Permission is granted1");
            return true;
        }
    }

    private static double angle(Point pt1, Point pt2, Point pt0) {
        double dx1 = pt1.x - pt0.x;
        double dy1 = pt1.y - pt0.y;
        double dx2 = pt2.x - pt0.x;
        double dy2 = pt2.y - pt0.y;
        return (dx1 * dx2 + dy1 * dy2) / Math.sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2) + 1e-10);
    }

    private void setLabel(Mat im, String label, MatOfPoint contour) {
        int fontface = 0; //Core.FONT_HERSHEY_SIMPLEX;
        double scale = 3;//0.4;
        int thickness = 3;//1;
        int[] baseline = new int[1];
        Size text = Imgproc.getTextSize(label, fontface, scale, thickness, baseline);
        Rect r = Imgproc.boundingRect(contour);
        Point pt = new Point(r.x + ((r.width - text.width) / 2),r.y + ((r.height + text.height) / 2));
        Imgproc.putText(im, label, pt, fontface, scale, new Scalar(255, 0, 0), thickness);
    }

    private Scalar getScalarColour(DrawColour col) {
        Scalar sc;
        switch(col) {
            case RED: sc = new Scalar(255,0,0); break;
            case GREEN: sc = new Scalar(0,255,0); break;
            case BLUE: sc = new Scalar(0,0,255); break;
            case WHITE: sc = new Scalar(255,255,255); break;
            case BLACK:
            default: sc = new Scalar(0,0,0); break;
        }
        return sc;
    }

    private void drawRect(Mat im, MatOfPoint contour, DrawColour col) {
        Rect rect = Imgproc.boundingRect(contour);
        Scalar scol = getScalarColour(col);

        Imgproc.rectangle(im, rect.tl(), rect.br(), scol, 4);

        int mDetectedWidth = rect.width;
        int mDetectedHeight = rect.height;

        Log.v(TAG, "Rectangle width :"+mDetectedWidth+ " Rectangle height :"+mDetectedHeight);
    }

    private void showText(Mat fullImage, Rect r, String text, DrawColour col) {
        Scalar scol = getScalarColour(col);
        int fontface = 0; //Core.FONT_HERSHEY_SIMPLEX;
        double scale = 2;//0.4;
        int thickness = 2;//1;
        int[] baseline = new int[1];
        Size sz = Imgproc.getTextSize(text, fontface, scale, thickness, baseline);
        Point pt = new Point(r.x,r.y);
        Imgproc.putText(fullImage, text, pt, fontface, scale, scol, thickness);
    }

    private void initOcrLib() {
        String DATA_PATH = "/storage/emulated/0/Download/tess"; // "/eng.traineddata";
        try{
            mTessBaseAPI = new TessBaseAPI();
            mTessBaseAPI.init(DATA_PATH,"eng");
            mTessBaseAPI.setVariable("tessedit_char_whitelist", "0123456789");
        }catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
    }
    private void closeOcrLib() {
        try{
            mTessBaseAPI.end();
        }catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
    }

    private String getOcrText(Bitmap bitmap){
        String retStr = "No result";
        try{
            mTessBaseAPI.setVariable("tessedit_char_whitelist", "0123456789");
            mTessBaseAPI.setImage(bitmap);
            retStr = mTessBaseAPI.getUTF8Text();
        }catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
        return retStr;
    }

    private Mat prepareImageForOcr(Mat rgb)
    {
        Mat ret = rgb.clone();
        Mat kernel = Mat.ones(new Size(2, 2), CvType.CV_8U);

        int cols_to_remove = (int) (ret.cols() * 0.05);
        int rows_to_remove = (int) (ret.rows() * 0.05);
        Mat retfinal = ret.submat(rows_to_remove, ret.rows() - rows_to_remove, cols_to_remove, ret.cols() - cols_to_remove);
        Imgproc.erode(retfinal, retfinal, kernel, new Point(), 1);

        return retfinal;
    }

    private Mat doOcrOnGrid(Mat fullImage, MatOfPoint contour) {
        Rect rect = Imgproc.boundingRect(contour);
        mRectCurrentGrid = rect;
        boolean gridValid = false;

        int[][] prevBoardVals = mScanBoardVals;

        Mat greyImg = fullImage.clone();
        Imgproc.cvtColor(greyImg, greyImg, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.blur(greyImg, greyImg, new Size(3.d, 3.d));
        Imgproc.adaptiveThreshold(greyImg, greyImg, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 15, 4);

        //remove grid
        for (int i=0; i<20; i++) {
            double x = rect.x-10+i;
            double y = rect.y-10+i;
            if (x < 0 || y < 0)
                continue;
            Point currentPoint = new Point(x, y);
            Scalar BLACK = new Scalar(0);
            Imgproc.floodFill(greyImg, new Mat(), currentPoint, BLACK);
        }

        //extract first square
        int cellHeight = rect.height / 9;
        int cellWidth = rect.width / 9;
        for (int i=0; i<9; i++) {
            for (int j=0; j<9; j++) {
                Rect sq = new Rect(rect.x + (j * cellWidth) + 15, rect.y + (i * cellHeight) + 15, cellWidth - 20, cellHeight - 20);
                Imgproc.rectangle(fullImage, sq.tl(), sq.br(), new Scalar(255, 0, 0, .8), 4);

                Mat cropedMatOrig = new Mat(greyImg, sq);
                Mat cropedMat = prepareImageForOcr(cropedMatOrig);
                Bitmap Cropedimage = Bitmap.createBitmap(cropedMat.cols(), cropedMat.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(cropedMat, Cropedimage);

                String celltext = getOcrText(Cropedimage);
                showText(fullImage, sq, celltext, DrawColour.GREEN);

                if (!celltext.equals("") && celltext.length()==1) {
                    int val = Integer.parseInt(celltext);
                    mScanBoardVals[i][j] = val;
                    gridValid = true;
                }
                else
                    mScanBoardVals[i][j] = 0;
            }
        }

        //boolean gridChanged = !java.util.Arrays.deepEquals(prevBoardVals, scanBoardVals);

        if (gridValid) { //&& gridChanged) {
            for (int i = 0; i < 9; i++) {
                for (int j = 0; j < 9; j++) {
                    if (!isCellValid(mScanBoardVals, i, j))
                        gridValid = false;
                }
            }
        }

        mIsGridValid = gridValid;

        if (mIsGridValid) {
            copyBoard(mScanBoardVals, mCalcBoardVals);
            solveSudoku(mCalcBoardVals);
            showCalcedGridVals(fullImage, rect);
            mSolvedImg = fullImage.clone();
        }

        return greyImg;
    }

    void copyBoard(int[][] src, int[][] dest) {
        for (int i=0; i<9; i++) {
            for (int j=0; j<9; j++) {
                dest[i][j] = src[i][j];
            }
        }
    }

    void showCalcedGridVals(Mat img, Rect gridRect) {
        int cellHeight = gridRect.height / 9;
        int cellWidth = gridRect.width / 9;
        for (int i=0; i<9; i++) {
            for (int j = 0; j < 9; j++) {
                if (mScanBoardVals[i][j] != 0)
                    continue;
                String celltext = Integer.toString(mCalcBoardVals[i][j]);
                Rect sq = new Rect(gridRect.x + (j * cellWidth) + 15, gridRect.y + (i * cellHeight) + 15, cellWidth - 20, cellHeight - 20);
                showText(img, sq, celltext, DrawColour.BLUE);
            }
        }
    }

    boolean isCellValid(int[][] v, int row, int col) {
        //exception for unset cells
        if (v[row][col] == 0)
            return true;

        //check row
        for (int j=0; j<v[row].length; j++) {
            if (j != col && v[row][j] == v[row][col])
                return false;
        }

        //check column
        for (int i=0; i<v.length; i++) {
            if (i != row && v[i][col] == v[row][col])
                return false;
        }

        //check square
        int box_r = row / 3;
        int box_c = col / 3;
        for (int i=box_r*3; i<(box_r*3)+3; i++) {
            for (int j=box_c*3; j<(box_c*3)+3; j++) {
                if (i != row && j != col && v[i][j] == v[row][col])
                    return false;
            }
        }

        return true;
    }

    //backtracking method
    boolean solveSudoku(int[][] board) {
        boolean backtrack = false;
        int[][] v = new int[9][9];
        copyBoard(board, v);
        for (int i=0; i<v.length; i++) {
            for (int j=0; j<v[i].length; j++) {
                if (v[i][j] == 0) {
                    //attempt to set a value
                    v[i][j]++;
                    while (backtrack || !isCellValid(v, i, j)) {
                        if (++v[i][j] == 10) {
                            v[i][j] = 0;
                            //backtrack
                            //but only to squares that were not preset
                            do {
                                if (j==0) {
                                    if (i ==0)
                                        return false;
                                    i--;
                                    j=8;
                                }
                                else {
                                    j--;
                                }
                            } while (board[i][j] != 0);
                            backtrack = true;
                        }
                        else
                            backtrack = false;
                    }
                }
            }
        }
        //board = v;
        copyBoard(v, board);
        return true;
    }


}
