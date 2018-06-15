package com.example.lw3065.camera2app;


import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;

import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import android.view.View;

import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private String TAG = getClass().getCanonicalName();
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private ImageView  iv_show;
    private CameraManager mCameraManager;
    private Handler childHandler, mainHandler;
    private String mCameraID;
    private ImageReader mImageReader;
    private FrameLayout cameraPreview;
    private MediaRecorder mMediaRecorder;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraDevice mCameraDevice;
    private static CameraCharacteristics cameraCharacteristics;
    private File mVideoPath;
    protected static CaptureRequest.Builder mPreviewRequestBuilder;
    private int iCamera2Status = 0;
    Rect focusRect;
    Button captureButton;
    Button changeCameraID;
    Button recordButton;
    Button chooseFromAlbum;
    Button previewButton;
    private float finger_space, current_finger_space;
    private float zoom_level = 1;
    private String fileRecordName = null;
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        captureButton = (Button)findViewById(R.id.capture_button);
        changeCameraID = (Button)findViewById(R.id.front_back_button);
        recordButton = (Button)findViewById(R.id.record_button);
        chooseFromAlbum = (Button)findViewById(R.id.album_button);
        previewButton = (Button) findViewById(R.id.preview_button);
        mCameraID = "" + CameraCharacteristics.LENS_FACING_FRONT;
        cameraPreview = (FrameLayout)findViewById(R.id.camera_preview);
        initView();


        changeCameraID.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeCamera();
                if(mCameraID == ("" + CameraCharacteristics.LENS_FACING_FRONT)){
                    mCameraID = "" + CameraCharacteristics.LENS_FACING_BACK;
                } else {
                    mCameraID = "" + CameraCharacteristics.LENS_FACING_FRONT;
                }

                initCamera2();
            }
        });


        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });

        recordButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if(0 == iCamera2Status) {
                    iCamera2Status = 1;
                    Log.e(TAG, "recordButton 1" );
                    takeRecord();
                    recordButton.setTextColor(Color.RED);
                }
                else {
                    iCamera2Status = 0;
                    Log.e(TAG, "recordButton 2" );
                    stopRecord();
                    recordButton.setTextColor(Color.BLACK);
                }
            }
        });

        chooseFromAlbum.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAlbum();
            }
        });

        previewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                closeCamera();
                initView();
                initCamera2();
            }
        });

        cameraPreview.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getPointerCount()>1){
                    Log.e(TAG, "X0: "+ event.getX(0)+ ", Y0: "+event.getY(0)+", fingerCount: "+event.getPointerCount());
                    Log.e(TAG, "X1: "+ event.getX(1)+ ", Y1: "+event.getY(1));
                    Log.e("calculateTapArea","getMaxZoom: "+ cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM));
                    updateZoomPreview(event);

                }
                else{
                    focusRect = calculateTapArea(event.getX(), event.getY(), cameraPreview.getWidth(), cameraPreview.getHeight());
                    updateFocusPreview();
                    Log.e(TAG, "X: "+ event.getX()+ ", Y: "+event.getY());
                }

                return  true;
            }
        });
    }

    @Override
    public void onClick(View v) {
        //takePreview();
    }

    private static Rect calculateTapArea(float x, float y, int width, int height){
        int focusAreaSize = 150;

        Rect rect = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

//        Rect cropRegion = mPreviewRequestBuilder.build().get(CaptureRequest.SCALER_CROP_REGION);
//        int cropWidth = cropRegion.width(), cropHeight = cropRegion.height();
        Log.e("calculateTapArea", "cropRegion.width: "+rect.width()+", cropRegion.height: "+rect.height()+", rect.right: "+rect.right+", rect.bottom: "+rect.bottom);
        int centerX = (int)(((y)*rect.right)/width);
        int centerY = (int)(((width-x)*rect.bottom)/height);

        Log.e("calculateTapArea", "centerX: "+centerX+", centerY: "+centerY);


        RectF rectF = new RectF(clamp(centerX-focusAreaSize, 0, rect.right),
                clamp(centerY-focusAreaSize, 0, rect.bottom),
                clamp(centerX+focusAreaSize, 0, rect.right),
                clamp(centerY+focusAreaSize, 0, rect.bottom));
        Log.e("calculateTapArea","left: "+ Math.round(rectF.left)+", top: "+Math.round(rectF.top)+", right: "+Math.round(rectF.right)+ ", bottom: "+ Math.round(rectF.bottom));
        return new Rect(Math.round(rectF.left),Math.round(rectF.top),Math.round(rectF.right),Math.round(rectF.bottom));
    }

    private static int clamp(int num, int min, int max){
        if(num > max) {
            return max;
        }
        if(num < min){
            return min;
        }
        return num;
    }

    private void  initView(){
        iv_show = (ImageView)findViewById(R.id.iv_show_camera2_activity);

        mSurfaceView = (SurfaceView)findViewById(R.id.surface_view_camera2_activity);

        mSurfaceView.setOnClickListener(this);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.setKeepScreenOn(true);
        mSurfaceHolder.setFixedSize(1280,720);


        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                initCamera2();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if(null != mCameraDevice) {
                    Log.e("surfaceDestroyed", "closeCamera");
                    closeCamera();
                }

            }
        });

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initCamera2(){
        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        childHandler = new Handler(handlerThread.getLooper());
        mainHandler = new Handler(getMainLooper());

        mImageReader = ImageReader.newInstance(1280,720, ImageFormat.JPEG,1);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                closeCamera();
                setPreviewGone();


                Image image = reader.acquireNextImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];

                buffer.get(bytes);
                final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if(bitmap != null) {
                    iv_show.setImageBitmap(bitmap);
                }
                saveToSDCard(bytes);
                image.close();

            }
        }, mainHandler);

        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try{
            ActivityCompat.requestPermissions(MainActivity.this, new String[] {Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE}, 1);

            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)!= PackageManager.PERMISSION_GRANTED){
                Log.d("initCamera2", "no permission_granted");
                ActivityCompat.requestPermissions(MainActivity.this, new String[] {Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
                return;
            }
            mCameraManager.openCamera(mCameraID, stateCallback, mainHandler);
            cameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraID);

        } catch (CameraAccessException e){
            e.printStackTrace();
        }

    }

    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            takePreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            closeCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Toast.makeText(MainActivity.this, "摄像头开启失败", Toast.LENGTH_SHORT).show();
        }
    };

    private void setPreviewGone(){
        mSurfaceView.setVisibility(View.INVISIBLE);
        iv_show.setVisibility(View.VISIBLE);
        cameraPreview.setVisibility(FrameLayout.GONE);
        captureButton.setVisibility(Button.GONE);
        changeCameraID.setVisibility(Button.GONE);
        recordButton.setVisibility(Button.GONE);
        chooseFromAlbum.setVisibility(Button.GONE);
        previewButton.setVisibility(Button.VISIBLE);
    }
    private void setPreviewVisible(){
        mSurfaceView.setVisibility(View.VISIBLE);
        iv_show.setVisibility(View.GONE);
        cameraPreview.setVisibility(FrameLayout.VISIBLE);
        captureButton.setVisibility(Button.VISIBLE);
        changeCameraID.setVisibility(Button.VISIBLE);
        recordButton.setVisibility(Button.VISIBLE);
        chooseFromAlbum.setVisibility(Button.VISIBLE);
        previewButton.setVisibility(Button.GONE);
    }

    private void closeCamera(){
        if(null != mCameraDevice) {
            Log.e("closeCamera", "is closing");
            mCameraDevice.close();
            MainActivity.this.mCameraDevice = null;
        }else
            Log.e("closeCamera", "is null");
    }

    private void takePreview(){
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setPreviewVisible();
        try {
            // 创建预览需要的CaptureRequest.Builder
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // 将SurfaceView的surface作为CaptureRequest.Builder的目标
            mPreviewRequestBuilder.addTarget(mSurfaceHolder.getSurface());
            Log.e("TAG","createCaptureSession");
            // 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求
            mCameraDevice.createCaptureSession(Arrays.asList(mSurfaceHolder.getSurface(), mImageReader.getSurface()), new CameraCaptureSession.StateCallback() // ③
            {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    if (null == mCameraDevice) {
                        Log.e("createCaptureSession", "null == mCameraDevice");
                        return;
                    }
                    // 当摄像头已经准备好时，开始显示预览
                    mCameraCaptureSession = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "配置失败", Toast.LENGTH_SHORT).show();
                } }, childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void closePreviewSession(){

        try{
            mCameraCaptureSession.abortCaptures();
        }catch(CameraAccessException e){
            e.printStackTrace();
        }

        mCameraCaptureSession.close();
        mCameraCaptureSession = null;

    }



    private void takePicture(){
        if(mCameraDevice == null) return;

        final CaptureRequest.Builder captureRequestBuilder;
        try{
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();

            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            CaptureRequest mCaptureRequest = captureRequestBuilder.build();
            mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.capture(mCaptureRequest, null, childHandler);

        }catch(CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void setUpMediaRecord() throws  IOException{
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mVideoPath = getOutputMediaFile();
        mMediaRecorder.setOutputFile(mVideoPath.getAbsolutePath());
        mMediaRecorder.setVideoEncodingBitRate(10*1024*10240);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(1280,720);

        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        mMediaRecorder.setOrientationHint(ORIENTATIONS.get(rotation));
        mMediaRecorder.prepare();

    }

    private void takeRecord(){
        if(mCameraDevice == null) return;

        try{
            mMediaRecorder = new MediaRecorder();
            closePreviewSession();
            fileRecordName = null;
            setUpMediaRecord();

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);


            Surface previewSurface = mSurfaceHolder.getSurface();
            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            mPreviewRequestBuilder.addTarget(previewSurface);
            Surface recordSurface = mMediaRecorder.getSurface();
            surfaces.add(recordSurface);
            mPreviewRequestBuilder.addTarget(recordSurface);

            Log.e(TAG, "createCaptureSession" );
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mCameraCaptureSession = session;
                    updatePreview();
                    Toast.makeText(MainActivity.this, "start record video success", Toast.LENGTH_SHORT).show();
                    mMediaRecorder.start();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(MainActivity.this, "配置失败", Toast.LENGTH_SHORT).show();
                }
            }, childHandler);
        }catch (CameraAccessException | IOException e){
            e.printStackTrace();
        }

    }

    private void stopRecord(){
        if(null != mMediaRecorder){
            closePreviewSession();

            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.setOnInfoListener(null);
            mMediaRecorder.setPreviewDisplay(null);
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
            if(null != fileRecordName){
                MediaScannerConnection.scanFile(this, new String[]{Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)+"/"+fileRecordName+".mp4"}, null, null);
            }
        }

        Toast.makeText(MainActivity.this, "stop record video", Toast.LENGTH_SHORT).show();
        takePreview();
    }

    protected void updatePreview(){
        if(null == mCameraDevice){
            Log.e("CameraActivity", "updatePreview error");
            return;
        }
        Log.e("CameraActivity", "updatePreview");
        // 自动对焦
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        // 打开闪光灯
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try{
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, childHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    protected void updateZoomPreview(MotionEvent event){
        float maxZoom = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        Rect m =cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        int action = event.getAction();


        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        current_finger_space = (float)Math.sqrt((double)(x*x + y*y));

        if(finger_space != 0){
            if(current_finger_space > finger_space && maxZoom > zoom_level){
                zoom_level++;
            }else if(current_finger_space<finger_space && zoom_level > 1){
                zoom_level--;
            }
            int minW = (int)(m.width()/maxZoom);
            int minH = (int)(m.height()/maxZoom);
            int difW = m.width()-minW;
            int difH = m.height()-minH;
            Log.e(TAG, "difW: "+ difW+ ", difH: "+difH);
            int cropW = difW/10*(int)zoom_level;
            int cropH = difH/10*(int)zoom_level;
            Log.e(TAG, "cropW: "+ cropW+ ", cropH: "+cropH);
            cropW -= cropW & 3;
            cropH -= cropH & 3;
            Log.e(TAG, "cropW: "+ cropW+ ", cropH: "+cropH);
            Rect zoom = new Rect(cropW, cropH, m.width()-cropW, m.height()-cropH);
            mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        }
        finger_space = current_finger_space;
        try{
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, childHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    protected void updateFocusPreview(){
        if(null == mCameraDevice){
            Log.e("CameraActivity", "updatePreview error");
            return;
        }
//        Log.e("CameraActivity", "updateFocusPreview");
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[] {new MeteringRectangle(focusRect, 1000)});
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[] {new MeteringRectangle(focusRect, 1000)});
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

        try{
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, childHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }

    }




    public void saveToSDCard(byte[] data){
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        String fileName = format.format(date)+".jpg";

//        File file =new File(this.getExternalFilesDir(null), fileName);
        File file =new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), fileName);
        try(FileOutputStream outputStream = new FileOutputStream(file)){
            outputStream.write(data);
            outputStream.flush();
            outputStream.close();
            Toast.makeText(MainActivity.this, "保存"+Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)+"/"+fileName, Toast.LENGTH_SHORT).show();
        }catch (Exception e){
            e.printStackTrace();
        }
        MediaScannerConnection.scanFile(this, new String[]{Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)+"/"+fileName}, null, null);
    }

    public File getOutputMediaFile(){
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        fileRecordName = format.format(date);
        File filepath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        if(!filepath.exists()){
            if(!filepath.mkdir()){
                Log.d("Camera2Activity", "failed to create directory");
                return null;
            }
        }

        File file = null;
        try{
            file = File.createTempFile(fileRecordName,".mp4", filepath);
        }catch (IOException e){
            e.printStackTrace();
        }

        return file;
    }

    private void openAlbum(){
        Intent intent = new Intent("android.intent.action.GET_CONTENT");
        intent.setType("image/*");
        startActivityForResult(intent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode){
            case 1:
                handleImageOnKitKat(data);
                break;
        }
    }

    @TargetApi(19)
    private void handleImageOnKitKat(Intent data){
        String imagePath = null;
        Uri uri = data.getData();
        Log.e(TAG, "uri: "+ uri);

        if(DocumentsContract.isDocumentUri(MainActivity.this, uri)){
            String docId = DocumentsContract.getDocumentId(uri);

            if("com.android.providers.media.documents".equals(uri.getAuthority()))
            {
                String id = docId.split(":")[1];
                String selection = MediaStore.Images.Media._ID + "=" + id;

                imagePath = getImagePath(MainActivity.this, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection);
            }
        }else if("content".equalsIgnoreCase(uri.getScheme())){

            imagePath = getImagePath(MainActivity.this, uri, null);
            Log.e(TAG, "imagePath is "+ imagePath);
        }else if("file".equalsIgnoreCase(uri.getScheme())){
            imagePath = uri.getPath();
            Log.e(TAG, "imagePath2 is "+ imagePath);
        }
        diaplayImage(imagePath);
    }

    private String getImagePath(Context context, Uri uri, String selection){
        String path = null;
        Cursor cursor = context.getContentResolver().query(uri, null, selection, null, null);
        if(null!= cursor){
            if(cursor.moveToFirst()){
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            }
            cursor.close();
        }
        return path;
    }

    private void diaplayImage(String imagePath){

        closeCamera();
        setPreviewGone();
        if(null != imagePath){
            Log.e("diaplayImage", "imagePath:"+imagePath);
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            iv_show.setImageBitmap(bitmap);

        }else{
            Toast.makeText(this, "failed to get image", Toast.LENGTH_SHORT).show();
        }
    }
}




