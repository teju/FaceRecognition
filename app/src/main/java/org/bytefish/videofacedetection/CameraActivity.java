// Copyright (c) Philipp Wagner. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

package org.bytefish.videofacedetection;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;

import android.hardware.Camera;
import android.hardware.SensorManager;
import android.media.FaceDetector;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.util.SparseArray;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.darwin.viola.still.FaceDetectionListener;
import com.darwin.viola.still.Viola;
import com.darwin.viola.still.model.CropAlgorithm;
import com.darwin.viola.still.model.FaceDetectionError;
import com.darwin.viola.still.model.FaceOptions;
import com.darwin.viola.still.model.FacePortrait;
import com.darwin.viola.still.model.Result;
import com.google.android.gms.vision.Frame;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;


public class CameraActivity extends Activity
        implements SurfaceHolder.Callback , Camera.PictureCallback {

    public static final String TAG = CameraActivity.class.getSimpleName();
    public List<Rect> faceRects;

    private Camera mCamera;
    boolean mFaceDetectionAvailable = false;

    // We need the phone orientation to correctly draw the overlay:
    private int mOrientation;
    private int mOrientationCompensation;
    private OrientationEventListener mOrientationEventListener;

    // Let's keep track of the display rotation and orientation also:
    private int mDisplayRotation;
    private int mDisplayOrientation;

    // Holds the Face Detection result:
    private Camera.Face[] mFaces;

    // The surface view for the camera data
    private SurfaceView mView;

    // Draw rectangles and other fancy stuff:
    private FaceOverlayView mFaceView;

    // Log all errors:
    private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();

    /**
     * Sets the faces for the overlay view, so it can be updated
     * and the face overlays will be drawn again.
     */
    private Camera.FaceDetectionListener faceDetectionListener = new Camera.FaceDetectionListener() {
        @Override
        public void onFaceDetection(Camera.Face[] faces, Camera camera) {
            Log.d("onFaceDetection", "Number of Faces:" + faces.length);
            // Update the view now!
            mFaceView.setFaces(faces);
            if(faces.length > 0) {
                btncapture.setEnabled(true);
            } else {
                btncapture.setEnabled(false);
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // Do something after 5s = 5000ms
                          //  btncapture.performClick();
                        }
                    }, 1000);
                }
            });

           // captureImage();
        }
    };
    private ImageView imageView,res_imageView;
    private Button btncapture;
    private int mFaceWidth,mFaceHeight;
    private int mDisplayStyle;
    private Bitmap mFaceBitmap;
    private final int imagePickerIntentId = 1;
    private Viola viola;
    private Bitmap bitmap;
    private RelativeLayout rl_res_images;
    private File file;
    private PermissionHelper permissionHelper;
    private boolean safeToTakePicture = false;

    public void cropImage(){

    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        imageView = (ImageView)findViewById(R.id.imageView);
        res_imageView = (ImageView)findViewById(R.id.res_imageView);
        rl_res_images = (RelativeLayout)findViewById(R.id.rl_res_images);
        btncapture = (Button)findViewById(R.id.btncapture);
        Button btncrop = (Button) findViewById(R.id.btncrop);
        mView = (SurfaceView) findViewById(R.id.mView);
        // Now create the OverlayView:
        mFaceView = new FaceOverlayView(this);
        addContentView(mFaceView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        // Create and Start the OrientationListener:
        mOrientationEventListener = new SimpleOrientationEventListener(this);
        mOrientationEventListener.enable();
        prepareFaceCropper();
        permissionHelper = new PermissionHelper(this);
        requestStoragePermission();
        btncapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mView.getVisibility() == View.VISIBLE) {
                    captureImage();
                } else {
                    rl_res_images.setVisibility(View.GONE);
                    mView.setVisibility(View.VISIBLE);
                }
            }
        });
        btncrop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    bitmap = BitmapFactory.decodeFile(file.getPath(), options);
                    try {
                        bitmap = Util.modifyOrientation(bitmap, file.getPath());
                        imageView.setImageBitmap(bitmap);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                crop();
            }
        });

    }
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
    private void requestStoragePermission() {
        permissionHelper.setListener(permissionsListener);
        String[] requiredPermissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.CAMERA};
        permissionHelper.requestPermission(requiredPermissions, 100);
    }


    private final PermissionHelper.PermissionsListener permissionsListener = new PermissionHelper.PermissionsListener() {
        @Override
        public void onPermissionGranted(int request_code) {
            mView.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPermissionRejectedManyTimes(@NonNull List<String> rejectedPerms, int request_code, boolean neverAsk) {

        }
    };

    private void crop() {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bitmap = BitmapFactory.decodeFile(file.getPath(), options);
            try {
                bitmap = Util.modifyOrientation(bitmap, file.getPath());
                imageView.setImageBitmap(bitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        FaceOptions faceOption = new
                FaceOptions.Builder()
                .cropAlgorithm(CropAlgorithm.SQUARE)
                .setMinimumFaceSize(1)
                .enableDebug()
                .build();

        viola.detectFace(bitmap, faceOption);
    }

    private void prepareFaceCropper() {
        viola = new Viola(listener);
        viola.addAgeClassificationPlugin(this);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.po_single, options);
        imageView.setImageBitmap(bitmap);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        SurfaceHolder holder = mView.getHolder();
        holder.addCallback(this);
    }

    @Override
    protected void onPause() {
        mOrientationEventListener.disable();
        super.onPause();
    }

    @Override
    protected void onResume() {
        mOrientationEventListener.enable();
        super.onResume();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);

        try {
            mCamera.setPreviewDisplay(surfaceHolder);
        } catch (Exception e) {
            Log.e(TAG, "Could not preview the image.", e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        // We have no surface, return immediately:
        if (surfaceHolder.getSurface() == null) {
            return;
        }
        // Try to stop the current preview:
        try {
            mCamera.stopPreview();
            safeToTakePicture = false;

        } catch (Exception e) {
            // Ignore...
        }

        configureCamera(width, height);
        setDisplayOrientation();
        setErrorCallback();

        // Everything is configured! Finally start the camera preview again:
        mCamera.startPreview();
        if(mFaceDetectionAvailable) {
            mCamera.startFaceDetection();
            mCamera.setFaceDetectionListener(faceDetectionListener);

        }
        safeToTakePicture = true;
    }

    private void setErrorCallback() {
        mCamera.setErrorCallback(mErrorCallback);
    }

    private void setDisplayOrientation() {
        // Now set the display orientation:
        mDisplayRotation = Util.getDisplayRotation(CameraActivity.this);
        mDisplayOrientation = Util.getDisplayOrientation(mDisplayRotation, 0);

        mCamera.setDisplayOrientation(mDisplayOrientation);

        if (mFaceView != null) {
            mFaceView.setDisplayOrientation(mDisplayOrientation);
        }
    }

    private void configureCamera(int width, int height) {
        Camera.Parameters parameters = mCamera.getParameters();
        // Set the PreviewSize and AutoFocus:
        setOptimalPreviewSize(parameters, width, height);
        setAutoFocus(parameters);
        // And set the parameters:
        mFaceDetectionAvailable = parameters.getMaxNumDetectedFaces() > 0;

        mCamera.setParameters(parameters);
    }

    private void setOptimalPreviewSize(Camera.Parameters cameraParameters, int width, int height) {
        List<Camera.Size> previewSizes = cameraParameters.getSupportedPreviewSizes();
        float targetRatio = (float) width / height;
        Camera.Size previewSize = Util.getOptimalPreviewSize(this, previewSizes, targetRatio);
        cameraParameters.setPreviewSize(previewSize.width, previewSize.height);
    }

    private void setAutoFocus(Camera.Parameters cameraParameters) {
        cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mCamera.setPreviewCallback(null);
        mCamera.setFaceDetectionListener(null);
        mCamera.setErrorCallback(null);
        mCamera.release();
        mCamera = null;
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        mView.setVisibility(View.GONE);
        rl_res_images.setVisibility(View.VISIBLE);
        bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        imageView.setImageBitmap(bitmap);
        processImage();
        crop();

        //processImage(bitmap);
    }
    public void captureImage() {
        if (mCamera != null && safeToTakePicture) {
            mCamera.takePicture(null, null, this);
        }
    }

    private void processImage() {
        try {
            if(bitmap!=null){

                file=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)+"/dirr");
                if(!file.isDirectory()){
                    file.mkdir();
                }

                file=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)+"/dirr",System.currentTimeMillis()+".jpg");


                try
                {
                    FileOutputStream fileOutputStream=new FileOutputStream(file);
                    bitmap.compress(Bitmap.CompressFormat.JPEG,100, fileOutputStream);

                    fileOutputStream.flush();
                    fileOutputStream.close();
                }
                catch(IOException e){
                    e.printStackTrace();
                }
                catch(Exception exception)
                {
                    exception.printStackTrace();
                }

            }

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private final FaceDetectionListener listener = new FaceDetectionListener() {
        @Override
        public void onFaceDetected( Result result) {
            Log.d("onFaceDetected","count is "+result.getFaceCount());
            res_imageView.setImageBitmap(result.getFacePortraits().get(0).getFace());
        }

        @Override
        public void onFaceDetectionFailed(FaceDetectionError error, String message) {
            Log.d("onFaceDetectionFailed","error is "+error.toString() +" message " +message);

        }
    };

    /**
     * We need to react on OrientationEvents to rotate the screen and
     * update the views.
     */
    private class SimpleOrientationEventListener extends OrientationEventListener {

        public SimpleOrientationEventListener(Context context) {
            super(context, SensorManager.SENSOR_DELAY_NORMAL);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mOrientation = Util.roundOrientation(orientation, mOrientation);
            // When the screen is unlocked, display rotation may change. Always
            // calculate the up-to-date orientationCompensation.
            int orientationCompensation = mOrientation
                    + Util.getDisplayRotation(CameraActivity.this);
            if (mOrientationCompensation != orientationCompensation) {
                mOrientationCompensation = orientationCompensation;
                mFaceView.setOrientation(mOrientationCompensation);
            }
        }
    }
}