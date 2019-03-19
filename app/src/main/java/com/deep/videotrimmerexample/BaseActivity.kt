package com.deep.videotrimmerexample

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast


abstract class BaseActivity : AppCompatActivity() {

  private var shouldPerformDispatchTouch = true
  private var title: TextView? = null
  private var toolbar: Toolbar? = null
  private var snackbar: Snackbar? = null

  var permissionListener: SetPermissionListener? = null

  override fun onDestroy() {
    super.onDestroy()
    if (snackbar != null && snackbar!!.isShown) snackbar!!.dismiss()
  }

  fun showToastShort(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
  }

  fun showToastLong(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
  }

  fun showSnackbar(view: View?, msg: String, LENGTH: Int, action: String, actionListener: OnSnackbarActionListener?) {
    if (view == null) return
    snackbar = Snackbar.make(view, msg, LENGTH)
    snackbar!!.setActionTextColor(ContextCompat.getColor(this, R.color.colorAccent))
    if (actionListener != null) {
      snackbar!!.setAction(action) { view1 ->
        snackbar!!.dismiss()
        actionListener.onAction()
      }
    }
    val sbView = snackbar!!.view
    val textView = sbView.findViewById<View>(android.support.design.R.id.snackbar_text) as TextView
    textView.setTextColor(ContextCompat.getColor(applicationContext, R.color.white))
    snackbar!!.show()
  }

  fun setUpToolbar(strTitle: String) {
    setUpToolbarWithBackArrow(strTitle, false)
  }

  @JvmOverloads
  fun setUpToolbarWithBackArrow(strTitle: String, isBackArrow: Boolean = true) {
    toolbar = findViewById<View>(R.id.toolbar) as Toolbar
    setSupportActionBar(toolbar)
    val actionBar = supportActionBar
    if (actionBar != null) {
      actionBar.setDisplayShowTitleEnabled(false)
      actionBar.setDisplayHomeAsUpEnabled(isBackArrow)
      actionBar.setHomeAsUpIndicator(R.drawable.ic_vector_arrow_back_black)
      title = toolbar?.findViewById<View>(R.id.title) as TextView
      title?.text = strTitle
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      android.R.id.home -> onBackPressed()
    }
    return super.onOptionsItemSelected(item)
  }

  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    var ret = false
    try {
      val view = currentFocus
      ret = super.dispatchTouchEvent(event)
      if (shouldPerformDispatchTouch) {
        if (view is EditText) {
          val w = currentFocus
          val scrCords = IntArray(2)
          if (w != null) {
            w.getLocationOnScreen(scrCords)
            val x = event.rawX + w.left - scrCords[0]
            val y = event.rawY + w.top - scrCords[1]

            if (event.action == MotionEvent.ACTION_UP && (x < w.left || x >= w.right || y < w.top || y > w.bottom)) {
              val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
              imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
            }
          }
        }
      }
      return ret
    } catch (e: Exception) {
      return ret
    }

  }

  fun showPermissionSettingDialog(message: String) {
    val builder = AlertDialog.Builder(this)
    builder.setTitle(R.string.need_permission)
    builder.setMessage(message)
    builder.setPositiveButton(R.string.app_settings) { dialog, which ->
      val intent = Intent()
      intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
      intent.addCategory(Intent.CATEGORY_DEFAULT)
      intent.data = Uri.parse("package:$packageName")
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
      intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
      startActivity(intent)
      dialog.dismiss()
    }
    builder.setNegativeButton(R.string.cancel) { dialog, which -> dialog.dismiss() }
    builder.create().show()
  }

  fun requestAppPermissions(requestedPermissions: Array<String>, requestCode: Int, listener: SetPermissionListener) {
    this.permissionListener = listener
    var permissionCheck = PackageManager.PERMISSION_GRANTED
    for (permission in requestedPermissions) {
      permissionCheck += ContextCompat.checkSelfPermission(this, permission)
    }
    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, requestedPermissions, requestCode)
    } else {
      if (permissionListener != null) permissionListener!!.onPermissionGranted(requestCode)
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    for (permission in permissions) {
      if (ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
        if (permissionListener != null) permissionListener!!.onPermissionGranted(requestCode)
        break
      } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
        if (permissionListener != null) permissionListener!!.onPermissionDenied(requestCode)
        break
      } else {
        if (permissionListener != null) permissionListener!!.onPermissionNeverAsk(requestCode)
        break
      }
    }
  }

  interface SetPermissionListener {
    fun onPermissionGranted(requestCode: Int)

    fun onPermissionDenied(requestCode: Int)

    fun onPermissionNeverAsk(requestCode: Int)
  }

  companion object {

    init {
      AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    }
  }
}
