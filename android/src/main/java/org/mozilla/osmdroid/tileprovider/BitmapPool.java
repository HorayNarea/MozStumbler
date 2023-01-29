package org.mozilla.osmdroid.tileprovider;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.LinkedList;

/*
 * @TODO: vng 
 * This is crazy. We don't know how much heap or native memory is on
 * the device, so we don't know how many Bitmap instances we can
 * reliably hold on to. 
 *
 * mPool is an unbounded LinkedList and everytime the LRUMapTileCache
 * evicts a tile, it goes back into the pool.
 */
public class BitmapPool {
    private static BitmapPool sInstance;
    final LinkedList<Bitmap> mPool = new LinkedList<Bitmap>();

    public static BitmapPool getInstance() {
        if (sInstance == null) {
            sInstance = new BitmapPool();
        }

        return sInstance;
    }

    public void returnDrawableToPool(ReusableBitmapDrawable drawable) {
        Bitmap b = drawable.tryRecycle();
        if (b != null && b.isMutable()) {
            synchronized (mPool) {
                mPool.addLast(b);
            }
        }
    }

    public void applyReusableOptions(final BitmapFactory.Options aBitmapOptions) {
        aBitmapOptions.inBitmap = obtainBitmapFromPool();
        aBitmapOptions.inSampleSize = 1;
        aBitmapOptions.inMutable = true;
    }

    public Bitmap obtainBitmapFromPool() {
        synchronized (mPool) {
            if (mPool.isEmpty()) {
                return null;
            } else {
                final Bitmap bitmap = mPool.removeFirst();
                if (bitmap.isRecycled()) {
                    return obtainBitmapFromPool(); // recurse
                } else {
                    return bitmap;
                }
            }
        }
    }

    public Bitmap obtainSizedBitmapFromPool(final int aWidth, final int aHeight) {
        synchronized (mPool) {
            if (mPool.isEmpty()) {
                return null;
            } else {
                for (final Bitmap bitmap : mPool) {
                    if (bitmap.isRecycled()) {
                        mPool.remove(bitmap);
                        return obtainSizedBitmapFromPool(aWidth, aHeight); // recurse to prevent ConcurrentModificationException
                    } else if (bitmap.getWidth() == aWidth && bitmap.getHeight() == aHeight) {
                        mPool.remove(bitmap);
                        return bitmap;
                    }
                }
            }
        }

        return null;
    }

    public void clearBitmapPool() {
        synchronized (sInstance.mPool) {
            while (!sInstance.mPool.isEmpty()) {
                Bitmap bitmap = sInstance.mPool.remove();
                bitmap.recycle();
            }
        }
    }
}
