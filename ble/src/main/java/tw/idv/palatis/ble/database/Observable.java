package tw.idv.palatis.ble.database;

import android.os.Handler;
import android.support.annotation.Nullable;

import java.util.ArrayList;

/**
 * Created by Palatis on 2017/3/14.
 */

public abstract class Observable<ObserverT> {
    private final Handler mHandler;

    private final ArrayList<ObserverT> mObservers = new ArrayList<>();

    public Observable(@Nullable final Handler handler) {
        mHandler = handler;
    }

    /**
     * Register an {@link ObserverT} to this {@link Observable}.
     * Must unregister the {@link ObserverT} after it's not needed anymore, otherwise memory-leak.
     *
     * @param observer the observer
     * @return true if registered, false otherwise.
     * @see #unregisterObserver(ObserverT)
     * @see #unregisterAllObservers()
     */
    public boolean registerObserver(final ObserverT observer) {
        return !mObservers.contains(observer) && mObservers.add(observer);
    }

    public boolean unregisterObserver(final ObserverT observer) {
        return mObservers.contains(observer) && mObservers.remove(observer);
    }

    public void unregisterAllObservers() {
        mObservers.clear();
        mObservers.trimToSize();
    }

    protected void dispatch(final OnDispatchCallback<ObserverT> callback) {
        if (mHandler == null) {
            // iterate backward, because observer may unregister itself.
            for (int i = mObservers.size() - 1; i >= 0; --i)
                callback.onDispatch(mObservers.get(i));
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // iterate backward, because observer may unregister itself.
                    for (int i = mObservers.size() - 1; i >= 0; --i)
                        callback.onDispatch(mObservers.get(i));
                }
            });
        }
    }

    public int numObservers() {
        return mObservers.size();
    }

    protected interface OnDispatchCallback<ObserverT> {
        void onDispatch(ObserverT observer);
    }
}
