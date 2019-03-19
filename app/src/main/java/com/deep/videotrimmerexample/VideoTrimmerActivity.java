package com.deep.videotrimmerexample;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import com.deep.videotrimmer.DeepVideoTrimmer;
import com.deep.videotrimmer.interfaces.OnTrimVideoListener;

import static com.deep.videotrimmerexample.Constants.EXTRA_VIDEO_PATH;

public class VideoTrimmerActivity extends BaseActivity implements OnTrimVideoListener {
  ViewDataBinding mBinder;
  private DeepVideoTrimmer mVideoTrimmer;
  TextView tvCroppingMessage;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinder = DataBindingUtil.setContentView(this, R.layout.activity_video_trimmer);

    Intent extraIntent = getIntent();
    String path = "";

    if (extraIntent != null) {
      path = extraIntent.getStringExtra(EXTRA_VIDEO_PATH);
    }

    mVideoTrimmer = ((DeepVideoTrimmer) findViewById(R.id.timeLine));
    tvCroppingMessage = (TextView) findViewById(R.id.tvCroppingMessage);

    if (mVideoTrimmer != null && path != null) {
      mVideoTrimmer.setOnTrimVideoListener(this);
      mVideoTrimmer.setVideoURI(Uri.parse(path));
    } else {
      showToastLong(getString(R.string.toast_cannot_retrieve_selected_video));
    }
  }

  @Override
  public void getResult(final Uri uri) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        tvCroppingMessage.setVisibility(View.GONE);
      }
    });
    Constants.croppedVideoURI = uri.toString();
    Intent intent = new Intent();
    intent.setData(uri);
    setResult(RESULT_OK, intent);
    finish();
  }

  @Override
  public void cancelAction() {
    mVideoTrimmer.destroy();
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        tvCroppingMessage.setVisibility(View.GONE);
      }
    });
    finish();
  }
}
