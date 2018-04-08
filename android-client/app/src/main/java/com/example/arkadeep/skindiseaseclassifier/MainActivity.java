package com.example.arkadeep.skindiseaseclassifier;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;


import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.UUID;

/**
 * Created by arkadeep on 4/12/16.
 */
public class MainActivity extends FragmentActivity {
    Button _cntButton2;
    String sendingId;
    TextView from;
    TextView to;
    TextView date;
    TextView weight;
    TextView categories;
    TextView photo;
    TextView desc;
    JSONObject detail_user;
    private int REQUEST_CAMERA = 0, SELECT_FILE = 1;
    private int INP_FRM = 2, INP_TO = 3;
    private Button btnSelect;
    private ImageView ivImage;
    private EditText inp_from;
    private EditText EditWt;
    private String Weight_curr;
    private String Desc_Curr;
    private EditText inp_to;
    private String userChoosenTask;
    private String fromData;
    private String latlngFrom;
    private String latlngTo;
    private String to_data;
    private boolean runClassifier = false;
    private boolean checkedPermissions = false;
    private TextView textView;
    private ImageClassifier classifier;
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mSendersPhotosReference;
    private EditText EditDesc;
    private ViewGroup mContentRoot;
    private ProgressDialog pd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = (TextView) findViewById(R.id.text);
        Typeface custom_font = Typeface.createFromAsset(getAssets(), "fonts/Gotham-Book.otf");
        _cntButton2 = (Button) findViewById(R.id.btn_continue2);
        try {
            classifier = new ImageClassifier(MainActivity.this);
        } catch (IOException e) {
            Log.e("MainActivity", "Failed to initialize an image classifier.");
        }
        // mChatPhotosReference = mFirebaseStorage.getReference().child("sender_photos");
        mFirebaseStorage = FirebaseStorage.getInstance();
        String uuid = UUID.randomUUID().toString();
        mSendersPhotosReference = mFirebaseStorage.getReference().child("sender_photos").child(uuid);
        ivImage = (ImageView) (findViewById(R.id.photo_take));
        mContentRoot = (ViewGroup) findViewById(android.R.id.content);

        ivImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectImage();
            }
        });
        _cntButton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent activ = new Intent(MainActivity.this, CameraActivity.class);
                startActivity(activ);
            }
        });
    }


    private void showToast(final String text) {
        final Activity activity = MainActivity.this;
        if (activity != null) {
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            textView.setText(text);
                        }
                    });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case Utility.MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (userChoosenTask.equals("Take Photo"))
                        cameraIntent();
                    else if (userChoosenTask.equals("Choose from Library"))
                        galleryIntent();
                } else {
                    //code for deny
                }
                break;
        }
    }

    /**
     * Classifies a frame from the preview stream.
     */
    private void classifyFrame(Bitmap bitmap) {
        if (classifier == null) {
            showToast("Uninitialized Classifier or invalid context.");
            return;
        }
        String textToShow = classifier.classifyFrame(bitmap);
        Log.d("Answer", textToShow);
        bitmap.recycle();
        showToast(textToShow);
    }

    private void selectImage() {
        final CharSequence[] items = {"Take Photo", "Choose from Library",
                "Cancel"};

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Add Photo!");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                boolean result = Utility.checkPermission(MainActivity.this);

                if (items[item].equals("Take Photo")) {
                    userChoosenTask = "Take Photo";
                    if (result)
                        cameraIntent();

                } else if (items[item].equals("Choose from Library")) {
                    userChoosenTask = "Choose from Library";
                    if (result)
                        galleryIntent();

                } else if (items[item].equals("Cancel")) {
                    dialog.dismiss();
                }
            }
        });
        builder.show();
    }

    private void galleryIntent() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);//
        startActivityForResult(Intent.createChooser(intent, "Select File"), SELECT_FILE);
    }

    private void cameraIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, REQUEST_CAMERA);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == SELECT_FILE)
                onSelectFromGalleryResult(data);
            else if (requestCode == REQUEST_CAMERA)
                onCaptureImageResult(data);
        }

    }

    private void onCaptureImageResult(Intent data) {
        Bitmap thumbnail = (Bitmap) data.getExtras().get("data");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, bytes);

        File destination = new File(Environment.getExternalStorageDirectory(),
                System.currentTimeMillis() + ".jpg");
        FileOutputStream fo;
        try {
            destination.createNewFile();
            fo = new FileOutputStream(destination);
            fo.write(bytes.toByteArray());
            fo.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ivImage.setImageBitmap(thumbnail);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(
                thumbnail, ImageClassifier.DIM_IMG_SIZE_X, ImageClassifier.DIM_IMG_SIZE_Y, false);
        classifyFrame(resizedBitmap);
    }

    @SuppressWarnings("deprecation")
    private void onSelectFromGalleryResult(Intent data) {
        Bitmap bm = null;
        if (data != null) {
            try {
                bm = MediaStore.Images.Media.getBitmap(getApplicationContext().getContentResolver(), data.getData());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        ivImage.setImageBitmap(bm);
        assert bm != null;
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(
                bm, ImageClassifier.DIM_IMG_SIZE_X, ImageClassifier.DIM_IMG_SIZE_Y, false);
        classifyFrame(resizedBitmap);
    }


}
