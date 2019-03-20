package com.deep.videotrimmer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import com.deep.videotrimmer.interfaces.OnProgressVideoListener;
import com.deep.videotrimmer.interfaces.OnRangeSeekBarListener;
import com.deep.videotrimmer.interfaces.OnTrimVideoListener;
import com.deep.videotrimmer.utils.BackgroundExecutor;
import com.deep.videotrimmer.utils.TrimVideoUtils;
import com.deep.videotrimmer.utils.UiThreadExecutor;
import com.deep.videotrimmer.view.ProgressBarView;
import com.deep.videotrimmer.view.RangeSeekBarView;
import com.deep.videotrimmer.view.TimeLineView;
import java.io.File;
import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

public class DeepVideoTrimmer extends FrameLayout
    implements MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, SeekBar.OnSeekBarChangeListener, OnRangeSeekBarListener, OnProgressVideoListener {

  private static final String TAG = DeepVideoTrimmer.class.getSimpleName();
  private static final int MIN_TIME_FRAME = 1000;

  private SeekBar mHolderTopView;
  private RangeSeekBarView mRangeSeekBarView;
  private FrameLayout mVideoLayout;
  private VideoView mVideoView;
  private ImageView mPlayView;
  private TextView mTextSize;
  private TextView mTextTimeFrame;
  private TextView mTextTime;
  private TimeLineView mTimeLineView;
  private FloatingActionButton mSendVideo;

  private Uri mSrc;
  private String mFinalPath;

  private List<OnProgressVideoListener> mListeners;
  private OnTrimVideoListener mOnTrimVideoListener;

  private final static int DEFAULT_VIDEO_WIDTH = 640;
  private final static int DEFAULT_VIDEO_HEIGHT = 360;
  private float compressionRatio = (long) (20 * 1024);  // 20 MB
  private int mDuration = 0;
  private int maxFileSize = 15 * 1024;  // 15 MB
  private int mTimeVideo = 0;
  private int mStartPosition = 0;
  private int mEndPosition = 0;
  private long mOriginSizeFile;
  private boolean mResetSeekBar = true;
  @NonNull
  private final MessageHandler mMessageHandler = new MessageHandler(this);
  private static final int SHOW_PROGRESS = 2;
  private boolean letUserProceed;
  private GestureDetector mGestureDetector;
  private int initialLength;
  @NonNull
  private final GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {
    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
      if (mVideoView.isPlaying()) {
        mPlayView.setVisibility(View.VISIBLE);
        mMessageHandler.removeMessages(SHOW_PROGRESS);
        mVideoView.pause();
      } else {
        mPlayView.setVisibility(View.GONE);

        if (mResetSeekBar) {
          mResetSeekBar = false;
          mVideoView.seekTo(mStartPosition);
        }

        mMessageHandler.sendEmptyMessage(SHOW_PROGRESS);
        mVideoView.start();
      }
      return true;
    }
  };

  @NonNull
  private final OnTouchListener mTouchListener = new OnTouchListener() {
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, @NonNull MotionEvent event) {
      mGestureDetector.onTouchEvent(event);
      return true;
    }
  };

  public DeepVideoTrimmer(@NonNull Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public DeepVideoTrimmer(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context);
  }

  @SuppressLint("ClickableViewAccessibility")
  private void init(Context context) {

    LayoutInflater.from(context).inflate(R.layout.view_time_line, this, true);

    mHolderTopView = findViewById(R.id.handlerTop);
    ProgressBarView progressVideoView = findViewById(R.id.timeVideoView);
    mRangeSeekBarView = findViewById(R.id.timeLineBar);
    mVideoLayout = findViewById(R.id.layout_surface_view);
    mVideoView = findViewById(R.id.video_loader);
    mPlayView = findViewById(R.id.icon_video_play);
    mTextSize = findViewById(R.id.textSize);
    mTextTimeFrame = findViewById(R.id.textTimeSelection);
    mTextTime = findViewById(R.id.textTime);
    mTimeLineView = findViewById(R.id.timeLineView);
    mSendVideo = findViewById(R.id.sendVideo);
    mSendVideo.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        startTrimming();
      }
    });

    mListeners = new ArrayList<>();
    mListeners.add(this);
    mListeners.add(progressVideoView);

    mHolderTopView.setMax(1000);
    mHolderTopView.setSecondaryProgress(0);

    mRangeSeekBarView.addOnRangeSeekBarListener(this);
    mRangeSeekBarView.addOnRangeSeekBarListener(progressVideoView);

    int marge = mRangeSeekBarView.getThumbs().get(0).getWidthBitmap();
    int widthSeek = mHolderTopView.getThumb().getMinimumWidth() / 2;

    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mHolderTopView.getLayoutParams();
    lp.setMargins(marge - widthSeek, 0, marge - widthSeek, 0);
    mHolderTopView.setLayoutParams(lp);

    lp = (RelativeLayout.LayoutParams) mTimeLineView.getLayoutParams();
    lp.setMargins(marge, 0, marge, 0);
    mTimeLineView.setLayoutParams(lp);

    lp = (RelativeLayout.LayoutParams) progressVideoView.getLayoutParams();
    lp.setMargins(marge, 0, marge, 0);
    progressVideoView.setLayoutParams(lp);

    mHolderTopView.setOnSeekBarChangeListener(this);

    mVideoView.setOnPreparedListener(this);
    mVideoView.setOnCompletionListener(this);
    mVideoView.setOnErrorListener(this);

    mGestureDetector = new GestureDetector(getContext(), mGestureListener);
    mVideoView.setOnTouchListener(mTouchListener);

    setDefaultDestinationPath();
  }

  private void startTrimming() {
    if (letUserProceed) {
      if (mStartPosition <= 0 && mEndPosition >= mDuration) {
        mOnTrimVideoListener.getResult(mSrc);
      } else {
        mPlayView.setVisibility(View.VISIBLE);
        mVideoView.pause();

        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource(getContext(), mSrc);
        long METADATA_KEY_DURATION = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

        if (mSrc.getPath() == null) {
          return;
        }
        File file = new File(mSrc.getPath());

        if (mTimeVideo < MIN_TIME_FRAME) {

          if ((METADATA_KEY_DURATION - mEndPosition) > (MIN_TIME_FRAME - mTimeVideo)) {
            mEndPosition += (MIN_TIME_FRAME - mTimeVideo);
          } else if (mStartPosition > (MIN_TIME_FRAME - mTimeVideo)) {
            mStartPosition -= (MIN_TIME_FRAME - mTimeVideo);
          }
        }
        mOnTrimVideoListener.trimStarted();
        startTrimVideo(file, mFinalPath, mStartPosition, mEndPosition, mOnTrimVideoListener);
      }
    } else {
      Toast.makeText(getContext(), "Please trim your video less than 15 MB of size", Toast.LENGTH_SHORT).show();
    }
  }

  public void setVideoURI(final Uri videoURI) {
    mSrc = videoURI;
    initMediaData();
    mVideoView.setVideoURI(mSrc);
    mVideoView.requestFocus();
    mTimeLineView.setVideo(mSrc);
  }

  public void setDestinationPath(final String finalPath) {
    mFinalPath = finalPath + File.separator;
    Log.d(TAG, "Setting custom path " + mFinalPath);
  }

  private void initMediaData() {
    if (mSrc.getPath() == null) {
      return;
    }
    File file = new File(mSrc.getPath());
    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    retriever.setDataSource(file.getPath());
    String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
    String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
    int originalWidth = Integer.valueOf(width);
    int originalHeight = Integer.valueOf(height);
    Log.i(TAG, "checkCompressionRatio: originalWidth = " + originalWidth + " originalHeight = " + originalHeight);
    if (originalWidth <= DEFAULT_VIDEO_WIDTH || originalHeight <= DEFAULT_VIDEO_HEIGHT) {
      compressionRatio = (long) (1024); // 1 MB - no compression
    }
    String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
    int totalDuration = Integer.valueOf(duration) / 1000;

    long originalLength = file.length();
    long totalFileSizeInKB = (originalLength / 1024);
    int compressedTotalSize = (int) getCompressedSize(totalFileSizeInKB);
    initialLength = totalDuration;
    if (compressedTotalSize <= maxFileSize) {
      mStartPosition = 0;
      mEndPosition = totalDuration * 1000;
      getSizeFile(false);
    } else {
      int newDuration = (int) Math.ceil((float) maxFileSize * totalDuration / compressedTotalSize);
      int maxDuration = newDuration > 0 ? newDuration : totalDuration;

      mStartPosition = 0;
      mEndPosition = maxDuration * 1000;
      // set size for updated duration
      long newSize = ((compressedTotalSize / totalDuration) * maxDuration);
      newSize = (long) (newSize * compressionRatio);
      setVideoSize(newSize / 1024);
    }
    Log.i(TAG, "checkCompressionRatio: compressionRatio = " + compressionRatio);
  }

  private void setDefaultDestinationPath() {
    File folder = Environment.getExternalStorageDirectory();
    mFinalPath = folder.getPath() + File.separator;
    Log.d(TAG, "Setting default path " + mFinalPath);
  }

  @Override
  public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    int duration = (int) ((mDuration * progress) / 1000L);

    if (fromUser) {
      if (duration < mStartPosition) {
        setProgressBarPosition(mStartPosition);
        duration = mStartPosition;
      } else if (duration > mEndPosition) {
        setProgressBarPosition(mEndPosition);
        duration = mEndPosition;
      }
      setTimeVideo(duration);
    }
  }

  @Override
  public void onStartTrackingTouch(SeekBar seekBar) {
    mMessageHandler.removeMessages(SHOW_PROGRESS);
    mVideoView.pause();
    mPlayView.setVisibility(View.VISIBLE);
    updateProgress(false);
  }

  @Override
  public void onStopTrackingTouch(@NonNull SeekBar seekBar) {
    mMessageHandler.removeMessages(SHOW_PROGRESS);
    mVideoView.pause();
    mPlayView.setVisibility(View.VISIBLE);

    int duration = (int) ((mDuration * seekBar.getProgress()) / 1000L);
    mVideoView.seekTo(duration);
    setTimeVideo(duration);
    updateProgress(false);
  }

  @Override
  public void onPrepared(@NonNull MediaPlayer mp) {
 /*        Adjust the size of the video
         so it fits on the screen*/
    int videoWidth = mp.getVideoWidth();
    int videoHeight = mp.getVideoHeight();
    float videoProportion = (float) videoWidth / (float) videoHeight;
    int screenWidth = mVideoLayout.getWidth();
    int screenHeight = mVideoLayout.getHeight();
    float screenProportion = (float) screenWidth / (float) screenHeight;
    ViewGroup.LayoutParams lp = mVideoView.getLayoutParams();

    if (videoProportion > screenProportion) {
      lp.width = screenWidth;
      lp.height = (int) ((float) screenWidth / videoProportion);
    } else {
      lp.width = (int) (videoProportion * (float) screenHeight);
      lp.height = screenHeight;
    }
    mVideoView.setLayoutParams(lp);

    mPlayView.setVisibility(View.VISIBLE);

    mDuration = mVideoView.getDuration();
    setSeekBarPosition();
    setTimeFrames();
    setTimeVideo(0);
    letUserProceed = getCroppedFileSize() < maxFileSize;
  }

  public int getMaxFileSize() {
    return maxFileSize;
  }

  public void setMaxFileSize(int maxFileSize) {
    this.maxFileSize = maxFileSize;
  }

  private void setSeekBarPosition() {

    mRangeSeekBarView.setThumbValue(0, 0f);
    mRangeSeekBarView.setThumbValue(1, (float) (mEndPosition * 100) / mDuration);

    setProgressBarPosition(mStartPosition);
    mVideoView.seekTo(mStartPosition);

    mTimeVideo = mDuration;
    mRangeSeekBarView.initMaxWidth();
  }

  private void startTrimVideo(@NonNull final File file, @NonNull final String dst, final int startVideo, final int endVideo, @NonNull final OnTrimVideoListener callback) {
    BackgroundExecutor.execute(new BackgroundExecutor.Task("", 0L, "") {
      @Override
      public void execute() {
        try {
          TrimVideoUtils.startTrim(file, dst, startVideo, endVideo, callback);
        } catch (final Throwable e) {
          Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
        }
      }
    });
  }

  private void setTimeFrames() {
    mTextTimeFrame.setText(String.format("%s - %s", stringForTime(mStartPosition), stringForTime(mEndPosition)));
  }

  private void setTimeVideo(int position) {
    String seconds = getContext().getString(R.string.short_seconds);
    mTextTime.setText(String.format("%s %s", stringForTime(position), seconds));
  }

  @Override
  public void onCreate(RangeSeekBarView rangeSeekBarView, int index, float value) {

  }

  @Override
  public void onSeek(RangeSeekBarView rangeSeekBarView, int index, float value) {
 /*        0 is Left selector
         1 is right selector*/
    switch (index) {
      case 0: {
        mStartPosition = (int) ((mDuration * value) / 100L);
        mVideoView.seekTo(mStartPosition);
        break;
      }
      case 1: {
        mEndPosition = (int) ((mDuration * value) / 100L);
        break;
      }
    }
    setProgressBarPosition(mStartPosition);

    setTimeFrames();
    getSizeFile(true);
    mTimeVideo = mEndPosition - mStartPosition;
    letUserProceed = getCroppedFileSize() < maxFileSize;
  }

  @Override
  public void onSeekStart(RangeSeekBarView rangeSeekBarView, int index, float value) {

  }

  @Override
  public void onSeekStop(RangeSeekBarView rangeSeekBarView, int index, float value) {
    mMessageHandler.removeMessages(SHOW_PROGRESS);
    mVideoView.pause();
    mPlayView.setVisibility(View.VISIBLE);
  }

  private String stringForTime(int timeMs) {
    int totalSeconds = timeMs / 1000;

    int seconds = totalSeconds % 60;
    int minutes = (totalSeconds / 60) % 60;
    int hours = totalSeconds / 3600;

    Formatter mFormatter = new Formatter();
    if (hours > 0) {
      return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
    } else {
      return mFormatter.format("%02d:%02d", minutes, seconds).toString();
    }
  }

  private void getSizeFile(boolean isChanged) {
    if (isChanged) {
      long initSize = getFileSize();
      long newSize;
      newSize = ((initSize / initialLength) * (mEndPosition - mStartPosition));
      setVideoSize(newSize / 1024);
    } else {
      if (mOriginSizeFile == 0 && mSrc.getPath() != null) {
        File file = new File(mSrc.getPath());
        mOriginSizeFile = file.length();
        long fileSizeInKB = mOriginSizeFile / 1024;
        setVideoSize(fileSizeInKB);
      }
    }
  }

  private void setVideoSize(long fileSizeInKB) {
    float compressedSize = getCompressedSize(fileSizeInKB);
    if (compressedSize > 1000) {
      float fileSizeInMB = compressedSize / 1024f;
      DecimalFormat df = new DecimalFormat("###.#");
      mTextSize.setText(String.format("%s %s", df.format(fileSizeInMB), getContext().getString(R.string.megabyte)));
    } else {
      mTextSize.setText(String.format("%s %s", (int) compressedSize, getContext().getString(R.string.kilobyte)));
    }
  }

  private float getCompressedSize(long fileSizeInKB) {
    if (compressionRatio == 1024.0) {
      return fileSizeInKB;
    }
    float estimatedCompressedFileSize;
    if (fileSizeInKB > compressionRatio) {
      estimatedCompressedFileSize = (fileSizeInKB / compressionRatio) * 1024;
    } else if (fileSizeInKB > 30) {
      estimatedCompressedFileSize = (fileSizeInKB / 40f);
    } else {
      estimatedCompressedFileSize = 1;
    }
    return estimatedCompressedFileSize; // in KB
  }

  private long getFileSize() {
    if (mSrc.getPath() == null) {
      return 0;
    }
    File file = new File(mSrc.getPath());
    mOriginSizeFile = file.length();
    return mOriginSizeFile / 1024;
  }

  private long getCroppedFileSize() {
    long initSize = getFileSize();
    long newSize;
    newSize = ((initSize / initialLength) * (mEndPosition - mStartPosition));
    long compressedSize = (long) getCompressedSize(newSize);
    return compressedSize / 1024;
  }

  public void setOnTrimVideoListener(OnTrimVideoListener onTrimVideoListener) {
    mOnTrimVideoListener = onTrimVideoListener;
  }

  @Override
  public void onCompletion(MediaPlayer mediaPlayer) {
    mVideoView.seekTo(0);
  }

  @Override
  public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
    return false;
  }

  private static class MessageHandler extends Handler {

    @NonNull
    private final WeakReference<DeepVideoTrimmer> mView;

    MessageHandler(DeepVideoTrimmer view) {
      mView = new WeakReference<>(view);
    }

    @Override
    public void handleMessage(Message msg) {
      DeepVideoTrimmer view = mView.get();
      if (view == null || view.mVideoView == null) {
        return;
      }

      view.updateProgress(true);
      if (view.mVideoView.isPlaying()) {
        sendEmptyMessageDelayed(0, 10);
      }
    }
  }

  private void updateProgress(boolean all) {
    if (mDuration == 0) return;

    int position = mVideoView.getCurrentPosition();
    if (all) {
      for (OnProgressVideoListener item : mListeners) {
        item.updateProgress(position, mDuration, (float) ((position * 100) / mDuration));
      }
    } else {
      mListeners.get(1).updateProgress(position, mDuration, (float) ((position * 100) / mDuration));
    }
  }

  @Override
  public void updateProgress(int time, int max, float scale) {
    if (mVideoView == null) {
      return;
    }

    if (time >= mEndPosition) {
      mMessageHandler.removeMessages(SHOW_PROGRESS);
      mVideoView.pause();
      mPlayView.setVisibility(View.VISIBLE);
      mResetSeekBar = true;
      return;
    }

    if (mHolderTopView != null) {
      //use long to avoid overflow
      setProgressBarPosition(time);
    }
    setTimeVideo(time);
  }

  private void setProgressBarPosition(int position) {
    if (mDuration > 0) {
      long pos = 1000L * position / mDuration;
      mHolderTopView.setProgress((int) pos);
    }
  }

  public void destroy() {
    BackgroundExecutor.cancelAll("", true);
    UiThreadExecutor.cancelAll("");
  }
}
