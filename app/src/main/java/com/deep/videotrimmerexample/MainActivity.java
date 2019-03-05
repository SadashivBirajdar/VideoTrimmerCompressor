package com.deep.videotrimmerexample;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.deep.videotrimmerexample.databinding.ActivityMainBinding;
import com.iceteck.silicompressorr.FileUtils;
import com.iceteck.silicompressorr.SiliCompressor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

import static com.deep.videotrimmerexample.Constants.EXTRA_VIDEO_PATH;

public class MainActivity extends BaseActivity implements View.OnClickListener {
  ActivityMainBinding mBinder;
  public static final int PERMISSION_STORAGE = 100;
  private final int REQUEST_VIDEO_TRIMMER_RESULT = 342;

  private final int REQUEST_VIDEO_TRIMMER = 0x12;
  private File thumbFile;
  private String selectedVideoName = null;
  private RequestOptions simpleOptions;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinder = DataBindingUtil.setContentView(this, R.layout.activity_main);
    setUpToolbar("Video Trimmer Example");
    mBinder.btnSelectVideo.setOnClickListener(this);
    simpleOptions = new RequestOptions().centerCrop().placeholder(R.color.blackOverlay).error(R.color.blackOverlay).diskCacheStrategy(DiskCacheStrategy.RESOURCE);
  }

  @Override
  public void onClick(View view) {
    switch (view.getId()) {
      case R.id.btnSelectVideo:
        checkForPermission();
        break;
    }
  }

  private void checkForPermission() {
    requestAppPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, PERMISSION_STORAGE, new BaseActivity.setPermissionListener() {
      @Override
      public void onPermissionGranted(int requestCode) {
        selectVideoDialog();
      }

      @Override
      public void onPermissionDenied(int requestCode) {
        showSnackbar(mBinder.getRoot(), getString(R.string.critical_permission_denied), Snackbar.LENGTH_INDEFINITE, getString(R.string.allow), new OnSnackbarActionListener() {
          @Override
          public void onAction() {
            checkForPermission();
          }
        });
      }

      @Override
      public void onPermissionNeverAsk(int requestCode) {
        showPermissionSettingDialog(getString(R.string.permission_gallery_camera));
      }
    });
  }

  private void selectVideoDialog() {
    new VideoPicker(this) {
      @Override
      protected void onCameraClicked() {
        openVideoCapture();
      }

      @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
      @Override
      protected void onGalleryClicked() {
        Intent intent = new Intent();
        intent.setTypeAndNormalize("video/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_video)), REQUEST_VIDEO_TRIMMER);
      }
    }.show();
  }

  private void openVideoCapture() {
    Intent videoCapture = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
    startActivityForResult(videoCapture, REQUEST_VIDEO_TRIMMER);
  }

  private void startTrimActivity(@NonNull Uri uri) {
    Intent intent = new Intent(this, VideoTrimmerActivity.class);
    intent.putExtra(EXTRA_VIDEO_PATH, FileUtils.getPath(this, uri));
    startActivityForResult(intent, REQUEST_VIDEO_TRIMMER_RESULT);
  }

  private File getFileFromBitmap(Bitmap bmp) {
    /*//create a file to write bitmap data*/
    thumbFile = new File(this.getCacheDir(), "thumb_" + selectedVideoName + ".png");
    try {
      thumbFile.createNewFile();
    } catch (IOException e) {
      e.printStackTrace();
    }
    /*//Convert bitmap to byte array*/
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    bmp.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG*/, bos);
    byte[] bitmapdata = bos.toByteArray();
    /*//write the bytes in file*/
    try {
      FileOutputStream fos = new FileOutputStream(thumbFile);
      fos.write(bitmapdata);
      fos.flush();
      fos.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return thumbFile;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == Activity.RESULT_OK) {
      switch (requestCode) {
        case REQUEST_VIDEO_TRIMMER:
          final Uri selectedUri = data.getData();
          if (selectedUri != null) {
            startTrimActivity(selectedUri);
          } else {
            showToastShort(getString(R.string.toast_cannot_retrieve_selected_video));
          }
          break;
        case REQUEST_VIDEO_TRIMMER_RESULT:
          final Uri selectedVideoUri = data.getData();

          if (selectedVideoUri != null) {
            selectedVideoName = data.getData().getLastPathSegment();
            //  Bitmap thumb = ThumbnailUtils.createVideoThumbnail(selectedVideoUri.getPath(), MediaStore.Images.Thumbnails.FULL_SCREEN_KIND);

            //  Glide.with(this).load(getFileFromBitmap(thumb)).apply(simpleOptions).into(mBinder.selectedVideoThumb);

            //create destination directory
            File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + "/Silicompressor/videos");
            //  String path = FileUtils.getRealPath(this, selectedVideoUri);
            String videoFilePath = selectedVideoUri.getPath();
            //compress and output new video specs
            if (f.mkdirs() || f.isDirectory()) {
              new VideoCompressAsyncTask(this).execute(videoFilePath, f.getPath());
            }
          } else {
            showToastShort(getString(R.string.toast_cannot_retrieve_selected_video));
          }
          break;
      }
    }
  }

  @SuppressLint("StaticFieldLeak")
  class VideoCompressAsyncTask extends AsyncTask<String, String, String> {

    Context mContext;

    public VideoCompressAsyncTask(Context context) {
      mContext = context;
    }

    @Override
    protected void onPreExecute() {
      super.onPreExecute();
      mBinder.compressionMsg.setVisibility(View.VISIBLE);
      mBinder.picDescription.setVisibility(View.GONE);
    }

    @Override
    protected String doInBackground(String... paths) {
      String filePath = null;
      try {

        filePath = SiliCompressor.with(mContext).compressVideo(paths[0], paths[1]);
      } catch (Exception e) {
        e.printStackTrace();
      }
      return filePath;
    }

    @Override
    protected void onPostExecute(String compressedFilePath) {
      super.onPostExecute(compressedFilePath);
      File imageFile = new File(compressedFilePath);
      float length = imageFile.length() / 1024f; // Size in KB
      String value;
      if (length >= 1024) {
        value = length / 1024f + " MB";
      } else {
        value = length + " KB";
      }
      String text = String.format(Locale.US, "%s\nName: %s\nSize: %s", getString(R.string.video_compression_complete), imageFile.getName(), value);
      mBinder.compressionMsg.setVisibility(View.GONE);
      mBinder.picDescription.setVisibility(View.VISIBLE);
      mBinder.picDescription.setText(text);
      Log.i("Silicompressor", "Path: " + compressedFilePath);
    }
  }
}
