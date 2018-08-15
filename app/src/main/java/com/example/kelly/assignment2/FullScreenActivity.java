// Kelly Pitts 09098321
// Assignment 2 159.336
package com.example.kelly.assignment2;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;

public class FullScreenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_screen);

        ImageView image = (ImageView) findViewById(R.id.fullImage);

        Bitmap myBitmap;
        //Getting the image filepath and orientation from the main activity
        String filePath = getIntent().getStringExtra("Image");
        int orientation = getIntent().getIntExtra("Orientation", -1);
        //checking the values passed are correct
        if ((filePath != null) && (orientation != -1)) {
            //Displaying a full sized higher resolution image to the screen
            myBitmap = MainActivity.decodeBitmapFromFile(filePath, 250, 250);
            myBitmap = MainActivity.orientation(myBitmap, orientation);
            myBitmap = Bitmap.createScaledBitmap(myBitmap, myBitmap.getWidth(), myBitmap.getHeight(), false);
            image.setImageBitmap(myBitmap);
        }
    }
}