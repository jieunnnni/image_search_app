package com.example.imagesearch_app

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.imagesearch_app.data.Repository
import com.example.imagesearch_app.data.model.PhotoResponse
import com.example.imagesearch_app.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        bindViews()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            fetchRandomPhotos()
        } else {
            requestWriteStoragePermission()
        }
    }

    private fun initViews() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        binding.recyclerView.adapter = PhotoAdapter()
    }

    private fun bindViews() {
        binding.searchEditText.setOnEditorActionListener { editText, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { // SEARCH ?????? ??????????????? ??????
                currentFocus?.let { view ->
                    // search ??? ????????? ????????? ???????????? ???
                    val inputMethodManager =
                        getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)

                    view.clearFocus() // search ??? ????????? ?????? ???????????? ???????????? ???
                }
                // editText ??? ?????? ??????, fetch ?????? ???????????? ??????????????? ?????????
                fetchRandomPhotos(editText.text.toString())
            }
            true
        }
        binding.refreshLayout.setOnRefreshListener {
            // ????????? ???????????? ????????? refresh ??????, ????????? ???????????? ?????? ?????? ???????????????
            fetchRandomPhotos(binding.searchEditText.text.toString())
        }
        (binding.recyclerView.adapter as? PhotoAdapter)?.onClickPhoto = { photo ->
            showDownloadPhotoConfirmationDialog(photo)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchRandomPhotos(query: String? = null) = scope.launch {
        try {
            Repository.getRandomPhotos(query)?.let { photos ->

                binding.errorDescriptionTextView.visibility = View.GONE
                (binding.recyclerView.adapter as? PhotoAdapter)?.apply {
                    this.photos = photos
                    notifyDataSetChanged()
                }
            }
            binding.recyclerView.visibility = View.VISIBLE
        } catch (exception: Exception) {
            binding.recyclerView.visibility = View.INVISIBLE
            binding.errorDescriptionTextView.visibility = View.VISIBLE
        } finally {
            binding.shimmerLayout.visibility = View.GONE
            binding.refreshLayout.isRefreshing = false
        }
    }

    private fun showDownloadPhotoConfirmationDialog(photo: PhotoResponse) {
        AlertDialog.Builder(this)
            .setMessage("??? ????????? ?????????????????????????")
            .setPositiveButton("??????") { dialog, _ ->
                downloadPhoto(photo.urls?.full)
                dialog.dismiss()
            }
            .setNegativeButton("??????") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun downloadPhoto(photoUrl: String?) {
        photoUrl ?: return

        Glide.with(this)
            .asBitmap()
            .load(photoUrl)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(
                object : CustomTarget<Bitmap>(SIZE_ORIGINAL, SIZE_ORIGINAL) {
                    @RequiresApi(Build.VERSION_CODES.M)
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) { // ??????????????? ??? ?????? ??????
                        saveBitmapToMediaStore(resource)

                        val wallpaperManager = WallpaperManager.getInstance(this@MainActivity)

                        val snackbar = Snackbar.make(
                            binding.root,
                            "???????????? ??????",
                            Snackbar.LENGTH_SHORT
                        )

                        if (wallpaperManager.isWallpaperSupported
                            && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                                    && wallpaperManager.isSetWallpaperAllowed)
                        ) {
                            snackbar.setAction("?????????????????? ??????") {
                                try {
                                    wallpaperManager.setBitmap(resource)
                                } catch (exception: Exception) {
                                    Snackbar.make(binding.root, "???????????? ?????? ??????", Snackbar.LENGTH_SHORT)
                                }
                            }
                            snackbar.duration = Snackbar.LENGTH_INDEFINITE
                        }
                        snackbar.show()
                    }

                    override fun onLoadStarted(placeholder: Drawable?) {
                        super.onLoadStarted(placeholder)
                        Snackbar.make(
                            binding.root,
                            "???????????? ???...",
                            Snackbar.LENGTH_INDEFINITE
                        ).show()
                    }

                    // ??????????????? ???????????? ???????????? ??????????????? ???
                    override fun onLoadCleared(placeholder: Drawable?) {}

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        super.onLoadFailed(errorDrawable)
                        Snackbar.make(
                            binding.root,
                            "???????????? ??????",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            )
    }

    private fun saveBitmapToMediaStore(bitmap: Bitmap) {
        val fileName = "${System.currentTimeMillis()}.jpg"
        val resolver = applicationContext.contentResolver
        val imageCollectionUri =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                )
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        val imageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // ????????? ???????????? ?????????(0?????? ????????? ?????????) ????????? ????????? ???????????? ????????????
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        // ????????? ?????????????????? ????????? uri ??? ???????????? ???
        val imageUri = resolver.insert(imageCollectionUri, imageDetails)

        imageUri ?: return // imageUri ??? null ??? ?????? ???????????? ?????? ??????

        resolver.openOutputStream(imageUri).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            imageDetails.clear()
            // ???????????? ?????????????????? 1????????? ????????? ?????? 0?????? ?????????
            imageDetails.put(MediaStore.Images.Media.IS_PENDING, 0)
            // imageUri ??? imageDetails ??? ????????????
            resolver.update(imageUri, imageDetails, null, null)
        }
    }

    private fun requestWriteStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val writeExternalStoragePermissionGranted =
            requestCode == REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (writeExternalStoragePermissionGranted) {
            fetchRandomPhotos()
        }
    }

    companion object {
        private const val REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 102
    }
}