package com.deep.videotrimmerexample

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.support.design.widget.BottomSheetDialog
import android.view.View
import kotlinx.android.synthetic.main.dialog_video_picker.*


abstract class VideoPicker(context: Context) : BottomSheetDialog(context), View.OnClickListener {

  private var lastClickTime: Long = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.dialog_video_picker)

    camera.setOnClickListener(this)
    gallery.setOnClickListener(this)
  }

  override fun onClick(view: View) {
    preventDoubleClick(view)
    dismiss()
    when (view.id) {
      R.id.camera -> onCameraClicked()
      R.id.gallery -> onGalleryClicked()
    }
  }

  private fun preventDoubleClick(view: View) {
    /*// preventing double, using threshold of 1000 ms*/
    if (SystemClock.elapsedRealtime() - lastClickTime < 1000) {
      return
    }
    lastClickTime = SystemClock.elapsedRealtime()
  }

  protected abstract fun onCameraClicked()

  protected abstract fun onGalleryClicked()
}