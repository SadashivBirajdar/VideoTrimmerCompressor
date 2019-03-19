package com.deep.videotrimmerexample

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.deep.videotrimmer.DeepVideoTrimmer
import com.deep.videotrimmer.interfaces.OnTrimVideoListener
import com.deep.videotrimmerexample.Constants.EXTRA_VIDEO_PATH

class VideoTrimmerActivity : BaseActivity(), OnTrimVideoListener {

  private var isActive = false
  private var dialog: CustomProgressDialog? = null
  private var mVideoTrimmer: DeepVideoTrimmer? = null
  private var tvCroppingMessage: TextView? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_video_trimmer)

    initProgressDialog()
    trimStarted()

    val extraIntent = intent
    var path: String? = ""

    if (extraIntent != null) {
      path = extraIntent.getStringExtra(EXTRA_VIDEO_PATH)
    }

    mVideoTrimmer = findViewById<View>(R.id.timeLine) as DeepVideoTrimmer
    tvCroppingMessage = findViewById<View>(R.id.tvCroppingMessage) as TextView

    if (mVideoTrimmer != null && path != null) {
      mVideoTrimmer!!.setOnTrimVideoListener(this)
      mVideoTrimmer!!.setVideoURI(Uri.parse(path))
    } else {
      showToastLong(getString(R.string.toast_cannot_retrieve_selected_video))
    }
    dialog?.dismiss()
  }

  private fun initProgressDialog() {
    dialog = CustomProgressDialog.newInstance("Processing, Please wait...")
  }

  override fun onResume() {
    super.onResume()
    isActive = true
  }

  override fun getResult(uri: Uri) {
    runOnUiThread {
      tvCroppingMessage?.visibility = View.GONE
      if (isActive && dialog != null && dialog!!.isVisible) {
        dialog?.dismiss()
      }
    }
    Constants.croppedVideoURI = uri.toString()
    val intent = Intent()
    intent.data = uri
    setResult(Activity.RESULT_OK, intent)
    finish()
  }

  override fun trimStarted() {
    dialog?.show(supportFragmentManager, CustomProgressDialog.TAG)
  }

  override fun onBackPressed() {
    cancelAction()
  }

  override fun cancelAction() {
    mVideoTrimmer?.destroy()
    runOnUiThread {
      tvCroppingMessage?.visibility = View.GONE
      if (dialog != null && dialog!!.isVisible)
        dialog!!.dismiss()
    }
    finish()
  }

  override fun onStop() {
    super.onStop()
    isActive = false
    if (dialog != null && dialog!!.isVisible) {
      dialog?.dismiss()
    }
  }
}
