package com.rayworks.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;

import com.tbruyelle.rxpermissions.RxPermissions;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import org.apache.commons.compress.utils.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import rx.functions.Action1;
import timber.log.Timber;

import static android.app.Activity.RESULT_OK;

/**
 * Created by Sean on 4/5/17.
 */

public final class ImageCropHelper {

    public final static String KEY_MY_AVATAR = "myAvatar.jpg";
    private final static int TYPE_CAMERA = 0;
    private final static int TYPE_LOCAL = 1;
    private final static int REQUEST_CODE_CAMERA = 2;
    private final static int REQUEST_CODE_CROP = REQUEST_CODE_CAMERA + 1;
    private final static int REQUEST_CODE_LOCAL = REQUEST_CODE_CROP + 1;
    public static final String AVATAR = "avatar";

    private Uri avatarUri;
    private final RxPermissions rxPermissions;

    public interface ImageCroppedListener {
        void onBitmapCropped(Bitmap bitmap);
    }

    private ImageCroppedListener imageCroppedListener;

    public ImageCropHelper setImageCroppedListener(ImageCroppedListener listener) {
        this.imageCroppedListener = listener;
        return this;
    }

    private final FragmentActivity context;

    public ImageCropHelper(FragmentActivity context) {
        this.context = context;
        rxPermissions = new RxPermissions(context);

        init();
    }

    private void init() {
        File dir = getAvatarFileFolder();
        File avatarTempFile = new File(dir, KEY_MY_AVATAR);

        String packageName = context.getPackageName();

        // http://www.jianshu.com/p/56b9fb319310
        // Keep compatible with Nougat 7.0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            avatarUri = Uri.fromFile(avatarTempFile);
        } else {
            avatarUri = FileProvider.getUriForFile(context, packageName, avatarTempFile);
        }
        Timber.i(">>> Uri avatar %s", avatarUri);
    }

    @NonNull
    private File getAvatarFileFolder() {
        File dir = new File(context.getFilesDir(), AVATAR);
        if (!dir.exists()) {
            dir.mkdir();
        }
        return dir;
    }

    /***
     * Provides the options to select the target image in the form of Dialog.
     */
    public void showDialog() {
        final String[] methods = new String[]{"拍照", "从相册选择"};
        AlertDialog.Builder adb = new AlertDialog.Builder(context);

        adb.setPositiveButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        adb.setItems(methods, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        switch (which) {
                            case TYPE_CAMERA:
                                rxPermissions.request(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                        .subscribe(new Action1<Boolean>() {
                                            @Override
                                            public void call(Boolean aBoolean) {
                                                if (aBoolean) {
                                                    startCameraIntent();
                                                }
                                            }
                                        });
                                break;
                            case TYPE_LOCAL:
                                rxPermissions.request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                        .subscribe(new Action1<Boolean>() {
                                            @Override
                                            public void call(Boolean aBoolean) {
                                                if (aBoolean) {
                                                    startPickingImageIntent();
                                                }
                                            }
                                        });
                                break;
                        }
                    }
                }
        ).show();
    }

    private void startCameraIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        intent.putExtra(MediaStore.EXTRA_OUTPUT, avatarUri);
        context.startActivityForResult(intent, REQUEST_CODE_CAMERA);
    }

    private void startPickingImageIntent() {
        Intent i = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        context.startActivityForResult(i, REQUEST_CODE_LOCAL);
    }

    /***
     * Processes the cropped image result.
     * This should be invoked as a delegate inside {@link Fragment#onActivityResult(int, int, Intent)}.
     *
     * @param requestCode
     * @param resultCode
     * @param data
     * @param displayWidth  image actual width for displaying
     * @param displayHeight image actual height for displaying
     */
    public boolean processActivityResult(int requestCode, int resultCode, Intent data,
                                         int displayWidth, int displayHeight) {
        if (resultCode == RESULT_OK) {

            switch (requestCode) {
                case REQUEST_CODE_CAMERA:
                    if (data != null) {
                        Timber.d(">>> Camera intent : %s", data);
                    }
                    // respect the output uri setting
                    CropImage.activity(avatarUri)
                            .setRequestedSize(displayWidth, displayHeight)
                            .setGuidelines(CropImageView.Guidelines.ON)
                            .start(context);
                    return true;

                case REQUEST_CODE_LOCAL:
                    CropImage.activity(data.getData())
                            .setRequestedSize(displayWidth, displayHeight)
                            .setGuidelines(CropImageView.Guidelines.ON)
                            .start(context);
                    return true;

                case CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE:
                    CropImage.ActivityResult result = CropImage.getActivityResult(data);
                    Uri urlCropped = result.getUri();
                    onHandleCroppedImage(urlCropped);
                    return true;
            }
        }

        return false;
    }

    private void onHandleCroppedImage(Uri uri) {
        // recved the updated uri
        InputStream imageStream = null;
        FileOutputStream output = null;
        try {
            File file = getImageTempFile();
            output = new FileOutputStream(file);

            imageStream = context.getContentResolver().openInputStream(uri);
            IOUtils.copy(imageStream, output);
            output.flush();

            persistAvatarLocally(file);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(imageStream);
            IOUtils.closeQuietly(output);
        }
    }

    @NonNull
    private File getImageTempFile() {
        return new File(getAvatarFileFolder(), "tmp");
    }

    private void persistAvatarLocally(final File file) {

        if (imageCroppedListener != null) {
            imageCroppedListener.onBitmapCropped(BitmapFactory.decodeFile(file.getAbsolutePath()));
        }
    }
}
