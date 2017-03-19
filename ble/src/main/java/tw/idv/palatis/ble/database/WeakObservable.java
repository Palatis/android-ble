package tw.idv.palatis.ble.database;

import android.os.Handler;
import android.support.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Created by Palatis on 2017/3/14.
 */

public abstract class WeakObservable<ObserverT> {
    private final ArrayList<WeakReference<ObserverT>> mObservers = new ArrayList<>();

    public boolean registerObserver(final ObserverT observer) {
        for (int i = mObservers.size() - 1; i >= 0; --i) {
            final ObserverT oldObserver = mObservers.get(i).get();
            if (oldObserver == null) {
                mObservers.remove(i);
            } else if (oldObserver == observer)
                return false;
        }
        mObservers.add(new WeakReference<>(observer));
        return true;
    }

    public boolean unregisterObserver(final ObserverT observer) {
        for (int i = mObservers.size() - 1; i >= 0; --i) {
            final ObserverT oldObserver = mObservers.get(i).get();
            if (oldObserver == null) {
                mObservers.remove(i);
            } else if (oldObserver == observer) {
                mObservers.remove(i);
                return true;
            }
        }
        return false;
    }

    public void unregisterAllObservers() {
        mObservers.clear();
        mObservers.trimToSize();
    }

    protected void housekeeping() {
        for (int i = mObservers.size() - 1; i >= 0; --i)
            if (mObservers.get(i).get() == null)
                mObservers.remove(i);
    }

    protected void dispatch(@NonNull Handler handler, final OnDispatchCallback<ObserverT> callback) {
        housekeeping();
        handler.post(new Runnable() {
            @Override
            public void run() {
                // iterate backward, because observer may unregister itself.
                for (int i = mObservers.size() - 1; i >= 0; --i) {
                    final ObserverT observer = mObservers.get(i).get();
                    if (observer != null)
                        callback.onDispatch(observer);
                }
            }
        });
    }

    public int numObservers() {
        return mObservers.size();
    }

    protected interface OnDispatchCallback<ObserverT> {
        void onDispatch(ObserverT observer);
    }
}
