// Kelly Pitts 09098321
// Assignment 2 159.336
package com.example.kelly.assignment2;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final int NCOLS=4;
    private GridView mTiles;
    private int mNumTiles = 0;
    private Cursor mCursor;
    private ArrayList<String> mListOfImagePaths = new ArrayList<>();
    private ArrayList<Integer> mListOfOrientation = new ArrayList<>();
    private LruCache<String, Bitmap> mMemoryCache;

    // for pinch to zoom
    private ScaleGestureDetector mScaleGestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Setting up cache for images - taken from android developers
        //allocating half of the apps memory to the cache
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 2;

        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                return bitmap.getByteCount() / 1024;
            }
        };

        //Getting persmission to view photos on users device
        requestPermissions();
    }

    //adds new bitmap to cache
    public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    //gets the bitmap from cache
    public Bitmap getBitmapFromMemCache(String key) {
        return mMemoryCache.get(key);
    }

    //Function to ask user for permission to access external storage
    private void requestPermissions() {

        //only needed if sdk version is greater than 22
        if(Build.VERSION.SDK_INT > 22) {
            if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                //if permission has been granted to read external storage then start the activity
                init();
            } else {
                //else request the permission to read external storage
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            }
        } else {
            //if sdk version is 22 or less we do not need permission to read external storage
            init();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == 1) {
            if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                init();
            }
        }
    }

    //We can initialize the program now that we have permission
    private void init() {
        // set the number of columns in the grid
        mTiles = (GridView) findViewById(R.id.gridview);
        mTiles.setNumColumns(NCOLS);
        // and the adapter for tile data
        final TileAdapter mTileAdapter = new TileAdapter();
        mTiles.setAdapter(mTileAdapter);
        // when a tile is clicked
        mTiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int v, long l) {
                //getting the first view that is visible on screen
                View layout = mTiles.getChildAt(v - mTiles.getFirstVisiblePosition());
                final ImageView tile;
                if (layout != null) {
                    tile = (ImageView) layout.findViewById(R.id.tilebtn);
                } else {
                    return;
                }
                if((Integer)tile.getTag() == v) {
                    //open new activity here that shows a full image with a higher resolution
                    Intent intent = new Intent(MainActivity.this, FullScreenActivity.class);
                    intent.putExtra("Image", mListOfImagePaths.get(v));
                    intent.putExtra("Orientation", mListOfOrientation.get(v));
                    startActivity(intent);
                }
            }
        });

        //taken from Martins demo code
        mScaleGestureDetector = new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            // must be a float so it knows if we are half way between integer values
            private float mCols = NCOLS;
            // not used
            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
            }
            // nut used
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
            }
            // change the columns if necessary
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                mCols = mCols/ detector.getScaleFactor();
                if(mCols < 1)
                    mCols = 1;
                if(mCols > 8)
                    mCols = 8;
                mTiles.setNumColumns((int)mCols);
                // recalculate the tile heights
                for(int i = 0; i < mTiles.getChildCount(); i++) {
                    if (mTiles.getChildAt(i) != null) {
                        mTiles.getChildAt(i).setMinimumHeight(mTiles.getWidth() / (int)(mCols));
                    }
                }
                // make sure it's redrawn
                mTiles.invalidate();
                return true;
            }
        });

        // call the ScaleGestureDetector when the view is touched
        mTiles.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                mScaleGestureDetector.onTouchEvent(motionEvent);
                return false;
            }
        });

        //Getting the paths as a string for all the images
        getAllShownImagesPath(this);
    }

    // creates a mCursor, goes through all images in the external storage and adds the paths and the
    // orientation of each image to an arraylist
    public void getAllShownImagesPath(Activity activity) {

        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        mCursor = activity.getContentResolver().query(uri, null, null, null,
                MediaStore.MediaColumns.DATE_ADDED + " DESC");

        //creating a runnable background thread to go through the external storage
        //finding all the images
        new Thread(new Runnable() {
            @Override
            public void run() {

                mCursor.moveToFirst();
                for (int i = 0; i < mCursor.getCount(); i++) {
                    mCursor.moveToPosition(i);
                    mListOfOrientation.add(mCursor.getInt(mCursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION)));
                    mListOfImagePaths.add(mCursor.getString(mCursor.getColumnIndex(MediaStore.Images.Media.DATA)));
                }
                mCursor.close();
            }
        }).start();
        mNumTiles = mCursor.getCount();
    }

    //taken from stack overflow
    //determines the orientation of the bitmap and returns the rotated bitmap.
    public static Bitmap orientation(Bitmap b, float rotate) {
        Matrix mat = new Matrix();
        mat.postRotate(rotate);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), mat, true);
    }

    // gets view data - taken and modified from Martins demo code
    private class TileAdapter extends BaseAdapter {

        // how many tiles
        @Override
        public int getCount() {
            return mNumTiles;
        }
        // not used
        @Override
        public Object getItem(int i) {
            return null;
        }
        // not used
        @Override
        public long getItemId(int i) {
            return i;
        }

        // populate a view
        @Override
        public View getView(int i, View convertView, ViewGroup viewGroup) {

            ImageView image;
            if (convertView == null) {
                // if it's not recycled, inflate it from xml
                convertView = getLayoutInflater().inflate(R.layout.tile, null);
                // convertview will be a LinearLayout
            }
            // set size to be square
            convertView.setMinimumHeight(mTiles.getWidth() /  mTiles.getNumColumns());
            // get the imageview in this view
            image = (ImageView) convertView.findViewById(R.id.tilebtn);
            image.setTag(i);

            try {
                //for each tile, gets the bitmap from the cache
                //checks if there is an image stored in cache and displays it otherwise load the image from storage
                //and store in the cache

                final Bitmap bitmap = getBitmapFromMemCache(mListOfImagePaths.get(i));

                if (bitmap != null) {
                    Bitmap thumbnail = ThumbnailUtils.extractThumbnail(bitmap, 100, 100);
                    image.setImageBitmap(Bitmap.createScaledBitmap(bitmap, thumbnail.getWidth(), thumbnail.getHeight(), false));

                } else {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.outHeight = 50;
                    options.outWidth = 50;
                    Bitmap b = BitmapFactory.decodeResource(getResources(), R.id.tilebtn, options);

                    image.setImageBitmap(b);

                    // Start a new async task to load images into cache in the background
                    BitmapWorkerTask task = new BitmapWorkerTask(i, image);
                    task.execute(mListOfImagePaths.get(i));
                }
            } catch (Exception e) {
                Log.e("MainActivity, GetView()", e.getMessage());
            }
            return convertView;
        }
    }

    //Async task that loads images into cache in the background
    private class BitmapWorkerTask extends AsyncTask<String, Void, Integer> {

        private int mPos;
        private ImageView mView;
        private Bitmap mBitmap;

        BitmapWorkerTask(int pos, ImageView v) {
            mPos = pos;
            mView = v;
        }

        @Override
        protected Integer doInBackground(String... params) {
            String filePath = params[0];
            mBitmap = getBitmapFromMemCache(filePath);
            if(mBitmap != null) {
                return 1;
            }

            //giving options to the bitmap to display small thumbnails at a low resolution
            mBitmap = decodeBitmapFromFile(filePath, 100, 100);
            mBitmap = ThumbnailUtils.extractThumbnail(mBitmap, 100, 100);
            mBitmap = orientation(mBitmap, mListOfOrientation.get(mPos));
            mBitmap = Bitmap.createScaledBitmap(mBitmap, mBitmap.getWidth(), mBitmap.getHeight(), false);
            //only add the bitmap to cache if it exist
            if(mBitmap != null) {
                addBitmapToMemoryCache(filePath, mBitmap);
                return 1;
            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);

            //displaying the image after it has been loaded
            if((Integer) mView.getTag() == mPos) {
                if (result == 1) {
                    mView.setImageBitmap(mBitmap);
                }
            }
        }
    }

    //Taken from Android Developers website to produce a low resolution image
    public static Bitmap decodeBitmapFromFile(String fp, int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        BitmapFactory.Options options = new BitmapFactory.Options();
        // Calculate inSampleSize
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(fp, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(fp, options);
    }

    //Taken from Android Developers website
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}