package com.deep.videotrimmerexample

import android.app.Dialog
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatDialogFragment
import android.view.LayoutInflater
import android.view.Window
import android.widget.ProgressBar
import android.widget.TextView

class CustomProgressDialog : AppCompatDialogFragment() {

  companion object {

    const val TAG = "CustomProgressDialog"
    private const val KEY_PROGRESS_TEXT = "progress_text"

    fun newInstance(title: String): CustomProgressDialog {
      val dialog = CustomProgressDialog()
      val bundle = Bundle()
      bundle.putString(KEY_PROGRESS_TEXT, title)
      dialog.arguments = bundle
      return dialog
    }
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    // Use the Builder class for convenient dialog construction

    val builder = AlertDialog.Builder(activity!!, R.style.dialogTheme)
    val view = LayoutInflater.from(activity).inflate(R.layout.progress_dialog, null, false)
    builder.setView(view)

    val progressBar = view.findViewById<ProgressBar>(R.id.progress)
    val message = view.findViewById<TextView>(R.id.message)

    val arguments = arguments!!
    val progressText = arguments.getString(KEY_PROGRESS_TEXT)
    message.text = progressText
    progressBar.indeterminateDrawable.setColorFilter(ContextCompat.getColor(context!!, R.color.colorAccent), PorterDuff.Mode.MULTIPLY)
    val dialog = builder.create()
    isCancelable = false
    dialog.setCanceledOnTouchOutside(false)
    if (dialog.window != null) {
      dialog.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
      dialog.window.requestFeature(Window.FEATURE_NO_TITLE)
    }
    return dialog
  }
}